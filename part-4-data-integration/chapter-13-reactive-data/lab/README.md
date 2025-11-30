# Lab 13: Reactive Data Access with R2DBC and MongoDB

## Objectives

By the end of this lab, you will:

1. Set up R2DBC with PostgreSQL for reactive SQL access
2. Build reactive repositories with Spring Data R2DBC
3. Implement custom queries and handle relationships
4. Set up reactive MongoDB for document storage
5. Implement reactive Redis caching
6. Compare performance with blocking JDBC
7. Test reactive repositories with Testcontainers

## Prerequisites

- Completed Part III (WebFlux fundamentals)
- Understanding of Project Reactor (Mono/Flux)
- Docker installed for running databases
- Java 17+ and Maven installed

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup & Docker | 15 min |
| 2 | R2DBC with PostgreSQL | 30 min |
| 3 | Custom Queries & Relationships | 25 min |
| 4 | Reactive MongoDB | 25 min |
| 5 | Reactive Redis Caching | 20 min |
| 6 | Performance Comparison | 15 min |
| 7 | Testing with Testcontainers | 15 min |
| **Total** | | **145 min** |

---

## Part 1: Project Setup & Docker (15 min)

### Step 1.1: Create the Project

Create a new directory and add the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>reactive-data-lab</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <testcontainers.version>1.19.3</testcontainers.version>
    </properties>

    <dependencies>
        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- R2DBC with PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Reactive MongoDB -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
        </dependency>

        <!-- Reactive Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>

        <!-- Testing -->
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

        <!-- Testcontainers -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mongodb</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- H2 for simple tests -->
        <dependency>
            <groupId>io.r2dbc</groupId>
            <artifactId>r2dbc-h2</artifactId>
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

### Step 1.2: Create Docker Compose File

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: reactive-postgres
    environment:
      POSTGRES_DB: reactivedb
      POSTGRES_USER: reactive
      POSTGRES_PASSWORD: reactive123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

  mongodb:
    image: mongo:7
    container_name: reactive-mongodb
    environment:
      MONGO_INITDB_ROOT_USERNAME: reactive
      MONGO_INITDB_ROOT_PASSWORD: reactive123
      MONGO_INITDB_DATABASE: reactivedb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db

  redis:
    image: redis:7-alpine
    container_name: reactive-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  mongodb_data:
  redis_data:
```

### Step 1.3: Create Database Schema

Create `init.sql`:

```sql
-- Users table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER REFERENCES orders(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

-- Sample data
INSERT INTO users (email, first_name, last_name) VALUES
    ('alice@example.com', 'Alice', 'Johnson'),
    ('bob@example.com', 'Bob', 'Smith'),
    ('carol@example.com', 'Carol', 'Williams');

INSERT INTO orders (user_id, status, total_amount) VALUES
    (1, 'COMPLETED', 150.00),
    (1, 'PENDING', 75.50),
    (2, 'COMPLETED', 200.00);

INSERT INTO order_items (order_id, product_name, quantity, unit_price) VALUES
    (1, 'Widget A', 2, 50.00),
    (1, 'Widget B', 1, 50.00),
    (2, 'Gadget X', 3, 25.17),
    (3, 'Device Y', 4, 50.00);
```

### Step 1.4: Start Services

```bash
docker-compose up -d
```

### Step 1.5: Create Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/reactivedb
    username: reactive
    password: reactive123
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m

  data:
    mongodb:
      uri: mongodb://reactive:reactive123@localhost:27017/reactivedb?authSource=admin

  redis:
    host: localhost
    port: 6379

logging:
  level:
    org.springframework.data.r2dbc: DEBUG
    io.r2dbc.postgresql: DEBUG
```

---

## Part 2: R2DBC with PostgreSQL (30 min)

### Step 2.1: Create the Main Application

```java
package com.example.reactivedata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveDataLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveDataLabApplication.class, args);
    }
}
```

### Step 2.2: Create Entity Classes

```java
package com.example.reactivedata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
public class User {

    @Id
    private Long id;

    private String email;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Default constructor
    public User() {}

    // Constructor for new users
    public User(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
```

```java
package com.example.reactivedata.entity;

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

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Order() {}

    public Order(Long userId, BigDecimal totalAmount) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

```java
package com.example.reactivedata.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("order_items")
public class OrderItem {

    @Id
    private Long id;

    @Column("order_id")
    private Long orderId;

    @Column("product_name")
    private String productName;

    private Integer quantity;

    @Column("unit_price")
    private BigDecimal unitPrice;

    public OrderItem() {}

    public OrderItem(Long orderId, String productName, Integer quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
```

### Step 2.3: Create Reactive Repositories

```java
package com.example.reactivedata.repository;

import com.example.reactivedata.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Mono<User> findByEmail(String email);

    Flux<User> findByLastName(String lastName);

    @Query("SELECT * FROM users WHERE first_name ILIKE '%' || :name || '%' OR last_name ILIKE '%' || :name || '%'")
    Flux<User> searchByName(String name);

    @Query("SELECT * FROM users ORDER BY created_at DESC LIMIT :limit")
    Flux<User> findRecentUsers(int limit);
}
```

```java
package com.example.reactivedata.repository;

import com.example.reactivedata.entity.Order;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    Flux<Order> findByUserId(Long userId);

    Flux<Order> findByStatus(String status);

    Flux<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT * FROM orders WHERE total_amount > :minAmount")
    Flux<Order> findLargeOrders(BigDecimal minAmount);

    @Modifying
    @Query("UPDATE orders SET status = :status, updated_at = NOW() WHERE id = :id")
    Mono<Integer> updateStatus(Long id, String status);

    @Query("SELECT COUNT(*) FROM orders WHERE user_id = :userId")
    Mono<Long> countByUserId(Long userId);
}
```

```java
package com.example.reactivedata.repository;

import com.example.reactivedata.entity.OrderItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

    Flux<OrderItem> findByOrderId(Long orderId);

    Flux<OrderItem> findByOrderIdIn(Iterable<Long> orderIds);
}
```

### Step 2.4: Create Service Layer

```java
package com.example.reactivedata.service;

import com.example.reactivedata.entity.User;
import com.example.reactivedata.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<User> createUser(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName);
        return userRepository.save(user)
            .doOnSuccess(saved -> log.info("Created user: {}", saved.getEmail()));
    }

    public Mono<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    public Flux<User> searchByName(String name) {
        return userRepository.searchByName(name);
    }

    public Mono<User> updateUser(Long id, String firstName, String lastName) {
        return userRepository.findById(id)
            .flatMap(user -> {
                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setUpdatedAt(java.time.LocalDateTime.now());
                return userRepository.save(user);
            })
            .doOnSuccess(updated -> log.info("Updated user: {}", updated.getId()));
    }

    public Mono<Void> deleteUser(Long id) {
        return userRepository.deleteById(id)
            .doOnSuccess(v -> log.info("Deleted user: {}", id));
    }
}
```

### Step 2.5: Create REST Controller

```java
package com.example.reactivedata.controller;

import com.example.reactivedata.entity.User;
import com.example.reactivedata.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Flux<User> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUserById(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Flux<User> searchUsers(@RequestParam String name) {
        return userService.searchByName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody CreateUserRequest request) {
        return userService.createUser(request.email(), request.firstName(), request.lastName());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<User>> updateUser(@PathVariable Long id,
                                                  @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request.firstName(), request.lastName())
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }

    public record CreateUserRequest(String email, String firstName, String lastName) {}
    public record UpdateUserRequest(String firstName, String lastName) {}
}
```

### Step 2.6: Test Basic Operations

Start the application and test with curl:

```bash
# Get all users
curl http://localhost:8080/api/users

# Get user by ID
curl http://localhost:8080/api/users/1

# Search users
curl "http://localhost:8080/api/users/search?name=alice"

# Create a new user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"dave@example.com","firstName":"Dave","lastName":"Brown"}'

# Update user
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Alice","lastName":"Johnson-Smith"}'
```

---

## Part 3: Custom Queries & Relationships (25 min)

### Step 3.1: Create DTOs for Complex Results

```java
package com.example.reactivedata.dto;

import com.example.reactivedata.entity.Order;
import com.example.reactivedata.entity.OrderItem;
import com.example.reactivedata.entity.User;

