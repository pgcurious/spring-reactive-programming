# Lab 18: Observability for Reactive Applications

## Objectives

By the end of this lab, you will:

1. Configure structured logging with context propagation
2. Set up Micrometer metrics with Prometheus
3. Implement distributed tracing with Zipkin
4. Create custom health indicators
5. Build monitoring dashboards
6. Implement alerting rules
7. Debug reactive code with checkpoints

## Prerequisites

- Completed Chapter 17 (Testing)
- Docker and Docker Compose installed
- Basic understanding of Prometheus and Grafana

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup & Infrastructure | 15 min |
| 2 | Structured Logging | 25 min |
| 3 | Metrics with Micrometer | 25 min |
| 4 | Distributed Tracing | 25 min |
| 5 | Health Checks | 15 min |
| 6 | Dashboards & Alerting | 20 min |
| 7 | Debugging Techniques | 15 min |
| **Total** | | **140 min** |

---

## Part 1: Project Setup & Infrastructure (15 min)

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
    <artifactId>observability-lab</artifactId>
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
        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Micrometer Prometheus -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Micrometer Tracing -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>

        <!-- Zipkin Reporter -->
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>

        <!-- Logback with Logstash Encoder -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>

        <!-- R2DBC for database metrics demo -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.r2dbc</groupId>
            <artifactId>r2dbc-h2</artifactId>
            <scope>runtime</scope>
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

### Step 1.2: Docker Compose for Monitoring Stack

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v2.48.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:10.2.2
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana

  zipkin:
    image: openzipkin/zipkin:2.24
    ports:
      - "9411:9411"

  loki:
    image: grafana/loki:2.9.2
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

volumes:
  grafana-data:
```

### Step 1.3: Prometheus Configuration

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

### Step 1.4: Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: observability-lab

  r2dbc:
    url: r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1
    username: sa
    password:

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, info
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

logging:
  level:
    root: INFO
    com.example: DEBUG
    io.micrometer.tracing: DEBUG
```

### Step 1.5: Start Infrastructure

```bash
docker-compose up -d

# Wait for services to start
sleep 10

# Verify services are running
curl http://localhost:9090/-/healthy  # Prometheus
curl http://localhost:9411/health     # Zipkin
curl http://localhost:3000/api/health # Grafana
```

---

## Part 2: Structured Logging (25 min)

### Step 2.1: Configure Logback for JSON

Create `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console appender for development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <!-- File appender for production -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

    <logger name="com.example" level="DEBUG"/>
</configuration>
```

### Step 2.2: Create Request Context Filter

```java
package com.example.observability.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Component
public class RequestContextFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String USER_ID_KEY = "userId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders()
            .getFirst("X-Request-ID");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        String userId = exchange.getRequest().getHeaders()
            .getFirst("X-User-ID");
        if (userId == null) {
            userId = "anonymous";
        }

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        long startTime = System.currentTimeMillis();

        final String finalRequestId = requestId;
        final String finalUserId = userId;

        // Set MDC for initial log
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(USER_ID_KEY, userId);
        log.info("Request started: {} {}", method, path);
        MDC.clear();

        return chain.filter(exchange)
            .contextWrite(Context.of(
                REQUEST_ID_KEY, finalRequestId,
                USER_ID_KEY, finalUserId
            ))
            .doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                MDC.put(REQUEST_ID_KEY, finalRequestId);
                MDC.put(USER_ID_KEY, finalUserId);
                log.info("Request completed: {} {} [status={}, duration={}ms]",
                    method, path,
                    exchange.getResponse().getStatusCode(),
                    duration);
                MDC.clear();
            });
    }
}
```

### Step 2.3: Create Context-Aware Logging Utility

