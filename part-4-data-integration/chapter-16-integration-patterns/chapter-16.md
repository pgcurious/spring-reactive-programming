# Chapter 16: Integration Patterns

> "A distributed system is one in which the failure of a computer you didn't even know existed can render your own computer unusable."
> — Leslie Lamport

## Introduction

Throughout Parts II-IV, we've built reactive applications that handle HTTP requests, access databases, manage transactions, and communicate via messages. But real-world systems rarely exist in isolation. They integrate with payment gateways, inventory systems, notification services, external APIs, and legacy systems—each with different protocols, availability guarantees, and failure modes.

This chapter explores the patterns that make these integrations resilient. We'll learn how to aggregate data from multiple sources, protect against cascading failures with circuit breakers, limit request rates, cache expensive operations, and handle the inevitable failures that distributed systems experience.

## 16.1 The Gateway Pattern

### Aggregating Multiple Services

A common scenario: your API needs data from several downstream services to fulfill a single request.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY PATTERN                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client Request: GET /api/dashboard                                    │
│        │                                                                 │
│        ▼                                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                     API Gateway                                   │   │
│   │                                                                   │   │
│   │   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │   │
│   │   │  User   │   │  Order  │   │ Product │   │Analytics│        │   │
│   │   │ Service │   │ Service │   │ Service │   │ Service │        │   │
│   │   └────┬────┘   └────┬────┘   └────┬────┘   └────┬────┘        │   │
│   │        │             │             │             │              │   │
│   │        └─────────────┴─────────────┴─────────────┘              │   │
│   │                           │                                      │   │
│   │                           ▼                                      │   │
│   │                    Aggregate Response                            │   │
│   │                    {                                             │   │
│   │                      user: {...},                                │   │
│   │                      recentOrders: [...],                        │   │
│   │                      recommendedProducts: [...],                 │   │
│   │                      metrics: {...}                              │   │
│   │                    }                                             │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Key insight: All calls happen IN PARALLEL, not sequentially           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Implementing the Gateway

```java
@Service
public class DashboardAggregator {

    private final UserServiceClient userService;
    private final OrderServiceClient orderService;
    private final ProductServiceClient productService;
    private final AnalyticsServiceClient analyticsService;

    public Mono<DashboardResponse> getDashboard(String userId) {
        // All calls execute in parallel using zip
        return Mono.zip(
            userService.getUser(userId),
            orderService.getRecentOrders(userId),
            productService.getRecommendations(userId),
            analyticsService.getUserMetrics(userId)
        ).map(tuple -> new DashboardResponse(
            tuple.getT1(),  // user
            tuple.getT2(),  // orders
            tuple.getT3(),  // products
            tuple.getT4()   // metrics
        ));
    }
}
```

### Handling Partial Failures

What happens when one service is down? With `zip()`, the entire request fails. We need more resilience:

```java
@Service
public class ResilientDashboardAggregator {

    private static final Logger log = LoggerFactory.getLogger(ResilientDashboardAggregator.class);

    public Mono<DashboardResponse> getDashboard(String userId) {
        // Each call has its own fallback
        Mono<User> userMono = userService.getUser(userId)
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> {
                log.warn("User service unavailable, using cached data");
                return getCachedUser(userId);
            });

        Mono<List<Order>> ordersMono = orderService.getRecentOrders(userId)
            .timeout(Duration.ofSeconds(3))
            .onErrorReturn(Collections.emptyList());  // Graceful degradation

        Mono<List<Product>> productsMono = productService.getRecommendations(userId)
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(getDefaultRecommendations());

        Mono<Metrics> metricsMono = analyticsService.getUserMetrics(userId)
            .timeout(Duration.ofSeconds(1))
            .onErrorReturn(Metrics.empty());  // Non-critical, use empty

        return Mono.zip(userMono, ordersMono, productsMono, metricsMono)
            .map(tuple -> new DashboardResponse(
                tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()
            ));
    }
}
```

### Priority-Based Aggregation

Sometimes some data is essential, and some is optional:

```java
public Mono<DashboardResponse> getDashboardWithPriority(String userId) {
    // Essential data - must succeed
    Mono<User> essentialUser = userService.getUser(userId)
        .timeout(Duration.ofSeconds(5))
        .retry(2);

    // Optional data - nice to have
    Mono<List<Product>> optionalProducts = productService.getRecommendations(userId)
        .timeout(Duration.ofSeconds(2))
        .onErrorReturn(Collections.emptyList());

    Mono<Metrics> optionalMetrics = analyticsService.getUserMetrics(userId)
        .timeout(Duration.ofMillis(500))
        .onErrorReturn(Metrics.empty());

    // User is required; products and metrics are optional
    return essentialUser.flatMap(user ->
        Mono.zip(
            Mono.just(user),
            optionalProducts,
            optionalMetrics
        ).map(tuple -> new DashboardResponse(
            tuple.getT1(),
            Collections.emptyList(),  // orders - fetch separately
            tuple.getT2(),
            tuple.getT3()
        ))
    );
}
```

## 16.2 Circuit Breaker Pattern

### The Problem: Cascading Failures

When a downstream service is slow or failing, naive retry logic makes things worse:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CASCADING FAILURE                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Without Circuit Breaker:                                              │
│                                                                          │
│   Service A ──────▶ Service B (slow/failing)                           │
│        │                  │                                             │
│        │     Retries repeatedly                                         │
│        │          │                                                     │
│        ▼          ▼                                                     │
│   Threads pile up waiting                                               │
│        │                                                                 │
│        ▼                                                                 │
│   Service A resources exhausted                                         │
│        │                                                                 │
│        ▼                                                                 │
│   Service A fails                                                        │
│        │                                                                 │
│        ▼                                                                 │
│   All callers of A fail (cascade continues)                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Circuit Breaker States

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CIRCUIT BREAKER STATES                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                    ┌─────────────┐                                      │
│                    │   CLOSED    │◀────────────────────────┐            │
│                    │  (normal)   │                         │            │
│                    └──────┬──────┘                         │            │
│                           │                                 │            │
│              Failure threshold exceeded                     │            │
│                           │                                 │            │
│                           ▼                                 │            │
│                    ┌─────────────┐                         │            │
│                    │    OPEN     │                    Success            │
│                    │  (failing)  │                         │            │
│                    └──────┬──────┘                         │            │
│                           │                                 │            │
│                  Wait time elapsed                          │            │
│                           │                                 │            │
│                           ▼                                 │            │
│                    ┌─────────────┐                         │            │
│                    │ HALF-OPEN   │─────────────────────────┘            │
│                    │  (testing)  │                                      │
│                    └──────┬──────┘                                      │
│                           │                                              │
│                        Failure                                           │
│                           │                                              │
│                           └───────────▶ Back to OPEN                    │
│                                                                          │
│   CLOSED: Requests flow through normally                                │
│   OPEN: Requests fail immediately (fast fail)                           │
│   HALF-OPEN: Limited requests test if service recovered                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Implementing with Resilience4j

Add the dependency:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-reactor</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>2.2.0</version>
</dependency>
```

Configure the circuit breaker:

```java
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)              // Open when 50% fail
            .slowCallRateThreshold(50)             // Open when 50% are slow
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(10)              // Need 10 calls before evaluating
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("payment-service");
    }

    @Bean
    public CircuitBreaker inventoryCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("inventory-service");
    }
}
```

Use the circuit breaker in your service:

```java
@Service
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public PaymentServiceClient(WebClient.Builder builder, CircuitBreaker paymentCircuitBreaker) {
        this.webClient = builder.baseUrl("http://payment-service").build();
        this.circuitBreaker = paymentCircuitBreaker;
    }

    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResult.class)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .doOnError(CallNotPermittedException.class, e ->
                log.warn("Circuit breaker is OPEN for payment service"))
            .onErrorResume(CallNotPermittedException.class, e ->
                Mono.error(new ServiceUnavailableException("Payment service temporarily unavailable")));
    }
}
```

### Custom Fallback Strategies

```java
@Service
public class InventoryServiceClient {

    private final CircuitBreaker circuitBreaker;
    private final CacheService cacheService;

    public Mono<InventoryStatus> checkInventory(String productId) {
        return webClient.get()
            .uri("/inventory/{productId}", productId)
            .retrieve()
            .bodyToMono(InventoryStatus.class)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(this::handleInventoryError);
    }

