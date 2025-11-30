# Lab 14: Implementing Reactive Transactions

## Objectives

By the end of this lab, you will:

1. Implement declarative transactions with `@Transactional`
2. Use programmatic transactions with `TransactionalOperator`
3. Handle transaction boundaries in complex workflows
4. Implement error handling and rollback scenarios
5. Test transaction behavior with different isolation levels
6. Build a saga pattern for distributed operations

## Prerequisites

- Completed Chapter 13 (Reactive Data Access)
- Understanding of R2DBC and reactive repositories
- Docker installed for running PostgreSQL

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup | 10 min |
| 2 | Declarative Transactions | 25 min |
| 3 | Programmatic Transactions | 25 min |
| 4 | Error Handling & Rollback | 20 min |
| 5 | Transaction Isolation | 20 min |
| 6 | Saga Pattern | 25 min |
| 7 | Testing Transactions | 15 min |
| **Total** | | **140 min** |

---

## Part 1: Project Setup (10 min)

### Step 1.1: Create the Project

Create a new directory with `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>reactive-transactions-lab</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>1.19.3</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>1.19.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 1.2: Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: txlab
      POSTGRES_USER: txuser
      POSTGRES_PASSWORD: txpass
    ports:
      - "5432:5432"
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
```

### Step 1.3: Database Schema

Create `init.sql`:

```sql
-- Accounts table
CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Transactions log
CREATE TABLE transaction_log (
    id SERIAL PRIMARY KEY,
    from_account_id INTEGER REFERENCES accounts(id),
    to_account_id INTEGER REFERENCES accounts(id),
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Orders table
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Order items
CREATE TABLE order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER REFERENCES orders(id),
    product_id INTEGER NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

-- Inventory
CREATE TABLE inventory (
    id SERIAL PRIMARY KEY,
    product_id INTEGER UNIQUE NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    reserved INTEGER NOT NULL DEFAULT 0
);

-- Audit log
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id INTEGER NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample data
INSERT INTO accounts (account_number, owner_name, balance) VALUES
    ('ACC001', 'Alice', 1000.00),
    ('ACC002', 'Bob', 500.00),
    ('ACC003', 'Carol', 2000.00);

INSERT INTO inventory (product_id, product_name, quantity) VALUES
    (1, 'Widget A', 100),
    (2, 'Widget B', 50),
    (3, 'Gadget X', 25);
```

### Step 1.4: Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/txlab
    username: txuser
    password: txpass

logging:
  level:
    org.springframework.transaction: DEBUG
    org.springframework.r2dbc: DEBUG
```

### Step 1.5: Start Database

```bash
docker-compose up -d
```

---

## Part 2: Declarative Transactions (25 min)

### Step 2.1: Create Entity Classes

```java
package com.example.transactions.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("accounts")
public class Account {

    @Id
    private Long id;
    private String accountNumber;
    private String ownerName;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    @Version
    private Integer version;

    public Account() {}

    public Account(String accountNumber, String ownerName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = balance;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber, amount, balance);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
```

```java
package com.example.transactions.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("transaction_log")
public class TransactionLog {

    @Id
    private Long id;

    @Column("from_account_id")
    private Long fromAccountId;

    @Column("to_account_id")
    private Long toAccountId;

    private BigDecimal amount;
    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    public TransactionLog() {}

