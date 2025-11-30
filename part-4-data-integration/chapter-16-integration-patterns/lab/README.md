# Lab 16: Building Resilient Integrations

## Objectives

By the end of this lab, you will:

1. Implement the Gateway pattern to aggregate multiple services
2. Configure and use circuit breakers with Resilience4j
3. Apply rate limiting to protect your service
4. Implement caching with Caffeine and Redis
5. Combine multiple resilience patterns together
6. Monitor and observe resilience metrics

## Prerequisites

- Completed Chapters 13-15 (Data, Transactions, Messaging)
- Docker and Docker Compose installed
- Understanding of WebClient and reactive operators

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup | 10 min |
| 2 | Gateway Pattern | 25 min |
| 3 | Circuit Breaker | 25 min |
| 4 | Rate Limiting | 20 min |
| 5 | Caching | 25 min |
| 6 | Combined Resilience | 25 min |
| 7 | Monitoring | 15 min |
| **Total** | | **145 min** |

---

## Part 1: Project Setup (10 min)

### Step 1.1: Create the Project

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>resilient-integration-lab</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
        <resilience4j.version>2.2.0</resilience4j.version>
    </properties>

    <dependencies>
        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Resilience4j -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-reactor</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-circuitbreaker</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-ratelimiter</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-bulkhead</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-micrometer</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>

        <!-- Caching -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Actuator for metrics -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
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
            <groupId>com.github.tomakehurst</groupId>
            <artifactId>wiremock-jre8-standalone</artifactId>
            <version>3.0.1</version>
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
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  # Simulated external services
  user-service:
    image: wiremock/wiremock:3.0.1
    ports:
      - "8081:8080"
    volumes:
      - ./wiremock/user-service:/home/wiremock
    command: --verbose

  order-service:
    image: wiremock/wiremock:3.0.1
    ports:
      - "8082:8080"
    volumes:
      - ./wiremock/order-service:/home/wiremock
    command: --verbose

  product-service:
    image: wiremock/wiremock:3.0.1
    ports:
      - "8083:8080"
    volumes:
      - ./wiremock/product-service:/home/wiremock
    command: --verbose
```

### Step 1.3: WireMock Stubs

Create `wiremock/user-service/mappings/get-user.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": "/users/.*"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "id": "{{request.pathSegments.[1]}}",
      "name": "John Doe",
      "email": "john@example.com"
    },
    "fixedDelayMilliseconds": 100
  }
}
```

Create `wiremock/order-service/mappings/get-orders.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": "/orders/user/.*"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": [
      {"orderId": "ORD-001", "amount": 99.99, "status": "DELIVERED"},
      {"orderId": "ORD-002", "amount": 149.99, "status": "PENDING"}
    ],
    "fixedDelayMilliseconds": 200
  }
}
```

Create `wiremock/product-service/mappings/get-recommendations.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": "/recommendations/.*"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": [
      {"productId": "P001", "name": "Widget Pro", "price": 29.99},
      {"productId": "P002", "name": "Gadget Plus", "price": 49.99}
    ],
    "fixedDelayMilliseconds": 150
  }
}
```

### Step 1.4: Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: resilient-integration-lab

  data:
    redis:
      host: localhost
      port: 6379

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: ${spring.application.name}

external-services:
  user-service:
    base-url: http://localhost:8081
  order-service:
    base-url: http://localhost:8082
  product-service:
    base-url: http://localhost:8083

logging:
  level:
    io.github.resilience4j: DEBUG
    com.example: DEBUG
```

### Step 1.5: Start Infrastructure

```bash
docker-compose up -d
```

---

## Part 2: Gateway Pattern (25 min)

### Step 2.1: Create Domain Models

```java
package com.example.integration.model;

import java.math.BigDecimal;
import java.util.List;

public record User(String id, String name, String email) {}

public record Order(String orderId, BigDecimal amount, String status) {}

public record Product(String productId, String name, BigDecimal price) {}

public record DashboardResponse(
    User user,
    List<Order> recentOrders,
    List<Product> recommendations,
    boolean ordersAvailable,
    boolean recommendationsAvailable
) {
    public static DashboardResponse withPartialData(
            User user,
            List<Order> orders,
            List<Product> products) {
        return new DashboardResponse(
            user,
            orders != null ? orders : List.of(),
            products != null ? products : List.of(),
            orders != null && !orders.isEmpty(),
            products != null && !products.isEmpty()
        );
    }
}
```