```java
package com.example.observability.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.function.Consumer;

public class ReactiveLogger {

    private final Logger logger;

    private ReactiveLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static ReactiveLogger getLogger(Class<?> clazz) {
        return new ReactiveLogger(clazz);
    }

    public <T> Mono<T> logOnNext(Mono<T> mono, String message, Object... args) {
        return mono.doOnEach(signal -> {
            if (signal.hasValue()) {
                withContext(signal.getContextView(), () ->
                    logger.info(message, args));
            }
        });
    }

    public <T> Mono<T> logOnError(Mono<T> mono, String message) {
        return mono.doOnEach(signal -> {
            if (signal.hasError()) {
                withContext(signal.getContextView(), () ->
                    logger.error(message, signal.getThrowable()));
            }
        });
    }

    public void info(ContextView ctx, String message, Object... args) {
        withContext(ctx, () -> logger.info(message, args));
    }

    public void error(ContextView ctx, String message, Throwable t) {
        withContext(ctx, () -> logger.error(message, t));
    }

    public void debug(ContextView ctx, String message, Object... args) {
        withContext(ctx, () -> logger.debug(message, args));
    }

    private void withContext(ContextView ctx, Runnable action) {
        Map<String, String> mdcContext = extractMdc(ctx);
        try {
            mdcContext.forEach(MDC::put);
            action.run();
        } finally {
            mdcContext.keySet().forEach(MDC::remove);
        }
    }

    private Map<String, String> extractMdc(ContextView ctx) {
        return Map.of(
            "requestId", ctx.getOrDefault("requestId", "unknown"),
            "userId", ctx.getOrDefault("userId", "anonymous")
        );
    }
}
```

### Step 2.4: Use Reactive Logger in Service

```java
package com.example.observability.service;

import com.example.observability.logging.ReactiveLogger;
import com.example.observability.model.Order;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {

    private static final ReactiveLogger log = ReactiveLogger.getLogger(OrderService.class);

    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            log.info(ctx, "Creating order for user: {}, items: {}",
                request.userId(), request.itemCount());

            return validateOrder(request)
                .flatMap(this::processPayment)
                .flatMap(this::saveOrder)
                .doOnEach(signal -> {
                    if (signal.hasValue()) {
                        log.info(signal.getContextView(),
                            "Order created successfully: {}",
                            signal.get().id());
                    }
                });
        });
    }

    private Mono<CreateOrderRequest> validateOrder(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            log.debug(ctx, "Validating order");
            if (request.itemCount() <= 0) {
                return Mono.error(new IllegalArgumentException("Order must have items"));
            }
            return Mono.just(request);
        });
    }

    private Mono<CreateOrderRequest> processPayment(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            log.debug(ctx, "Processing payment for amount: {}", request.amount());
            return Mono.just(request)
                .delayElement(java.time.Duration.ofMillis(100));  // Simulate payment
        });
    }

    private Mono<Order> saveOrder(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            log.debug(ctx, "Saving order to database");
            Order order = new Order(
                UUID.randomUUID().toString(),
                request.userId(),
                request.amount(),
                "CREATED"
            );
            return Mono.just(order);
        });
    }

    public record CreateOrderRequest(String userId, int itemCount, BigDecimal amount) {}
}
```

---

## Part 3: Metrics with Micrometer (25 min)

### Step 3.1: Create Custom Metrics Service

```java
package com.example.observability.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class OrderMetrics {

    private final Counter ordersCreated;
    private final Counter ordersFailed;
    private final Timer orderProcessingTime;
    private final AtomicInteger activeOrders;
    private final DistributionSummary orderAmounts;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("orders.created.total")
            .description("Total number of orders created")
            .tag("service", "order-service")
            .register(registry);

        this.ordersFailed = Counter.builder("orders.failed.total")
            .description("Total number of failed orders")
            .tag("service", "order-service")
            .register(registry);

        this.orderProcessingTime = Timer.builder("orders.processing.duration")
            .description("Time to process an order")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .tag("service", "order-service")
            .register(registry);

        this.activeOrders = registry.gauge("orders.active",
            Tags.of("service", "order-service"),
            new AtomicInteger(0));

        this.orderAmounts = DistributionSummary.builder("orders.amount")
            .description("Order amounts")
            .baseUnit("dollars")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void incrementCreated() {
        ordersCreated.increment();
    }

    public void incrementFailed() {
        ordersFailed.increment();
    }

    public void recordProcessingTime(long durationMs) {
        orderProcessingTime.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public <T> T timeOperation(Supplier<T> operation) {
        return orderProcessingTime.record(operation);
    }

    public void incrementActive() {
        activeOrders.incrementAndGet();
    }

    public void decrementActive() {
        activeOrders.decrementAndGet();
    }

    public void recordAmount(double amount) {
        orderAmounts.record(amount);
    }
}
```

