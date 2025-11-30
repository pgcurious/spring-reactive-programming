# Lab 11: Building a Service Aggregator with WebClient

## Objectives

By the end of this lab, you will:

1. Configure WebClient with timeouts, connection pooling, and logging
2. Make GET, POST, PUT, and DELETE requests reactively
3. Handle errors and implement retry strategies
4. Execute parallel requests and aggregate results
5. Implement filters for authentication and monitoring
6. Build a real-world dashboard that aggregates multiple services

## Prerequisites

- Completed Chapters 8-10 (WebFlux fundamentals)
- Understanding of Project Reactor (Mono/Flux)
- Java 17+ and Maven installed
- Your favorite IDE

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup with Mock Services | 15 min |
| 2 | Basic WebClient Configuration | 15 min |
| 3 | Making HTTP Requests | 20 min |
| 4 | Error Handling and Retries | 20 min |
| 5 | Parallel Requests and Aggregation | 25 min |
| 6 | Filters and Interceptors | 15 min |
| 7 | Building a Dashboard Service | 20 min |
| 8 | Reflection | 5 min |
| **Total** | | **135 min** |

---

## Part 1: Project Setup with Mock Services (15 min)

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
    <artifactId>webclient-lab</artifactId>
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
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- For metrics and monitoring -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-core</artifactId>
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
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
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

### Step 1.2: Create the Project Structure

```
src/main/java/com/example/webclient/
├── WebClientLabApplication.java
├── config/
│   └── WebClientConfig.java
├── client/
│   ├── UserServiceClient.java
│   ├── OrderServiceClient.java
│   └── InventoryServiceClient.java
├── service/
│   └── DashboardService.java
├── controller/
│   └── DashboardController.java
├── dto/
│   ├── User.java
│   ├── Order.java
│   ├── Product.java
│   └── Dashboard.java
├── filter/
│   └── WebClientFilters.java
├── exception/
│   └── ServiceException.java
└── mock/
    └── MockServiceController.java
```

### Step 1.3: Create the Main Application

```java
package com.example.webclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebClientLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebClientLabApplication.class, args);
    }
}
```

### Step 1.4: Create DTOs

```java
package com.example.webclient.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record User(
    String id,
    String name,
    String email,
    String role,
    LocalDateTime createdAt
) {
    public static User anonymous() {
        return new User("anonymous", "Anonymous", "unknown@example.com", "GUEST", LocalDateTime.now());
    }
}
```

```java
package com.example.webclient.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Order(
    String id,
    String userId,
    List<OrderItem> items,
    BigDecimal total,
    String status,
    LocalDateTime createdAt
) {}
```

```java
package com.example.webclient.dto;

import java.math.BigDecimal;

public record OrderItem(
    String productId,
    String productName,
    int quantity,
    BigDecimal price
) {}
```

```java
package com.example.webclient.dto;

import java.math.BigDecimal;

public record Product(
    String id,
    String name,
    String description,
    BigDecimal price,
    int stockQuantity,
    String category
) {}
```

```java
package com.example.webclient.dto;

import java.util.List;

public record Dashboard(
    User user,
    List<Order> recentOrders,
    List<Product> recommendedProducts,
    DashboardStats stats
) {
    public record DashboardStats(
        int totalOrders,
        java.math.BigDecimal totalSpent,
        int loyaltyPoints
    ) {}
}
```

### Step 1.5: Create Mock Services

We'll create mock endpoints that simulate external services:

```java
package com.example.webclient.mock;

import com.example.webclient.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/mock")
public class MockServiceController {

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, List<Order>> userOrders = new ConcurrentHashMap<>();
    private final List<Product> products;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final Random random = new Random();

    public MockServiceController() {
        // Initialize sample data
        users.put("user-1", new User("user-1", "Alice Johnson", "alice@example.com", "PREMIUM", LocalDateTime.now().minusDays(365)));
        users.put("user-2", new User("user-2", "Bob Smith", "bob@example.com", "STANDARD", LocalDateTime.now().minusDays(180)));
        users.put("user-3", new User("user-3", "Charlie Brown", "charlie@example.com", "PREMIUM", LocalDateTime.now().minusDays(90)));

        userOrders.put("user-1", List.of(
            new Order("order-1", "user-1", List.of(
                new OrderItem("prod-1", "Laptop", 1, new BigDecimal("999.99")),
                new OrderItem("prod-2", "Mouse", 2, new BigDecimal("29.99"))
            ), new BigDecimal("1059.97"), "DELIVERED", LocalDateTime.now().minusDays(30)),
            new Order("order-2", "user-1", List.of(
                new OrderItem("prod-3", "Keyboard", 1, new BigDecimal("149.99"))
            ), new BigDecimal("149.99"), "SHIPPED", LocalDateTime.now().minusDays(5))
        ));

        userOrders.put("user-2", List.of(
            new Order("order-3", "user-2", List.of(
                new OrderItem("prod-4", "Headphones", 1, new BigDecimal("199.99"))
            ), new BigDecimal("199.99"), "PROCESSING", LocalDateTime.now().minusDays(1))
        ));

        products = List.of(
            new Product("prod-1", "Laptop Pro", "High-performance laptop", new BigDecimal("999.99"), 50, "Electronics"),
            new Product("prod-2", "Wireless Mouse", "Ergonomic wireless mouse", new BigDecimal("29.99"), 200, "Electronics"),
            new Product("prod-3", "Mechanical Keyboard", "RGB mechanical keyboard", new BigDecimal("149.99"), 75, "Electronics"),
            new Product("prod-4", "Noise-Canceling Headphones", "Premium wireless headphones", new BigDecimal("199.99"), 100, "Electronics"),
            new Product("prod-5", "USB-C Hub", "7-in-1 USB-C hub", new BigDecimal("49.99"), 150, "Electronics")
        );
    }

    // ==================== User Service Mock ====================

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable String id,
                              @RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay,
                              @RequestHeader(value = "X-Simulate-Error", defaultValue = "false") boolean simulateError) {
        requestCount.incrementAndGet();

        if (simulateError && random.nextInt(100) < 30) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated error"));
        }

        return Mono.justOrEmpty(users.get(id))
            .delayElement(Duration.ofMillis(delay))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")));
    }

    @GetMapping("/users")
    public Flux<User> getAllUsers(@RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay) {
        return Flux.fromIterable(users.values())
            .delayElements(Duration.ofMillis(delay));
    }

    @PostMapping("/users")
    public Mono<User> createUser(@RequestBody CreateUserRequest request) {
        String id = "user-" + (users.size() + 1);
        User user = new User(id, request.name(), request.email(), "STANDARD", LocalDateTime.now());
        users.put(id, user);
        return Mono.just(user);
    }

    public record CreateUserRequest(String name, String email) {}

    // ==================== Order Service Mock ====================

    @GetMapping("/users/{userId}/orders")
    public Flux<Order> getUserOrders(@PathVariable String userId,
                                     @RequestParam(defaultValue = "10") int limit,
                                     @RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay) {
        return Flux.fromIterable(userOrders.getOrDefault(userId, List.of()))
            .take(limit)
            .delayElements(Duration.ofMillis(delay));
    }

    @GetMapping("/orders/{id}")
    public Mono<Order> getOrder(@PathVariable String id) {
        return Flux.fromIterable(userOrders.values())
            .flatMap(Flux::fromIterable)
            .filter(order -> order.id().equals(id))
            .next()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @PostMapping("/orders")
    public Mono<Order> createOrder(@RequestBody CreateOrderRequest request) {
        String orderId = "order-" + System.currentTimeMillis();
        BigDecimal total = request.items().stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(orderId, request.userId(), request.items(), total, "PENDING", LocalDateTime.now());

        userOrders.computeIfAbsent(request.userId(), k -> new java.util.ArrayList<>());
        ((java.util.ArrayList<Order>) userOrders.get(request.userId())).add(order);

        return Mono.just(order);
    }

    public record CreateOrderRequest(String userId, List<OrderItem> items) {}

    // ==================== Product/Inventory Service Mock ====================

    @GetMapping("/products")
    public Flux<Product> getProducts(@RequestParam(required = false) String category,
                                     @RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay) {
        return Flux.fromIterable(products)
            .filter(p -> category == null || p.category().equalsIgnoreCase(category))
            .delayElements(Duration.ofMillis(delay));
    }

    @GetMapping("/products/{id}")
    public Mono<Product> getProduct(@PathVariable String id) {
        return Flux.fromIterable(products)
            .filter(p -> p.id().equals(id))
            .next()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
    }

    @GetMapping("/products/recommended")
    public Flux<Product> getRecommendedProducts(@RequestParam String userId,
                                                @RequestParam(defaultValue = "5") int limit,
                                                @RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay) {
        // Simulate personalized recommendations
        return Flux.fromIterable(products)
            .take(limit)
            .delayElements(Duration.ofMillis(delay));
    }

    // ==================== Stats Endpoint ====================

    @GetMapping("/stats/user/{userId}")
    public Mono<Map<String, Object>> getUserStats(@PathVariable String userId,
                                                   @RequestHeader(value = "X-Simulate-Delay", defaultValue = "0") int delay) {
        List<Order> orders = userOrders.getOrDefault(userId, List.of());
        BigDecimal totalSpent = orders.stream()
            .map(Order::total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Mono.just(Map.of(
            "totalOrders", orders.size(),
            "totalSpent", totalSpent,
            "loyaltyPoints", totalSpent.intValue() / 10
        )).delayElement(Duration.ofMillis(delay));
    }

    // ==================== Debug Endpoints ====================

    @GetMapping("/debug/request-count")
    public Mono<Map<String, Integer>> getRequestCount() {
        return Mono.just(Map.of("count", requestCount.get()));
    }

    @PostMapping("/debug/reset")
    public Mono<Void> reset() {
        requestCount.set(0);
        return Mono.empty();
    }

    // Endpoint that always fails
    @GetMapping("/fail")
    public Mono<String> fail() {
        return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Intentional failure"));
    }

    // Endpoint with variable latency
    @GetMapping("/slow")
    public Mono<String> slow(@RequestParam(defaultValue = "1000") int delayMs) {
        return Mono.just("Response after " + delayMs + "ms")
            .delayElement(Duration.ofMillis(delayMs));
    }
}
```

### Step 1.6: Test the Mock Services

Start the application and verify the mock services work:

```bash
# Get a user
curl http://localhost:8080/mock/users/user-1

# Get user orders
curl http://localhost:8080/mock/users/user-1/orders

# Get products
curl http://localhost:8080/mock/products

# Get recommended products
curl "http://localhost:8080/mock/products/recommended?userId=user-1"

# Get user stats
curl http://localhost:8080/mock/stats/user/user-1

# Test with simulated delay
curl -H "X-Simulate-Delay: 500" http://localhost:8080/mock/users/user-1
```

---

## Part 2: Basic WebClient Configuration (15 min)

### Step 2.1: Create the WebClient Configuration

```java
package com.example.webclient.config;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${app.base-url:http://localhost:8080/mock}")
    private String baseUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // Configure connection pool
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom")
            .maxConnections(100)
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(5))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();

        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10));

        return builder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Client-Name", "webclient-lab")
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.info("Response: {} from {}", response.statusCode(), response.request().getURI());
            return Mono.just(response);
        });
    }
}
```

### Step 2.2: Create Exception Classes

```java
package com.example.webclient.exception;

import org.springframework.http.HttpStatusCode;

public class ServiceException extends RuntimeException {

    private final String serviceName;
    private final HttpStatusCode statusCode;

    public ServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public ServiceException(String serviceName, HttpStatusCode statusCode, String message) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public ServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public String getServiceName() {
        return serviceName;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
```

```java
package com.example.webclient.exception;

public class UserNotFoundException extends ServiceException {
    public UserNotFoundException(String userId) {
        super("UserService", "User not found: " + userId);
    }
}
```

---

## Part 3: Making HTTP Requests (20 min)

### Step 3.1: Create the User Service Client

```java
package com.example.webclient.client;

import com.example.webclient.dto.User;
import com.example.webclient.exception.ServiceException;
import com.example.webclient.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient webClient;

    public UserServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Get a user by ID
     */
    public Mono<User> getUser(String userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .onStatus(status -> status == HttpStatus.NOT_FOUND,
                response -> Mono.error(new UserNotFoundException(userId)))
            .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ServiceException("UserService", response.statusCode(), "Service unavailable")))
            .bodyToMono(User.class)
            .doOnSuccess(user -> log.debug("Retrieved user: {}", user.id()))
            .doOnError(e -> log.error("Failed to get user {}: {}", userId, e.getMessage()));
    }

    /**
     * Get a user, returning anonymous user if not found
     */
    public Mono<User> getUserOrAnonymous(String userId) {
        return getUser(userId)
            .onErrorResume(UserNotFoundException.class, e -> {
                log.warn("User {} not found, returning anonymous", userId);
                return Mono.just(User.anonymous());
            });
    }

    /**
     * Get all users
     */
    public Flux<User> getAllUsers() {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlux(User.class)
            .doOnComplete(() -> log.debug("Retrieved all users"));
    }

    /**
     * Create a new user
     */
    public Mono<User> createUser(String name, String email) {
        record CreateUserRequest(String name, String email) {}

        return webClient.post()
            .uri("/users")
            .bodyValue(new CreateUserRequest(name, email))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new ServiceException("UserService", response.statusCode(), body))))
            .bodyToMono(User.class)
            .doOnSuccess(user -> log.info("Created user: {}", user.id()));
    }
}
```

### Step 3.2: Create the Order Service Client

```java
package com.example.webclient.client;

import com.example.webclient.dto.Order;
import com.example.webclient.dto.OrderItem;
import com.example.webclient.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final WebClient webClient;

    public OrderServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Get orders for a user
     */
    public Flux<Order> getUserOrders(String userId, int limit) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/users/{userId}/orders")
                .queryParam("limit", limit)
                .build(userId))
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ServiceException("OrderService", "Service unavailable")))
            .bodyToFlux(Order.class)
            .doOnComplete(() -> log.debug("Retrieved orders for user: {}", userId));
    }

    /**
     * Get a specific order by ID
     */
    public Mono<Order> getOrder(String orderId) {
        return webClient.get()
            .uri("/orders/{id}", orderId)
            .retrieve()
            .onStatus(status -> status == HttpStatus.NOT_FOUND,
                response -> Mono.empty())
            .bodyToMono(Order.class);
    }

    /**
     * Create a new order
     */
    public Mono<Order> createOrder(String userId, List<OrderItem> items) {
        record CreateOrderRequest(String userId, List<OrderItem> items) {}

        return webClient.post()
            .uri("/orders")
            .bodyValue(new CreateOrderRequest(userId, items))
            .retrieve()
            .bodyToMono(Order.class)
            .doOnSuccess(order -> log.info("Created order: {}", order.id()));
    }

    /**
     * Get orders with fallback to empty list
     */
    public Mono<List<Order>> getUserOrdersSafe(String userId, int limit) {
        return getUserOrders(userId, limit)
            .collectList()
            .onErrorResume(e -> {
                log.warn("Failed to get orders for {}: {}", userId, e.getMessage());
                return Mono.just(List.of());
            });
    }
}
```

### Step 3.3: Create the Inventory Service Client