### Step 2.2: Create Service Clients

```java
package com.example.integration.client;

import com.example.integration.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient webClient;

    public UserServiceClient(
            WebClient.Builder builder,
            @Value("${external-services.user-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<User> getUser(String userId) {
        log.debug("Fetching user: {}", userId);

        return webClient.get()
            .uri("/users/{userId}", userId)
            .retrieve()
            .bodyToMono(User.class)
            .timeout(Duration.ofSeconds(5))
            .doOnNext(user -> log.debug("Received user: {}", user.id()))
            .doOnError(e -> log.error("Error fetching user {}: {}", userId, e.getMessage()));
    }
}
```

```java
package com.example.integration.client;

import com.example.integration.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Service
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final WebClient webClient;

    public OrderServiceClient(
            WebClient.Builder builder,
            @Value("${external-services.order-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Flux<Order> getRecentOrders(String userId) {
        log.debug("Fetching orders for user: {}", userId);

        return webClient.get()
            .uri("/orders/user/{userId}", userId)
            .retrieve()
            .bodyToFlux(Order.class)
            .timeout(Duration.ofSeconds(5))
            .doOnComplete(() -> log.debug("Completed fetching orders for user: {}", userId))
            .doOnError(e -> log.error("Error fetching orders for {}: {}", userId, e.getMessage()));
    }
}
```

```java
package com.example.integration.client;

import com.example.integration.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Service
public class ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceClient.class);

    private final WebClient webClient;

    public ProductServiceClient(
            WebClient.Builder builder,
            @Value("${external-services.product-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Flux<Product> getRecommendations(String userId) {
        log.debug("Fetching recommendations for user: {}", userId);

        return webClient.get()
            .uri("/recommendations/{userId}", userId)
            .retrieve()
            .bodyToFlux(Product.class)
            .timeout(Duration.ofSeconds(3))
            .doOnComplete(() -> log.debug("Completed fetching recommendations"))
            .doOnError(e -> log.error("Error fetching recommendations: {}", e.getMessage()));
    }
}
```

### Step 2.3: Create Dashboard Aggregator

```java
package com.example.integration.service;

import com.example.integration.client.OrderServiceClient;
import com.example.integration.client.ProductServiceClient;
import com.example.integration.client.UserServiceClient;
import com.example.integration.model.DashboardResponse;
import com.example.integration.model.Order;
import com.example.integration.model.Product;
import com.example.integration.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
public class DashboardAggregator {

    private static final Logger log = LoggerFactory.getLogger(DashboardAggregator.class);

    private final UserServiceClient userService;
    private final OrderServiceClient orderService;
    private final ProductServiceClient productService;

    public DashboardAggregator(
            UserServiceClient userService,
            OrderServiceClient orderService,
            ProductServiceClient productService) {
        this.userService = userService;
        this.orderService = orderService;
        this.productService = productService;
    }

    /**
     * Aggregates data from multiple services.
     * User is required, orders and recommendations are optional.
     */
    public Mono<DashboardResponse> getDashboard(String userId) {
        log.info("Building dashboard for user: {}", userId);

        // User is essential - if it fails, the whole request fails
        Mono<User> userMono = userService.getUser(userId);

        // Orders and recommendations are optional - graceful degradation
        Mono<List<Order>> ordersMono = orderService.getRecentOrders(userId)
            .collectList()
            .onErrorReturn(Collections.emptyList())
            .defaultIfEmpty(Collections.emptyList());

        Mono<List<Product>> productsMono = productService.getRecommendations(userId)
            .collectList()
            .onErrorReturn(Collections.emptyList())
            .defaultIfEmpty(Collections.emptyList());

        // Fetch all in parallel
        return Mono.zip(userMono, ordersMono, productsMono)
            .map(tuple -> DashboardResponse.withPartialData(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3()
            ))
            .doOnSuccess(response -> log.info(
                "Dashboard built: orders={}, recommendations={}",
                response.ordersAvailable(),
                response.recommendationsAvailable()
            ));
    }
}
```