    private Mono<InventoryStatus> handleInventoryError(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            // Circuit is open - use cached data or pessimistic default
            return cacheService.getCachedInventory(productId)
                .switchIfEmpty(Mono.just(InventoryStatus.unknown()));
        }

        if (e instanceof WebClientResponseException.ServiceUnavailable) {
            return Mono.just(InventoryStatus.unknown());
        }

        return Mono.error(e);
    }
}
```

### Monitoring Circuit Breaker State

```java
@Component
public class CircuitBreakerMonitor {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMonitor.class);

    public CircuitBreakerMonitor(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                .onStateTransition(event -> log.info(
                    "Circuit breaker '{}' state changed: {} -> {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
                ))
                .onFailureRateExceeded(event -> log.warn(
                    "Circuit breaker '{}' failure rate: {}%",
                    event.getCircuitBreakerName(),
                    event.getFailureRate()
                ))
                .onSlowCallRateExceeded(event -> log.warn(
                    "Circuit breaker '{}' slow call rate: {}%",
                    event.getCircuitBreakerName(),
                    event.getSlowCallRate()
                ));
        });
    }
}
```

## 16.3 Rate Limiting

### Why Rate Limit?

Rate limiting protects your service from:
- Abusive clients consuming all resources
- Accidental client bugs causing request floods
- Cascading load during traffic spikes
- Exceeding quotas on downstream services you call

### Token Bucket Algorithm

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TOKEN BUCKET ALGORITHM                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────────────────────────────────┐                           │
│   │     Bucket (capacity: 10 tokens)        │                           │
│   │   ┌───┬───┬───┬───┬───┬───┬───┬───┐    │                           │
│   │   │ T │ T │ T │ T │ T │ T │   │   │    │   Tokens refill at        │
│   │   └───┴───┴───┴───┴───┴───┴───┴───┘    │   fixed rate (e.g., 10/s) │
│   └─────────────────────────────────────────┘                           │
│                      │                                                   │
│                      │ Each request consumes 1 token                    │
│                      ▼                                                   │
│   Request arrives ──▶ Token available? ──Yes──▶ Process request        │
│                              │                                          │
│                              No                                         │
│                              │                                          │
│                              ▼                                          │
│                      Reject with 429 Too Many Requests                  │
│                                                                          │
│   Benefits:                                                              │
│   • Allows bursts up to bucket capacity                                 │
│   • Smooths out traffic over time                                       │
│   • Simple to implement and understand                                  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Implementing with Resilience4j

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))  // Refill period
            .limitForPeriod(100)                         // 100 requests per second
            .timeoutDuration(Duration.ofMillis(500))     // Wait time for permit
            .build();

        return RateLimiterRegistry.of(config);
    }

    @Bean
    public RateLimiter apiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("api");
    }
}
```