```java
package com.example.webclient.client;

import com.example.webclient.dto.Product;
import com.example.webclient.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class InventoryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClient.class);

    private final WebClient webClient;

    public InventoryServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Get all products
     */
    public Flux<Product> getProducts() {
        return webClient.get()
            .uri("/products")
            .retrieve()
            .bodyToFlux(Product.class);
    }

    /**
     * Get products by category
     */
    public Flux<Product> getProductsByCategory(String category) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/products")
                .queryParam("category", category)
                .build())
            .retrieve()
            .bodyToFlux(Product.class);
    }

    /**
     * Get recommended products for a user
     */
    public Flux<Product> getRecommendedProducts(String userId, int limit) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/products/recommended")
                .queryParam("userId", userId)
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ServiceException("InventoryService", "Service unavailable")))
            .bodyToFlux(Product.class);
    }

    /**
     * Get a specific product
     */
    public Mono<Product> getProduct(String productId) {
        return webClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(Product.class);
    }

    /**
     * Get user statistics
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getUserStats(String userId) {
        return webClient.get()
            .uri("/stats/user/{userId}", userId)
            .retrieve()
            .bodyToMono(Map.class)
            .map(map -> (Map<String, Object>) map);
    }

    /**
     * Get recommendations with fallback
     */
    public Mono<List<Product>> getRecommendedProductsSafe(String userId, int limit) {
        return getRecommendedProducts(userId, limit)
            .collectList()
            .onErrorResume(e -> {
                log.warn("Failed to get recommendations for {}: {}", userId, e.getMessage());
                return Mono.just(List.of());
            });
    }
}
```

### Step 3.4: Test the Clients

Create a simple test controller:

```java
package com.example.webclient.controller;

import com.example.webclient.client.OrderServiceClient;
import com.example.webclient.client.UserServiceClient;
import com.example.webclient.client.InventoryServiceClient;
import com.example.webclient.dto.Order;
import com.example.webclient.dto.Product;
import com.example.webclient.dto.User;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/test")
public class TestController {

    private final UserServiceClient userClient;
    private final OrderServiceClient orderClient;
    private final InventoryServiceClient inventoryClient;

    public TestController(UserServiceClient userClient,
                          OrderServiceClient orderClient,
                          InventoryServiceClient inventoryClient) {
        this.userClient = userClient;
        this.orderClient = orderClient;
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return userClient.getUser(id);
    }

    @GetMapping("/users/{id}/orders")
    public Flux<Order> getUserOrders(@PathVariable String id) {
        return orderClient.getUserOrders(id, 10);
    }

    @GetMapping("/products")
    public Flux<Product> getProducts() {
        return inventoryClient.getProducts();
    }

    @PostMapping("/users")
    public Mono<User> createUser(@RequestParam String name, @RequestParam String email) {
        return userClient.createUser(name, email);
    }
}
```

Test the endpoints:

```bash
# Get user
curl http://localhost:8080/test/users/user-1

# Get user orders
curl http://localhost:8080/test/users/user-1/orders

# Get products
curl http://localhost:8080/test/products

# Create user
curl -X POST "http://localhost:8080/test/users?name=NewUser&email=new@example.com"
```

---

## Part 4: Error Handling and Retries (20 min)

### Step 4.1: Create Enhanced Clients with Retry Logic

Update `UserServiceClient` with retry support:

```java
package com.example.webclient.client;

import com.example.webclient.dto.User;
import com.example.webclient.exception.ServiceException;
import com.example.webclient.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient webClient;

    public UserServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Get a user by ID with retry logic
     */
    public Mono<User> getUser(String userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .onStatus(status -> status == HttpStatus.NOT_FOUND,
                response -> Mono.error(new UserNotFoundException(userId)))
            .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ServiceException("UserService", response.statusCode(), "Service unavailable")))
            .bodyToMono(User.class)
            .retryWhen(createRetrySpec("getUser"))
            .doOnSuccess(user -> log.debug("Retrieved user: {}", user.id()))
            .doOnError(e -> log.error("Failed to get user {}: {}", userId, e.getMessage()));
    }

    /**
     * Get a user with simulated unreliable service
     */
    public Mono<User> getUserUnreliable(String userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .header("X-Simulate-Error", "true")
            .retrieve()
            .onStatus(status -> status == HttpStatus.NOT_FOUND,
                response -> Mono.error(new UserNotFoundException(userId)))
            .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ServiceException("UserService", response.statusCode(), "Service unavailable")))
            .bodyToMono(User.class)
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.5)
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn("Retrying getUser, attempt {}, error: {}",
                    signal.totalRetries() + 1, signal.failure().getMessage())))
            .doOnSuccess(user -> log.info("Retrieved user {} after retries", user.id()))
            .doOnError(e -> log.error("Failed to get user {} after all retries: {}", userId, e.getMessage()));
    }

    /**
     * Get a user with timeout
     */
    public Mono<User> getUserWithTimeout(String userId, Duration timeout) {
        return getUser(userId)
            .timeout(timeout)
            .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                log.warn("Timeout getting user {}", userId);
                return Mono.error(new ServiceException("UserService", "Request timed out"));
            });
    }

    /**
     * Get a user, returning anonymous user if not found or service fails
     */
    public Mono<User> getUserOrAnonymous(String userId) {
        return getUser(userId)
            .onErrorResume(UserNotFoundException.class, e -> {
                log.warn("User {} not found, returning anonymous", userId);
                return Mono.just(User.anonymous());
            })
            .onErrorResume(ServiceException.class, e -> {
                log.warn("Service error getting user {}, returning anonymous", userId);
                return Mono.just(User.anonymous());
            });
    }

    /**
     * Get all users
     */
    public Flux<User> getAllUsers() {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlux(User.class)
            .retryWhen(createRetrySpec("getAllUsers"))
            .doOnComplete(() -> log.debug("Retrieved all users"));
    }

    /**
     * Create a new user
     */
    public Mono<User> createUser(String name, String email) {
        record CreateUserRequest(String name, String email) {}

        return webClient.post()
            .uri("/users")
            .bodyValue(new CreateUserRequest(name, email))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new ServiceException("UserService", response.statusCode(), body))))
            .bodyToMono(User.class)
            .doOnSuccess(user -> log.info("Created user: {}", user.id()));
    }

    private Retry createRetrySpec(String operation) {
        return Retry.backoff(3, Duration.ofMillis(100))
            .maxBackoff(Duration.ofSeconds(2))
            .jitter(0.5)
            .filter(this::isRetryable)
            .doBeforeRetry(signal -> log.warn("{}: retry attempt {}, error: {}",
                operation, signal.totalRetries() + 1, signal.failure().getMessage()))
            .onRetryExhaustedThrow((spec, signal) -> {
                log.error("{}: retries exhausted", operation);
                return new ServiceException("UserService", "Service unavailable after retries", signal.failure());
            });
    }

    private boolean isRetryable(Throwable throwable) {
        // Retry on 5xx errors and connection issues
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        // Retry on connection errors
        return throwable instanceof WebClientRequestException
            || throwable instanceof ServiceException;
    }
}
```