import java.util.List;

public record OrderDetails(
    Order order,
    User user,
    List<OrderItem> items
) {
    public java.math.BigDecimal getCalculatedTotal() {
        return items.stream()
            .map(OrderItem::getLineTotal)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
```

```java
package com.example.reactivedata.dto;

import java.math.BigDecimal;

public record UserOrderSummary(
    Long userId,
    String userName,
    String email,
    long orderCount,
    BigDecimal totalSpent
) {}
```

### Step 3.2: Create Order Service with Relationship Handling

```java
package com.example.reactivedata.service;

import com.example.reactivedata.dto.OrderDetails;
import com.example.reactivedata.dto.UserOrderSummary;
import com.example.reactivedata.entity.Order;
import com.example.reactivedata.entity.OrderItem;
import com.example.reactivedata.repository.OrderItemRepository;
import com.example.reactivedata.repository.OrderRepository;
import com.example.reactivedata.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final DatabaseClient databaseClient;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        UserRepository userRepository,
                        DatabaseClient databaseClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.databaseClient = databaseClient;
    }

    /**
     * Fetch order with all related data (user and items)
     */
    public Mono<OrderDetails> getOrderDetails(Long orderId) {
        return orderRepository.findById(orderId)
            .flatMap(order -> Mono.zip(
                Mono.just(order),
                userRepository.findById(order.getUserId()),
                orderItemRepository.findByOrderId(orderId).collectList()
            ))
            .map(tuple -> new OrderDetails(
                tuple.getT1(),  // order
                tuple.getT2(),  // user
                tuple.getT3()   // items
            ))
            .doOnSuccess(details ->
                log.info("Fetched order details for order {}", orderId));
    }

    /**
     * Fetch all orders for a user with items
     */
    public Flux<OrderDetails> getUserOrdersWithDetails(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .flatMap(order ->
                orderItemRepository.findByOrderId(order.getId())
                    .collectList()
                    .zipWith(userRepository.findById(order.getUserId()))
                    .map(tuple -> new OrderDetails(order, tuple.getT2(), tuple.getT1()))
            );
    }

    /**
     * Batch fetch orders with optimized queries
     */
    public Flux<OrderDetails> getOrdersWithDetails(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Flux.empty();
        }

        // Fetch all orders
        Mono<java.util.Map<Long, Order>> ordersMap = orderRepository.findAllById(orderIds)
            .collectMap(Order::getId);

        // Fetch all items for these orders
        Mono<java.util.Map<Long, List<OrderItem>>> itemsMap = orderItemRepository
            .findByOrderIdIn(orderIds)
            .collectList()
            .map(items -> items.stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderId)));

        // Fetch all users for these orders
        Mono<java.util.Map<Long, com.example.reactivedata.entity.User>> usersMap =
            ordersMap.flatMap(orders -> {
                List<Long> userIds = orders.values().stream()
                    .map(Order::getUserId)
                    .distinct()
                    .toList();
                return userRepository.findAllById(userIds)
                    .collectMap(com.example.reactivedata.entity.User::getId);
            });

        return Mono.zip(ordersMap, itemsMap, usersMap)
            .flatMapMany(tuple -> {
                var orders = tuple.getT1();
                var items = tuple.getT2();
                var users = tuple.getT3();

                return Flux.fromIterable(orderIds)
                    .filter(orders::containsKey)
                    .map(id -> {
                        Order order = orders.get(id);
                        return new OrderDetails(
                            order,
                            users.get(order.getUserId()),
                            items.getOrDefault(id, List.of())
                        );
                    });
            });
    }

    /**
     * Create order with items in a transaction
     */
    @Transactional
    public Mono<OrderDetails> createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        // Calculate total
        BigDecimal total = itemRequests.stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(userId, total);

        return orderRepository.save(order)
            .flatMap(savedOrder -> {
                // Create items
                List<OrderItem> items = itemRequests.stream()
                    .map(req -> new OrderItem(
                        savedOrder.getId(),
                        req.productName(),
                        req.quantity(),
                        req.unitPrice()
                    ))
                    .toList();

                return orderItemRepository.saveAll(items)
                    .collectList()
                    .zipWith(userRepository.findById(userId))
                    .map(tuple -> new OrderDetails(savedOrder, tuple.getT2(), tuple.getT1()));
            })
            .doOnSuccess(details ->
                log.info("Created order {} for user {}", details.order().getId(), userId));
    }

    /**
     * Get user order summary using DatabaseClient
     */
    public Flux<UserOrderSummary> getUserOrderSummaries() {
        return databaseClient.sql("""
            SELECT
                u.id as user_id,
                u.first_name || ' ' || u.last_name as user_name,
                u.email,
                COUNT(o.id) as order_count,
                COALESCE(SUM(o.total_amount), 0) as total_spent
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            GROUP BY u.id, u.first_name, u.last_name, u.email
            ORDER BY total_spent DESC
            """)
            .map((row, metadata) -> new UserOrderSummary(
                row.get("user_id", Long.class),
                row.get("user_name", String.class),
                row.get("email", String.class),
                row.get("order_count", Long.class),
                row.get("total_spent", BigDecimal.class)
            ))
            .all();
    }

    public record OrderItemRequest(String productName, int quantity, BigDecimal unitPrice) {}
}
```

### Step 3.3: Create Order Controller

```java
package com.example.reactivedata.controller;

