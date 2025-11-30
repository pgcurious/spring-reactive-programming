# Chapter 11: WebClient - The Reactive HTTP Client

> "The best programs are written so that computing machines can perform them quickly and so that human beings can understand them clearly."
> — Donald Knuth

## Introduction

In previous chapters, we built reactive web endpoints that receive and respond to HTTP requests. But real applications rarely exist in isolation—they call other services, integrate with external APIs, and orchestrate data from multiple sources. This is where **WebClient** shines.

WebClient is Spring WebFlux's reactive HTTP client, designed to replace the blocking `RestTemplate`. While RestTemplate waits for each HTTP call to complete, WebClient integrates naturally with reactive streams, allowing you to compose multiple HTTP calls, handle responses asynchronously, and maintain backpressure throughout your application.

This chapter explores WebClient not just as a RestTemplate replacement, but as a powerful tool for building efficient, resilient service integrations in a reactive world.

## 11.1 Why WebClient Over RestTemplate?

### The Problem with RestTemplate

RestTemplate has served Spring developers well for over a decade, but it has a fundamental limitation: **every HTTP call blocks a thread**.

```java
// RestTemplate - blocking approach
public List<User> getUsersWithOrders() {
    User user = restTemplate.getForObject("/users/1", User.class);  // Thread waits
    List<Order> orders = restTemplate.getForObject("/orders?userId=1", List.class);  // Thread waits again
    user.setOrders(orders);
    return List.of(user);
}
```

When you have many concurrent requests, each one consumes a thread while waiting for I/O. With thousands of concurrent users, you quickly run out of threads or waste resources on idle waiting.

### WebClient: Non-Blocking by Design

WebClient returns `Mono` and `Flux`, integrating naturally with the reactive stack:

```java
// WebClient - non-blocking approach
public Mono<User> getUserWithOrders() {
    return webClient.get()
        .uri("/users/1")
        .retrieve()
        .bodyToMono(User.class)
        .flatMap(user ->
            webClient.get()
                .uri("/orders?userId={id}", user.getId())
                .retrieve()
                .bodyToFlux(Order.class)
                .collectList()
                .map(orders -> {
                    user.setOrders(orders);
                    return user;
                })
        );
}
```

No threads are blocked. The thread starts the HTTP request, then does other work while waiting for the response.

### Mental Model: The Waiter Analogy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    RESTTEMPLATE vs WEBCLIENT                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   RESTTEMPLATE (Dedicated Waiter)           WEBCLIENT (Efficient Waiter) │
│   ═══════════════════════════════           ════════════════════════════ │
│                                                                          │
│   ┌────────┐                               ┌────────┐                   │
│   │Waiter 1│→ Takes order                  │        │                   │
│   │        │→ Walks to kitchen             │ Waiter │→ Takes order      │
│   │        │→ WAITS at kitchen             │        │→ Places with chef │
│   │        │→ Gets food                    │        │→ Takes next order │
│   │        │→ Delivers to table            │        │→ Gets notification│
│   └────────┘                               │        │→ Delivers food    │
│                                            └────────┘                   │
│   Thread per request                        One thread, many requests   │
│   Blocked during I/O                        Never blocked               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Feature Comparison

| Feature | RestTemplate | WebClient |
|---------|--------------|-----------|
| Blocking | Yes | No (by default) |
| Streaming | No | Yes (Flux) |
| Backpressure | No | Yes |
| Connection pooling | Manual configuration | Built-in |
| Timeout handling | Basic | Fine-grained |
| Retry support | Manual | Reactor retry operators |
| Functional API | No | Yes |
| Spring Boot 3+ | Deprecated | Recommended |

## 11.2 Building WebClient Instances

### Basic Creation

The simplest way to create a WebClient:

```java
WebClient webClient = WebClient.create();

// With a base URL
WebClient webClient = WebClient.create("https://api.example.com");
```

### Using the Builder

For more control, use the builder:

```java
WebClient webClient = WebClient.builder()
    .baseUrl("https://api.example.com")
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
    .defaultHeader("X-Api-Key", apiKey)
    .build();
```

### Configuring with Spring Boot

In Spring Boot applications, configure WebClient as a bean:

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