### Step 3.2: Integrate Metrics in Service

```java
package com.example.observability.service;

import com.example.observability.logging.ReactiveLogger;
import com.example.observability.metrics.OrderMetrics;
import com.example.observability.model.Order;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MetricsAwareOrderService {

    private static final ReactiveLogger log = ReactiveLogger.getLogger(MetricsAwareOrderService.class);

    private final OrderMetrics metrics;

    public MetricsAwareOrderService(OrderMetrics metrics) {
        this.metrics = metrics;
    }

    public Mono<Order> createOrder(OrderService.CreateOrderRequest request) {
        long startTime = System.currentTimeMillis();

        return Mono.deferContextual(ctx -> {
            metrics.incrementActive();
            log.info(ctx, "Creating order for user: {}", request.userId());

            return processOrder(request)
                .doOnSuccess(order -> {
                    metrics.incrementCreated();
                    metrics.recordAmount(request.amount().doubleValue());
                    metrics.recordProcessingTime(System.currentTimeMillis() - startTime);
                    log.info(ctx, "Order created: {}", order.id());
                })
                .doOnError(e -> {
                    metrics.incrementFailed();
                    metrics.recordProcessingTime(System.currentTimeMillis() - startTime);
                    log.error(ctx, "Order creation failed", e);
                })
                .doFinally(signal -> metrics.decrementActive());
        });
    }

    private Mono<Order> processOrder(OrderService.CreateOrderRequest request) {
        return Mono.just(new Order(
            UUID.randomUUID().toString(),
            request.userId(),
            request.amount(),
            "CREATED"
        )).delayElement(java.time.Duration.ofMillis(50 + (long)(Math.random() * 200)));
    }
}
```

### Step 3.3: Create Metrics Controller

```java
package com.example.observability.controller;

import com.example.observability.model.Order;
import com.example.observability.service.MetricsAwareOrderService;
import com.example.observability.service.OrderService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MetricsAwareOrderService orderService;

    public OrderController(MetricsAwareOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Mono<Order> createOrder(@RequestBody OrderService.CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/generate-load")
    public Flux<Order> generateLoad(@RequestParam(defaultValue = "10") int count) {
        return Flux.range(1, count)
            .flatMap(i -> {
                var request = new OrderService.CreateOrderRequest(
                    "user-" + i,
                    (int)(Math.random() * 10) + 1,
                    BigDecimal.valueOf(Math.random() * 1000)
                );
                return orderService.createOrder(request);
            }, 5);  // 5 concurrent
    }
}
```

### Step 3.4: Test Metrics

```bash
# Start the application
./mvnw spring-boot:run

# Generate some load
curl "http://localhost:8080/api/orders/generate-load?count=100"

# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep orders

# Expected output:
# orders_created_total 100.0
# orders_processing_duration_seconds_count 100.0
# orders_processing_duration_seconds_sum X.XX
# orders_amount_sum XXXX.XX
```

---

## Part 4: Distributed Tracing (25 min)

### Step 4.1: Create Traced Service