Apply to endpoints:

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    private final RateLimiter rateLimiter;
    private final OrderService orderService;

    @GetMapping("/orders")
    public Mono<List<Order>> getOrders() {
        return orderService.getOrders()
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            .onErrorResume(RequestNotPermitted.class, e ->
                Mono.error(new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded")));
    }
}
```

### Per-User Rate Limiting

```java
@Service
public class UserRateLimiterService {

    private final RateLimiterRegistry registry;
    private final RateLimiterConfig userConfig;

    public UserRateLimiterService() {
        this.userConfig = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(10)  // 10 requests/second per user
            .timeoutDuration(Duration.ZERO)  // Fail immediately
            .build();

        this.registry = RateLimiterRegistry.of(userConfig);
    }

    public RateLimiter getRateLimiterForUser(String userId) {
        return registry.rateLimiter("user-" + userId, userConfig);
    }

    public <T> Mono<T> withRateLimit(String userId, Mono<T> operation) {
        RateLimiter limiter = getRateLimiterForUser(userId);
        return operation.transformDeferred(RateLimiterOperator.of(limiter));
    }
}
```

### Reactive Rate Limiter Filter

```java
@Component
public class RateLimitingFilter implements WebFilter {

    private final UserRateLimiterService rateLimiterService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId = extractUserId(exchange);
        if (userId == null) {
            return chain.filter(exchange);
        }

        RateLimiter limiter = rateLimiterService.getRateLimiterForUser(userId);

        return Mono.fromCallable(() -> limiter.acquirePermission())
            .flatMap(permitted -> {
                if (permitted) {
                    return chain.filter(exchange);
                } else {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                }
            });
    }

    private String extractUserId(ServerWebExchange exchange) {
        // Extract from JWT, header, or session
        return exchange.getRequest().getHeaders().getFirst("X-User-Id");
    }
}
```

## 16.4 Caching Patterns

### Why Cache?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CACHING BENEFITS                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Without Cache:                                                         │
│   Request ──▶ Service ──▶ Database ──▶ Response                        │
│                            (100ms)                                       │
│                                                                          │
│   With Cache:                                                            │
│   Request ──▶ Cache Hit? ──Yes──▶ Response (1ms)                        │
│                   │                                                      │
│                   No                                                     │
│                   │                                                      │
│                   ▼                                                      │
│            Service ──▶ Database ──▶ Store in Cache ──▶ Response        │
│                                                                          │
│   Benefits:                                                              │
│   • Reduced latency (1ms vs 100ms)                                      │
│   • Lower database load                                                 │
│   • Protection during downstream failures                               │
│   • Cost savings on external API calls                                  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Cache-Aside Pattern with Caffeine

```java
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, User> userCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }

    @Bean
    public Cache<String, List<Product>> productCache() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();
    }
}
```

```java
@Service
public class CachingUserService {

    private static final Logger log = LoggerFactory.getLogger(CachingUserService.class);

    private final UserRepository userRepository;
    private final Cache<String, User> userCache;

    public Mono<User> getUser(String userId) {
        // Try cache first
        User cached = userCache.getIfPresent(userId);
        if (cached != null) {
            log.debug("Cache hit for user: {}", userId);
            return Mono.just(cached);
        }

        // Cache miss - fetch from database
        log.debug("Cache miss for user: {}", userId);
        return userRepository.findById(userId)
            .doOnNext(user -> userCache.put(userId, user));
    }

    public Mono<User> updateUser(String userId, UserUpdate update) {
        return userRepository.findById(userId)
            .flatMap(user -> {
                user.apply(update);
                return userRepository.save(user);
            })
            .doOnNext(user -> userCache.put(userId, user));  // Update cache
    }

    public Mono<Void> deleteUser(String userId) {
        return userRepository.deleteById(userId)
            .doOnSuccess(v -> userCache.invalidate(userId));  // Invalidate cache
    }
}
```

### Reactive Cache with Reactor Addons

```java
@Service
public class ReactiveCachingService {

    private final CacheMono<String, User> reactiveCache;
    private final UserRepository userRepository;

    public ReactiveCachingService(UserRepository userRepository) {
        this.userRepository = userRepository;

        // Create reactive cache
        this.reactiveCache = CacheMono.lookup(
            key -> Mono.justOrEmpty(caffeine.getIfPresent(key))
                       .map(Signal::next),
            String.class
        ).onCacheMissResume(key -> userRepository.findById(key))
         .andWriteWith((key, signal) -> Mono.fromRunnable(() -> {
             if (signal.get() != null) {
                 caffeine.put(key, signal.get());
             }
         }));
    }

    public Mono<User> getUser(String userId) {
        return reactiveCache.lookup(userId);
    }
}
```

### Distributed Caching with Redis

```java
@Configuration
public class RedisCacheConfig {

    @Bean
    public ReactiveRedisTemplate<String, User> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        RedisSerializationContext<String, User> context =
            RedisSerializationContext.<String, User>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(new Jackson2JsonRedisSerializer<>(User.class))
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(new Jackson2JsonRedisSerializer<>(User.class))
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
```

```java
@Service
public class RedisCachingUserService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ReactiveRedisTemplate<String, User> redisTemplate;
    private final UserRepository userRepository;

    public Mono<User> getUser(String userId) {
        String cacheKey = "user:" + userId;

        return redisTemplate.opsForValue().get(cacheKey)
            .switchIfEmpty(
                userRepository.findById(userId)
                    .flatMap(user ->
                        redisTemplate.opsForValue()
                            .set(cacheKey, user, CACHE_TTL)
                            .thenReturn(user)
                    )
            );
    }