Spring Boot auto-configures a `WebClient.Builder` with sensible defaults.

### Configuring Timeouts

Timeouts are crucial for resilient applications:

```java
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Bean
public WebClient webClient() {
    HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(5))
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

    return WebClient.builder()
        .baseUrl("https://api.example.com")
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
}
```

### Connection Pooling

Configure connection pooling for better performance:

```java
import reactor.netty.resources.ConnectionProvider;

@Bean
public WebClient webClient() {
    ConnectionProvider provider = ConnectionProvider.builder("custom")
        .maxConnections(100)
        .maxIdleTime(Duration.ofSeconds(30))
        .maxLifeTime(Duration.ofMinutes(5))
        .pendingAcquireTimeout(Duration.ofSeconds(60))
        .evictInBackground(Duration.ofSeconds(120))
        .build();

    HttpClient httpClient = HttpClient.create(provider)
        .responseTimeout(Duration.ofSeconds(10));

    return WebClient.builder()
        .baseUrl("https://api.example.com")
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
}
```

### Multiple WebClient Instances

Create specialized WebClient instances for different services:

```java
@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("userServiceClient")
    public WebClient userServiceClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://user-service.internal")
            .defaultHeader("X-Service-Name", "my-app")
            .build();
    }

    @Bean
    @Qualifier("paymentServiceClient")
    public WebClient paymentServiceClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://payment-service.internal")
            .defaultHeader("X-Service-Name", "my-app")
            .filter(loggingFilter())
            .filter(retryFilter())
            .build();
    }

    @Bean
    @Qualifier("externalApiClient")
    public WebClient externalApiClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(30)); // Longer timeout for external

        return builder
            .baseUrl("https://external-api.com/v1")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Authorization", "Bearer " + externalApiToken)
            .build();
    }
}
```

## 11.3 Making Requests

### GET Requests

```java
// Simple GET returning a Mono
public Mono<User> getUser(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class);
}

// GET returning a Flux (list)
public Flux<User> getAllUsers() {
    return webClient.get()
        .uri("/users")
        .retrieve()
        .bodyToFlux(User.class);
}

// GET with query parameters
public Flux<User> searchUsers(String name, String role, int page, int size) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/users/search")
            .queryParam("name", name)
            .queryParam("role", role)
            .queryParam("page", page)
            .queryParam("size", size)
            .build())
        .retrieve()
        .bodyToFlux(User.class);
}

// GET with dynamic path and query
public Mono<List<Order>> getUserOrders(String userId, OrderStatus status) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/users/{id}/orders")
            .queryParamIfPresent("status", Optional.ofNullable(status))
            .build(userId))
        .retrieve()
        .bodyToFlux(Order.class)
        .collectList();
}
```

### POST Requests

```java
// POST with body
public Mono<User> createUser(UserCreateRequest request) {
    return webClient.post()
        .uri("/users")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(User.class);
}

// POST with reactive body
public Mono<User> createUser(Mono<UserCreateRequest> requestMono) {
    return webClient.post()
        .uri("/users")
        .body(requestMono, UserCreateRequest.class)
        .retrieve()
        .bodyToMono(User.class);
}

// POST form data
public Mono<String> submitForm(String username, String password) {
    return webClient.post()
        .uri("/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData("username", username)
            .with("password", password))
        .retrieve()
        .bodyToMono(String.class);
}

// POST multipart/form-data
public Mono<UploadResponse> uploadFile(Path filePath) {
    return webClient.post()
        .uri("/upload")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData("file",
            new FileSystemResource(filePath)))
        .retrieve()
        .bodyToMono(UploadResponse.class);
}
```

### PUT and PATCH Requests

```java
// PUT - full update
public Mono<User> updateUser(String id, UserUpdateRequest request) {
    return webClient.put()
        .uri("/users/{id}", id)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(User.class);
}

// PATCH - partial update
public Mono<User> patchUser(String id, Map<String, Object> updates) {
    return webClient.patch()
        .uri("/users/{id}", id)
        .bodyValue(updates)
        .retrieve()
        .bodyToMono(User.class);
}
```

### DELETE Requests