### Step 2.4: Create Controller

```java
package com.example.integration.controller;

import com.example.integration.model.DashboardResponse;
import com.example.integration.service.DashboardAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardAggregator dashboardAggregator;

    public DashboardController(DashboardAggregator dashboardAggregator) {
        this.dashboardAggregator = dashboardAggregator;
    }

    @GetMapping("/dashboard/{userId}")
    public Mono<DashboardResponse> getDashboard(@PathVariable String userId) {
        return dashboardAggregator.getDashboard(userId);
    }
}
```

### Step 2.5: Test the Gateway

```bash
# Test the dashboard endpoint
curl http://localhost:8080/api/dashboard/user123 | jq

# You should see aggregated data from all services
```

---

## Part 3: Circuit Breaker (25 min)

### Step 3.1: Configure Circuit Breakers

```java
package com.example.integration.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)              // Open when 50% of calls fail
            .slowCallRateThreshold(50)             // Open when 50% of calls are slow
            .slowCallDurationThreshold(Duration.ofSeconds(2))  // Slow if > 2s
            .waitDurationInOpenState(Duration.ofSeconds(10))   // Wait 10s before half-open
            .permittedNumberOfCallsInHalfOpenState(3)          // Test with 3 calls
            .minimumNumberOfCalls(5)               // Need 5 calls before evaluating
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)                 // Last 10 calls
            .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public CircuitBreaker userServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("user-service");
    }

    @Bean
    public CircuitBreaker orderServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("order-service");
    }

    @Bean
    public CircuitBreaker productServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("product-service");
    }
}
```

### Step 3.2: Add Circuit Breaker Monitoring

```java
package com.example.integration.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerMonitor {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMonitor.class);

    public CircuitBreakerMonitor(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                .onStateTransition(event -> log.warn(
                    "Circuit Breaker '{}' state change: {} -> {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
                ))
                .onFailureRateExceeded(event -> log.warn(
                    "Circuit Breaker '{}' failure rate exceeded: {}%",
                    event.getCircuitBreakerName(),
                    event.getFailureRate()
                ))
                .onCallNotPermitted(event -> log.warn(
                    "Circuit Breaker '{}' rejected call - circuit is OPEN",
                    event.getCircuitBreakerName()
                ));
        });
    }
}
```

### Step 3.3: Update Service Clients with Circuit Breaker

```java
package com.example.integration.client;

import com.example.integration.model.User;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ResilientUserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientUserServiceClient.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public ResilientUserServiceClient(
            WebClient.Builder builder,
            @Value("${external-services.user-service.base-url}") String baseUrl,
            CircuitBreaker userServiceCircuitBreaker) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.circuitBreaker = userServiceCircuitBreaker;
    }

    public Mono<User> getUser(String userId) {
        log.debug("Fetching user: {} (circuit: {})",
            userId, circuitBreaker.getState());

        return webClient.get()
            .uri("/users/{userId}", userId)
            .retrieve()
            .bodyToMono(User.class)
            .timeout(Duration.ofSeconds(5))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .doOnNext(user -> log.debug("Received user: {}", user.id()))
            .doOnError(e -> log.error("Error fetching user {}: {}", userId, e.getMessage()));
    }

    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }
}
```

### Step 3.4: Create a Failing Service Stub

Add to `wiremock/order-service/mappings/get-orders-failing.json`:

```json
{
  "priority": 1,
  "request": {
    "method": "GET",
    "urlPathPattern": "/orders/user/fail.*"
  },
  "response": {
    "status": 500,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "error": "Service temporarily unavailable"
    }
  }
}
```

### Step 3.5: Test Circuit Breaker

```bash
# Make several failing requests to trigger circuit breaker
for i in {1..10}; do
  curl -s http://localhost:8080/api/dashboard/failuser$i | jq .ordersAvailable
  sleep 0.5
done

# Check circuit breaker state via actuator
curl http://localhost:8080/actuator/health | jq
```

