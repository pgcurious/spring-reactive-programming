# Chapter 14: Transactions in Reactive World

> "The art of progress is to preserve order amid change and to preserve change amid order."
> — Alfred North Whitehead

## Introduction

Transactions are the backbone of data integrity. They ensure that a series of operations either all succeed or all fail together—the famous ACID guarantees. But in the reactive world, transactions become... complicated.

The fundamental challenge: **traditional transactions rely on thread-local storage**, but reactive streams can hop between threads at any moment. How do you maintain transaction context when the code executing your operations might run on different threads at different points in time?

This chapter tackles this challenge head-on. We'll explore how Spring manages transactions in a reactive context, when to use declarative versus programmatic transactions, and patterns for handling distributed operations that span multiple services or data stores.

## 14.1 The Thread-Local Problem

### How Traditional Transactions Work

In traditional Spring applications with JDBC, transaction management relies heavily on `ThreadLocal`:

```java
// Simplified view of how Spring MVC transactions work
public class TransactionSynchronizationManager {
    private static final ThreadLocal<Map<Object, Object>> resources =
        new ThreadLocal<>();
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
        new ThreadLocal<>();

    // The connection is bound to THIS thread
    public static void bindResource(Object key, Object value) {
        resources.get().put(key, value);
    }
}
```

When you annotate a method with `@Transactional`:

1. Spring opens a database connection
2. Starts a transaction on that connection
3. **Binds the connection to the current thread**
4. Your code runs (all on the same thread)
5. Spring commits or rolls back
6. Unbinds and returns the connection

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THREAD-LOCAL TRANSACTIONS (Traditional)               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1:                                                              │
│   ─────────────────────────────────────────────────────────────────▶    │
│                                                                          │
│   │ Begin TX │ Query 1 │ Query 2 │ Insert │ Commit TX │                 │
│                                                                          │
│   ThreadLocal: ┌──────────────────────────────────────────┐             │
│                │ Connection bound to Thread 1             │             │
│                └──────────────────────────────────────────┘             │
│                                                                          │
│   Same thread from start to finish - ThreadLocal works!                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Why This Breaks in Reactive

Reactive streams don't guarantee thread affinity. Operations can switch threads:

```java
userRepository.findById(id)
    .flatMap(user -> orderRepository.findByUserId(user.getId()))  // Might be different thread!
    .flatMap(order -> paymentService.process(order))              // Another thread!
    .flatMap(payment -> notificationService.send(payment))        // Yet another!
```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE EXECUTION - THREAD HOPPING                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1: ────│findById│────────────────────────────────────▶        │
│                         ╲                                                │
│   Thread 2: ─────────────│flatMap(orders)│──────────────────────▶      │
│                                          ╲                               │
│   Thread 3: ──────────────────────────────│flatMap(payment)│────▶      │
│                                                              ╲           │
│   Thread 1: ──────────────────────────────────────────────────│send│▶   │
│                                                                          │
│   ThreadLocal on Thread 1: ┌──────────────────┐                         │
│                            │ Connection A     │                         │
│                            └──────────────────┘                         │
│                                                                          │
│   But Thread 2 and Thread 3 don't have access to ThreadLocal!           │
│   Transaction context is LOST                                            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

If ThreadLocal holds your database connection, operations on Thread 2 and Thread 3 won't find it. They'll either fail or use a different connection—outside your transaction.

## 14.2 Reactor Context: The Solution

### What is Reactor Context?

Reactor provides a solution: **Context**. It's an immutable key-value store that flows through the reactive pipeline, regardless of which thread executes each operator.

```java
Mono.just("data")
    .contextWrite(Context.of("txConnection", connection))  // Add to context
    .flatMap(data -> {
        // Can access context from ANY operator, ANY thread
        return Mono.deferContextual(ctx -> {
            Connection conn = ctx.get("txConnection");
            return executeQuery(conn, data);
        });
    });
```