### Step 4.2: Add Test Endpoints for Error Handling

Add to `TestController`:

```java
@GetMapping("/users/{id}/unreliable")
public Mono<User> getUserUnreliable(@PathVariable String id) {
    return userClient.getUserUnreliable(id);
}

@GetMapping("/users/{id}/with-timeout")
public Mono<User> getUserWithTimeout(@PathVariable String id,
                                      @RequestParam(defaultValue = "5000") long timeoutMs) {
    return userClient.getUserWithTimeout(id, Duration.ofMillis(timeoutMs));
}

@GetMapping("/users/{id}/safe")
public Mono<User> getUserSafe(@PathVariable String id) {
    return userClient.getUserOrAnonymous(id);
}
```

### Step 4.3: Test Error Handling

```bash
# Test retry with unreliable service (may need multiple tries)
curl http://localhost:8080/test/users/user-1/unreliable

# Test timeout (mock service has simulated delay)
curl -H "X-Simulate-Delay: 10000" http://localhost:8080/test/users/user-1/with-timeout?timeoutMs=1000

# Test fallback
curl http://localhost:8080/test/users/non-existent/safe

# Watch the logs to see retry behavior
```

---

## Part 5: Parallel Requests and Aggregation (25 min)

### Step 5.1: Create the Dashboard Service

```java
package com.example.webclient.service;

import com.example.webclient.client.InventoryServiceClient;
import com.example.webclient.client.OrderServiceClient;
import com.example.webclient.client.UserServiceClient;
import com.example.webclient.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final UserServiceClient userClient;
    private final OrderServiceClient orderClient;
    private final InventoryServiceClient inventoryClient;

    public DashboardService(UserServiceClient userClient,
                            OrderServiceClient orderClient,
                            InventoryServiceClient inventoryClient) {
        this.userClient = userClient;
        this.orderClient = orderClient;
        this.inventoryClient = inventoryClient;
    }

    /**
     * Get a complete dashboard by aggregating data from multiple services in parallel
     */
    public Mono<Dashboard> getDashboard(String userId) {
        log.info("Building dashboard for user: {}", userId);
        long startTime = System.currentTimeMillis();

        // Define all service calls
        Mono<User> userMono = userClient.getUserOrAnonymous(userId)
            .timeout(Duration.ofSeconds(3))
            .doOnSuccess(u -> log.debug("User fetched in {}ms", System.currentTimeMillis() - startTime));

        Mono<List<Order>> ordersMono = orderClient.getUserOrdersSafe(userId, 5)
            .timeout(Duration.ofSeconds(3))
            .doOnSuccess(o -> log.debug("Orders fetched in {}ms", System.currentTimeMillis() - startTime));

        Mono<List<Product>> recommendationsMono = inventoryClient.getRecommendedProductsSafe(userId, 5)
            .timeout(Duration.ofSeconds(3))
            .doOnSuccess(p -> log.debug("Recommendations fetched in {}ms", System.currentTimeMillis() - startTime));

        Mono<Dashboard.DashboardStats> statsMono = getUserStats(userId)
            .timeout(Duration.ofSeconds(3))
            .doOnSuccess(s -> log.debug("Stats fetched in {}ms", System.currentTimeMillis() - startTime));

        // Execute all in parallel and combine results
        return Mono.zip(userMono, ordersMono, recommendationsMono, statsMono)
            .map(tuple -> {
                Dashboard dashboard = new Dashboard(
                    tuple.getT1(),  // User
                    tuple.getT2(),  // Orders
                    tuple.getT3(),  // Recommendations
                    tuple.getT4()   // Stats
                );
                log.info("Dashboard built in {}ms", System.currentTimeMillis() - startTime);
                return dashboard;
            })
            .doOnError(e -> log.error("Failed to build dashboard: {}", e.getMessage()));
    }

    /**
     * Get user statistics, with fallback
     */
    private Mono<Dashboard.DashboardStats> getUserStats(String userId) {
        return inventoryClient.getUserStats(userId)
            .map(stats -> new Dashboard.DashboardStats(
                ((Number) stats.getOrDefault("totalOrders", 0)).intValue(),
                new BigDecimal(stats.getOrDefault("totalSpent", 0).toString()),
                ((Number) stats.getOrDefault("loyaltyPoints", 0)).intValue()
            ))
            .onErrorResume(e -> {
                log.warn("Failed to get stats for {}: {}", userId, e.getMessage());
                return Mono.just(new Dashboard.DashboardStats(0, BigDecimal.ZERO, 0));
            });
    }

    /**
     * Get dashboard with sequential calls (for comparison)
     */
    public Mono<Dashboard> getDashboardSequential(String userId) {
        log.info("Building dashboard sequentially for user: {}", userId);
        long startTime = System.currentTimeMillis();

        return userClient.getUserOrAnonymous(userId)
            .flatMap(user -> orderClient.getUserOrdersSafe(userId, 5)
                .flatMap(orders -> inventoryClient.getRecommendedProductsSafe(userId, 5)
                    .flatMap(products -> getUserStats(userId)
                        .map(stats -> {
                            Dashboard dashboard = new Dashboard(user, orders, products, stats);
                            log.info("Dashboard built sequentially in {}ms", System.currentTimeMillis() - startTime);
                            return dashboard;
                        }))));
    }

    /**
     * Get a lightweight dashboard with only essential data
     */
    public Mono<LightweightDashboard> getLightweightDashboard(String userId) {
        Mono<User> userMono = userClient.getUserOrAnonymous(userId);
        Mono<Integer> orderCountMono = orderClient.getUserOrders(userId, 100)
            .count()
            .map(Long::intValue)
            .onErrorReturn(0);

        return Mono.zip(userMono, orderCountMono)
            .map(tuple -> new LightweightDashboard(
                tuple.getT1().name(),
                tuple.getT1().role(),
                tuple.getT2()
            ));
    }

    public record LightweightDashboard(String userName, String role, int orderCount) {}
}
```