    public TransactionLog(Long fromAccountId, Long toAccountId, BigDecimal amount, String status) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

### Step 2.2: Create Custom Exception

```java
package com.example.transactions.entity;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final String accountNumber;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientFundsException(String accountNumber, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient funds in account %s: requested %.2f, available %.2f",
            accountNumber, requested, available));
        this.accountNumber = accountNumber;
        this.requested = requested;
        this.available = available;
    }

    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getRequested() { return requested; }
    public BigDecimal getAvailable() { return available; }
}
```

### Step 2.3: Create Repositories

```java
package com.example.transactions.repository;

import com.example.transactions.entity.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {
    Mono<Account> findByAccountNumber(String accountNumber);
}
```

```java
package com.example.transactions.repository;

import com.example.transactions.entity.TransactionLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TransactionLogRepository extends ReactiveCrudRepository<TransactionLog, Long> {
    Flux<TransactionLog> findByFromAccountIdOrToAccountId(Long fromId, Long toId);
}
```

### Step 2.4: Create Transfer Service with @Transactional

```java
package com.example.transactions.service;

import com.example.transactions.entity.Account;
import com.example.transactions.entity.TransactionLog;
import com.example.transactions.repository.AccountRepository;
import com.example.transactions.repository.TransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;

    public TransferService(AccountRepository accountRepository,
                           TransactionLogRepository transactionLogRepository) {
        this.accountRepository = accountRepository;
        this.transactionLogRepository = transactionLogRepository;
    }

    /**
     * Transfer funds between accounts - fully transactional.
     * If ANY operation fails, ALL changes are rolled back.
     */
    @Transactional
    public Mono<TransferResult> transfer(String fromAccountNumber,
                                          String toAccountNumber,
                                          BigDecimal amount) {
        log.info("Starting transfer: {} -> {}, amount: {}",
            fromAccountNumber, toAccountNumber, amount);

        return Mono.zip(
                accountRepository.findByAccountNumber(fromAccountNumber),
                accountRepository.findByAccountNumber(toAccountNumber)
            )
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found")))
            .flatMap(accounts -> {
                Account from = accounts.getT1();
                Account to = accounts.getT2();

                // Perform the transfer
                from.withdraw(amount);  // Throws if insufficient funds
                to.deposit(amount);

                // Save both accounts
                return accountRepository.save(from)
                    .then(accountRepository.save(to))
                    .then(createTransactionLog(from.getId(), to.getId(), amount, "COMPLETED"))
                    .map(txLog -> new TransferResult(
                        txLog.getId(),
                        from.getAccountNumber(),
                        to.getAccountNumber(),
                        amount,
                        "SUCCESS"
                    ));
            })
            .doOnSuccess(result -> log.info("Transfer completed: {}", result))
            .doOnError(e -> log.error("Transfer failed: {}", e.getMessage()));
    }

    private Mono<TransactionLog> createTransactionLog(Long fromId, Long toId,
                                                       BigDecimal amount, String status) {
        return transactionLogRepository.save(
            new TransactionLog(fromId, toId, amount, status));
    }

    @Transactional(readOnly = true)
    public Mono<Account> getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    public record TransferResult(
        Long transactionId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String status
    ) {}
}
```

### Step 2.5: Create Controller

```java
package com.example.transactions.controller;

import com.example.transactions.entity.Account;
import com.example.transactions.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public Mono<ResponseEntity<TransferService.TransferResult>> transfer(
            @RequestBody TransferRequest request) {
        return transferService.transfer(
                request.fromAccount(),
                request.toAccount(),
                request.amount()
            )
            .map(result -> ResponseEntity.ok(result))
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest()
                .body(new TransferService.TransferResult(
                    null,
                    request.fromAccount(),
                    request.toAccount(),
                    request.amount(),
                    "FAILED: " + e.getMessage()
                ))));
    }

    @GetMapping("/accounts/{accountNumber}")
    public Mono<ResponseEntity<Account>> getAccount(@PathVariable String accountNumber) {
        return transferService.getAccount(accountNumber)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public record TransferRequest(String fromAccount, String toAccount, BigDecimal amount) {}
}
```

### Step 2.6: Test Declarative Transactions

```bash
# Check initial balances
curl http://localhost:8080/api/transfers/accounts/ACC001  # Alice: 1000
curl http://localhost:8080/api/transfers/accounts/ACC002  # Bob: 500

# Successful transfer
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":200}'

# Check balances after transfer
curl http://localhost:8080/api/transfers/accounts/ACC001  # Alice: 800
curl http://localhost:8080/api/transfers/accounts/ACC002  # Bob: 700

# Failed transfer (insufficient funds) - should rollback
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC001","toAccount":"ACC002","amount":10000}'

# Verify no partial changes
curl http://localhost:8080/api/transfers/accounts/ACC001  # Still 800
curl http://localhost:8080/api/transfers/accounts/ACC002  # Still 700
```

---

## Part 3: Programmatic Transactions (25 min)

### Step 3.1: Create Order Entities

```java
package com.example.transactions.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("orders")
public class Order {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    private String status;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Order() {}

    public Order(Long userId, BigDecimal totalAmount) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

```java
package com.example.transactions.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("inventory")
public class Inventory {