Context flows **upstream** (from subscription point toward the source) and is available to all operators in the chain.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTOR CONTEXT FLOW                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   source()                                                               │
│      │                                                                   │
│      ▼                                                                   │
│   map(x -> x)              Context available here ◀──┐                  │
│      │                                                │                  │
│      ▼                                                │                  │
│   flatMap(...)             Context available here ◀──┤                  │
│      │                                                │                  │
│      ▼                                                │                  │
│   filter(...)              Context available here ◀──┤                  │
│      │                                                │ Context flows    │
│      ▼                                                │ upstream         │
│   contextWrite(ctx)  ─────────────────────────────────┘                  │
│      │                                                                   │
│      ▼                                                                   │
│   subscribe()                                                            │
│                                                                          │
│   Context is immutable and thread-safe                                   │
│   Available on ANY thread executing ANY operator                         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### How Spring Uses Context for Transactions

Spring's reactive transaction management uses Reactor Context to propagate transaction state:

```java
// Simplified view of ReactiveTransactionManager
public class R2dbcTransactionManager implements ReactiveTransactionManager {

    @Override
    public Mono<ReactiveTransaction> getReactiveTransaction(TransactionDefinition definition) {
        return Mono.deferContextual(ctx -> {
            // Check if transaction already exists in context
            if (ctx.hasKey(TransactionContext.class)) {
                return Mono.just(ctx.get(TransactionContext.class));
            }
            // Start new transaction
            return startNewTransaction(definition);
        });
    }
}
```

When you use `@Transactional` on a reactive method, Spring:

1. Intercepts the method call
2. Acquires a connection and starts a transaction
3. Adds the transaction to Reactor Context via `contextWrite`
4. Your code runs with the transaction available in context
5. Spring commits or rolls back based on the result

## 14.3 Declarative Transactions with @Transactional

### Basic Usage

The familiar `@Transactional` annotation works with reactive methods:

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Mono<Order> placeOrder(CreateOrderRequest request) {
        return inventoryRepository.reserveItems(request.getItems())
            .then(paymentRepository.processPayment(request.getPayment()))
            .then(orderRepository.save(new Order(request)))
            .doOnError(e -> log.error("Order placement failed", e));
        // Transaction automatically commits on success, rolls back on error
    }
}
```

### Important: Return the Reactive Type!

A common mistake is not returning the Mono/Flux:

```java
// WRONG - transaction won't work properly!
@Transactional
public void processOrder(Long orderId) {
    orderRepository.findById(orderId)
        .flatMap(this::process)
        .subscribe();  // Fire and forget - transaction context lost!
}

// CORRECT - return the reactive type
@Transactional
public Mono<Order> processOrder(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(this::process);
    // Spring manages subscription and transaction
}
```

### Read-Only Transactions

For read operations, use read-only transactions for optimization:

```java
@Transactional(readOnly = true)
public Flux<Order> getOrderHistory(Long userId) {
    return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
}
```

### Isolation Levels

Control isolation like traditional transactions:

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public Mono<Void> transferFunds(Long fromAccount, Long toAccount, BigDecimal amount) {
    return accountRepository.withdraw(fromAccount, amount)
        .then(accountRepository.deposit(toAccount, amount));
}

@Transactional(isolation = Isolation.READ_COMMITTED)
public Flux<AccountBalance> getBalances(List<Long> accountIds) {
    return accountRepository.findAllById(accountIds);
}
```

### Propagation Behavior

Transaction propagation works similarly to traditional Spring:

```java
@Transactional(propagation = Propagation.REQUIRED)  // Default
public Mono<Order> createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .flatMap(order -> addOrderItems(order, request.getItems()));
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public Mono<AuditLog> logOrderCreation(Order order) {
    // Always creates a new transaction, suspending any existing one
    return auditRepository.save(new AuditLog(order));
}

@Transactional(propagation = Propagation.MANDATORY)
public Mono<Void> updateInventory(List<OrderItem> items) {
    // Must be called within an existing transaction
    return inventoryRepository.decrementStock(items);
}
```

## 14.4 Programmatic Transactions with TransactionalOperator

### When to Use Programmatic Transactions

Programmatic transactions give you more control:

- Fine-grained transaction boundaries within a method
- Conditional transaction logic
- Mixing transactional and non-transactional operations
- More explicit code (some teams prefer this)

### Using TransactionalOperator

