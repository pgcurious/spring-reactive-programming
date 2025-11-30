# Chapter 18: Observability

> "Without observability, you're flying blind. With it, you can see not just what happened, but why."
> — Charity Majors

## Introduction

Your reactive application works perfectly in development. It passes all tests. Then it goes to production, and strange things start happening: requests are slow but you don't know why, errors occur but you can't reproduce them, and somewhere in your distributed system, something is consuming resources but you can't identify it.

Observability is the ability to understand what's happening inside your system by examining its outputs. In reactive systems, this is particularly challenging because the asynchronous, non-blocking nature means traditional debugging approaches fail. Thread stacks don't tell the whole story when a single thread handles thousands of concurrent requests.

This chapter covers the three pillars of observability—logging, metrics, and tracing—and how to implement them effectively in reactive applications.

## 18.1 The Observability Challenge in Reactive Systems

### Why Traditional Approaches Fail

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TRADITIONAL vs REACTIVE DEBUGGING                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional (Thread-Per-Request):                                     │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Thread-123                                                   │      │
│   │  ├── UserController.getUser()                                │      │
│   │  │   └── UserService.findById()                             │      │
│   │  │       └── UserRepository.findById()                      │      │
│   │  │           └── [Blocking wait for DB]                     │      │
│   │  │       └── [Return to service]                            │      │
│   │  │   └── [Return to controller]                             │      │
│   │  └── [Return response]                                       │      │
│   └──────────────────────────────────────────────────────────────┘      │
│   • Thread ID = Request ID (for logging)                                │
│   • Stack trace shows full call path                                   │
│   • Thread-local context (MDC) works                                   │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   Reactive (Event Loop):                                                │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  EventLoop-1                                                  │      │
│   │  ├── Handle request A (start)                                │      │
│   │  ├── Handle request B (start)                                │      │
│   │  ├── Handle request A (db callback)                          │      │
│   │  ├── Handle request C (start)                                │      │
│   │  ├── Handle request B (db callback)                          │      │
│   │  ├── Handle request A (complete)                             │      │
│   │  └── ...                                                      │      │
│   └──────────────────────────────────────────────────────────────┘      │
│   • Thread ID ≠ Request ID                                             │
│   • Stack trace shows callback, not original call                      │
│   • Thread-local context is LOST between callbacks                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### The Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THREE PILLARS OF OBSERVABILITY                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐      │
│   │     LOGS        │   │    METRICS      │   │    TRACES       │      │
│   ├─────────────────┤   ├─────────────────┤   ├─────────────────┤      │
│   │                 │   │                 │   │                 │      │
│   │  What happened  │   │  How much/many  │   │  The full path  │      │
│   │  at a point     │   │  over time      │   │  of a request   │      │
│   │  in time        │   │                 │   │                 │      │
│   │                 │   │  • Counters     │   │  Service A      │      │
│   │  "User 123      │   │  • Gauges       │   │     │           │      │
│   │   logged in"    │   │  • Histograms   │   │     ▼           │      │
│   │                 │   │                 │   │  Service B      │      │
│   │  "Error in      │   │  "99th %ile     │   │     │           │      │
│   │   payment"      │   │   latency: 2s"  │   │     ▼           │      │
│   │                 │   │                 │   │  Database       │      │
│   └─────────────────┘   └─────────────────┘   └─────────────────┘      │
│                                                                          │
│   Together they answer:                                                  │
│   • Logs: What specifically happened?                                   │
│   • Metrics: Is this normal? Is it getting worse?                      │
│   • Traces: Where did the request spend its time?                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 18.2 Logging in Reactive Systems

### The MDC Problem

In traditional applications, we use MDC (Mapped Diagnostic Context) to attach context to log messages:

```java
// Traditional - works because thread doesn't change
MDC.put("userId", "123");
MDC.put("requestId", "abc-xyz");
logger.info("Processing request");  // Logs include userId and requestId
userService.findById(123);          // Same thread, MDC still available
logger.info("Request complete");    // Still has MDC context
MDC.clear();
```

In reactive:

```java
// Reactive - MDC is LOST
MDC.put("userId", "123");
MDC.put("requestId", "abc-xyz");
logger.info("Processing request");  // MDC works here
userService.findById(123)           // Switches threads!
    .doOnNext(user ->
        logger.info("Found user")   // MDC is EMPTY - different thread!
    )
    .subscribe();
```

### Solution: Reactor Context

```java
@Service
public class ContextAwareUserService {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareUserService.class);

    public Mono<User> findById(Long id) {
        return Mono.deferContextual(ctx -> {
            // Read from Reactor Context
            String requestId = ctx.getOrDefault("requestId", "unknown");
            String userId = ctx.getOrDefault("userId", "anonymous");

            // Temporarily set MDC for this log statement
            try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
                log.info("Finding user {}", id);
            }

            return userRepository.findById(id);
        });
    }
}
```

### Automatic Context Propagation with Hooks

```java
@Configuration
public class ReactorContextConfig {

    @PostConstruct
    public void setupContextPropagation() {
        // Automatically copy MDC to Reactor Context on subscription
        Hooks.onEachOperator(
            "mdc",
            Operators.lift((scannable, subscriber) ->
                new MDCContextSubscriber<>(subscriber))
        );
    }

    private static class MDCContextSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<T> actual;
        private final Map<String, String> mdcContext;

        MDCContextSubscriber(CoreSubscriber<T> actual) {
            this.actual = actual;
            this.mdcContext = MDC.getCopyOfContextMap();
        }

        @Override
        public Context currentContext() {
            Context context = actual.currentContext();
            if (mdcContext != null) {
                context = context.put("mdc", mdcContext);
            }
            return context;
        }

        @Override
        public void onSubscribe(Subscription s) {
            actual.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                actual.onNext(t);
            } finally {
                MDC.clear();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                actual.onError(t);
            } finally {
                MDC.clear();
            }
        }

        @Override
        public void onComplete() {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                actual.onComplete();
            } finally {
                MDC.clear();
            }
        }
    }
}
```

### Request Logging Filter

```java
@Component
public class RequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        ServerHttpRequest request = exchange.getRequest();
        log.info("Request started: {} {} [requestId={}]",
            request.getMethod(), request.getPath(), requestId);

        return chain.filter(exchange)
            .contextWrite(Context.of("requestId", requestId))
            .doOnSuccess(v -> {
                long duration = System.currentTimeMillis() - startTime;
                log.info("Request completed: {} {} [requestId={}, duration={}ms, status={}]",
                    request.getMethod(), request.getPath(),
                    requestId, duration, exchange.getResponse().getStatusCode());
            })
            .doOnError(e -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Request failed: {} {} [requestId={}, duration={}ms, error={}]",
                    request.getMethod(), request.getPath(),
                    requestId, duration, e.getMessage());
            });
    }
}
```

### Structured Logging

```java
@Service
public class StructuredLoggingService {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingService.class);

    public Mono<Order> processOrder(Order order) {
        return Mono.just(order)
            .doOnNext(o -> log.info("Processing order",
                kv("orderId", o.getId()),
                kv("userId", o.getUserId()),
                kv("amount", o.getAmount()),
                kv("itemCount", o.getItems().size())))
            .flatMap(this::validateOrder)
            .flatMap(this::chargePayment)
            .doOnNext(o -> log.info("Order processed successfully",
                kv("orderId", o.getId()),
                kv("status", "COMPLETED")))
            .doOnError(e -> log.error("Order processing failed",
                kv("orderId", order.getId()),
                kv("error", e.getMessage()),
                kv("errorType", e.getClass().getSimpleName())));
    }

    private static StructuredArgument kv(String key, Object value) {
        return StructuredArguments.keyValue(key, value);
    }
}
```

## 18.3 Metrics with Micrometer

### Setting Up Micrometer

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

### Built-in Reactor Metrics

```java
@Configuration
public class ReactorMetricsConfig {

    @PostConstruct
    public void enableReactorMetrics() {
        // Enable built-in Reactor metrics
        Schedulers.enableMetrics();
        Hooks.enableAutomaticContextPropagation();
    }
}
```

### Custom Business Metrics