### Step 5.2: Create the Dashboard Controller

```java
package com.example.webclient.controller;

import com.example.webclient.dto.Dashboard;
import com.example.webclient.service.DashboardService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get full dashboard (parallel execution)
     */
    @GetMapping("/{userId}")
    public Mono<Dashboard> getDashboard(@PathVariable String userId) {
        return dashboardService.getDashboard(userId);
    }

    /**
     * Get full dashboard (sequential execution - for comparison)
     */
    @GetMapping("/{userId}/sequential")
    public Mono<Dashboard> getDashboardSequential(@PathVariable String userId) {
        return dashboardService.getDashboardSequential(userId);
    }

    /**
     * Get lightweight dashboard
     */
    @GetMapping("/{userId}/light")
    public Mono<DashboardService.LightweightDashboard> getLightweightDashboard(@PathVariable String userId) {
        return dashboardService.getLightweightDashboard(userId);
    }
}
```

### Step 5.3: Compare Parallel vs Sequential

Test with simulated delays to see the difference:

```bash
# First, let's add delay to mock services
# Parallel request (should complete in ~max(delay1, delay2, delay3, delay4))
time curl -H "X-Simulate-Delay: 200" http://localhost:8080/dashboard/user-1

# Sequential request (should complete in ~sum(delay1, delay2, delay3, delay4))
time curl -H "X-Simulate-Delay: 200" http://localhost:8080/dashboard/user-1/sequential

# Lightweight dashboard
curl http://localhost:8080/dashboard/user-1/light
```

Watch the logs to see the timing difference!

---

## Part 6: Filters and Interceptors (15 min)

### Step 6.1: Create WebClient Filters

```java
package com.example.webclient.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WebClientFilters {

    private static final Logger log = LoggerFactory.getLogger(WebClientFilters.class);

    /**
     * Adds a unique request ID to each request
     */
    public static ExchangeFilterFunction requestIdFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            return Mono.just(ClientRequest.from(request)
                .header("X-Request-Id", requestId)
                .build());
        });
    }

    /**
     * Logs request details
     */
    public static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info(">>> {} {} Headers: {}",
                request.method(),
                request.url(),
                sanitizeHeaders(request.headers()));
            return Mono.just(request);
        });
    }

    /**
     * Logs response details with timing
     */
    public static ExchangeFilterFunction logResponseWithTiming() {
        return (request, next) -> {
            long startTime = System.currentTimeMillis();
            return next.exchange(request)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("<<< {} {} - {} ({}ms)",
                        request.method(),
                        request.url(),
                        response.statusCode(),
                        duration);
                });
        };
    }

    /**
     * Adds authentication token
     */
    public static ExchangeFilterFunction authenticationFilter(String token) {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
            Mono.just(ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build()));
    }

    /**
     * Adds authentication token from a provider (dynamic)
     */
    public static ExchangeFilterFunction dynamicAuthFilter(Mono<String> tokenProvider) {
        return (request, next) -> tokenProvider
            .flatMap(token -> next.exchange(ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build()));
    }

    /**
     * Tracks request metrics
     */
    public static ExchangeFilterFunction metricsFilter(RequestMetrics metrics) {
        return (request, next) -> {
            String uri = request.url().getPath();
            long startTime = System.currentTimeMillis();

            return next.exchange(request)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.record(uri, response.statusCode().value(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordError(uri, duration);
                });
        };
    }

    /**
     * Retry filter that retries on specific conditions
     */
    public static ExchangeFilterFunction retryFilter(int maxRetries) {
        return (request, next) -> next.exchange(request)
            .flatMap(response -> {
                if (response.statusCode().is5xxServerError()) {
                    return response.releaseBody()
                        .then(Mono.error(new RuntimeException("Server error: " + response.statusCode())));
                }
                return Mono.just(response);
            })
            .retry(maxRetries);
    }

    private static String sanitizeHeaders(HttpHeaders headers) {
        // Don't log sensitive headers
        HttpHeaders sanitized = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("X-Api-Key")) {
                sanitized.add(name, "[REDACTED]");
            } else {
                sanitized.addAll(name, values);
            }
        });
        return sanitized.toString();
    }

    /**
     * Simple metrics collector
     */
    public static class RequestMetrics {
        private final ConcurrentHashMap<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

        public void record(String uri, int statusCode, long durationMs) {
            requestCounts.computeIfAbsent(uri, k -> new AtomicLong()).incrementAndGet();
            totalDurations.computeIfAbsent(uri, k -> new AtomicLong()).addAndGet(durationMs);
        }

        public void recordError(String uri, long durationMs) {
            errorCounts.computeIfAbsent(uri, k -> new AtomicLong()).incrementAndGet();
            totalDurations.computeIfAbsent(uri, k -> new AtomicLong()).addAndGet(durationMs);
        }

        public ConcurrentHashMap<String, AtomicLong> getRequestCounts() {
            return requestCounts;
        }

        public ConcurrentHashMap<String, AtomicLong> getErrorCounts() {
            return errorCounts;
        }

        public double getAverageDuration(String uri) {
            long count = requestCounts.getOrDefault(uri, new AtomicLong(0)).get();
            long total = totalDurations.getOrDefault(uri, new AtomicLong(0)).get();
            return count > 0 ? (double) total / count : 0;
        }
    }
}
```