```java
// DELETE returning empty response
public Mono<Void> deleteUser(String id) {
    return webClient.delete()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(Void.class);
}

// DELETE with response body
public Mono<DeletionResult> deleteUserWithResult(String id) {
    return webClient.delete()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(DeletionResult.class);
}
```

### Request Headers

```java
public Mono<User> getUserWithAuth(String id, String authToken) {
    return webClient.get()
        .uri("/users/{id}", id)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
        .header("X-Request-Id", UUID.randomUUID().toString())
        .headers(headers -> {
            headers.add("X-Custom-Header", "value");
            headers.addAll(additionalHeaders);
        })
        .accept(MediaType.APPLICATION_JSON)
        .acceptCharset(StandardCharsets.UTF_8)
        .retrieve()
        .bodyToMono(User.class);
}
```

## 11.4 Handling Responses

### Basic Response Handling

```java
// Just get the body
Mono<User> user = webClient.get()
    .uri("/users/1")
    .retrieve()
    .bodyToMono(User.class);

// Get the full response with status and headers
Mono<ResponseEntity<User>> response = webClient.get()
    .uri("/users/1")
    .retrieve()
    .toEntity(User.class);

// Use the response entity
webClient.get()
    .uri("/users/1")
    .retrieve()
    .toEntity(User.class)
    .flatMap(entity -> {
        HttpStatusCode status = entity.getStatusCode();
        HttpHeaders headers = entity.getHeaders();
        User user = entity.getBody();
        // Process...
        return Mono.just(user);
    });
```

### Handling Empty Responses

```java
// Return default if not found
public Mono<User> getUserOrDefault(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class)
        .defaultIfEmpty(User.anonymous());
}

// Throw if empty
public Mono<User> getUserOrError(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class)
        .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
}
```

### Streaming Responses

```java
// Stream as Flux
public Flux<Event> streamEvents() {
    return webClient.get()
        .uri("/events/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(Event.class);
}

// Stream NDJSON
public Flux<LogEntry> streamLogs() {
    return webClient.get()
        .uri("/logs/stream")
        .accept(MediaType.APPLICATION_NDJSON)
        .retrieve()
        .bodyToFlux(LogEntry.class)
        .take(Duration.ofMinutes(5)); // Stop after 5 minutes
}
```

### Handling Response Headers

```java
public Mono<PagedResult<User>> getUsersWithPagination() {
    return webClient.get()
        .uri("/users")
        .retrieve()
        .toEntity(new ParameterizedTypeReference<List<User>>() {})
        .map(entity -> {
            List<User> users = entity.getBody();

            // Extract pagination headers
            int total = Integer.parseInt(
                entity.getHeaders().getFirst("X-Total-Count"));
            int page = Integer.parseInt(
                entity.getHeaders().getFirst("X-Page"));
            int pages = Integer.parseInt(
                entity.getHeaders().getFirst("X-Total-Pages"));

            return new PagedResult<>(users, page, pages, total);
        });
}
```

## 11.5 Error Handling

### Default Error Behavior

By default, WebClient throws exceptions for 4xx and 5xx responses:

```java
webClient.get()
    .uri("/users/999")  // Returns 404
    .retrieve()
    .bodyToMono(User.class)
    // Throws WebClientResponseException.NotFound
```

### Handling Specific Status Codes

```java
public Mono<User> getUser(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, response -> {
            if (response.statusCode() == HttpStatus.NOT_FOUND) {
                return Mono.error(new UserNotFoundException(id));
            }
            return response.bodyToMono(String.class)
                .flatMap(body -> Mono.error(new ClientException(body)));
        })
        .onStatus(HttpStatusCode::is5xxServerError, response ->
            Mono.error(new ServiceUnavailableException("User service unavailable")))
        .bodyToMono(User.class);
}
```

### Comprehensive Error Handling

```java
public Mono<User> getUserWithErrorHandling(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .onStatus(status -> status == HttpStatus.NOT_FOUND,
            response -> Mono.error(new UserNotFoundException(id)))
        .onStatus(status -> status == HttpStatus.UNAUTHORIZED,
            response -> Mono.error(new UnauthorizedException()))
        .onStatus(status -> status == HttpStatus.FORBIDDEN,
            response -> Mono.error(new ForbiddenException()))
        .onStatus(HttpStatusCode::is4xxClientError, response ->
            response.bodyToMono(ApiError.class)
                .flatMap(error -> Mono.error(new ApiException(error))))
        .onStatus(HttpStatusCode::is5xxServerError, response ->
            Mono.error(new ServiceException("Service unavailable")))
        .bodyToMono(User.class)
        .onErrorResume(WebClientRequestException.class, e ->
            Mono.error(new ConnectionException("Failed to connect", e)));
}
```