```java
@Service
public class MetricsAwareOrderService {

    private final Counter ordersCreated;
    private final Counter ordersFailed;
    private final Timer orderProcessingTime;
    private final AtomicInteger activeOrders;

    public MetricsAwareOrderService(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("orders.created")
            .description("Number of orders created")
            .tag("service", "order-service")
            .register(registry);

        this.ordersFailed = Counter.builder("orders.failed")
            .description("Number of failed orders")
            .tag("service", "order-service")
            .register(registry);

        this.orderProcessingTime = Timer.builder("orders.processing.time")
            .description("Time to process an order")
            .publishPercentileHistogram()
            .register(registry);

        this.activeOrders = registry.gauge("orders.active",
            new AtomicInteger(0));
    }

    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.fromCallable(() -> {
                activeOrders.incrementAndGet();
                return System.nanoTime();
            })
            .flatMap(startTime ->
                doCreateOrder(request)
                    .doOnSuccess(order -> {
                        ordersCreated.increment();
                        orderProcessingTime.record(
                            System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                    })
                    .doOnError(e -> {
                        ordersFailed.increment();
                        orderProcessingTime.record(
                            System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                    })
            )
            .doFinally(signal -> activeOrders.decrementAndGet());
    }

    public Mono<Order> createOrderWithTags(CreateOrderRequest request, String region) {
        return Timer.builder("orders.regional.processing")
            .tag("region", region)
            .tag("type", request.getType())
            .register(meterRegistry)
            .record(() -> doCreateOrder(request));
    }
}
```

### WebClient Metrics

```java
@Configuration
public class WebClientMetricsConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder, MeterRegistry registry) {
        return builder
            .filter(MetricsWebClientFilterFunction.builder(registry)
                .metricsName("http.client.requests")
                .build())
            .build();
    }
}
```

### Database Metrics

```java
@Configuration
public class R2dbcMetricsConfig {

    @Bean
    public ConnectionPoolMetrics connectionPoolMetrics(
            ConnectionPool connectionPool,
            MeterRegistry registry) {
        return new ConnectionPoolMetrics(connectionPool, "r2dbc.pool", registry);
    }
}
```

## 18.4 Distributed Tracing

### The Need for Distributed Tracing

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DISTRIBUTED REQUEST FLOW                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   User Request                                                           │
│        │                                                                 │
│        ▼                                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  API Gateway (50ms)                                              │   │
│   │       │                                                          │   │
│   │       ├───────▶ Auth Service (20ms)                             │   │
│   │       │                                                          │   │
│   │       └───────▶ Order Service (200ms)                           │   │
│   │                      │                                           │   │
│   │                      ├───────▶ Inventory Service (50ms)         │   │
│   │                      │                                           │   │
│   │                      ├───────▶ Payment Service (100ms)          │   │
│   │                      │              │                            │   │
│   │                      │              └───▶ External API (80ms)   │   │
│   │                      │                                           │   │
│   │                      └───────▶ Notification Service (30ms)      │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Question: Request took 400ms. WHERE was the time spent?               │
│   Answer: Distributed tracing shows the complete picture.              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Setting Up Micrometer Tracing

```xml
<dependencies>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
</dependencies>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% in dev, lower in production
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

### Automatic Trace Propagation

```java
@Configuration
public class TracingConfig {

    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
            .observationHandler(new TracingObservationHandler());
        return registry;
    }
}
```

### Custom Spans

```java
@Service
public class TracedOrderService {

    private final Tracer tracer;
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            // Create a new span
            Span span = tracer.nextSpan()
                .name("order.create")
                .tag("userId", request.getUserId())
                .start();

            return Mono.just(request)
                .flatMap(this::validateOrder)
                .flatMap(this::processPayment)
                .flatMap(this::saveOrder)
                .doOnSuccess(order -> {
                    span.tag("orderId", order.getId());
                    span.event("order.created");
                    span.end();
                })
                .doOnError(e -> {
                    span.error(e);
                    span.end();
                })
                .contextWrite(Context.of(Span.class, span));
        });
    }

    private Mono<Order> processPayment(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            Span parentSpan = ctx.get(Span.class);
            Span paymentSpan = tracer.nextSpan(parentSpan)
                .name("payment.process")
                .tag("amount", request.getAmount().toString())
                .start();

            return paymentClient.charge(request)
                .doFinally(signal -> paymentSpan.end());
        });
    }
}
```

### Context Propagation in WebClient

```java
@Configuration
public class TracingWebClientConfig {