---

## Part 4: Rate Limiting (20 min)

### Step 4.1: Configure Rate Limiter

```java
package com.example.integration.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimiterConfiguration {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))  // Refresh every second
            .limitForPeriod(10)                         // 10 requests per second
            .timeoutDuration(Duration.ofMillis(500))    // Wait 500ms for permit
            .build();

        return RateLimiterRegistry.of(config);
    }

    @Bean
    public RateLimiter apiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("api", RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(20)  // 20 requests/second for general API
            .timeoutDuration(Duration.ZERO)  // Fail immediately
            .build());
    }

    @Bean
    public RateLimiter externalApiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("external-api", RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(5)  // 5 requests/second to external service
            .timeoutDuration(Duration.ofMillis(200))
            .build());
    }
}
```

### Step 4.2: Create Rate-Limited Service

```java
package com.example.integration.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RateLimitedService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedService.class);

    private final RateLimiter apiRateLimiter;
    private final DashboardAggregator dashboardAggregator;

    public RateLimitedService(
            RateLimiter apiRateLimiter,
            DashboardAggregator dashboardAggregator) {
        this.apiRateLimiter = apiRateLimiter;
        this.dashboardAggregator = dashboardAggregator;
    }

    public <T> Mono<T> withRateLimit(Mono<T> operation) {
        return operation
            .transformDeferred(RateLimiterOperator.of(apiRateLimiter))
            .doOnError(RequestNotPermitted.class, e ->
                log.warn("Rate limit exceeded"));
    }
}
```

### Step 4.3: Add Rate Limiting Filter

```java
package com.example.integration.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RateLimitingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiterRegistry registry;

    public RateLimitingFilter(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only rate limit API endpoints
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        // Get or create rate limiter per IP
        String clientIp = getClientIp(exchange);
        RateLimiter limiter = registry.rateLimiter("client-" + clientIp,
            registry.getDefaultConfig());

        boolean permitted = limiter.acquirePermission();
        if (permitted) {
            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded for client: {}", clientIp);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("Retry-After", "1");
            return exchange.getResponse().setComplete();
        }
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }
}
```

### Step 4.4: Test Rate Limiting

```bash
# Rapid requests to trigger rate limiting
for i in {1..30}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/dashboard/user1
done

# You should see some 429 responses
```

---

## Part 5: Caching (25 min)

### Step 5.1: Configure Caffeine Cache

```java
package com.example.integration.config;

import com.example.integration.model.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, User> userCache() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }

    @Bean
    public Cache<String, List<?>> listCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(2))
            .recordStats()
            .build();
    }
}
```

### Step 5.2: Create Caching User Service

```java
package com.example.integration.service;

import com.example.integration.client.ResilientUserServiceClient;
import com.example.integration.model.User;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CachingUserService {

    private static final Logger log = LoggerFactory.getLogger(CachingUserService.class);

    private final ResilientUserServiceClient userClient;
    private final Cache<String, User> userCache;

    public CachingUserService(
            ResilientUserServiceClient userClient,
            Cache<String, User> userCache) {
        this.userClient = userClient;
        this.userCache = userCache;
    }

    public Mono<User> getUser(String userId) {
        // Try cache first
        User cached = userCache.getIfPresent(userId);
        if (cached != null) {
            log.debug("Cache HIT for user: {}", userId);
            return Mono.just(cached);
        }

        log.debug("Cache MISS for user: {}", userId);
        return userClient.getUser(userId)
            .doOnNext(user -> {
                userCache.put(userId, user);
                log.debug("Cached user: {}", userId);
            });
    }

    public void invalidateUser(String userId) {
        userCache.invalidate(userId);
        log.debug("Invalidated cache for user: {}", userId);
    }

    public long getCacheSize() {
        return userCache.estimatedSize();
    }
}
```

### Step 5.3: Redis Cache for Distributed Scenarios