### Using onErrorResume

```java
public Mono<User> getUserWithFallback(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class)
        .onErrorResume(WebClientResponseException.NotFound.class, e -> {
            log.warn("User {} not found, returning empty", id);
            return Mono.empty();
        })
        .onErrorResume(WebClientResponseException.class, e -> {
            log.error("HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
            return Mono.error(new ServiceException("Failed to fetch user", e));
        })
        .onErrorResume(Exception.class, e -> {
            log.error("Unexpected error fetching user", e);
            return Mono.error(new ServiceException("Unexpected error", e));
        });
}
```

### Retry on Failure

```java
public Mono<User> getUserWithRetry(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class)
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable
                              || throwable instanceof WebClientRequestException)
            .onRetryExhaustedThrow((spec, signal) ->
                new ServiceException("Service unavailable after retries")));
}
```

### Advanced Retry Strategies

```java
public Mono<User> getUserWithAdvancedRetry(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(User.class)
        .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
            .maxBackoff(Duration.ofSeconds(5))
            .jitter(0.5)  // Add randomness to prevent thundering herd
            .filter(this::isRetryable)
            .doBeforeRetry(signal -> log.warn("Retrying request, attempt {}",
                signal.totalRetries() + 1))
            .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
}

private boolean isRetryable(Throwable throwable) {
    if (throwable instanceof WebClientResponseException e) {
        return e.getStatusCode().is5xxServerError();
    }
    return throwable instanceof WebClientRequestException;
}
```

## 11.6 Exchange for Full Control

### When to Use Exchange

The `retrieve()` method handles most cases, but `exchangeToMono()` and `exchangeToFlux()` give you full access to the response:

```java
// Using exchangeToMono for full control
public Mono<User> getUserWithExchange(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.OK)) {
                return response.bodyToMono(User.class);
            } else if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                return Mono.empty();
            } else {
                return response.createError();
            }
        });
}
```

### Accessing Response Details

```java
public Mono<UserResponse> getUserWithDetails(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .exchangeToMono(response -> {
            HttpStatusCode status = response.statusCode();
            HttpHeaders headers = response.headers().asHttpHeaders();
            List<ResponseCookie> cookies = response.cookies().values()
                .stream()
                .flatMap(List::stream)
                .toList();

            return response.bodyToMono(User.class)
                .map(user -> new UserResponse(
                    user,
                    status,
                    headers.getFirst("X-Request-Id"),
                    headers.getFirst("X-Rate-Limit-Remaining")
                ));
        });
}
```

### Handling Different Content Types

```java
public Mono<Object> getResource(String path) {
    return webClient.get()
        .uri(path)
        .exchangeToMono(response -> {
            MediaType contentType = response.headers()
                .contentType()
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

            if (contentType.includes(MediaType.APPLICATION_JSON)) {
                return response.bodyToMono(JsonNode.class);
            } else if (contentType.includes(MediaType.TEXT_PLAIN)) {
                return response.bodyToMono(String.class);
            } else if (contentType.includes(MediaType.APPLICATION_PDF)) {
                return response.bodyToMono(byte[].class);
            } else {
                return response.bodyToMono(DataBuffer.class)
                    .map(DataBuffer::asByteBuffer);
            }
        });
}
```

### Conditional Processing

```java
public Mono<User> getUpdatedUser(String id, String etag) {
    return webClient.get()
        .uri("/users/{id}", id)
        .header(HttpHeaders.IF_NONE_MATCH, etag)
        .exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.NOT_MODIFIED)) {
                // Resource hasn't changed, use cached version
                return Mono.empty();
            } else if (response.statusCode().equals(HttpStatus.OK)) {
                // Resource updated, get new version
                String newEtag = response.headers()
                    .header(HttpHeaders.ETAG)
                    .stream()
                    .findFirst()
                    .orElse(null);
                return response.bodyToMono(User.class)
                    .doOnNext(user -> cacheUser(user, newEtag));
            } else {
                return response.createError();
            }
        });
}
```

