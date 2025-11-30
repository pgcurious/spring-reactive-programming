# First Principles: Deriving Reactive Transaction Management

## Forget That TransactionalOperator Exists

You've built a reactive application with R2DBC. Your database operations are non-blocking. Life is good. But now you need transactions—multiple operations that must succeed or fail together.

How would you implement transactions in a reactive system? Let's derive the solution from first principles.

## Step 1: What Is a Transaction, Really?

At its core, a database transaction is:

1. **A boundary** around a set of operations
2. **A promise** that either all operations succeed (commit) or none take effect (rollback)
3. **Isolation** from other concurrent transactions

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION: THE CONCEPT                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   BEGIN TRANSACTION                                                      │
│   ┌────────────────────────────────────────────────────────────────┐    │
│   │                                                                 │    │
│   │   INSERT INTO orders (user_id, total) VALUES (1, 100.00);      │    │
│   │   UPDATE inventory SET quantity = quantity - 5 WHERE id = 42;   │    │
│   │   INSERT INTO audit_log (action) VALUES ('order_placed');       │    │
│   │                                                                 │    │
│   │   All three succeed → COMMIT (all changes permanent)            │    │
│   │   Any one fails    → ROLLBACK (no changes applied)              │    │
│   │                                                                 │    │
│   └────────────────────────────────────────────────────────────────┘    │
│   END TRANSACTION                                                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: How Databases Implement Transactions

When you start a transaction, the database:

1. Assigns a **transaction ID** to your session
2. All operations on that **connection** are part of the transaction
3. The connection maintains the transaction state until commit/rollback

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONNECTION = TRANSACTION SCOPE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Connection A                        Connection B                       │
│   ┌─────────────────┐                 ┌─────────────────┐               │
│   │ Transaction 1   │                 │ Transaction 2   │               │
│   │                 │                 │                 │               │
│   │ INSERT ...      │                 │ UPDATE ...      │               │
│   │ UPDATE ...      │                 │ DELETE ...      │               │
│   │                 │                 │                 │               │
│   │ COMMIT          │                 │ ROLLBACK        │               │
│   └─────────────────┘                 └─────────────────┘               │
│                                                                          │
│   Key insight: The CONNECTION defines the transaction boundary           │
│   Operations on the same connection = same transaction                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: The Framework's Job

A transaction framework (like Spring) must:

1. **Acquire a connection** before your transactional code runs
2. **Start a transaction** on that connection
3. **Ensure ALL your operations use that same connection**
4. **Commit or rollback** based on success or failure
5. **Release the connection** back to the pool

The critical challenge: **ensuring all operations use the same connection**.

## Step 4: The Traditional Solution - ThreadLocal

In blocking code, there's a simple solution: bind the connection to the current thread.

```java
// Pseudo-code of traditional transaction management
public class TransactionManager {
    private static ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public void beginTransaction() {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        connectionHolder.set(conn);  // Bind to this thread
    }

    // All operations on this thread find the same connection
    public Connection getConnection() {
        return connectionHolder.get();
    }

    public void commit() {
        connectionHolder.get().commit();
        connectionHolder.remove();
    }
}
```

This works because in traditional programming:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING: ONE THREAD, ONE TRANSACTION                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1:                                                              │
│   ──────────────────────────────────────────────────────────────▶       │
│                                                                          │
│   │ begin() │ insert() │ update() │ select() │ commit() │               │
│   │         │          │          │          │          │               │
│   │         │          │          │          │          │               │
│   ThreadLocal holds Connection throughout                                │
│                                                                          │
│   ALL operations execute on the SAME thread                              │
│   ThreadLocal is always accessible                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 5: Why ThreadLocal Fails in Reactive

In reactive code, operations don't stick to one thread:

```java
userRepository.findById(id)           // Thread 1
    .flatMap(user -> {
        return orderRepository.save()  // Thread 2 (maybe)
            .then(paymentRepository.charge()); // Thread 3 (maybe)
    });
```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE: THREAD HOPPING                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1: ────│ findById │─────────────────────────────────────▶     │
│                          ╲                                               │
│   Thread 2: ──────────────│ save │──────────────────────────────▶      │
│                                  ╲                                       │
│   Thread 3: ──────────────────────│ charge │────────────────────▶      │
│                                                                          │
│   ThreadLocal on Thread 1: [Connection A]                                │
│   ThreadLocal on Thread 2: [empty!]                                      │
│   ThreadLocal on Thread 3: [empty!]                                      │
│                                                                          │
│   The save() and charge() operations can't find the connection!          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: We Need a Different Mechanism

Requirements for reactive transaction context:

1. Must be accessible regardless of which thread executes the operator
2. Must flow through the entire reactive chain
3. Must be isolated (different reactive chains have different contexts)
4. Should be immutable (thread-safe)

## Step 7: Reactor Context - The Solution

Reactor provides exactly this: **Context**.

Context is an immutable key-value store that:
- Is attached to a subscription
- Is accessible from any operator in the chain
- Flows upstream (from subscriber to publisher)
- Is thread-safe

```java
// Adding to context
Mono.just("data")
    .contextWrite(ctx -> ctx.put("txConnection", connection));

// Reading from context
Mono.deferContextual(ctx -> {
    Connection conn = ctx.get("txConnection");
    return executeQuery(conn);
});
```

## Step 8: Context Flow Direction