```java
@Service
public class OrderService {

    private final TransactionalOperator transactionalOperator;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public OrderService(TransactionalOperator transactionalOperator,
                        OrderRepository orderRepository,
                        InventoryService inventoryService,
                        NotificationService notificationService) {
        this.transactionalOperator = transactionalOperator;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }

    public Mono<Order> placeOrder(CreateOrderRequest request) {
        // Only the database operations are transactional
        Mono<Order> transactionalPart = inventoryService.reserveItems(request.getItems())
            .then(orderRepository.save(new Order(request)))
            .as(transactionalOperator::transactional);  // Wrap in transaction

        // Notification is outside the transaction
        return transactionalPart
            .flatMap(order -> notificationService.sendConfirmation(order)
                .thenReturn(order));
    }
}
```

### The `transactional()` Method

```java
// Method 1: Use as() with transactional
Mono<Order> result = orderRepository.save(order)
    .as(transactionalOperator::transactional);

// Method 2: Use execute() for more control
Mono<Order> result = transactionalOperator.execute(status -> {
    // status allows you to setRollbackOnly() if needed
    return orderRepository.save(order);
}).single();  // execute() returns Flux, use single() if expecting one result
```

### Conditional Transactions

```java
public Mono<Order> processOrder(Order order) {
    Mono<Order> operation = validateOrder(order)
        .then(inventoryService.checkStock(order))
        .then(orderRepository.save(order));

    if (order.getTotalAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
        // High-value orders: use SERIALIZABLE isolation
        return operation.as(tx -> transactionalOperator.execute(
            new DefaultTransactionDefinition(TransactionDefinition.ISOLATION_SERIALIZABLE),
            status -> tx
        ).single());
    } else {
        // Normal orders: default isolation
        return operation.as(transactionalOperator::transactional);
    }
}
```

### Manual Rollback

```java
public Mono<Order> processOrderWithValidation(Order order) {
    return transactionalOperator.execute(status -> {
        return orderRepository.save(order)
            .flatMap(saved -> {
                if (saved.getTotalAmount().compareTo(MAX_ORDER_AMOUNT) > 0) {
                    status.setRollbackOnly();  // Mark for rollback
                    return Mono.error(new OrderTooLargeException(saved.getId()));
                }
                return Mono.just(saved);
            });
    }).single();
}
```

## 14.5 Transaction Boundaries and Gotchas

### Understanding Where Transactions Start and End

```java
@Service
public class OrderService {

    @Transactional
    public Mono<Order> createOrder(OrderRequest request) {
        // Transaction starts when this Mono is subscribed

        return orderRepository.save(new Order(request))
            .flatMap(order -> itemRepository.saveAll(order.getItems())
                .collectList()
                .thenReturn(order))
            .doOnSuccess(order -> log.info("Order created: {}", order.getId()));

        // Transaction commits when Mono completes successfully
        // Transaction rolls back when Mono errors
    }
}
```

### Gotcha #1: Operations After Error Recovery

```java
@Transactional
public Mono<Order> createOrderWithFallback(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .flatMap(order -> paymentService.process(order.getPayment())
            .onErrorResume(e -> {
                // Payment failed, but we caught the error
                // The transaction will still COMMIT!
                log.warn("Payment failed, creating pending order");
                order.setStatus(OrderStatus.PENDING_PAYMENT);
                return orderRepository.save(order);
            }));
}
```

If you want to rollback on payment failure:

```java
@Transactional
public Mono<Order> createOrderWithRollback(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .flatMap(order -> paymentService.process(order.getPayment())
            .onErrorMap(e -> {
                // Transform to a different error - still causes rollback
                return new OrderCreationException("Payment failed", e);
            }));
}
```

### Gotcha #2: Nested Reactive Chains

```java
@Transactional
public Mono<Order> processOrder(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(order -> {
            // This inner chain IS part of the transaction
            return itemRepository.findByOrderId(orderId)
                .flatMap(item -> inventoryService.reserve(item))
                .then(Mono.just(order));
        });
}
```

But be careful with `subscribeOn`:

```java
@Transactional
public Mono<Order> processOrderBroken(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(order -> {
            // DANGER: subscribeOn changes the subscription context
            return heavyComputation(order)
                .subscribeOn(Schedulers.boundedElastic())  // Might lose TX context!
                .then(orderRepository.save(order));
        });
}
```

### Gotcha #3: Fire and Forget Within Transactions