import com.example.reactivedata.dto.OrderDetails;
import com.example.reactivedata.dto.UserOrderSummary;
import com.example.reactivedata.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<OrderDetails>> getOrderDetails(@PathVariable Long id) {
        return orderService.getOrderDetails(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public Flux<OrderDetails> getUserOrders(@PathVariable Long userId) {
        return orderService.getUserOrdersWithDetails(userId);
    }

    @GetMapping("/summaries")
    public Flux<UserOrderSummary> getUserSummaries() {
        return orderService.getUserOrderSummaries();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderDetails> createOrder(@RequestBody CreateOrderRequest request) {
        List<OrderService.OrderItemRequest> items = request.items().stream()
            .map(item -> new OrderService.OrderItemRequest(
                item.productName(),
                item.quantity(),
                item.unitPrice()
            ))
            .toList();
        return orderService.createOrder(request.userId(), items);
    }

    public record CreateOrderRequest(Long userId, List<OrderItemDto> items) {}
    public record OrderItemDto(String productName, int quantity, BigDecimal unitPrice) {}
}
```

### Step 3.4: Test Relationship Handling

```bash
# Get order with details
curl http://localhost:8080/api/orders/1

# Get user's orders
curl http://localhost:8080/api/orders/user/1

# Get user summaries
curl http://localhost:8080/api/orders/summaries

# Create new order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "items": [
      {"productName": "New Product", "quantity": 3, "unitPrice": 29.99}
    ]
  }'
```

---

## Part 4: Reactive MongoDB (25 min)

### Step 4.1: Create MongoDB Document

```java
package com.example.reactivedata.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String name;

    private String description;

    private BigDecimal price;

    @Field("category_id")
    private String categoryId;

    private List<String> tags;

    private Map<String, Object> attributes;

    private boolean active;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public Product() {}

    public Product(String sku, String name, String description, BigDecimal price, String categoryId) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.categoryId = categoryId;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

### Step 4.2: Create MongoDB Repository

```java
package com.example.reactivedata.repository;

import com.example.reactivedata.document.Product;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ProductRepository extends ReactiveMongoRepository<Product, String> {

    Mono<Product> findBySku(String sku);

    Flux<Product> findByCategoryId(String categoryId);

    Flux<Product> findByTagsContaining(String tag);

    Flux<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    Flux<Product> findByActiveTrue();

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Flux<Product> searchByName(String namePattern);

    @Query("{ 'tags': { $all: ?0 } }")
    Flux<Product> findByAllTags(java.util.List<String> tags);
}
```

### Step 4.3: Create Product Service with Advanced Queries