    public Mono<Void> invalidateUser(String userId) {
        return redisTemplate.delete("user:" + userId).then();
    }
}
```

### Cache Stampede Prevention

When a popular cache entry expires, many requests may simultaneously try to refresh it:

```java
@Service
public class StampedeProtectedCache {

    private final Cache<String, User> cache;
    private final Map<String, Mono<User>> inFlight = new ConcurrentHashMap<>();
    private final UserRepository repository;

    public Mono<User> getUser(String userId) {
        User cached = cache.getIfPresent(userId);
        if (cached != null) {
            return Mono.just(cached);
        }

        // Use computeIfAbsent to ensure only one fetch per key
        return inFlight.computeIfAbsent(userId, key ->
            repository.findById(key)
                .doOnNext(user -> cache.put(key, user))
                .doFinally(signal -> inFlight.remove(key))
                .cache()  // Cache the Mono to share among subscribers
        );
    }
}
```

## 16.5 Retry Patterns

### Retry with Exponential Backoff

```java
@Service
public class ResilientExternalApiClient {

    private final WebClient webClient;

    public Mono<ApiResponse> callExternalApi(ApiRequest request) {
        return webClient.post()
            .uri("/api/external")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .jitter(0.5)  // Add randomness to prevent thundering herd
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn(
                    "Retrying external API call: attempt {}, error: {}",
                    signal.totalRetries() + 1,
                    signal.failure().getMessage()
                ))
            );
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            // Retry on 5xx errors and 429 Too Many Requests
            return ex.getStatusCode().is5xxServerError() ||
                   ex.getStatusCode().value() == 429;
        }
        // Retry on connection errors
        return e instanceof ConnectException ||
               e instanceof TimeoutException;
    }
}
```

### Retry with Circuit Breaker

Combine retry and circuit breaker for comprehensive resilience:

```java
@Service
public class FullyResilientClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public FullyResilientClient(WebClient.Builder builder,
                                 CircuitBreakerRegistry cbRegistry,
                                 RetryRegistry retryRegistry) {
        this.webClient = builder.baseUrl("http://external-api").build();
        this.circuitBreaker = cbRegistry.circuitBreaker("external-api");
        this.retry = retryRegistry.retry("external-api");
    }

    public Mono<Data> fetchData(String id) {
        return webClient.get()
            .uri("/data/{id}", id)
            .retrieve()
            .bodyToMono(Data.class)
            // Order matters: retry first, then circuit breaker
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .timeout(Duration.ofSeconds(10))
            .onErrorResume(this::handleError);
    }

    private Mono<Data> handleError(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            return Mono.error(new ServiceUnavailableException("External API unavailable"));
        }
        if (e instanceof TimeoutException) {
            return Mono.error(new ServiceTimeoutException("External API timeout"));
        }
        return Mono.error(e);
    }
}
```

## 16.6 Bulkhead Pattern

### Isolating Failures

The bulkhead pattern prevents one failing component from consuming all resources:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BULKHEAD PATTERN                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Without Bulkhead:                                                      │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                    Shared Thread Pool (100)                    │      │
│   │  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐    │      │
│   │  │ A  │ │ B  │ │ A  │ │ C  │ │ B  │ │ A  │ │ B  │ │ A  │    │      │
│   │  └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘    │      │
│   └──────────────────────────────────────────────────────────────┘      │
│   If Service A is slow, it consumes ALL threads → B and C fail too     │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   With Bulkhead:                                                         │
│   ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐        │
│   │   Pool A (40)    │ │   Pool B (40)    │ │   Pool C (20)    │        │
│   │  ┌──┐┌──┐┌──┐    │ │  ┌──┐┌──┐┌──┐    │ │  ┌──┐┌──┐       │        │
│   │  │A ││A ││A │    │ │  │B ││B ││B │    │ │  │C ││C │       │        │
│   │  └──┘└──┘└──┘    │ │  └──┘└──┘└──┘    │ │  └──┘└──┘       │        │
│   └──────────────────┘ └──────────────────┘ └──────────────────┘        │
│   If Service A is slow, only its pool fills → B and C continue!        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Semaphore-Based Bulkhead

```java
@Configuration
public class BulkheadConfig {

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig paymentConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(20)           // Max 20 concurrent calls
            .maxWaitDuration(Duration.ZERO)   // Fail immediately when full
            .build();