```java
@Transactional
public Mono<Order> createOrderBroken(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .doOnSuccess(order -> {
            // WRONG: This subscribes independently, outside TX context
            auditService.logOrderCreation(order).subscribe();
        });
}

@Transactional
public Mono<Order> createOrderCorrect(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .flatMap(order -> {
            // CORRECT: Part of the same reactive chain
            return auditService.logOrderCreation(order)
                .thenReturn(order);
        });
}
```

## 14.6 Error Handling and Rollback

### Automatic Rollback on Errors

By default, any error signal causes rollback:

```java
@Transactional
public Mono<Order> processOrder(OrderRequest request) {
    return validateRequest(request)               // Might error
        .then(inventoryService.reserve(request))  // Might error
        .then(paymentService.charge(request))     // Might error
        .then(orderRepository.save(request));     // Might error
    // ANY error in the chain causes rollback of ALL operations
}
```

### Controlling Rollback

```java
// Don't rollback for specific exceptions
@Transactional(noRollbackFor = {ValidationException.class, DuplicateOrderException.class})
public Mono<Order> createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}

// Only rollback for specific exceptions
@Transactional(rollbackFor = {PaymentException.class, InventoryException.class})
public Mono<Order> processPayment(Order order) {
    return paymentService.process(order.getPayment());
}
```

### Handling Partial Failures

```java
@Service
public class BulkOrderService {

    private final TransactionalOperator transactionalOperator;

    public Flux<OrderResult> processBulkOrders(List<OrderRequest> requests) {
        return Flux.fromIterable(requests)
            .flatMap(request ->
                // Each order in its own transaction
                processOrderTransactionally(request)
                    .map(order -> OrderResult.success(order))
                    .onErrorResume(e -> Mono.just(OrderResult.failure(request, e)))
            );
    }

    private Mono<Order> processOrderTransactionally(OrderRequest request) {
        return orderRepository.save(new Order(request))
            .flatMap(this::processPayment)
            .as(transactionalOperator::transactional);
    }
}
```

### Saga Pattern for Complex Workflows

When you need compensation instead of rollback:

```java
@Service
public class OrderSaga {

    public Mono<Order> executeOrderSaga(OrderRequest request) {
        return reserveInventory(request)
            .flatMap(reservation ->
                processPayment(request)
                    .onErrorResume(e ->
                        // Compensate: release inventory
                        releaseInventory(reservation)
                            .then(Mono.error(e))
                    )
            )
            .flatMap(payment ->
                createOrder(request, payment)
                    .onErrorResume(e ->
                        // Compensate: refund payment and release inventory
                        refundPayment(payment)
                            .then(Mono.error(e))
                    )
            )
            .flatMap(order ->
                sendConfirmation(order)
                    .onErrorResume(e -> {
                        // Non-critical failure - log but don't rollback
                        log.warn("Failed to send confirmation", e);
                        return Mono.just(order);
                    })
            );
    }
}
```

## 14.7 Testing Reactive Transactions

### Testing with @DataR2dbcTest

```java
@DataR2dbcTest
@Testcontainers
class OrderServiceTransactionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @Test
    void transactionShouldRollbackOnError() {
        Order order = new Order("test-order");

        Mono<Order> operation = orderRepository.save(order)
            .flatMap(saved -> Mono.<Order>error(new RuntimeException("Simulated error")))
            .as(transactionalOperator::transactional);

        StepVerifier.create(operation)
            .expectError(RuntimeException.class)
            .verify();

        // Verify rollback: order should not exist
        StepVerifier.create(orderRepository.findById(order.getId()))
            .expectNextCount(0)
            .verifyComplete();
    }

    @Test
    void transactionShouldCommitOnSuccess() {
        Order order = new Order("test-order");

        Mono<Order> operation = orderRepository.save(order)
            .flatMap(saved -> {
                saved.setStatus("CONFIRMED");
                return orderRepository.save(saved);
            })
            .as(transactionalOperator::transactional);

        StepVerifier.create(operation)
            .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo("CONFIRMED"))
            .verifyComplete();

        // Verify commit: order should exist with updated status
        StepVerifier.create(orderRepository.findById(order.getId()))
            .assertNext(found -> assertThat(found.getStatus()).isEqualTo("CONFIRMED"))
            .verifyComplete();
    }
}
```

### Testing @Transactional Methods