    @Id
    private Long id;

    @Column("product_id")
    private Integer productId;

    @Column("product_name")
    private String productName;

    private Integer quantity;
    private Integer reserved;

    public Inventory() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getReserved() { return reserved; }
    public void setReserved(Integer reserved) { this.reserved = reserved; }

    public int getAvailable() {
        return quantity - reserved;
    }

    public void reserve(int amount) {
        if (getAvailable() < amount) {
            throw new IllegalStateException(
                "Cannot reserve " + amount + " of " + productName + ". Available: " + getAvailable());
        }
        this.reserved += amount;
    }

    public void release(int amount) {
        this.reserved = Math.max(0, this.reserved - amount);
    }

    public void deduct(int amount) {
        this.quantity -= amount;
        this.reserved -= amount;
    }
}
```

### Step 3.2: Create Repositories

```java
package com.example.transactions.repository;

import com.example.transactions.entity.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
}
```

```java
package com.example.transactions.repository;

import com.example.transactions.entity.Inventory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface InventoryRepository extends ReactiveCrudRepository<Inventory, Long> {
    Mono<Inventory> findByProductId(Integer productId);
}
```

### Step 3.3: Create Order Service with TransactionalOperator

```java
package com.example.transactions.service;

import com.example.transactions.entity.Inventory;
import com.example.transactions.entity.Order;
import com.example.transactions.repository.InventoryRepository;
import com.example.transactions.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final TransactionalOperator transactionalOperator;

    public OrderService(OrderRepository orderRepository,
                        InventoryRepository inventoryRepository,
                        TransactionalOperator transactionalOperator) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * Create order with programmatic transaction control.
     * Only the critical database operations are in the transaction.
     */
    public Mono<OrderResult> createOrder(CreateOrderRequest request) {
        // Validation - not transactional
        return validateRequest(request)
            // Transactional part
            .then(createOrderTransactionally(request))
            // Post-processing - not transactional
            .doOnSuccess(result -> log.info("Order created successfully: {}", result.orderId()));
    }

    private Mono<Void> validateRequest(CreateOrderRequest request) {
        if (request.items().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Order must have items"));
        }
        return Mono.empty();
    }

    private Mono<OrderResult> createOrderTransactionally(CreateOrderRequest request) {
        // Calculate total
        BigDecimal total = request.items().stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create order
        Order order = new Order(request.userId(), total);

        return orderRepository.save(order)
            .flatMap(savedOrder ->
                // Reserve inventory for each item
                Flux.fromIterable(request.items())
                    .flatMap(item -> reserveInventory(item.productId(), item.quantity()))
                    .then(Mono.just(savedOrder))
            )
            .flatMap(savedOrder -> {
                savedOrder.setStatus("CONFIRMED");
                return orderRepository.save(savedOrder);
            })
            .map(finalOrder -> new OrderResult(
                finalOrder.getId(),
                finalOrder.getStatus(),
                finalOrder.getTotalAmount()
            ))
            // Wrap in transaction
            .as(transactionalOperator::transactional);
    }

