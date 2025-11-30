# Chapter 10: Functional Endpoints

> "Functions are the fundamental building blocks. When you compose them well, complexity becomes manageable."
> — John Hughes

## Introduction

In Chapter 9, we explored WebFlux using annotated controllers—the familiar `@RestController`, `@GetMapping`, and `@PostMapping` annotations that feel comfortable to any Spring MVC developer. But WebFlux offers an alternative programming model that embraces functional programming more directly: **functional endpoints**.

Why learn another way to do the same thing? Because functional endpoints aren't just a different syntax—they represent a fundamentally different way of thinking about web handlers. Instead of annotations that are processed at startup through reflection, functional endpoints are explicit compositions of functions. Everything is visible, testable, and composable.

This chapter explores functional endpoints not as a replacement for annotated controllers, but as an alternative that shines in specific scenarios. By the end, you'll understand when to use each approach and how to compose complex routing logic from simple building blocks.

## 10.1 The Philosophy: Functions as Building Blocks

### Why Functions?

Annotated controllers rely on Spring's annotation processing magic:

```java
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }
}
```

This works beautifully, but there's hidden complexity:
- Reflection at startup scans for annotations
- Argument resolvers figure out how to populate `@PathVariable`
- Return value handlers determine how to serialize the response
- Exception handlers catch and transform errors

With functional endpoints, all of this becomes explicit:

```java
RouterFunction<ServerResponse> route = RouterFunctions.route()
    .GET("/users/{id}", request -> {
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok().bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    })
    .build();
```

### The Trade-off

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ANNOTATED vs FUNCTIONAL                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ANNOTATED CONTROLLERS              FUNCTIONAL ENDPOINTS                 │
│  ═══════════════════                ════════════════════                 │
│                                                                          │
│  • Familiar Spring MVC style        • Explicit composition               │
│  • Less boilerplate                 • More verbose                       │
│  • Magic via annotations            • Everything visible                 │
│  • Good IDE support                 • Better for complex routing         │
│  • Easier for simple CRUD           • Easier to test in isolation        │
│                                                                          │
│  Choose based on your needs, not ideology.                               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Mental Model: Lego Blocks

Think of functional endpoints as Lego blocks:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│   ROUTER FUNCTION = PREDICATE + HANDLER                                  │
│                                                                          │
│   ┌──────────────┐     ┌──────────────────┐     ┌───────────────────┐   │
│   │  Request     │────▶│  Does Predicate  │─Yes─▶│  Handler          │   │
│   │  Comes In    │     │  Match?          │     │  Processes        │   │
│   └──────────────┘     └──────────────────┘     └───────────────────┘   │
│                               │                         │                │
│                               No                        ▼                │
│                               │                 ┌───────────────────┐   │
│                               ▼                 │  ServerResponse   │   │
│                        ┌──────────────┐        │  Returned         │   │
│                        │  Try Next    │        └───────────────────┘   │
│                        │  Route       │                                 │
│                        └──────────────┘                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 10.2 RouterFunction: Defining Routes

### The Core Abstraction

`RouterFunction<ServerResponse>` is the central type. It's a function that takes a `ServerRequest` and returns an optional handler:

```java
@FunctionalInterface
public interface RouterFunction<T extends ServerResponse> {
    Mono<HandlerFunction<T>> route(ServerRequest request);
}
```

If the request matches, it returns a `HandlerFunction`. If not, it returns `Mono.empty()`, signaling to try the next route.

### Building Routes with RouterFunctions.route()

The fluent builder is the most common approach:

```java
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class UserRouter {

    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return route()
            .GET("/users", handler::listUsers)
            .GET("/users/{id}", handler::getUser)
            .POST("/users", handler::createUser)
            .PUT("/users/{id}", handler::updateUser)
            .DELETE("/users/{id}", handler::deleteUser)
            .build();
    }
}
```

### Nested Routes

For complex APIs, nest routes under a common path:

```java
@Bean
public RouterFunction<ServerResponse> apiRoutes(
        UserHandler userHandler,
        OrderHandler orderHandler,
        ProductHandler productHandler) {

    return route()
        .path("/api/v1", builder -> builder
            .path("/users", userBuilder -> userBuilder
                .GET("", userHandler::listUsers)
                .GET("/{id}", userHandler::getUser)
                .POST("", userHandler::createUser)
                .nest(accept(APPLICATION_JSON), nestedBuilder -> nestedBuilder
                    .PUT("/{id}", userHandler::updateUser)
                    .DELETE("/{id}", userHandler::deleteUser)
                )
            )
            .path("/orders", orderBuilder -> orderBuilder
                .GET("", orderHandler::listOrders)
                .GET("/{id}", orderHandler::getOrder)
                .POST("", orderHandler::createOrder)
            )
            .path("/products", productBuilder -> productBuilder
                .GET("", productHandler::listProducts)
                .GET("/{id}", productHandler::getProduct)
            )
        )
        .build();
}
```

### Combining Multiple RouterFunctions

RouterFunctions can be composed with `and()` and `andRoute()`:

```java
@Bean
public RouterFunction<ServerResponse> combinedRoutes(
        RouterFunction<ServerResponse> publicRoutes,
        RouterFunction<ServerResponse> adminRoutes) {

    return publicRoutes.and(adminRoutes);
}
```

Or compose them explicitly:

```java
@Bean
public RouterFunction<ServerResponse> allRoutes() {
    return route()
        .add(publicRoutes())
        .add(authenticatedRoutes())
        .add(adminRoutes())
        .build();
}

private RouterFunction<ServerResponse> publicRoutes() {
    return route()
        .GET("/health", request -> ServerResponse.ok().bodyValue("UP"))
        .GET("/info", request -> ServerResponse.ok().bodyValue(appInfo))
        .build();
}

private RouterFunction<ServerResponse> authenticatedRoutes() {
    return route()
        .path("/api", builder -> builder
            .filter(authenticationFilter())
            // ... routes
        )
        .build();
}
```

## 10.3 HandlerFunction: Handling Requests

### The Handler Contract

A `HandlerFunction` is simply a function from `ServerRequest` to `Mono<ServerResponse>`:

```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
```

This simplicity is powerful. Any method matching this signature can be a handler.

### Creating Handler Classes

The convention is to create handler classes that group related operations:

```java
@Component
public class UserHandler {

    private final UserRepository userRepository;

    public UserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<ServerResponse> listUsers(ServerRequest request) {
        // Extract optional query parameters
        Optional<String> role = request.queryParam("role");

        Flux<User> users = role
            .map(userRepository::findByRole)
            .orElseGet(userRepository::findAll);

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(users, User.class);
    }

    public Mono<ServerResponse> getUser(ServerRequest request) {
        String id = request.pathVariable("id");

        return userRepository.findById(id)
            .flatMap(user -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(UserCreateRequest.class)
            .flatMap(this::validateAndCreate)
            .flatMap(user -> ServerResponse
                .created(URI.create("/users/" + user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user));
    }

    public Mono<ServerResponse> updateUser(ServerRequest request) {
        String id = request.pathVariable("id");

        return request.bodyToMono(UserUpdateRequest.class)
            .flatMap(updateRequest -> userRepository.findById(id)
                .flatMap(existing -> {
                    existing.setName(updateRequest.getName());
                    existing.setEmail(updateRequest.getEmail());
                    return userRepository.save(existing);
                }))
            .flatMap(user -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        String id = request.pathVariable("id");

        return userRepository.findById(id)
            .flatMap(user -> userRepository.delete(user)
                .then(ServerResponse.noContent().build()))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<User> validateAndCreate(UserCreateRequest request) {
        // Validation logic
        if (request.getEmail() == null || !request.getEmail().contains("@")) {
            return Mono.error(new ValidationException("Invalid email"));
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        return userRepository.save(user);
    }
}
```