    @Bean
    public WebClient tracingWebClient(
            WebClient.Builder builder,
            Tracer tracer) {
        return builder
            .filter((request, next) -> {
                return Mono.deferContextual(ctx -> {
                    Span currentSpan = ctx.getOrDefault(Span.class, null);
                    if (currentSpan != null) {
                        // Inject trace headers
                        ClientRequest tracedRequest = ClientRequest.from(request)
                            .header("X-B3-TraceId", currentSpan.context().traceId())
                            .header("X-B3-SpanId", currentSpan.context().spanId())
                            .build();
                        return next.exchange(tracedRequest);
                    }
                    return next.exchange(request);
                });
            })
            .build();
    }
}
```

### Extracting Trace Context in Incoming Requests

```java
@Component
public class TraceContextFilter implements WebFilter {

    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
        String spanId = exchange.getRequest().getHeaders().getFirst("X-B3-SpanId");

        Span span;
        if (traceId != null && spanId != null) {
            // Continue existing trace
            TraceContext context = TraceContext.newBuilder()
                .traceId(traceId)
                .spanId(spanId)
                .build();
            span = tracer.nextSpan(context).name("http.request").start();
        } else {
            // Start new trace
            span = tracer.nextSpan().name("http.request").start();
        }

        span.tag("http.method", exchange.getRequest().getMethod().name());
        span.tag("http.url", exchange.getRequest().getPath().value());

        return chain.filter(exchange)
            .contextWrite(Context.of(Span.class, span))
            .doFinally(signal -> {
                span.tag("http.status", exchange.getResponse().getStatusCode().toString());
                span.end();
            });
    }
}
```

## 18.5 Health Checks

### Reactive Health Indicators

```java
@Component
public class DatabaseHealthIndicator implements ReactiveHealthIndicator {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Health> health() {
        return databaseClient.sql("SELECT 1")
            .fetch()
            .first()
            .map(result -> Health.up()
                .withDetail("database", "PostgreSQL")
                .build())
            .onErrorResume(e -> Mono.just(Health.down()
                .withDetail("error", e.getMessage())
                .build()))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> Mono.just(Health.down()
                .withDetail("error", "Timeout")
                .build()));
    }
}
```

```java
@Component
public class ExternalServiceHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;

    @Override
    public Mono<Health> health() {
        return webClient.get()
            .uri("/health")
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> Health.up()
                .withDetail("service", "external-api")
                .build())
            .onErrorResume(e -> Mono.just(Health.down()
                .withDetail("service", "external-api")
                .withDetail("error", e.getMessage())
                .build()))
            .timeout(Duration.ofSeconds(3));
    }
}
```

### Composite Health Check

```java
@Component
public class CompositeHealthIndicator implements ReactiveHealthIndicator {

    private final List<ReactiveHealthIndicator> indicators;

    @Override
    public Mono<Health> health() {
        return Flux.fromIterable(indicators)
            .flatMap(ReactiveHealthIndicator::health)
            .collectList()
            .map(healths -> {
                boolean allUp = healths.stream()
                    .allMatch(h -> h.getStatus() == Status.UP);

                Health.Builder builder = allUp ? Health.up() : Health.down();

                for (int i = 0; i < indicators.size(); i++) {
                    builder.withDetail(
                        indicators.get(i).getClass().getSimpleName(),
                        healths.get(i).getStatus().toString()
                    );
                }

                return builder.build();
            });
    }
}
```

## 18.6 Debugging Reactive Code

### Using doOnXxx for Debugging

```java
public Mono<Order> processOrder(String orderId) {
    return orderRepository.findById(orderId)
        .doOnSubscribe(s -> log.debug("Starting to find order: {}", orderId))
        .doOnNext(order -> log.debug("Found order: {}", order))
        .doOnError(e -> log.error("Error finding order {}: {}", orderId, e.getMessage()))
        .doOnCancel(() -> log.warn("Order lookup cancelled: {}", orderId))
        .doOnTerminate(() -> log.debug("Order lookup terminated: {}", orderId))
        .doFinally(signal -> log.debug("Order lookup finished with: {}", signal))
        .flatMap(this::processPayment)
        .doOnNext(order -> log.debug("Payment processed for: {}", orderId));
}
```

### Checkpoint for Better Stack Traces

```java
public Mono<Order> createOrder(CreateOrderRequest request) {
    return validateRequest(request)
        .checkpoint("After validation")
        .flatMap(this::checkInventory)
        .checkpoint("After inventory check")
        .flatMap(this::processPayment)
        .checkpoint("After payment")
        .flatMap(this::saveOrder)
        .checkpoint("After save");
}
```

When an error occurs, the stack trace includes checkpoint information:

```
java.lang.RuntimeException: Payment failed
    ...
    Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