    private Mono<Inventory> reserveInventory(Integer productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + productId)))
            .flatMap(inventory -> {
                inventory.reserve(quantity);  // Throws if insufficient
                return inventoryRepository.save(inventory);
            });
    }

    /**
     * Cancel order with manual transaction control
     */
    public Mono<OrderResult> cancelOrder(Long orderId, List<OrderItem> items) {
        return transactionalOperator.execute(status -> {
            return orderRepository.findById(orderId)
                .flatMap(order -> {
                    if ("CANCELLED".equals(order.getStatus())) {
                        // Already cancelled - mark rollback but don't error
                        status.setRollbackOnly();
                        return Mono.just(new OrderResult(orderId, "ALREADY_CANCELLED", order.getTotalAmount()));
                    }

                    order.setStatus("CANCELLED");
                    return orderRepository.save(order)
                        .then(releaseInventory(items))
                        .thenReturn(new OrderResult(orderId, "CANCELLED", order.getTotalAmount()));
                });
        }).single();
    }

    private Mono<Void> releaseInventory(List<OrderItem> items) {
        return Flux.fromIterable(items)
            .flatMap(item ->
                inventoryRepository.findByProductId(item.productId())
                    .flatMap(inventory -> {
                        inventory.release(item.quantity());
                        return inventoryRepository.save(inventory);
                    })
            )
            .then();
    }

    public record CreateOrderRequest(Long userId, List<OrderItem> items) {}
    public record OrderItem(Integer productId, String productName, Integer quantity, BigDecimal unitPrice) {}
    public record OrderResult(Long orderId, String status, BigDecimal totalAmount) {}
}
```

### Step 3.4: Create Order Controller

```java
package com.example.transactions.controller;

import com.example.transactions.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderService.OrderResult> createOrder(
            @RequestBody OrderService.CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @PostMapping("/{orderId}/cancel")
    public Mono<ResponseEntity<OrderService.OrderResult>> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody CancelRequest request) {
        return orderService.cancelOrder(orderId, request.items())
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    public record CancelRequest(java.util.List<OrderService.OrderItem> items) {}
}
```

### Step 3.5: Test Programmatic Transactions

```bash
# Create order (should reserve inventory)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {"productId": 1, "productName": "Widget A", "quantity": 5, "unitPrice": 10.00}
    ]
  }'

# Try to order more than available (should fail and rollback)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {"productId": 3, "productName": "Gadget X", "quantity": 100, "unitPrice": 50.00}
    ]
  }'
```

---

## Part 4: Error Handling & Rollback (20 min)

### Step 4.1: Create Error-Aware Transfer Service

```java
package com.example.transactions.service;