```java
package com.example.reactivedata.service;

import com.example.reactivedata.document.Product;
import com.example.reactivedata.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository,
                          ReactiveMongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Mono<Product> createProduct(Product product) {
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return productRepository.save(product)
            .doOnSuccess(p -> log.info("Created product: {}", p.getSku()));
    }

    public Mono<Product> findById(String id) {
        return productRepository.findById(id);
    }

    public Mono<Product> findBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    public Flux<Product> findAll() {
        return productRepository.findAll();
    }

    public Flux<Product> findByCategory(String categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public Flux<Product> searchProducts(ProductSearchCriteria criteria) {
        Query query = new Query();

        if (criteria.getCategoryId() != null) {
            query.addCriteria(Criteria.where("categoryId").is(criteria.getCategoryId()));
        }

        if (criteria.getMinPrice() != null) {
            query.addCriteria(Criteria.where("price").gte(criteria.getMinPrice()));
        }

        if (criteria.getMaxPrice() != null) {
            query.addCriteria(Criteria.where("price").lte(criteria.getMaxPrice()));
        }

        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").all(criteria.getTags()));
        }

        if (criteria.getSearchText() != null && !criteria.getSearchText().isBlank()) {
            query.addCriteria(Criteria.where("name").regex(criteria.getSearchText(), "i"));
        }

        if (criteria.isActiveOnly()) {
            query.addCriteria(Criteria.where("active").is(true));
        }

        // Sorting
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        // Pagination
        if (criteria.getOffset() > 0) {
            query.skip(criteria.getOffset());
        }
        if (criteria.getLimit() > 0) {
            query.limit(criteria.getLimit());
        }

        return mongoTemplate.find(query, Product.class);
    }

    public Mono<Product> updateProduct(String id, Map<String, Object> updates) {
        return productRepository.findById(id)
            .flatMap(product -> {
                if (updates.containsKey("name")) {
                    product.setName((String) updates.get("name"));
                }
                if (updates.containsKey("price")) {
                    product.setPrice(new BigDecimal(updates.get("price").toString()));
                }
                if (updates.containsKey("tags")) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) updates.get("tags");
                    product.setTags(tags);
                }
                if (updates.containsKey("active")) {
                    product.setActive((Boolean) updates.get("active"));
                }
                product.setUpdatedAt(Instant.now());
                return productRepository.save(product);
            });
    }

    public Mono<Void> deleteProduct(String id) {
        return productRepository.deleteById(id);
    }

    // Search criteria class
    public static class ProductSearchCriteria {
        private String categoryId;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private List<String> tags;
        private String searchText;
        private boolean activeOnly = true;
        private int offset = 0;
        private int limit = 20;

        // Getters and setters
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }

        public boolean isActiveOnly() { return activeOnly; }
        public void setActiveOnly(boolean activeOnly) { this.activeOnly = activeOnly; }

        public int getOffset() { return offset; }
        public void setOffset(int offset) { this.offset = offset; }

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
    }
}
```

### Step 4.4: Create Product Controller

```java
package com.example.reactivedata.controller;

import com.example.reactivedata.document.Product;
import com.example.reactivedata.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Flux<Product> getAllProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProduct(@PathVariable String id) {
        return productService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    public Mono<ResponseEntity<Product>> getProductBySku(@PathVariable String sku) {
        return productService.findBySku(sku)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Flux<Product> searchProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        ProductService.ProductSearchCriteria criteria = new ProductService.ProductSearchCriteria();
        criteria.setCategoryId(category);
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setTags(tags);
        criteria.setSearchText(q);
        criteria.setOffset(offset);
        criteria.setLimit(limit);

        return productService.searchProducts(criteria);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Product> createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Product>> updateProduct(@PathVariable String id,
                                                        @RequestBody Map<String, Object> updates) {
        return productService.updateProduct(id, updates)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProduct(@PathVariable String id) {
        return productService.deleteProduct(id);
    }
}
```

### Step 4.5: Test MongoDB Operations

```bash
# Create products
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "WIDGET-001",
    "name": "Premium Widget",
    "description": "A high-quality widget",
    "price": 29.99,
    "categoryId": "widgets",
    "tags": ["premium", "bestseller"]
  }'

# Get all products
curl http://localhost:8080/api/products

# Search products
curl "http://localhost:8080/api/products/search?q=widget&minPrice=10&maxPrice=50"
```

---

## Part 5: Reactive Redis Caching (20 min)

### Step 5.1: Configure Redis Template

```java
package com.example.reactivedata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(redisObjectMapper, Object.class);

        RedisSerializationContext<String, Object> context =
            RedisSerializationContext.<String, Object>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(serializer)
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
```