```java
@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createOrderShouldBeTransactional() {
        CreateOrderRequest request = new CreateOrderRequest(/* ... */);

        // If any step fails, the entire operation should rollback
        StepVerifier.create(orderService.createOrder(request))
            .assertNext(order -> {
                assertThat(order.getId()).isNotNull();
                assertThat(order.getStatus()).isEqualTo("CREATED");
            })
            .verifyComplete();

        // Verify all related data was persisted
        StepVerifier.create(orderRepository.findById(/* orderId */))
            .assertNext(found -> assertThat(found).isNotNull())
            .verifyComplete();
    }
}
```

## 14.8 Best Practices

### 1. Keep Transactions Short

```java
// BAD: Long-running transaction with external calls
@Transactional
public Mono<Order> processOrder(OrderRequest request) {
    return orderRepository.save(new Order(request))
        .flatMap(order -> externalPaymentGateway.process(order.getPayment()))  // Slow!
        .flatMap(payment -> externalShippingService.schedule(order))           // Slow!
        .flatMap(shipping -> orderRepository.updateWithShipping(order, shipping));
}

// GOOD: Only database operations in transaction
public Mono<Order> processOrder(OrderRequest request) {
    // Step 1: Create order (transactional)
    return createOrderTransactionally(request)
        // Step 2: External calls (non-transactional)
        .flatMap(order -> externalPaymentGateway.process(order.getPayment())
            .map(payment -> order.withPayment(payment)))
        .flatMap(order -> externalShippingService.schedule(order)
            .map(shipping -> order.withShipping(shipping)))
        // Step 3: Update order (separate transaction)
        .flatMap(this::updateOrderTransactionally);
}
```

### 2. Use Read-Only Transactions for Queries

```java
@Transactional(readOnly = true)
public Flux<Order> getOrderHistory(Long userId) {
    return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
}
```

### 3. Be Explicit About Transaction Boundaries

```java
@Service
public class OrderService {

    private final TransactionalOperator txOperator;

    // Clear method name indicates transactional behavior
    public Mono<Order> createOrderWithTransaction(OrderRequest request) {
        return validateRequest(request)  // Not transactional
            .then(createOrderTransactionally(request))  // Transactional
            .flatMap(this::sendNotification);  // Not transactional
    }

    private Mono<Order> createOrderTransactionally(OrderRequest request) {
        return orderRepository.save(new Order(request))
            .flatMap(order -> itemRepository.saveAll(order.getItems())
                .then(Mono.just(order)))
            .as(txOperator::transactional);
    }
}
```

### 4. Handle Distributed Transactions Carefully

```java
// For operations spanning multiple services, use eventual consistency
@Service
public class DistributedOrderService {

    public Mono<Order> placeOrder(OrderRequest request) {
        return localOrderCreation(request)  // Local TX
            .flatMap(order ->
                inventoryServiceClient.reserve(order)  // Remote call
                    .onErrorResume(e -> compensateOrderCreation(order))
            )
            .flatMap(order ->
                paymentServiceClient.charge(order)  // Remote call
                    .onErrorResume(e -> compensateInventoryReservation(order))
            );
    }
}
```

## Summary

Transactions in the reactive world require understanding how Reactor Context replaces ThreadLocal for propagating transaction state. Key takeaways:

| Aspect | Traditional | Reactive |
|--------|-------------|----------|
| **Context Propagation** | ThreadLocal | Reactor Context |
| **Declarative TX** | `@Transactional` | `@Transactional` (same!) |
| **Programmatic TX** | `TransactionTemplate` | `TransactionalOperator` |
| **Rollback Trigger** | Exception thrown | Error signal |
| **Commit Trigger** | Method returns | Mono/Flux completes |

**Remember:**

1. **Always return the reactive type** from `@Transactional` methods. If you subscribe internally, the transaction context is lost.

2. **Don't use `subscribeOn` carelessly** within transactions. It can move execution to a context without the transaction.

3. **Keep transactions short**. Long transactions with external calls can cause connection pool exhaustion and deadlocks.

4. **Test your transaction boundaries**. Verify that rollback and commit work as expected with `StepVerifier`.

5. **Consider eventual consistency** for distributed operations. Traditional distributed transactions (2PC) don't work well in reactive/microservice architectures.

Reactive transactions are not fundamentally different from traditional transactions—they just use a different mechanism for context propagation. Once you understand how Reactor Context flows through your reactive chains, transaction management becomes straightforward.