```java
package com.example.observability.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TracedPaymentService {

    private final Tracer tracer;
    private final ExternalPaymentClient paymentClient;

    public TracedPaymentService(Tracer tracer, ExternalPaymentClient paymentClient) {
        this.tracer = tracer;
        this.paymentClient = paymentClient;
    }

    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return Mono.deferContextual(ctx -> {
            Span span = tracer.nextSpan()
                .name("payment.process")
                .tag("userId", request.userId())
                .tag("amount", request.amount().toString())
                .start();

            return validatePayment(request, span)
                .flatMap(req -> chargePayment(req, span))
                .doOnSuccess(result -> {
                    span.tag("paymentId", result.paymentId());
                    span.event("payment.completed");
                    span.end();
                })
                .doOnError(e -> {
                    span.error(e);
                    span.end();
                });
        });
    }

    private Mono<PaymentRequest> validatePayment(PaymentRequest request, Span parentSpan) {
        Span validationSpan = tracer.nextSpan(parentSpan)
            .name("payment.validate")
            .start();

        return Mono.just(request)
            .delayElement(java.time.Duration.ofMillis(20))
            .doFinally(signal -> validationSpan.end());
    }

    private Mono<PaymentResult> chargePayment(PaymentRequest request, Span parentSpan) {
        Span chargeSpan = tracer.nextSpan(parentSpan)
            .name("payment.charge")
            .tag("gateway", "stripe")
            .start();

        return paymentClient.charge(request)
            .doFinally(signal -> chargeSpan.end());
    }

    public record PaymentRequest(String userId, java.math.BigDecimal amount) {}
    public record PaymentResult(String paymentId, String status) {}
}
```

### Step 4.2: Create External Client with Tracing

```java
package com.example.observability.service;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class ExternalPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentClient.class);

    private final WebClient webClient;
    private final Tracer tracer;

    public ExternalPaymentClient(WebClient.Builder builder, Tracer tracer) {
        this.webClient = builder.baseUrl("http://localhost:8081").build();
        this.tracer = tracer;
    }

    public Mono<TracedPaymentService.PaymentResult> charge(
            TracedPaymentService.PaymentRequest request) {
        // Simulate external API call
        return Mono.just(new TracedPaymentService.PaymentResult(
            UUID.randomUUID().toString(),
            "COMPLETED"
        )).delayElement(java.time.Duration.ofMillis(50 + (long)(Math.random() * 100)));
    }
}
```

### Step 4.3: Create Full Traced Order Flow

```java
package com.example.observability.service;

import com.example.observability.model.Order;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FullyTracedOrderService {

    private final Tracer tracer;
    private final TracedPaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public FullyTracedOrderService(
            Tracer tracer,
            TracedPaymentService paymentService,
            InventoryService inventoryService,
            NotificationService notificationService) {
        this.tracer = tracer;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }

    public Mono<Order> createOrder(String userId, int itemCount, BigDecimal amount) {
        return Mono.deferContextual(ctx -> {
            Span orderSpan = tracer.nextSpan()
                .name("order.create")
                .tag("userId", userId)
                .tag("itemCount", String.valueOf(itemCount))
                .start();

            return checkInventory(itemCount, orderSpan)
                .then(processPayment(userId, amount, orderSpan))
                .map(paymentResult -> createOrderRecord(userId, amount, paymentResult))
                .flatMap(order -> sendNotification(order, orderSpan).thenReturn(order))
                .doOnSuccess(order -> {
                    orderSpan.tag("orderId", order.id());
                    orderSpan.event("order.completed");
                    orderSpan.end();
                })
                .doOnError(e -> {
                    orderSpan.error(e);
                    orderSpan.tag("error.type", e.getClass().getSimpleName());
                    orderSpan.end();
                });
        });
    }

    private Mono<Void> checkInventory(int itemCount, Span parentSpan) {
        Span span = tracer.nextSpan(parentSpan)
            .name("inventory.check")
            .start();

        return inventoryService.checkAvailability(itemCount)
            .doFinally(signal -> span.end());
    }

    private Mono<TracedPaymentService.PaymentResult> processPayment(
            String userId, BigDecimal amount, Span parentSpan) {
        Span span = tracer.nextSpan(parentSpan)
            .name("payment.process")
            .tag("amount", amount.toString())
            .start();

        return paymentService.processPayment(
                new TracedPaymentService.PaymentRequest(userId, amount))
            .doFinally(signal -> span.end());
    }

    private Order createOrderRecord(String userId, BigDecimal amount,
            TracedPaymentService.PaymentResult payment) {
        return new Order(
            UUID.randomUUID().toString(),
            userId,
            amount,
            "COMPLETED"
        );
    }

    private Mono<Void> sendNotification(Order order, Span parentSpan) {
        Span span = tracer.nextSpan(parentSpan)
            .name("notification.send")
            .tag("orderId", order.id())
            .start();

        return notificationService.sendOrderConfirmation(order)
            .doFinally(signal -> span.end());
    }
}
```