## 11.7 Advanced Patterns

### Parallel Requests

```java
// Execute multiple requests in parallel
public Mono<UserProfile> getUserProfile(String userId) {
    Mono<User> userMono = webClient.get()
        .uri("/users/{id}", userId)
        .retrieve()
        .bodyToMono(User.class);

    Mono<List<Order>> ordersMono = webClient.get()
        .uri("/orders?userId={id}", userId)
        .retrieve()
        .bodyToFlux(Order.class)
        .collectList();

    Mono<List<Review>> reviewsMono = webClient.get()
        .uri("/reviews?userId={id}", userId)
        .retrieve()
        .bodyToFlux(Review.class)
        .collectList();

    // Execute all three in parallel and combine results
    return Mono.zip(userMono, ordersMono, reviewsMono)
        .map(tuple -> new UserProfile(
            tuple.getT1(),  // User
            tuple.getT2(),  // Orders
            tuple.getT3()   // Reviews
        ));
}
```

### Sequential Dependent Requests

```java
// Requests that depend on each other
public Mono<OrderWithDetails> getOrderWithDetails(String orderId) {
    return webClient.get()
        .uri("/orders/{id}", orderId)
        .retrieve()
        .bodyToMono(Order.class)
        .flatMap(order ->
            // Get product details for each item
            Flux.fromIterable(order.getItems())
                .flatMap(item -> webClient.get()
                    .uri("/products/{id}", item.getProductId())
                    .retrieve()
                    .bodyToMono(Product.class)
                    .map(product -> new OrderItemWithProduct(item, product)))
                .collectList()
                .map(itemsWithProducts -> new OrderWithDetails(order, itemsWithProducts))
        );
}
```

### Request Caching

```java
@Service
public class CachedUserService {

    private final WebClient webClient;
    private final Map<String, Mono<User>> cache = new ConcurrentHashMap<>();

    public Mono<User> getUser(String id) {
        return cache.computeIfAbsent(id, this::fetchUser);
    }

    private Mono<User> fetchUser(String id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(User.class)
            .cache(Duration.ofMinutes(5));  // Cache for 5 minutes
    }

    public void invalidateUser(String id) {
        cache.remove(id);
    }
}
```

### Circuit Breaker Pattern

```java
// Using Resilience4j with WebClient
@Service
public class ResilientUserService {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public ResilientUserService(WebClient webClient, CircuitBreakerRegistry registry) {
        this.webClient = webClient;
        this.circuitBreaker = registry.circuitBreaker("userService");
    }

    public Mono<User> getUser(String id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(User.class)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker is open, returning fallback");
                return Mono.just(User.fallback());
            });
    }
}
```

### Aggregating Multiple Services

```java
@Service
public class DashboardService {

    private final WebClient userClient;
    private final WebClient orderClient;
    private final WebClient inventoryClient;
    private final WebClient analyticsClient;

    public Mono<Dashboard> getDashboard(String userId) {
        // Define all service calls
        Mono<UserSummary> userSummary = userClient.get()
            .uri("/users/{id}/summary", userId)
            .retrieve()
            .bodyToMono(UserSummary.class)
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> Mono.just(UserSummary.empty()));

        Mono<List<Order>> recentOrders = orderClient.get()
            .uri("/orders?userId={id}&limit=5", userId)
            .retrieve()
            .bodyToFlux(Order.class)
            .collectList()
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> Mono.just(List.of()));

        Mono<InventoryStatus> inventory = inventoryClient.get()
            .uri("/inventory/status")
            .retrieve()
            .bodyToMono(InventoryStatus.class)
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> Mono.just(InventoryStatus.unknown()));

        Mono<Analytics> analytics = analyticsClient.get()
            .uri("/analytics/user/{id}", userId)
            .retrieve()
            .bodyToMono(Analytics.class)
            .timeout(Duration.ofSeconds(3))
            .onErrorResume(e -> Mono.just(Analytics.empty()));

        // Execute all in parallel and combine
        return Mono.zip(userSummary, recentOrders, inventory, analytics)
            .map(tuple -> Dashboard.builder()
                .user(tuple.getT1())
                .recentOrders(tuple.getT2())
                .inventory(tuple.getT3())
                .analytics(tuple.getT4())
                .build());
    }
}
```