```java
package com.example.integration.service;

import com.example.integration.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String USER_KEY_PREFIX = "user:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final ReactiveRedisTemplate<String, User> redisTemplate;

    public RedisCacheService(ReactiveRedisTemplate<String, User> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<User> getUser(String userId) {
        String key = USER_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
            .doOnNext(user -> log.debug("Redis cache HIT: {}", userId))
            .doOnSubscribe(s -> log.debug("Checking Redis cache for: {}", userId));
    }

    public Mono<Boolean> cacheUser(String userId, User user) {
        String key = USER_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().set(key, user, DEFAULT_TTL)
            .doOnSuccess(success -> log.debug("Cached user in Redis: {}", userId));
    }

    public Mono<Boolean> invalidateUser(String userId) {
        String key = USER_KEY_PREFIX + userId;
        return redisTemplate.delete(key)
            .map(count -> count > 0)
            .doOnSuccess(deleted -> log.debug("Invalidated Redis cache: {}", userId));
    }
}
```

### Step 5.4: Configure Redis Template

```java
package com.example.integration.config;

import com.example.integration.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public ReactiveRedisTemplate<String, User> reactiveRedisUserTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<User> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, User.class);

        RedisSerializationContext<String, User> context =
            RedisSerializationContext.<String, User>newSerializationContext(new StringRedisSerializer())
                .value(serializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
```

### Step 5.5: Create Cache Statistics Endpoint

```java
package com.example.integration.controller;

import com.example.integration.model.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheStatsController {

    private final Cache<String, User> userCache;

    public CacheStatsController(Cache<String, User> userCache) {
        this.userCache = userCache;
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> getCacheStats() {
        CacheStats stats = userCache.stats();
        return Mono.just(Map.of(
            "size", userCache.estimatedSize(),
            "hitCount", stats.hitCount(),
            "missCount", stats.missCount(),
            "hitRate", stats.hitRate(),
            "evictionCount", stats.evictionCount()
        ));
    }
}
```

---

## Part 6: Combined Resilience (25 min)

### Step 6.1: Create Fully Resilient Service

```java
package com.example.integration.service;

import com.example.integration.client.ResilientUserServiceClient;
import com.example.integration.model.User;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class FullyResilientUserService {

    private static final Logger log = LoggerFactory.getLogger(FullyResilientUserService.class);

    private final ResilientUserServiceClient userClient;
    private final Cache<String, User> userCache;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final Bulkhead bulkhead;

    public FullyResilientUserService(
            ResilientUserServiceClient userClient,
            Cache<String, User> userCache,
            CircuitBreaker userServiceCircuitBreaker,
            RateLimiter externalApiRateLimiter) {
        this.userClient = userClient;
        this.userCache = userCache;
        this.circuitBreaker = userServiceCircuitBreaker;
        this.rateLimiter = externalApiRateLimiter;

        // Create bulkhead for concurrency control
        this.bulkhead = Bulkhead.of("user-service", BulkheadConfig.custom()
            .maxConcurrentCalls(20)
            .maxWaitDuration(Duration.ofMillis(500))
            .build());
    }

    public Mono<User> getUser(String userId) {
        // 1. Check cache first (no resilience needed for cache)
        User cached = userCache.getIfPresent(userId);
        if (cached != null) {
            log.debug("Cache hit for user: {}", userId);
            return Mono.just(cached);
        }

        // 2. Cache miss - call external service with full resilience stack
        return fetchUserWithResilience(userId);
    }

    private Mono<User> fetchUserWithResilience(String userId) {
        return userClient.getUser(userId)
            // Apply retry with exponential backoff
            .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                .maxBackoff(Duration.ofSeconds(2))
                .doBeforeRetry(signal -> log.warn(
                    "Retrying user fetch: attempt {}", signal.totalRetries() + 1)))
            // Apply timeout
            .timeout(Duration.ofSeconds(5))
            // Apply bulkhead (limit concurrent calls)
            .transformDeferred(BulkheadOperator.of(bulkhead))
            // Apply rate limiter
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            // Apply circuit breaker
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            // Cache successful result
            .doOnNext(user -> userCache.put(userId, user))
            // Fallback to cached data on failure
            .onErrorResume(e -> {
                log.warn("All resilience measures exhausted, trying fallback: {}", e.getMessage());
                return getFallbackUser(userId);
            });
    }

    private Mono<User> getFallbackUser(String userId) {
        // Try stale cache
        User stale = userCache.getIfPresent(userId);
        if (stale != null) {
            log.info("Returning stale cached user: {}", userId);
            return Mono.just(stale);
        }

        // Return a default user as last resort
        log.warn("No fallback available, returning default user");
        return Mono.just(new User(userId, "Unknown User", "unknown@example.com"));
    }

    public ResilienceStatus getStatus() {
        return new ResilienceStatus(
            circuitBreaker.getState().name(),
            rateLimiter.getMetrics().getAvailablePermissions(),
            bulkhead.getMetrics().getAvailableConcurrentCalls(),
            userCache.estimatedSize()
        );
    }

    public record ResilienceStatus(
        String circuitBreakerState,
        int availableRateLimitPermits,
        int availableBulkheadCalls,
        long cacheSize
    ) {}
}
```