### Step 4.4: Create Supporting Services

```java
package com.example.observability.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class InventoryService {
    public Mono<Void> checkAvailability(int itemCount) {
        return Mono.delay(java.time.Duration.ofMillis(30))
            .then();
    }
}

@Service
public class NotificationService {
    public Mono<Void> sendOrderConfirmation(com.example.observability.model.Order order) {
        return Mono.delay(java.time.Duration.ofMillis(20))
            .then();
    }
}
```

### Step 4.5: Test Tracing

```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": "user-123", "itemCount": 3, "amount": 99.99}'

# View traces in Zipkin
open http://localhost:9411

# Look for "order.create" traces
# You should see the full trace tree with all spans
```

---

## Part 5: Health Checks (15 min)

### Step 5.1: Create Custom Health Indicators

```java
package com.example.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class DatabaseHealthIndicator implements ReactiveHealthIndicator {

    private final DatabaseClient databaseClient;

    public DatabaseHealthIndicator(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Health> health() {
        return checkDatabase()
            .map(result -> Health.up()
                .withDetail("database", "H2")
                .withDetail("status", "Connected")
                .build())
            .onErrorResume(e -> Mono.just(Health.down()
                .withDetail("database", "H2")
                .withDetail("error", e.getMessage())
                .build()))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> Mono.just(Health.down()
                .withDetail("error", "Timeout")
                .build()));
    }

    private Mono<Long> checkDatabase() {
        return databaseClient.sql("SELECT 1")
            .fetch()
            .rowsUpdated();
    }
}
```

```java
package com.example.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CircuitBreakerHealthIndicator implements ReactiveHealthIndicator {

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final int threshold = 5;

    @Override
    public Mono<Health> health() {
        return Mono.fromSupplier(() -> {
            int failures = failureCount.get();
            if (failures >= threshold) {
                return Health.down()
                    .withDetail("circuitBreaker", "OPEN")
                    .withDetail("failures", failures)
                    .withDetail("threshold", threshold)
                    .build();
            }
            return Health.up()
                .withDetail("circuitBreaker", "CLOSED")
                .withDetail("failures", failures)
                .withDetail("threshold", threshold)
                .build();
        });
    }

    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    public void recordSuccess() {
        failureCount.set(0);
    }
}
```

### Step 5.2: Test Health Endpoints

```bash
# Check health
curl http://localhost:8080/actuator/health | jq

# Expected output:
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "status": "Connected"
      }
    },
    "circuitBreaker": {
      "status": "UP",
      "details": {
        "circuitBreaker": "CLOSED",
        "failures": 0
      }
    }
  }
}
```

---

## Part 6: Dashboards & Alerting (20 min)

### Step 6.1: Import Grafana Dashboard

Access Grafana at http://localhost:3000 (admin/admin).

Create a new dashboard with panels:

**Panel 1: Request Rate**
```promql
rate(http_server_requests_seconds_count[5m])
```

**Panel 2: Error Rate**
```promql
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  /
rate(http_server_requests_seconds_count[5m])
```

**Panel 3: Latency Percentiles**
```promql
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
```

**Panel 4: Orders Created**
```promql
rate(orders_created_total[5m])
```

**Panel 5: Active Orders**
```promql
orders_active
```

### Step 6.2: Create Alert Rules

Add to `prometheus.yml`:

```yaml
rule_files:
  - /etc/prometheus/rules/*.yml

alerting:
  alertmanagers:
    - static_configs:
        - targets: []
```