Assembly trace from producer [reactor.core.publisher.MonoFlatMap]:
    checkpoint ⇢ After inventory check
Error has been observed at the following site(s):
    *__checkpoint ⇢ After inventory check
    *__checkpoint ⇢ After payment
```

### Enable Debug Mode (Development Only)

```java
@Configuration
@Profile("dev")
public class ReactorDebugConfig {

    @PostConstruct
    public void enableDebug() {
        // WARNING: Performance impact - development only!
        Hooks.onOperatorDebug();
    }
}
```

### Log Operator for Detailed Tracing

```java
public Flux<Product> searchProducts(String query) {
    return productRepository.search(query)
        .log("ProductSearch", Level.FINE, SignalType.ON_NEXT, SignalType.ON_ERROR)
        .filter(product -> product.isAvailable())
        .log("AfterFilter");
}
```

## 18.7 Alerting and Dashboards

### Prometheus Alerting Rules

```yaml
# prometheus-rules.yml
groups:
  - name: reactive-app-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors per second"

      - alert: HighLatency
        expr: histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "99th percentile latency is {{ $value }} seconds"

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker is open"
          description: "Circuit breaker {{ $labels.name }} is in open state"
```

### Grafana Dashboard Queries

```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  /
rate(http_server_requests_seconds_count[5m])

# Latency percentiles
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# Active subscriptions (Reactor metrics)
reactor_scheduler_tasks_active

# Pending tasks in schedulers
reactor_scheduler_tasks_pending
```

## 18.8 Best Practices

### Logging Best Practices

1. **Use structured logging** for machine-parseable logs
2. **Include request ID** in every log message
3. **Log at appropriate levels**: DEBUG for details, INFO for business events, WARN for recoverable issues, ERROR for failures
4. **Don't log sensitive data**: PII, passwords, tokens

### Metrics Best Practices

1. **Use meaningful names**: `orders.created` not `counter1`
2. **Add relevant tags**: region, status, type
3. **Use histograms for latency**: not just averages
4. **Set up alerts**: before users notice problems

### Tracing Best Practices

1. **Sample appropriately**: 100% in dev, lower in production
2. **Add business context**: user ID, order ID, etc.
3. **Propagate context**: across all service boundaries
4. **Keep spans focused**: one span per logical operation

## Summary

Observability in reactive systems requires different approaches than traditional applications:

| Aspect | Traditional | Reactive |
|--------|-------------|----------|
| **Logging context** | MDC (thread-local) | Reactor Context |
| **Request tracking** | Thread ID | Request ID in context |
| **Stack traces** | Complete call path | Checkpoint annotations |
| **Debugging** | Breakpoints | doOnXxx, log(), checkpoint() |

**Key Takeaways:**

1. **Context is king**: Use Reactor Context, not thread-locals
2. **Three pillars work together**: Logs for details, metrics for trends, traces for paths
3. **Debugging requires new tools**: checkpoint(), log(), doOnXxx()
4. **Automate what you can**: Let frameworks handle propagation
5. **Monitor proactively**: Alerts before users complain

With proper observability, your reactive applications become transparent systems where problems are visible, diagnosable, and fixable—before they impact users.