        BulkheadConfig inventoryConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(50)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();

        return BulkheadRegistry.of(Map.of(
            "payment", paymentConfig,
            "inventory", inventoryConfig
        ));
    }
}
```

```java
@Service
public class BulkheadProtectedService {

    private final Bulkhead paymentBulkhead;
    private final Bulkhead inventoryBulkhead;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return paymentClient.process(request)
            .transformDeferred(BulkheadOperator.of(paymentBulkhead))
            .onErrorResume(BulkheadFullException.class, e ->
                Mono.error(new ServiceBusyException("Payment service busy")));
    }

    public Mono<InventoryStatus> checkInventory(String productId) {
        return inventoryClient.check(productId)
            .transformDeferred(BulkheadOperator.of(inventoryBulkhead))
            .onErrorResume(BulkheadFullException.class, e ->
                Mono.just(InventoryStatus.unknown()));  // Graceful degradation
    }
}
```

## 16.7 Timeout Patterns

### Layered Timeouts

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TIMEOUT HIERARCHY                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Request (overall: 30s)                                                │
│   │                                                                      │
│   ├── Service Call A (5s)                                               │
│   │   └── Database Query (2s)                                           │
│   │                                                                      │
│   ├── Service Call B (10s)                                              │
│   │   ├── External API 1 (3s)                                           │
│   │   └── External API 2 (3s)                                           │
│   │                                                                      │
│   └── Service Call C (5s)                                               │
│       └── Cache Lookup (100ms)                                          │
│                                                                          │
│   Key Principle:                                                         │
│   Inner timeouts < Outer timeouts                                       │
│   Sum of inner timeouts < Outer timeout (for sequential operations)    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

```java
@Service
public class TimeoutAwareAggregator {

    public Mono<AggregatedData> aggregate(String id) {
        // Overall timeout for the entire operation
        return Mono.zip(
            fetchUserData(id).timeout(Duration.ofSeconds(5)),
            fetchOrderData(id).timeout(Duration.ofSeconds(5)),
            fetchAnalytics(id).timeout(Duration.ofSeconds(2))
        )
        .map(tuple -> new AggregatedData(tuple.getT1(), tuple.getT2(), tuple.getT3()))
        .timeout(Duration.ofSeconds(10));  // Overall timeout
    }

    private Mono<UserData> fetchUserData(String id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(UserData.class)
            .timeout(Duration.ofSeconds(3));  // Inner timeout
    }
}
```

### Deadline Propagation

Pass remaining time through the call chain:

```java
@Service
public class DeadlineAwareService {

    public Mono<Result> processWithDeadline(Request request, Instant deadline) {
        return Mono.deferContextual(ctx -> {
            Instant now = Instant.now();
            Duration remaining = Duration.between(now, deadline);

            if (remaining.isNegative() || remaining.isZero()) {
                return Mono.error(new DeadlineExceededException("Deadline already passed"));
            }

            return doProcess(request)
                .timeout(remaining)
                .onErrorMap(TimeoutException.class, e ->
                    new DeadlineExceededException("Operation exceeded deadline"));
        });
    }
}
```

## 16.8 File Processing with Backpressure

### Streaming Large Files

```java
@Service
public class ReactiveFileProcessor {

    private static final int BUFFER_SIZE = 8192;

    public Flux<DataBuffer> streamFile(Path filePath) {
        return DataBufferUtils.read(
            filePath,
            new DefaultDataBufferFactory(),
            BUFFER_SIZE
        );
    }

    public Mono<Void> processLargeFile(Path inputPath, Path outputPath) {
        return DataBufferUtils.read(inputPath, new DefaultDataBufferFactory(), BUFFER_SIZE)
            .map(this::processBuffer)
            .as(flux -> DataBufferUtils.write(flux, outputPath));
    }

    private DataBuffer processBuffer(DataBuffer buffer) {
        // Transform buffer contents
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);

        // Process bytes...
        byte[] processed = transform(bytes);