import com.example.transactions.entity.Account;
import com.example.transactions.entity.InsufficientFundsException;
import com.example.transactions.entity.TransactionLog;
import com.example.transactions.repository.AccountRepository;
import com.example.transactions.repository.TransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class RobustTransferService {

    private static final Logger log = LoggerFactory.getLogger(RobustTransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final TransactionalOperator txOperator;

    public RobustTransferService(AccountRepository accountRepository,
                                  TransactionLogRepository transactionLogRepository,
                                  TransactionalOperator txOperator) {
        this.accountRepository = accountRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.txOperator = txOperator;
    }

    /**
     * Transfer that logs failures outside the transaction
     */
    public Mono<TransferOutcome> transferWithAudit(String from, String to, BigDecimal amount) {
        return performTransfer(from, to, amount)
            .map(result -> new TransferOutcome(result, null))
            .onErrorResume(e -> {
                // Log failure OUTSIDE the transaction (won't be rolled back)
                return logFailedTransfer(from, to, amount, e.getMessage())
                    .thenReturn(new TransferOutcome(null, e.getMessage()));
            });
    }

    private Mono<TransferResult> performTransfer(String from, String to, BigDecimal amount) {
        return Mono.zip(
                accountRepository.findByAccountNumber(from),
                accountRepository.findByAccountNumber(to)
            )
            .flatMap(accounts -> {
                Account fromAccount = accounts.getT1();
                Account toAccount = accounts.getT2();

                fromAccount.withdraw(amount);
                toAccount.deposit(amount);

                return accountRepository.save(fromAccount)
                    .then(accountRepository.save(toAccount))
                    .then(transactionLogRepository.save(
                        new TransactionLog(fromAccount.getId(), toAccount.getId(), amount, "COMPLETED")))
                    .map(txLog -> new TransferResult(txLog.getId(), "SUCCESS"));
            })
            .as(txOperator::transactional);  // Transaction
    }

    private Mono<Void> logFailedTransfer(String from, String to, BigDecimal amount, String reason) {
        log.error("Transfer failed: {} -> {}, amount: {}, reason: {}", from, to, amount, reason);
        // This runs OUTSIDE any transaction
        return Mono.fromRunnable(() -> {
            // Could save to a separate audit database, send to monitoring, etc.
        });
    }

    /**
     * Transfer that doesn't rollback for specific exceptions
     */
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public Mono<TransferOutcome> transferWithPendingOnInsufficientFunds(
            String from, String to, BigDecimal amount) {

        return Mono.zip(
                accountRepository.findByAccountNumber(from),
                accountRepository.findByAccountNumber(to)
            )
            .flatMap(accounts -> {
                Account fromAccount = accounts.getT1();
                Account toAccount = accounts.getT2();

                try {
                    fromAccount.withdraw(amount);
                    toAccount.deposit(amount);

                    return accountRepository.save(fromAccount)
                        .then(accountRepository.save(toAccount))
                        .then(transactionLogRepository.save(
                            new TransactionLog(fromAccount.getId(), toAccount.getId(), amount, "COMPLETED")))
                        .map(txLog -> new TransferOutcome(
                            new TransferResult(txLog.getId(), "SUCCESS"), null));

                } catch (InsufficientFundsException e) {
                    // Create a PENDING transaction log instead of failing
                    return transactionLogRepository.save(
                            new TransactionLog(fromAccount.getId(), toAccount.getId(), amount, "PENDING"))
                        .map(txLog -> new TransferOutcome(
                            new TransferResult(txLog.getId(), "PENDING_FUNDS"), null));
                }
            });
    }

    public record TransferResult(Long transactionId, String status) {}
    public record TransferOutcome(TransferResult result, String error) {}
}
```

### Step 4.2: Test Error Scenarios

```java
package com.example.transactions;

import com.example.transactions.entity.Account;
import com.example.transactions.repository.AccountRepository;
import com.example.transactions.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransactionRollbackTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // Reset balances
        databaseClient.sql("UPDATE accounts SET balance = 1000 WHERE account_number = 'ACC001'")
            .fetch().rowsUpdated().block();
        databaseClient.sql("UPDATE accounts SET balance = 500 WHERE account_number = 'ACC002'")
            .fetch().rowsUpdated().block();
    }

    @Test
    void transferShouldRollbackOnInsufficientFunds() {
        BigDecimal originalFromBalance = accountRepository
            .findByAccountNumber("ACC001")
            .map(Account::getBalance)
            .block();

        BigDecimal originalToBalance = accountRepository
            .findByAccountNumber("ACC002")
            .map(Account::getBalance)
            .block();

        // Attempt transfer exceeding balance
        StepVerifier.create(transferService.transfer("ACC001", "ACC002", new BigDecimal("5000")))
            .expectError()
            .verify();

        // Verify no changes
        StepVerifier.create(accountRepository.findByAccountNumber("ACC001"))
            .assertNext(account -> assertThat(account.getBalance()).isEqualByComparingTo(originalFromBalance))
            .verifyComplete();

        StepVerifier.create(accountRepository.findByAccountNumber("ACC002"))
            .assertNext(account -> assertThat(account.getBalance()).isEqualByComparingTo(originalToBalance))
            .verifyComplete();
    }

    @Test
    void transferShouldCommitOnSuccess() {
        BigDecimal amount = new BigDecimal("200");

        StepVerifier.create(transferService.transfer("ACC001", "ACC002", amount))
            .assertNext(result -> assertThat(result.status()).isEqualTo("SUCCESS"))
            .verifyComplete();

        // Verify changes
        StepVerifier.create(accountRepository.findByAccountNumber("ACC001"))
            .assertNext(account -> assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("800")))
            .verifyComplete();

        StepVerifier.create(accountRepository.findByAccountNumber("ACC002"))
            .assertNext(account -> assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("700")))
            .verifyComplete();
    }
}
```

---

## Part 5: Transaction Isolation (20 min)

### Step 5.1: Create Isolation Demo Service

```java
package com.example.transactions.service;