### Step 5.2: Create Caching Service

```java
package com.example.reactivedata.service;

import com.example.reactivedata.entity.User;
import com.example.reactivedata.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class CachedUserService {

    private static final Logger log = LoggerFactory.getLogger(CachedUserService.class);
    private static final String USER_CACHE_PREFIX = "user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CachedUserService(UserRepository userRepository,
                             ReactiveRedisTemplate<String, Object> redisTemplate,
                             ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<User> findById(Long id) {
        String cacheKey = USER_CACHE_PREFIX + id;

        return redisTemplate.opsForValue().get(cacheKey)
            .map(cached -> objectMapper.convertValue(cached, User.class))
            .doOnNext(user -> log.debug("Cache HIT for user {}", id))
            .switchIfEmpty(
                userRepository.findById(id)
                    .doOnNext(user -> log.debug("Cache MISS for user {}", id))
                    .flatMap(user -> cacheUser(cacheKey, user).thenReturn(user))
            );
    }

    public Mono<User> findByEmail(String email) {
        String cacheKey = USER_CACHE_PREFIX + "email:" + email;

        return redisTemplate.opsForValue().get(cacheKey)
            .map(cached -> objectMapper.convertValue(cached, User.class))
            .switchIfEmpty(
                userRepository.findByEmail(email)
                    .flatMap(user -> cacheUser(cacheKey, user).thenReturn(user))
            );
    }

    public Mono<User> updateAndInvalidateCache(Long id, String firstName, String lastName) {
        return userRepository.findById(id)
            .flatMap(user -> {
                user.setFirstName(firstName);
                user.setLastName(lastName);
                return userRepository.save(user);
            })
            .flatMap(user -> invalidateCache(id).thenReturn(user));
    }

    private Mono<Boolean> cacheUser(String key, User user) {
        return redisTemplate.opsForValue()
            .set(key, user, CACHE_TTL)
            .doOnSuccess(result -> log.debug("Cached user at key: {}", key));
    }

    private Mono<Void> invalidateCache(Long id) {
        String cacheKey = USER_CACHE_PREFIX + id;
        return redisTemplate.delete(cacheKey)
            .doOnSuccess(count -> log.debug("Invalidated cache for user {}", id))
            .then();
    }
}
```

### Step 5.3: Create Cached User Controller

```java
package com.example.reactivedata.controller;

import com.example.reactivedata.entity.User;
import com.example.reactivedata.service.CachedUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/cached/users")
public class CachedUserController {

    private final CachedUserService cachedUserService;

    public CachedUserController(CachedUserService cachedUserService) {
        this.cachedUserService = cachedUserService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUserById(@PathVariable Long id) {
        return cachedUserService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public Mono<ResponseEntity<User>> getUserByEmail(@PathVariable String email) {
        return cachedUserService.findByEmail(email)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

### Step 5.4: Test Caching

```bash
# First call - cache miss (check logs)
curl http://localhost:8080/api/cached/users/1

# Second call - cache hit (should be faster)
curl http://localhost:8080/api/cached/users/1

# Check Redis
docker exec -it reactive-redis redis-cli
KEYS user:*
GET user:1
```

---

## Part 6: Performance Comparison (15 min)

### Step 6.1: Create Benchmark Controller

```java
package com.example.reactivedata.controller;

import com.example.reactivedata.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final UserRepository userRepository;

    public BenchmarkController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Execute N parallel queries and measure time
     */
    @GetMapping("/parallel")
    public Mono<Map<String, Object>> parallelBenchmark(
            @RequestParam(defaultValue = "100") int count) {

        Instant start = Instant.now();

        return Flux.fromStream(IntStream.rangeClosed(1, count).boxed())
            .flatMap(i -> userRepository.findById(1L), 50) // 50 concurrent
            .collectList()
            .map(results -> {
                Duration duration = Duration.between(start, Instant.now());
                return Map.of(
                    "queries", count,
                    "durationMs", duration.toMillis(),
                    "queriesPerSecond", count * 1000.0 / duration.toMillis(),
                    "results", results.size()
                );
            });
    }

    /**
     * Execute sequential queries
     */
    @GetMapping("/sequential")
    public Mono<Map<String, Object>> sequentialBenchmark(
            @RequestParam(defaultValue = "100") int count) {

        Instant start = Instant.now();

        return Flux.fromStream(IntStream.rangeClosed(1, count).boxed())
            .concatMap(i -> userRepository.findById(1L)) // Sequential
            .collectList()
            .map(results -> {
                Duration duration = Duration.between(start, Instant.now());
                return Map.of(
                    "queries", count,
                    "durationMs", duration.toMillis(),
                    "queriesPerSecond", count * 1000.0 / duration.toMillis(),
                    "results", results.size()
                );
            });
    }
}
```

### Step 6.2: Run Benchmarks

```bash
# Parallel queries
curl "http://localhost:8080/api/benchmark/parallel?count=1000"