### Step 6.2: Create Status Endpoint

```java
package com.example.integration.controller;

import com.example.integration.service.FullyResilientUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/status")
public class ResilienceStatusController {

    private final FullyResilientUserService userService;

    public ResilienceStatusController(FullyResilientUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/resilience")
    public Mono<FullyResilientUserService.ResilienceStatus> getResilienceStatus() {
        return Mono.just(userService.getStatus());
    }
}
```

### Step 6.3: Test Combined Resilience

```bash
# Check resilience status
curl http://localhost:8080/api/status/resilience | jq

# Simulate load
for i in {1..50}; do
  curl -s -o /dev/null -w "%{http_code} " http://localhost:8080/api/dashboard/user$((i % 5))
done

# Check status again
curl http://localhost:8080/api/status/resilience | jq
```

---

## Part 7: Monitoring (15 min)

### Step 7.1: Configure Micrometer Metrics

```java
package com.example.integration.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(
            CircuitBreakerRegistry registry,
            MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedRateLimiterMetrics rateLimiterMetrics(
            RateLimiterRegistry registry,
            MeterRegistry meterRegistry) {
        TaggedRateLimiterMetrics metrics = TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}
```

### Step 7.2: View Prometheus Metrics

```bash
# Get all resilience metrics
curl http://localhost:8080/actuator/prometheus | grep resilience4j

# Circuit breaker metrics
curl http://localhost:8080/actuator/prometheus | grep circuit

# Rate limiter metrics
curl http://localhost:8080/actuator/prometheus | grep rate_limiter
```

### Step 7.3: Create Health Indicators

```java
package com.example.integration.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CircuitBreakerHealthIndicator implements ReactiveHealthIndicator {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            Health.Builder builder = Health.up();

            registry.getAllCircuitBreakers().forEach(cb -> {
                builder.withDetail(cb.getName(), cb.getState().name());
                if (cb.getState() == CircuitBreaker.State.OPEN) {
                    builder.down();
                }
            });

            return builder.build();
        });
    }
}
```

---

## Reflection

### Key Takeaways

1. **Gateway pattern** enables efficient parallel aggregation with graceful degradation
2. **Circuit breakers** prevent cascading failures by failing fast
3. **Rate limiting** protects your service from being overwhelmed
4. **Caching** reduces latency and provides fallback during failures
5. **Combining patterns** provides defense in depth

### Best Practices

1. **Order matters**: Rate limiter → Cache → Bulkhead → Circuit breaker → Retry → Timeout
2. **Configure sensibly**: Tune thresholds based on actual service behavior
3. **Monitor everything**: Metrics tell you when patterns are activating
4. **Test failure modes**: Use chaos engineering to validate resilience

### Common Pitfalls

1. Timeouts longer than circuit breaker wait duration
2. Retry storms overwhelming recovering services
3. Cache invalidation in distributed environments
4. Over-aggressive rate limiting affecting legitimate users

---

## Summary

In this lab, you:

1. Built an API gateway that aggregates multiple services
2. Implemented circuit breakers to prevent cascading failures
3. Added rate limiting to protect against overload
4. Created caching layers with Caffeine and Redis
5. Combined all patterns for comprehensive resilience
6. Set up monitoring and health checks

Your services are now production-ready with proper resilience patterns!