import com.example.transactions.entity.Account;
import com.example.transactions.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class IsolationDemoService {

    private static final Logger log = LoggerFactory.getLogger(IsolationDemoService.class);

    private final AccountRepository accountRepository;

    public IsolationDemoService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Read Committed - sees committed changes from other transactions
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Mono<BigDecimal> readCommittedBalance(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .map(Account::getBalance)
            .doOnNext(balance -> log.info("READ_COMMITTED: Balance for {} is {}", accountNumber, balance));
    }

    /**
     * Repeatable Read - same query returns same results within transaction
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Mono<BalanceComparison> repeatableReadDemo(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .map(Account::getBalance)
            .flatMap(firstRead -> {
                log.info("REPEATABLE_READ: First read for {} is {}", accountNumber, firstRead);

                // Simulate some processing time
                return Mono.delay(Duration.ofSeconds(2))
                    .then(accountRepository.findByAccountNumber(accountNumber))
                    .map(Account::getBalance)
                    .map(secondRead -> {
                        log.info("REPEATABLE_READ: Second read for {} is {}", accountNumber, secondRead);
                        return new BalanceComparison(firstRead, secondRead);
                    });
            });
    }

    /**
     * Serializable - highest isolation, prevents phantom reads
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Mono<BigDecimal> serializableTransfer(String from, String to, BigDecimal amount) {
        return Mono.zip(
                accountRepository.findByAccountNumber(from),
                accountRepository.findByAccountNumber(to)
            )
            .flatMap(accounts -> {
                Account fromAccount = accounts.getT1();
                Account toAccount = accounts.getT2();

                fromAccount.withdraw(amount);
                toAccount.deposit(amount);

                return accountRepository.save(fromAccount)
                    .then(accountRepository.save(toAccount))
                    .thenReturn(amount);
            });
    }

    public record BalanceComparison(BigDecimal firstRead, BigDecimal secondRead) {
        public boolean isConsistent() {
            return firstRead.compareTo(secondRead) == 0;
        }
    }
}
```

---

## Part 6: Saga Pattern (25 min)

### Step 6.1: Create Saga Coordinator

```java
package com.example.transactions.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Saga pattern for distributed order processing.
 * Each step has a compensating action for rollback.
 */
@Service
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    private final InventoryStep inventoryStep;
    private final PaymentStep paymentStep;
    private final ShippingStep shippingStep;

    public OrderSaga(InventoryStep inventoryStep,
                     PaymentStep paymentStep,
                     ShippingStep shippingStep) {
        this.inventoryStep = inventoryStep;
        this.paymentStep = paymentStep;
        this.shippingStep = shippingStep;
    }

    public Mono<OrderSagaResult> executeOrderSaga(OrderSagaRequest request) {
        SagaContext context = new SagaContext(request);

        return inventoryStep.reserve(context)
            .flatMap(ctx ->
                paymentStep.charge(ctx)
                    .onErrorResume(e -> {
                        log.error("Payment failed, compensating inventory", e);
                        return inventoryStep.compensate(ctx)
                            .then(Mono.error(e));
                    })
            )
            .flatMap(ctx ->
                shippingStep.schedule(ctx)
                    .onErrorResume(e -> {
                        log.error("Shipping failed, compensating payment and inventory", e);
                        return paymentStep.compensate(ctx)
                            .then(inventoryStep.compensate(ctx))
                            .then(Mono.error(e));
                    })
            )
            .map(ctx -> new OrderSagaResult(
                ctx.getOrderId(),
                "COMPLETED",
                ctx.getInventoryReservationId(),
                ctx.getPaymentId(),
                ctx.getShippingId()
            ))
            .doOnSuccess(result -> log.info("Saga completed: {}", result))
            .doOnError(e -> log.error("Saga failed: {}", e.getMessage()));
    }

    public record OrderSagaRequest(
        Long orderId,
        Long userId,
        java.util.List<SagaOrderItem> items,
        BigDecimal totalAmount
    ) {}

    public record SagaOrderItem(Integer productId, Integer quantity) {}

    public record OrderSagaResult(
        Long orderId,
        String status,
        String inventoryReservationId,
        String paymentId,
        String shippingId
    ) {}
}
```

### Step 6.2: Create Saga Steps

```java
package com.example.transactions.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class InventoryStep {

    private static final Logger log = LoggerFactory.getLogger(InventoryStep.class);

    public Mono<SagaContext> reserve(SagaContext context) {
        log.info("Reserving inventory for order {}", context.getOrderId());

        // Simulate inventory reservation
        return Mono.fromCallable(() -> {
            // In real app: call inventory service or database
            String reservationId = "INV-" + UUID.randomUUID().toString().substring(0, 8);
            context.setInventoryReservationId(reservationId);
            log.info("Inventory reserved: {}", reservationId);
            return context;
        });
    }

    public Mono<Void> compensate(SagaContext context) {
        log.info("Releasing inventory reservation: {}", context.getInventoryReservationId());

        return Mono.fromRunnable(() -> {
            // In real app: release the reservation
            log.info("Inventory released for order {}", context.getOrderId());
        });
    }
}
```

```java
package com.example.transactions.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class PaymentStep {

    private static final Logger log = LoggerFactory.getLogger(PaymentStep.class);

    public Mono<SagaContext> charge(SagaContext context) {
        log.info("Charging payment for order {}: ${}", context.getOrderId(), context.getRequest().totalAmount());

        return Mono.fromCallable(() -> {
            // Simulate payment - fail for orders over $1000 (for testing)
            if (context.getRequest().totalAmount().compareTo(new java.math.BigDecimal("1000")) > 0) {
                throw new RuntimeException("Payment declined: amount exceeds limit");
            }

            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
            context.setPaymentId(paymentId);
            log.info("Payment charged: {}", paymentId);
            return context;
        });
    }

    public Mono<Void> compensate(SagaContext context) {
        log.info("Refunding payment: {}", context.getPaymentId());

        return Mono.fromRunnable(() -> {
            log.info("Payment refunded for order {}", context.getOrderId());
        });
    }
}
```

```java
package com.example.transactions.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class ShippingStep {

    private static final Logger log = LoggerFactory.getLogger(ShippingStep.class);

    public Mono<SagaContext> schedule(SagaContext context) {
        log.info("Scheduling shipping for order {}", context.getOrderId());

        return Mono.fromCallable(() -> {
            String shippingId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8);
            context.setShippingId(shippingId);
            log.info("Shipping scheduled: {}", shippingId);
            return context;
        });
    }

    public Mono<Void> compensate(SagaContext context) {
        log.info("Cancelling shipping: {}", context.getShippingId());

        return Mono.fromRunnable(() -> {
            log.info("Shipping cancelled for order {}", context.getOrderId());
        });
    }
}
```

```java
package com.example.transactions.saga;