### ServerRequest: Extracting Data

`ServerRequest` provides access to all request data:

```java
public Mono<ServerResponse> handleRequest(ServerRequest request) {
    // Path variables
    String id = request.pathVariable("id");

    // Query parameters
    Optional<String> filter = request.queryParam("filter");
    List<String> tags = request.queryParams().get("tags");

    // Headers
    Optional<String> authHeader = request.headers().header("Authorization").stream().findFirst();
    MediaType contentType = request.headers().contentType().orElse(MediaType.APPLICATION_JSON);

    // Cookies
    Optional<String> sessionId = request.cookies().getFirst("sessionId")
        .map(HttpCookie::getValue);

    // Request body
    Mono<MyRequest> body = request.bodyToMono(MyRequest.class);
    Flux<Item> streamBody = request.bodyToFlux(Item.class);

    // Principal (authentication)
    Mono<Principal> principal = request.principal();

    // Request attributes
    Optional<Object> attribute = request.attribute("myAttribute");

    // URI information
    URI uri = request.uri();
    String path = request.path();
    HttpMethod method = request.method();

    // ... process and return response
}
```

### ServerResponse: Building Responses

`ServerResponse` provides a fluent builder for constructing responses:

```java
// Simple 200 OK with body
ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(myObject);

// 201 Created with location header
ServerResponse.created(URI.create("/resources/" + id))
    .bodyValue(createdResource);

// 204 No Content
ServerResponse.noContent().build();

// 404 Not Found
ServerResponse.notFound().build();

// 400 Bad Request with error body
ServerResponse.badRequest()
    .bodyValue(new ErrorResponse("Invalid input"));

// Custom status
ServerResponse.status(HttpStatus.I_AM_A_TEAPOT)
    .bodyValue("I cannot brew coffee");

// Streaming response
ServerResponse.ok()
    .contentType(MediaType.APPLICATION_NDJSON)
    .body(dataFlux, DataItem.class);

// Server-Sent Events
ServerResponse.ok()
    .contentType(MediaType.TEXT_EVENT_STREAM)
    .body(eventFlux.map(event ->
        ServerSentEvent.builder(event)
            .id(event.getId())
            .event("update")
            .build()
    ), ServerSentEvent.class);

// With headers
ServerResponse.ok()
    .header("X-Custom-Header", "value")
    .headers(headers -> headers.addAll(additionalHeaders))
    .bodyValue(data);

// With cookies
ServerResponse.ok()
    .cookie(ResponseCookie.from("session", sessionId)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(Duration.ofHours(1))
        .build())
    .bodyValue(data);
```

## 10.4 Request Predicates

### What Are Predicates?

Request predicates determine whether a route matches a request. They can test the HTTP method, path, headers, content type, and more.

```java
import static org.springframework.web.reactive.function.server.RequestPredicates.*;

// Match GET requests to /users
GET("/users")

// Match POST requests with JSON content
POST("/users").and(contentType(MediaType.APPLICATION_JSON))

// Match any request to /health
path("/health")

// Match requests accepting JSON
accept(MediaType.APPLICATION_JSON)

// Match requests with specific header
headers(headers -> headers.header("X-Api-Version").equals(List.of("2")))
```

### Combining Predicates

Predicates compose with `and()`, `or()`, and `negate()`:

```java
// POST or PUT with JSON content type
RequestPredicate createOrUpdate = POST("/users")
    .or(PUT("/users/{id}"))
    .and(contentType(MediaType.APPLICATION_JSON));

// GET that does NOT accept XML
RequestPredicate jsonOnly = GET("/data")
    .and(accept(MediaType.APPLICATION_XML).negate());

// Using in routes
RouterFunction<ServerResponse> routes = route()
    .route(createOrUpdate, handler::saveUser)
    .route(jsonOnly, handler::getData)
    .build();
```

### Custom Predicates

Create custom predicates for complex matching logic:

```java
public class CustomPredicates {

    // Match requests from internal IPs
    public static RequestPredicate internalNetwork() {
        return request -> {
            String remoteAddress = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("");
            return remoteAddress.startsWith("10.") ||
                   remoteAddress.startsWith("192.168.");
        };
    }

    // Match requests with valid API key
    public static RequestPredicate hasValidApiKey(ApiKeyValidator validator) {
        return request -> request.headers()
            .header("X-Api-Key")
            .stream()
            .findFirst()
            .map(validator::isValid)
            .orElse(false);
    }

    // Match requests with specific query parameter value
    public static RequestPredicate queryParamEquals(String name, String value) {
        return request -> request.queryParam(name)
            .map(v -> v.equals(value))
            .orElse(false);
    }

    // Match requests for premium users
    public static RequestPredicate premiumUser() {
        return request -> request.principal()
            .map(principal -> {
                if (principal instanceof UserPrincipal userPrincipal) {
                    return userPrincipal.isPremium();
                }
                return false;
            })
            .block(); // Note: blocking here is usually avoided
    }
}
```

Using custom predicates:

```java
@Bean
public RouterFunction<ServerResponse> routes(Handler handler) {
    return route()
        // Admin endpoints only from internal network
        .path("/admin", builder -> builder
            .filter((request, next) ->
                CustomPredicates.internalNetwork().test(request)
                    ? next.handle(request)
                    : ServerResponse.status(HttpStatus.FORBIDDEN).build()
            )
            .GET("/stats", handler::getStats)
            .POST("/cache/clear", handler::clearCache)
        )
        // Premium endpoints
        .GET("/premium/features",
            CustomPredicates.hasValidApiKey(apiKeyValidator)
                .and(GET("/premium/features")),
            handler::premiumFeatures)
        .build();
}
```

### The RequestPredicate Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REQUEST PREDICATE EVALUATION                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Request: POST /api/users                                               │
│            Content-Type: application/json                                │
│            Accept: application/json                                      │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │ Route 1: GET("/api/users")                                        │  │
│   │   → Method doesn't match (GET ≠ POST) ────────────────────▶ SKIP │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │ Route 2: POST("/api/users").and(contentType(APPLICATION_JSON))   │  │
│   │   → Method matches (POST = POST) ✓                                │  │
│   │   → Path matches (/api/users = /api/users) ✓                      │  │
│   │   → Content-Type matches (application/json) ✓                     │  │
│   │   → ALL PREDICATES MATCH ─────────────────────────────▶ EXECUTE  │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 10.5 Filters and Error Handling

### Handler Filters

Filters wrap handlers to add cross-cutting concerns:

```java
RouterFunction<ServerResponse> routes = route()
    .path("/api", builder -> builder
        .filter(loggingFilter())
        .filter(authenticationFilter())
        .GET("/users", handler::listUsers)
        .POST("/users", handler::createUser)
    )
    .build();
```

### Creating Filters

A filter is a `HandlerFilterFunction`:

```java
@FunctionalInterface
public interface HandlerFilterFunction<T extends ServerResponse, R extends ServerResponse> {
    Mono<R> filter(ServerRequest request, HandlerFunction<T> next);
}
```

Common filter patterns:

```java
public class Filters {

    // Logging filter
    public static HandlerFilterFunction<ServerResponse, ServerResponse> loggingFilter() {
        return (request, next) -> {
            long startTime = System.currentTimeMillis();
            String path = request.path();
            String method = request.method().name();

            return next.handle(request)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("{} {} - {} in {}ms",
                        method, path, response.statusCode(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("{} {} - Error in {}ms: {}",
                        method, path, duration, error.getMessage());
                });
        };
    }

    // Authentication filter
    public static HandlerFilterFunction<ServerResponse, ServerResponse> authenticationFilter(
            AuthService authService) {
        return (request, next) -> {
            String authHeader = request.headers()
                .header("Authorization")
                .stream()
                .findFirst()
                .orElse("");

            if (!authHeader.startsWith("Bearer ")) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .bodyValue(new ErrorResponse("Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);

            return authService.validateToken(token)
                .flatMap(user -> {
                    // Add user to request attributes
                    ServerRequest mutatedRequest = ServerRequest.from(request)
                        .attribute("currentUser", user)
                        .build();
                    return next.handle(mutatedRequest);
                })
                .onErrorResume(AuthException.class, e ->
                    ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .bodyValue(new ErrorResponse(e.getMessage())));
        };
    }

    // Rate limiting filter
    public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimitFilter(
            RateLimiter rateLimiter) {
        return (request, next) -> {
            String clientId = request.headers()
                .header("X-Client-Id")
                .stream()
                .findFirst()
                .orElse(request.remoteAddress()
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown"));

            return rateLimiter.tryAcquire(clientId)
                .flatMap(allowed -> {
                    if (allowed) {
                        return next.handle(request);
                    } else {
                        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", "60")
                            .bodyValue(new ErrorResponse("Rate limit exceeded"));
                    }
                });
        };
    }

    // Request ID filter (for tracing)
    public static HandlerFilterFunction<ServerResponse, ServerResponse> requestIdFilter() {
        return (request, next) -> {
            String requestId = request.headers()
                .header("X-Request-Id")
                .stream()
                .findFirst()
                .orElse(UUID.randomUUID().toString());

            ServerRequest mutatedRequest = ServerRequest.from(request)
                .attribute("requestId", requestId)
                .build();

            return next.handle(mutatedRequest)
                .map(response -> ServerResponse.from(response)
                    .header("X-Request-Id", requestId)
                    .build());
        };
    }

    // CORS filter
    public static HandlerFilterFunction<ServerResponse, ServerResponse> corsFilter(
            String allowedOrigin) {
        return (request, next) -> next.handle(request)
            .map(response -> ServerResponse.from(response)
                .header("Access-Control-Allow-Origin", allowedOrigin)
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .build());
    }
}
```

### Error Handling

Functional endpoints handle errors explicitly in handlers or through filters:

```java
// In-handler error handling
public Mono<ServerResponse> getUser(ServerRequest request) {
    String id = request.pathVariable("id");

    return userRepository.findById(id)
        .flatMap(user -> ServerResponse.ok().bodyValue(user))
        .switchIfEmpty(ServerResponse.notFound().build())
        .onErrorResume(IllegalArgumentException.class, e ->
            ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage())))
        .onErrorResume(e ->
            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("An unexpected error occurred")));
}
```

### Global Error Handler Filter

Create a filter that catches all errors:

```java
public static HandlerFilterFunction<ServerResponse, ServerResponse> errorHandlingFilter() {
    return (request, next) -> next.handle(request)
        .onErrorResume(ValidationException.class, e ->
            ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse("VALIDATION_ERROR", e.getMessage())))
        .onErrorResume(NotFoundException.class, e ->
            ServerResponse.notFound().build())
        .onErrorResume(UnauthorizedException.class, e ->
            ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse("UNAUTHORIZED", e.getMessage())))
        .onErrorResume(ForbiddenException.class, e ->
            ServerResponse.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse("FORBIDDEN", e.getMessage())))
        .onErrorResume(e -> {
            log.error("Unexpected error: ", e);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
}
```

Apply it to all routes:

```java
@Bean
public RouterFunction<ServerResponse> routes(UserHandler userHandler) {
    return route()
        .filter(errorHandlingFilter())
        .filter(loggingFilter())
        .path("/api", builder -> builder
            .GET("/users", userHandler::listUsers)
            .GET("/users/{id}", userHandler::getUser)
            .POST("/users", userHandler::createUser)
        )
        .build();
}
```

### Error Response DTO

```java
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    String path
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }

    public ErrorResponse(String message) {
        this("ERROR", message, Instant.now(), null);
    }

    public ErrorResponse withPath(String path) {
        return new ErrorResponse(code, message, timestamp, path);
    }
}
```