### Step 6.2: Update WebClient Configuration with Filters

```java
package com.example.webclient.config;

import com.example.webclient.filter.WebClientFilters;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${app.base-url:http://localhost:8080/mock}")
    private String baseUrl;

    @Bean
    public WebClientFilters.RequestMetrics requestMetrics() {
        return new WebClientFilters.RequestMetrics();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder, WebClientFilters.RequestMetrics metrics) {
        // Configure connection pool
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom")
            .maxConnections(100)
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(5))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();

        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10));

        return builder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Client-Name", "webclient-lab")
            // Add filters
            .filter(WebClientFilters.requestIdFilter())
            .filter(WebClientFilters.metricsFilter(metrics))
            .filter(WebClientFilters.logRequest())
            .filter(WebClientFilters.logResponseWithTiming())
            .build();
    }
}
```

### Step 6.3: Add Metrics Endpoint

Add to `TestController`:

```java
@Autowired
private WebClientFilters.RequestMetrics metrics;

@GetMapping("/metrics")
public Mono<Map<String, Object>> getMetrics() {
    Map<String, Object> result = new java.util.HashMap<>();
    result.put("requestCounts", metrics.getRequestCounts());
    result.put("errorCounts", metrics.getErrorCounts());

    // Calculate average durations
    Map<String, Double> avgDurations = new java.util.HashMap<>();
    metrics.getRequestCounts().keySet().forEach(uri ->
        avgDurations.put(uri, metrics.getAverageDuration(uri)));
    result.put("averageDurations", avgDurations);

    return Mono.just(result);
}
```

### Step 6.4: Test Filters

```bash
# Make some requests
curl http://localhost:8080/test/users/user-1
curl http://localhost:8080/test/users/user-2
curl http://localhost:8080/dashboard/user-1

# Check metrics
curl http://localhost:8080/test/metrics

# Watch logs to see request IDs and timing
```

---

## Part 7: Building a Dashboard Service (20 min)

### Step 7.1: Enhanced Dashboard with Error Resilience

Update `DashboardService` with comprehensive error handling:

```java
/**
 * Get dashboard with comprehensive error handling and partial results
 */
public Mono<EnhancedDashboard> getEnhancedDashboard(String userId) {
    log.info("Building enhanced dashboard for user: {}", userId);
    long startTime = System.currentTimeMillis();

    // Each service call is independent and won't fail the entire dashboard
    Mono<ServiceResult<User>> userResult = userClient.getUserOrAnonymous(userId)
        .map(ServiceResult::success)
        .timeout(Duration.ofSeconds(3))
        .onErrorResume(e -> Mono.just(ServiceResult.failure("UserService", e.getMessage())));

    Mono<ServiceResult<List<Order>>> ordersResult = orderClient.getUserOrdersSafe(userId, 5)
        .map(ServiceResult::success)
        .timeout(Duration.ofSeconds(3))
        .onErrorResume(e -> Mono.just(ServiceResult.failure("OrderService", e.getMessage())));

    Mono<ServiceResult<List<Product>>> productsResult = inventoryClient.getRecommendedProductsSafe(userId, 5)
        .map(ServiceResult::success)
        .timeout(Duration.ofSeconds(3))
        .onErrorResume(e -> Mono.just(ServiceResult.failure("InventoryService", e.getMessage())));

    Mono<ServiceResult<Dashboard.DashboardStats>> statsResult = getUserStats(userId)
        .map(ServiceResult::success)
        .timeout(Duration.ofSeconds(3))
        .onErrorResume(e -> Mono.just(ServiceResult.failure("StatsService", e.getMessage())));

    return Mono.zip(userResult, ordersResult, productsResult, statsResult)
        .map(tuple -> {
            EnhancedDashboard dashboard = new EnhancedDashboard(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4(),
                System.currentTimeMillis() - startTime
            );
            log.info("Enhanced dashboard built in {}ms with {} successful services",
                dashboard.buildTimeMs(), dashboard.successfulServices());
            return dashboard;
        });
}

public record ServiceResult<T>(T data, boolean success, String serviceName, String error) {
    public static <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(data, true, null, null);
    }

    public static <T> ServiceResult<T> failure(String serviceName, String error) {
        return new ServiceResult<>(null, false, serviceName, error);
    }
}

public record EnhancedDashboard(
    ServiceResult<User> user,
    ServiceResult<List<Order>> orders,
    ServiceResult<List<Product>> recommendations,
    ServiceResult<Dashboard.DashboardStats> stats,
    long buildTimeMs
) {
    public int successfulServices() {
        int count = 0;
        if (user.success()) count++;
        if (orders.success()) count++;
        if (recommendations.success()) count++;
        if (stats.success()) count++;
        return count;
    }
}
```