## 11.8 Filters and Interceptors

### Request/Response Logging

```java
@Bean
public WebClient webClient(WebClient.Builder builder) {
    return builder
        .baseUrl("https://api.example.com")
        .filter(logRequest())
        .filter(logResponse())
        .build();
}

private ExchangeFilterFunction logRequest() {
    return ExchangeFilterFunction.ofRequestProcessor(request -> {
        log.info("Request: {} {}", request.method(), request.url());
        request.headers().forEach((name, values) ->
            values.forEach(value -> log.debug("Header: {}={}", name, value)));
        return Mono.just(request);
    });
}

private ExchangeFilterFunction logResponse() {
    return ExchangeFilterFunction.ofResponseProcessor(response -> {
        log.info("Response: {}", response.statusCode());
        return Mono.just(response);
    });
}
```

### Authentication Filter

```java
// Token-based authentication
private ExchangeFilterFunction authFilter(String token) {
    return (request, next) -> next.exchange(
        ClientRequest.from(request)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build()
    );
}

// Dynamic token (e.g., refreshing OAuth token)
private ExchangeFilterFunction dynamicAuthFilter(TokenProvider tokenProvider) {
    return (request, next) -> tokenProvider.getToken()
        .flatMap(token -> next.exchange(
            ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build()
        ));
}
```

### Request Timing

```java
private ExchangeFilterFunction timingFilter() {
    return (request, next) -> {
        long start = System.currentTimeMillis();
        return next.exchange(request)
            .doOnSuccess(response -> {
                long duration = System.currentTimeMillis() - start;
                log.info("{} {} completed in {}ms",
                    request.method(), request.url(), duration);
            });
    };
}
```

### Error Wrapping Filter

```java
private ExchangeFilterFunction errorWrappingFilter() {
    return (request, next) -> next.exchange(request)
        .flatMap(response -> {
            if (response.statusCode().isError()) {
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new ServiceException(
                        request.url().toString(),
                        response.statusCode(),
                        body
                    )));
            }
            return Mono.just(response);
        });
}
```

### Composing Filters

```java
@Bean
public WebClient webClient(WebClient.Builder builder, TokenProvider tokenProvider) {
    return builder
        .baseUrl("https://api.example.com")
        .filter(requestIdFilter())
        .filter(timingFilter())
        .filter(dynamicAuthFilter(tokenProvider))
        .filter(retryFilter())
        .filter(errorWrappingFilter())
        .filter(logRequest())
        .filter(logResponse())
        .build();
}
```

## Summary

WebClient is the cornerstone of reactive service integration in Spring WebFlux:

| Concept | Description |
|---------|-------------|
| **Non-blocking** | No threads wait for I/O; requests are composed with publishers |
| **Fluent API** | Build requests with a readable, chainable API |
| **retrieve()** | Simple access to response body |
| **exchangeToMono/Flux** | Full control over response processing |
| **Error handling** | Fine-grained with onStatus, onErrorResume, retry |
| **Filters** | Add cross-cutting concerns to all requests |
| **Connection pooling** | Built-in with configurable limits |

Key insights from this chapter:

1. **WebClient replaces RestTemplate** for non-blocking HTTP calls. It integrates naturally with Reactor's Mono and Flux.

2. **Configure WebClient properly** with timeouts, connection pooling, and base URLs for each service you integrate with.

3. **Handle errors explicitly** using `onStatus()` and `onErrorResume()`. Don't let exceptions propagate unexpectedly.

4. **Use filters for cross-cutting concerns** like authentication, logging, and timing. This keeps your business logic clean.

5. **Compose parallel requests with Mono.zip()** when fetching data from multiple services. This is where reactive truly shines over blocking code.

6. **Implement resilience patterns** like retry with backoff, circuit breakers, and timeouts to handle service failures gracefully.

In the next chapter, we'll explore WebSockets and streaming—real-time, bidirectional communication in a reactive world.