Context flows **upstream**, not downstream:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONTEXT FLOW: UPSTREAM                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   source()                                                               │
│      │      ◀──── Context available here                                │
│      ▼                                                                   │
│   operator1()                                                            │
│      │      ◀──── Context available here                                │
│      ▼                                                                   │
│   operator2()                                                            │
│      │      ◀──── Context available here                                │
│      ▼                                                                   │
│   contextWrite(ctx -> ctx.put("key", value))  ◀── Context added here   │
│      │                                                                   │
│      ▼                                                                   │
│   subscribe()  ◀── Subscription triggers context propagation            │
│                                                                          │
│   Context "bubbles up" from where it's written to all operators above   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: Building a Transaction Manager

With Reactor Context, we can build reactive transactions:

```java
public class ReactiveTransactionManager {

    private static final String TX_CONNECTION_KEY = "txConnection";

    public <T> Mono<T> executeInTransaction(Mono<T> operation) {
        return Mono.usingWhen(
            // Resource acquisition: get connection, start transaction
            acquireTransactionalConnection(),

            // Use resource: execute the operation with context
            connection -> operation
                .contextWrite(ctx -> ctx.put(TX_CONNECTION_KEY, connection)),

            // On success: commit
            this::commit,

            // On error: rollback
            (connection, error) -> rollback(connection),

            // On cancel: rollback
            this::rollback
        );
    }

    private Mono<Connection> acquireTransactionalConnection() {
        return connectionFactory.create()
            .flatMap(conn -> conn.beginTransaction().thenReturn(conn));
    }

    private Mono<Void> commit(Connection connection) {
        return connection.commitTransaction()
            .then(connection.close());
    }

    private Mono<Void> rollback(Connection connection) {
        return connection.rollbackTransaction()
            .then(connection.close());
    }
}
```

## Step 10: Using the Transaction Manager

Operations can now access the transaction connection via context:

```java
public class OrderRepository {

    public Mono<Order> save(Order order) {
        return Mono.deferContextual(ctx -> {
            // Get the transaction connection from context
            Connection conn = ctx.get("txConnection");

            // Use it for our operation
            return conn.createStatement("INSERT INTO orders ...")
                .bind(0, order.getUserId())
                .execute()
                .flatMap(result -> /* ... */);
        });
    }
}
```

## Step 11: The Complete Picture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE TRANSACTION LIFECYCLE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. subscribe() called                                                  │
│         │                                                                │
│         ▼                                                                │
│   2. TransactionManager acquires connection, begins TX                   │
│         │                                                                │
│         ▼                                                                │
│   3. Connection added to Reactor Context                                 │
│         │                                                                │
│         ▼                                                                │
│   4. Operations execute, each accessing connection from context          │
│      │                                                                   │
│      │   Thread 1: │ findById │                                         │
│      │                    ╲                                              │
│      │   Thread 2: ────────│ save │                                     │
│      │                          ╲                                        │
│      │   Thread 3: ──────────────│ charge │                             │
│      │                                                                   │
│      │   All threads access same connection via Context!                 │
│         │                                                                │
│         ▼                                                                │
│   5a. Mono completes successfully → COMMIT                               │
│   5b. Mono errors → ROLLBACK                                             │
│         │                                                                │
│         ▼                                                                │
│   6. Connection returned to pool                                         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 12: Spring's Implementation

Spring's `TransactionalOperator` is exactly this pattern:

```java
// Spring's TransactionalOperator (simplified)
public class TransactionalOperator {

    private final ReactiveTransactionManager txManager;

    public <T> Mono<T> transactional(Mono<T> mono) {
        return txManager.getReactiveTransaction(definition)
            .flatMap(tx -> mono
                .doOnSuccess(result -> tx.commit())
                .doOnError(error -> tx.rollback())
                .contextWrite(ctx -> ctx.put(TX_KEY, tx))
            );
    }
}
```

And `@Transactional` annotation does the same via AOP:

```java
// What @Transactional does behind the scenes
@Aspect
public class TransactionalAspect {

    @Around("@annotation(Transactional)")
    public Object wrapInTransaction(ProceedingJoinPoint pjp) {
        Object result = pjp.proceed();  // Returns Mono or Flux

        if (result instanceof Mono) {
            return transactionalOperator.transactional((Mono<?>) result);
        }
        // Similar for Flux...
    }
}
```

## The Key Insight

The fundamental problem—maintaining transaction context across threads—is solved by changing WHERE context is stored:

| Approach | Storage | Thread-Safe | Cross-Thread |
|----------|---------|-------------|--------------|
| Traditional | ThreadLocal | Yes (thread-confined) | No |
| Reactive | Reactor Context | Yes (immutable) | Yes |

ThreadLocal binds context to a **thread**.
Reactor Context binds context to a **subscription/reactive chain**.

Since reactive operators access their subscription's context regardless of which thread they execute on, transaction management works even when operations hop between threads.

## Summary

From first principles, we derived that:

1. **Transactions require a shared connection** for all operations
2. **ThreadLocal can't share across threads** (the reactive reality)
3. **Reactor Context provides subscription-scoped storage** that flows through operators
4. **Transaction managers use Context** to propagate the transactional connection
5. **`contextWrite`** adds the connection, **`deferContextual`** reads it
6. **Commit/rollback** happens based on signal (complete vs error)

The result: transactions that work correctly even when your reactive code executes across many threads. The connection follows the subscription, not the thread.

This is why reactive transactions require Reactor Context—it's the only mechanism that provides cross-thread, subscription-scoped storage. And Spring's `@Transactional` and `TransactionalOperator` are simply convenient wrappers around this pattern.