public class SagaContext {

    private final OrderSaga.OrderSagaRequest request;
    private String inventoryReservationId;
    private String paymentId;
    private String shippingId;

    public SagaContext(OrderSaga.OrderSagaRequest request) {
        this.request = request;
    }

    public OrderSaga.OrderSagaRequest getRequest() {
        return request;
    }

    public Long getOrderId() {
        return request.orderId();
    }

    public String getInventoryReservationId() {
        return inventoryReservationId;
    }

    public void setInventoryReservationId(String inventoryReservationId) {
        this.inventoryReservationId = inventoryReservationId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getShippingId() {
        return shippingId;
    }

    public void setShippingId(String shippingId) {
        this.shippingId = shippingId;
    }
}
```

### Step 6.3: Create Saga Controller

```java
package com.example.transactions.controller;

import com.example.transactions.saga.OrderSaga;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/saga")
public class SagaController {

    private final OrderSaga orderSaga;

    public SagaController(OrderSaga orderSaga) {
        this.orderSaga = orderSaga;
    }

    @PostMapping("/orders")
    public Mono<OrderSaga.OrderSagaResult> createOrder(@RequestBody OrderSaga.OrderSagaRequest request) {
        return orderSaga.executeOrderSaga(request);
    }
}
```

### Step 6.4: Test Saga Pattern

```bash
# Successful saga (under $1000)
curl -X POST http://localhost:8080/api/saga/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1,
    "userId": 1,
    "items": [{"productId": 1, "quantity": 5}],
    "totalAmount": 500
  }'