### Step 7.2: Add Enhanced Dashboard Endpoint

Add to `DashboardController`:

```java
/**
 * Get enhanced dashboard with partial results
 */
@GetMapping("/{userId}/enhanced")
public Mono<DashboardService.EnhancedDashboard> getEnhancedDashboard(@PathVariable String userId) {
    return dashboardService.getEnhancedDashboard(userId);
}
```

### Step 7.3: Create a Load Test

```java
package com.example.webclient.controller;

import com.example.webclient.dto.Dashboard;
import com.example.webclient.service.DashboardService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/load-test")
public class LoadTestController {

    private final DashboardService dashboardService;

    public LoadTestController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Run a load test fetching dashboards
     */
    @GetMapping("/run")
    public Mono<Map<String, Object>> runLoadTest(
            @RequestParam(defaultValue = "100") int requests,
            @RequestParam(defaultValue = "10") int concurrency) {

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        AtomicLong totalTime = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        return Flux.range(0, requests)
            .flatMap(i -> {
                String userId = "user-" + ((i % 3) + 1);
                long requestStart = System.currentTimeMillis();

                return dashboardService.getDashboard(userId)
                    .doOnSuccess(d -> {
                        successCount.incrementAndGet();
                        totalTime.addAndGet(System.currentTimeMillis() - requestStart);
                    })
                    .onErrorResume(e -> {
                        errorCount.incrementAndGet();
                        return Mono.empty();
                    });
            }, concurrency)
            .then(Mono.fromCallable(() -> {
                long totalDuration = System.currentTimeMillis() - startTime;
                long success = successCount.get();
                return Map.of(
                    "totalRequests", requests,
                    "successCount", success,
                    "errorCount", errorCount.get(),
                    "totalDurationMs", totalDuration,
                    "averageLatencyMs", success > 0 ? totalTime.get() / success : 0,
                    "requestsPerSecond", totalDuration > 0 ? (requests * 1000.0) / totalDuration : 0
                );
            }));
    }
}
```

### Step 7.4: Run Load Tests

```bash
# Simple load test
curl "http://localhost:8080/load-test/run?requests=50&concurrency=10"

# Higher load
curl "http://localhost:8080/load-test/run?requests=200&concurrency=20"

# Check metrics after
curl http://localhost:8080/test/metrics
```

---

## Part 8: Reflection (5 min)

### Questions to Consider

1. **How does parallel execution improve response time?**
   - Compare the parallel vs sequential dashboard endpoints
   - What's the theoretical speedup when services have similar latency?

2. **When should you use retry vs fallback?**
   - Retry: Transient failures (network glitches, temporary overload)
   - Fallback: Persistent failures (service down, not found)
   - Both: Defense in depth

3. **How do filters help maintain code quality?**
   - Separation of concerns
   - Consistent logging/metrics across all calls
   - Easy to add/remove cross-cutting features

4. **What are the risks of parallel requests?**
   - Can overwhelm downstream services
   - Need proper connection pooling
   - Timeouts become critical

5. **How would you handle authentication in a real system?**
   - Token refresh
   - Different auth for different services
   - Secure token storage

### Key Takeaways

1. **WebClient enables non-blocking HTTP** - No threads wasted waiting for responses

2. **Parallel execution is natural** - Use `Mono.zip()` to execute independent calls concurrently

3. **Error handling is explicit** - Use `onErrorResume`, `retry`, and timeouts for resilience

4. **Filters separate concerns** - Authentication, logging, and metrics don't clutter business logic

5. **Connection pooling matters** - Configure appropriately for your load

### Next Steps

- Add circuit breaker pattern (with Resilience4j)
- Implement request caching
- Add distributed tracing (with Spring Cloud Sleuth)
- Build more complex aggregation scenarios

---

## Summary

In this lab, you:

1. Configured WebClient with timeouts, connection pooling, and logging
2. Made various HTTP requests (GET, POST, PUT, DELETE)
3. Implemented error handling with retries and fallbacks
4. Built a dashboard service aggregating multiple parallel requests
5. Created filters for request tracking and metrics
6. Compared parallel vs sequential execution performance

WebClient is essential for building reactive microservices that efficiently communicate with other services without blocking threads.