Create `prometheus-rules.yml`:

```yaml
groups:
  - name: application-alerts
    rules:
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }}"

      - alert: HighLatency
        expr: |
          histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "99th percentile latency is {{ $value }}s"

      - alert: ServiceDown
        expr: up{job="spring-boot-app"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service is down"
          description: "The application is not responding"
```

---

## Part 7: Debugging Techniques (15 min)

### Step 7.1: Use Checkpoints

```java
package com.example.observability.service;

import com.example.observability.model.Order;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public class DebuggableOrderService {

    public Mono<Order> createOrderWithCheckpoints(String userId, BigDecimal amount) {
        return validateUser(userId)
            .checkpoint("After user validation")
            .flatMap(this::checkInventory)
            .checkpoint("After inventory check")
            .flatMap(req -> processPayment(userId, amount))
            .checkpoint("After payment processing")
            .flatMap(payment -> saveOrder(userId, amount))
            .checkpoint("After order save")
            .flatMap(this::sendConfirmation)
            .checkpoint("After confirmation sent");
    }

    private Mono<String> validateUser(String userId) {
        return Mono.just(userId);
    }

    private Mono<String> checkInventory(String userId) {
        return Mono.just(userId);
    }

    private Mono<String> processPayment(String userId, BigDecimal amount) {
        // Simulate occasional failure
        if (Math.random() < 0.1) {
            return Mono.error(new RuntimeException("Payment failed"));
        }
        return Mono.just("payment-" + UUID.randomUUID());
    }

    private Mono<Order> saveOrder(String userId, BigDecimal amount) {
        return Mono.just(new Order(
            UUID.randomUUID().toString(),
            userId,
            amount,
            "CREATED"
        ));
    }

    private Mono<Order> sendConfirmation(Order order) {
        return Mono.just(order);
    }
}
```

### Step 7.2: Use log() Operator

```java
public Mono<Order> createOrderWithLogging(String userId, BigDecimal amount) {
    return validateUser(userId)
        .log("validation", java.util.logging.Level.FINE)
        .flatMap(this::checkInventory)
        .log("inventory")
        .flatMap(req -> processPayment(userId, amount))
        .log("payment")
        .flatMap(payment -> saveOrder(userId, amount))
        .log("save");
}
```

### Step 7.3: Use doOnXxx for Side Effects

```java
public Mono<Order> createOrderWithDebugHooks(String userId, BigDecimal amount) {
    return validateUser(userId)
        .doOnSubscribe(s -> log.debug("Starting order creation"))
        .doOnNext(v -> log.debug("User validated: {}", v))
        .doOnError(e -> log.error("Validation failed", e))
        .flatMap(this::processPayment)
        .doOnNext(p -> log.debug("Payment processed: {}", p))
        .doOnCancel(() -> log.warn("Operation cancelled"))
        .doOnTerminate(() -> log.debug("Operation terminated"))
        .doFinally(signal -> log.debug("Final signal: {}", signal));
}
```

---

## Reflection

### Key Observability Concepts

1. **Structured Logging**: JSON format with consistent context
2. **Context Propagation**: Reactor Context instead of MDC
3. **Metrics**: Counters, gauges, histograms for monitoring
4. **Tracing**: Follow requests across services
5. **Health Checks**: Reactive health indicators

### Best Practices

1. Include request ID in every log message
2. Use percentiles for latency, not averages
3. Add checkpoints for debugging
4. Sample traces appropriately in production
5. Set up alerts before problems occur

### Common Pitfalls

1. Forgetting MDC doesn't work across threads
2. Not propagating trace context in WebClient calls
3. Too many metrics causing performance issues
4. Not testing health checks under failure conditions

---

## Summary

In this lab, you:

1. Set up structured JSON logging with context propagation
2. Created custom metrics with Micrometer
3. Implemented distributed tracing with Zipkin
4. Built reactive health indicators
5. Created monitoring dashboards in Grafana
6. Learned debugging techniques for reactive code

Your reactive application is now fully observable and production-ready!