# Failed saga (over $1000 - payment fails, compensation runs)
curl -X POST http://localhost:8080/api/saga/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 2,
    "userId": 1,
    "items": [{"productId": 1, "quantity": 100}],
    "totalAmount": 2000
  }'
```

---

## Part 7: Testing Transactions (15 min)

### Step 7.1: Complete Test Suite

```java
package com.example.transactions;

import com.example.transactions.entity.Account;
import com.example.transactions.entity.TransactionLog;
import com.example.transactions.repository.AccountRepository;
import com.example.transactions.repository.TransactionLogRepository;
import com.example.transactions.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionLogRepository transactionLogRepository;

    @BeforeEach
    void setUp() {
        // Set up test accounts
        accountRepository.deleteAll()
            .then(accountRepository.save(new Account("TEST001", "Test User 1", new BigDecimal("1000"))))
            .then(accountRepository.save(new Account("TEST002", "Test User 2", new BigDecimal("500"))))
            .block();
    }

    @Test
    void successfulTransferShouldCommit() {
        StepVerifier.create(
            transferService.transfer("TEST001", "TEST002", new BigDecimal("100"))
        )
        .assertNext(result -> {
            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.transactionId()).isNotNull();
        })
        .verifyComplete();

        // Verify account balances
        StepVerifier.create(accountRepository.findByAccountNumber("TEST001"))
            .assertNext(a -> assertThat(a.getBalance()).isEqualByComparingTo("900"))
            .verifyComplete();

        StepVerifier.create(accountRepository.findByAccountNumber("TEST002"))
            .assertNext(a -> assertThat(a.getBalance()).isEqualByComparingTo("600"))
            .verifyComplete();

        // Verify transaction log
        StepVerifier.create(transactionLogRepository.findAll())
            .assertNext(log -> assertThat(log.getStatus()).isEqualTo("COMPLETED"))
            .verifyComplete();
    }

    @Test
    void failedTransferShouldRollback() {
        StepVerifier.create(
            transferService.transfer("TEST001", "TEST002", new BigDecimal("5000"))
        )
        .expectError()
        .verify();

        // Verify no changes to accounts
        StepVerifier.create(accountRepository.findByAccountNumber("TEST001"))
            .assertNext(a -> assertThat(a.getBalance()).isEqualByComparingTo("1000"))
            .verifyComplete();

        StepVerifier.create(accountRepository.findByAccountNumber("TEST002"))
            .assertNext(a -> assertThat(a.getBalance()).isEqualByComparingTo("500"))
            .verifyComplete();
    }
}
```

---

## Reflection

### Key Takeaways

1. **Declarative (`@Transactional`) is simpler** for basic use cases
2. **Programmatic (`TransactionalOperator`) offers more control** for complex scenarios
3. **Always return the reactive type** from transactional methods
4. **Saga pattern** is essential for distributed transactions
5. **Test transaction boundaries** explicitly

### Common Pitfalls

1. Forgetting to return `Mono`/`Flux` from `@Transactional` methods
2. Using `subscribe()` inside a transactional method
3. Expecting rollback after `onErrorResume`
4. Not considering thread-switching with `subscribeOn`

---

## Summary

In this lab, you:

1. Implemented declarative transactions with `@Transactional`
2. Used programmatic transactions with `TransactionalOperator`
3. Handled rollback scenarios and error cases
4. Explored transaction isolation levels
5. Built a saga pattern for distributed operations
6. Tested transaction behavior comprehensively

You now have the skills to manage transactions effectively in reactive applications!