        DataBuffer result = new DefaultDataBufferFactory().wrap(processed);
        return result;
    }
}
```

### CSV Processing with Backpressure

```java
@Service
public class ReactiveCsvProcessor {

    public Flux<Record> processCsvFile(Path filePath) {
        return Flux.using(
            () -> Files.newBufferedReader(filePath),
            reader -> Flux.fromStream(reader.lines())
                .skip(1)  // Skip header
                .map(this::parseLine)
                .filter(Optional::isPresent)
                .map(Optional::get),
            reader -> {
                try { reader.close(); } catch (IOException ignored) {}
            }
        );
    }

    public Mono<Long> importCsvToDatabase(Path filePath, RecordRepository repository) {
        return processCsvFile(filePath)
            .buffer(100)  // Batch for efficiency
            .flatMap(batch -> repository.saveAll(batch).count(), 4)  // 4 concurrent batches
            .reduce(0L, Long::sum);
    }

    private Optional<Record> parseLine(String line) {
        try {
            String[] parts = line.split(",");
            return Optional.of(new Record(parts[0], parts[1], parts[2]));
        } catch (Exception e) {
            log.warn("Failed to parse line: {}", line);
            return Optional.empty();
        }
    }
}
```

## 16.9 gRPC Integration

### Reactive gRPC Client

```java
@Service
public class ReactiveGrpcClient {

    private final ReactorProductServiceGrpc.ReactorProductServiceStub productStub;

    public ReactiveGrpcClient(ManagedChannel channel) {
        this.productStub = ReactorProductServiceGrpc.newReactorStub(channel);
    }

    public Mono<Product> getProduct(String productId) {
        GetProductRequest request = GetProductRequest.newBuilder()
            .setProductId(productId)
            .build();

        return productStub.getProduct(request)
            .timeout(Duration.ofSeconds(5))
            .map(this::toProduct);
    }

    public Flux<Product> streamProducts(String category) {
        ListProductsRequest request = ListProductsRequest.newBuilder()
            .setCategory(category)
            .build();

        return productStub.listProducts(request)
            .map(this::toProduct);
    }

    private Product toProduct(ProductResponse response) {
        return new Product(
            response.getId(),
            response.getName(),
            response.getPrice()
        );
    }
}
```

## 16.10 GraphQL Integration

### Reactive GraphQL with Spring

```java
@Controller
public class ProductGraphQLController {

    private final ProductService productService;
    private final ReviewService reviewService;

    @QueryMapping
    public Mono<Product> product(@Argument String id) {
        return productService.getProduct(id);
    }

    @QueryMapping
    public Flux<Product> products(@Argument String category) {
        return productService.getProductsByCategory(category);
    }

    @SchemaMapping(typeName = "Product", field = "reviews")
    public Flux<Review> reviews(Product product) {
        return reviewService.getReviewsForProduct(product.getId());
    }

    @SubscriptionMapping
    public Flux<Product> productUpdates() {
        return productService.getProductUpdateStream();
    }
}
```

## Summary

Integration patterns are essential for building robust distributed systems:

| Pattern | Problem Solved | Key Benefit |
|---------|---------------|-------------|
| **Gateway** | Multiple service calls | Parallel aggregation, partial failure handling |
| **Circuit Breaker** | Cascading failures | Fast fail, system protection |
| **Rate Limiting** | Resource exhaustion | Fair resource allocation |
| **Caching** | Repeated expensive calls | Latency reduction, load reduction |
| **Retry** | Transient failures | Automatic recovery |
| **Bulkhead** | Resource contention | Failure isolation |
| **Timeout** | Slow operations | Bounded wait times |

**Key Takeaways:**

1. **Fail fast, fail gracefully**: Use timeouts and circuit breakers to prevent slow failures from cascading.

2. **Combine patterns**: Real resilience comes from layering—circuit breaker + retry + timeout + bulkhead.

3. **Design for partial failure**: Aggregate services should handle individual service failures gracefully.

4. **Monitor everything**: Circuit breaker states, cache hit rates, rate limiter rejections—these are your early warning system.

5. **Test failure modes**: Chaos engineering helps validate your resilience patterns work as expected.

The patterns in this chapter transform your reactive services from "works when everything is perfect" to "works reliably in the real world where failures are the norm, not the exception."