# Sequential queries
curl "http://localhost:8080/api/benchmark/sequential?count=100"
```

---

## Part 7: Testing with Testcontainers (15 min)

### Step 7.1: Create Test Configuration

```java
package com.example.reactivedata;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.MongoDBContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    }

    @Bean
    @ServiceConnection
    public MongoDBContainer mongoDBContainer() {
        return new MongoDBContainer("mongo:7");
    }
}
```

### Step 7.2: Create Repository Tests

```java
package com.example.reactivedata.repository;

import com.example.reactivedata.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // Create table
        databaseClient.sql("""
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                email VARCHAR(255) UNIQUE NOT NULL,
                first_name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """)
            .fetch()
            .rowsUpdated()
            .block();

        // Clean up
        userRepository.deleteAll().block();
    }

    @Test
    void shouldSaveAndFindUser() {
        User user = new User("test@example.com", "Test", "User");

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.findByEmail("test@example.com"))
        )
        .assertNext(found -> {
            assertThat(found.getEmail()).isEqualTo("test@example.com");
            assertThat(found.getFirstName()).isEqualTo("Test");
        })
        .verifyComplete();
    }

    @Test
    void shouldFindByLastName() {
        User user1 = new User("john@example.com", "John", "Smith");
        User user2 = new User("jane@example.com", "Jane", "Smith");
        User user3 = new User("bob@example.com", "Bob", "Jones");

        StepVerifier.create(
            userRepository.saveAll(java.util.List.of(user1, user2, user3))
                .thenMany(userRepository.findByLastName("Smith"))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldSearchByName() {
        User user = new User("alice@example.com", "Alice", "Wonder");

        StepVerifier.create(
            userRepository.save(user)
                .thenMany(userRepository.searchByName("alice"))
        )
        .assertNext(found -> assertThat(found.getFirstName()).isEqualTo("Alice"))
        .verifyComplete();
    }
}
```

### Step 7.3: Run Tests

```bash
mvn test
```

---

## Reflection

### Questions to Consider

1. **When would you choose R2DBC over JPA?**
   - High concurrency, simple data models, new projects
   - Avoid when: complex relationships, team expertise in JPA

2. **How does connection pooling differ in reactive?**
   - Fewer connections needed (non-blocking multiplexing)
   - Typical: 10-50 connections vs 100+ for JDBC

3. **What's the N+1 problem in reactive and how do you avoid it?**
   - Same problem: one query per item
   - Solution: Batch fetch with `findAllById`, `collectMap`

4. **When is caching most beneficial?**
   - Read-heavy, infrequently changing data
   - Be careful with invalidation complexity

### Key Takeaways

1. **R2DBC is not JPA** - Simpler but more manual relationship handling
2. **Batch operations are critical** - Always batch fetch related data
3. **Test with real databases** - Testcontainers ensures production parity
4. **Cache strategically** - Adds complexity, use only where needed
5. **Monitor connection pools** - Different sizing than blocking pools

---

## Summary

In this lab, you:

1. Set up R2DBC with PostgreSQL for reactive SQL access
2. Built repositories with derived and custom queries
3. Handled relationships explicitly without JPA magic
4. Integrated MongoDB for document storage
5. Implemented Redis caching with invalidation
6. Benchmarked parallel vs sequential queries
7. Tested with Testcontainers for production parity

You now have the skills to build fully reactive data access layers!