## 10.6 Organizing Functional Endpoints

### Project Structure

For larger applications, organize handlers and routes by domain:

```
src/main/java/com/example/
├── config/
│   └── RouterConfig.java           # Combines all routes
├── user/
│   ├── UserHandler.java            # User-related handlers
│   ├── UserRouter.java             # User routes
│   └── UserValidation.java         # User validation logic
├── order/
│   ├── OrderHandler.java
│   ├── OrderRouter.java
│   └── OrderValidation.java
├── product/
│   ├── ProductHandler.java
│   ├── ProductRouter.java
│   └── ProductValidation.java
└── common/
    ├── Filters.java                # Shared filters
    ├── ErrorHandling.java          # Global error handling
    └── Predicates.java             # Custom predicates
```

### Domain-Based Router

```java
// UserRouter.java
@Component
public class UserRouter {

    private final UserHandler handler;

    public UserRouter(UserHandler handler) {
        this.handler = handler;
    }

    public RouterFunction<ServerResponse> routes() {
        return route()
            .path("/users", builder -> builder
                .GET("", handler::listUsers)
                .GET("/{id}", handler::getUser)
                .POST("", contentType(MediaType.APPLICATION_JSON), handler::createUser)
                .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateUser)
                .DELETE("/{id}", handler::deleteUser)
                .GET("/{id}/orders", handler::getUserOrders)
            )
            .build();
    }
}
```

### Central Router Configuration

```java
// RouterConfig.java
@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> mainRouter(
            UserRouter userRouter,
            OrderRouter orderRouter,
            ProductRouter productRouter,
            HealthHandler healthHandler) {

        return route()
            // Health and info endpoints (no auth required)
            .GET("/health", healthHandler::health)
            .GET("/info", healthHandler::info)

            // API routes with authentication
            .path("/api/v1", apiBuilder -> apiBuilder
                .filter(Filters.loggingFilter())
                .filter(Filters.errorHandlingFilter())
                .filter(Filters.authenticationFilter())
                .add(userRouter.routes())
                .add(orderRouter.routes())
                .add(productRouter.routes())
            )

            // Fallback for unmatched routes
            .route(RequestPredicates.all(), request ->
                ServerResponse.notFound()
                    .bodyValue(new ErrorResponse("NOT_FOUND", "Resource not found")))
            .build();
    }
}
```

### Versioned APIs

Handle API versioning with nested paths:

```java
@Bean
public RouterFunction<ServerResponse> versionedRouter(
        UserHandlerV1 v1Handler,
        UserHandlerV2 v2Handler) {

    return route()
        .path("/api", builder -> builder
            // Version 1 API
            .path("/v1/users", v1Builder -> v1Builder
                .GET("", v1Handler::listUsers)
                .GET("/{id}", v1Handler::getUser)
            )
            // Version 2 API with enhanced features
            .path("/v2/users", v2Builder -> v2Builder
                .GET("", v2Handler::listUsers)       // Returns different format
                .GET("/{id}", v2Handler::getUser)
                .GET("/{id}/profile", v2Handler::getUserProfile)  // New endpoint
            )
        )
        .build();
}
```

Or version via headers:

```java
@Bean
public RouterFunction<ServerResponse> headerVersionedRouter(
        UserHandlerV1 v1Handler,
        UserHandlerV2 v2Handler) {

    RequestPredicate v2Predicate = headers(h ->
        h.header("X-Api-Version").contains("2"));

    return route()
        .path("/api/users", builder -> builder
            // V2 routes (checked first)
            .GET("", v2Predicate, v2Handler::listUsers)
            .GET("/{id}", v2Predicate, v2Handler::getUser)
            // V1 routes (fallback)
            .GET("", v1Handler::listUsers)
            .GET("/{id}", v1Handler::getUser)
        )
        .build();
}
```

## 10.7 Testing Functional Endpoints

### Unit Testing Handlers

Handlers are just functions, making them easy to unit test:

```java
@ExtendWith(MockitoExtension.class)
class UserHandlerTest {

    @Mock
    private UserRepository userRepository;

    private UserHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserHandler(userRepository);
    }

    @Test
    void getUser_whenExists_returnsUser() {
        // Given
        User user = new User("1", "John", "john@example.com");
        when(userRepository.findById("1")).thenReturn(Mono.just(user));

        MockServerRequest request = MockServerRequest.builder()
            .pathVariable("id", "1")
            .build();

        // When
        Mono<ServerResponse> response = handler.getUser(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.OK, serverResponse.statusCode());
            })
            .verifyComplete();
    }

    @Test
    void getUser_whenNotExists_returnsNotFound() {
        // Given
        when(userRepository.findById("1")).thenReturn(Mono.empty());

        MockServerRequest request = MockServerRequest.builder()
            .pathVariable("id", "1")
            .build();

        // When
        Mono<ServerResponse> response = handler.getUser(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.NOT_FOUND, serverResponse.statusCode());
            })
            .verifyComplete();
    }
}
```

### Integration Testing with WebTestClient

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserRoutesIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void listUsers_returnsAllUsers() {
        webTestClient.get()
            .uri("/api/v1/users")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(User.class)
            .hasSize(3);
    }

    @Test
    void getUser_whenExists_returnsUser() {
        webTestClient.get()
            .uri("/api/v1/users/1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("1")
            .jsonPath("$.name").isEqualTo("John");
    }

    @Test
    void getUser_whenNotExists_returnsNotFound() {
        webTestClient.get()
            .uri("/api/v1/users/999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void createUser_withValidData_returnsCreated() {
        UserCreateRequest request = new UserCreateRequest("Jane", "jane@example.com", "USER");

        webTestClient.post()
            .uri("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody()
            .jsonPath("$.name").isEqualTo("Jane")
            .jsonPath("$.email").isEqualTo("jane@example.com");
    }

    @Test
    void createUser_withInvalidData_returnsBadRequest() {
        UserCreateRequest request = new UserCreateRequest("Jane", "invalid-email", "USER");

        webTestClient.post()
            .uri("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}
```

### Testing Routers Directly

```java
class UserRouterTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        UserRepository mockRepo = mock(UserRepository.class);
        when(mockRepo.findAll()).thenReturn(Flux.just(
            new User("1", "John", "john@example.com"),
            new User("2", "Jane", "jane@example.com")
        ));

        UserHandler handler = new UserHandler(mockRepo);
        UserRouter router = new UserRouter(handler);

        webTestClient = WebTestClient
            .bindToRouterFunction(router.routes())
            .build();
    }

    @Test
    void listUsers_returnsUsers() {
        webTestClient.get()
            .uri("/users")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(User.class)
            .hasSize(2);
    }
}
```

## Summary

Functional endpoints provide an alternative to annotated controllers that embraces explicit function composition:

| Concept | Description |
|---------|-------------|
| **RouterFunction** | Defines routes by composing predicates and handlers |
| **HandlerFunction** | Processes requests and returns responses |
| **RequestPredicate** | Determines if a route matches a request |
| **Filters** | Wrap handlers to add cross-cutting concerns |
| **ServerRequest** | Provides access to all request data |
| **ServerResponse** | Fluent builder for constructing responses |

Key insights from this chapter:

1. **Functional endpoints aren't better or worse than annotated controllers—they're different**. Choose based on your team's preferences and the complexity of your routing logic.

2. **Everything is explicit**. There's no annotation magic—you can see exactly how requests are routed and handled.

3. **Composition is powerful**. Build complex routing logic from simple, testable building blocks.

4. **Filters replace @ControllerAdvice**. Use handler filters for cross-cutting concerns like authentication, logging, and error handling.

5. **Testing is straightforward**. Handlers are just functions, making unit testing simple. Use WebTestClient for integration testing.

In the next chapter, we'll explore WebClient—the reactive HTTP client that replaces RestTemplate for calling external services.
