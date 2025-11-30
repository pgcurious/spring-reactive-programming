# First Principles: Deriving Functional Endpoints

## Forget That Functional Endpoints Exist

Imagine you've just learned about reactive streams and you understand how Mono and Flux work. You've seen annotated controllers and understand they work through Spring's annotation processing. But you're wondering: **is there a more direct way to define web handlers?**

Let's derive functional endpoints from first principles, understanding why this programming model emerged and what problems it solves.

## Step 1: What Is a Web Handler, Really?

At its core, a web handler is a function:

```
Input: HTTP Request
Output: HTTP Response
```

That's it. All the complexity of web frameworks—annotations, argument resolvers, return value handlers—exists to make this fundamental function more convenient to write.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE ESSENCE OF A WEB HANDLER                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                    ┌──────────────┐                                      │
│   HTTP Request ───▶│   Handler    │───▶ HTTP Response                   │
│                    │   Function   │                                      │
│                    └──────────────┘                                      │
│                                                                          │
│   Everything else is convenience built on top of this.                  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: Making It Reactive

In a reactive world, our handler should return a publisher that eventually produces a response:

```java
// The fundamental reactive handler signature
Function<ServerRequest, Mono<ServerResponse>> handler;
```

This is exactly what `HandlerFunction` is:

```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
```

No annotations. No magic. Just a function.

## Step 3: The Routing Problem

Now we have handlers, but how do we route requests to the right handler?

With annotated controllers, Spring scans for `@GetMapping("/users")` at startup and builds a routing table. But there's another way: **make routing explicit through code**.

What if routing was also just a function?

```java
// Given a request, find the handler (if any)
Function<ServerRequest, Optional<HandlerFunction>> router;
```

Or in reactive terms:

```java
// Return empty Mono if no match
Function<ServerRequest, Mono<HandlerFunction>> router;
```

This is the essence of `RouterFunction`:

```java
@FunctionalInterface
public interface RouterFunction<T extends ServerResponse> {
    Mono<HandlerFunction<T>> route(ServerRequest request);
}
```

## Step 4: Matching Requests with Predicates

How does a router know if it should handle a request? It needs to test the request against some criteria:

- Is this a GET request?
- Does the path match `/users/{id}`?
- Is the content type JSON?

These tests are predicates—functions that return true or false:

```java
// A predicate tests if a request matches certain criteria
Function<ServerRequest, Boolean> predicate;
```

This becomes `RequestPredicate`:

```java
@FunctionalInterface
public interface RequestPredicate {
    boolean test(ServerRequest request);
}
```

## Step 5: Composing Routes

Now we have three building blocks:

1. **Predicates** - test if a request matches
2. **Handlers** - process requests and return responses
3. **Routers** - combine predicates and handlers

A single route is: **Predicate + Handler**

```java
// If predicate matches, use this handler
public static RouterFunction<ServerResponse> route(
        RequestPredicate predicate,
        HandlerFunction<ServerResponse> handler) {
    return request -> {
        if (predicate.test(request)) {
            return Mono.just(handler);
        }
        return Mono.empty();
    };
}
```

Multiple routes can be composed by trying them in sequence:

```java
// Try route1, then route2, then route3
RouterFunction<ServerResponse> combined = route1.and(route2).and(route3);
```

The `and()` operation:

```java
default RouterFunction<T> and(RouterFunction<T> other) {
    return request -> this.route(request)
        .switchIfEmpty(Mono.defer(() -> other.route(request)));
}
```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ROUTE COMPOSITION                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Request arrives                                                        │
│        │                                                                 │
│        ▼                                                                 │
│   ┌─────────────────┐                                                   │
│   │ Route 1: GET /a │───Match───▶ Handler 1 ───▶ Response               │
│   └────────┬────────┘                                                   │
│            │ No match                                                    │
│            ▼                                                             │
│   ┌─────────────────┐                                                   │
│   │ Route 2: POST /b│───Match───▶ Handler 2 ───▶ Response               │
│   └────────┬────────┘                                                   │
│            │ No match                                                    │
│            ▼                                                             │
│   ┌─────────────────┐                                                   │
│   │ Route 3: GET /c │───Match───▶ Handler 3 ───▶ Response               │
│   └────────┬────────┘                                                   │
│            │ No match                                                    │
│            ▼                                                             │
│       No handler found (404)                                            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: Nested Routes

REST APIs often have hierarchical paths:

```
/api/v1/users
/api/v1/users/{id}
/api/v1/users/{id}/orders
/api/v1/orders
/api/v1/products
```

Rather than repeating `/api/v1` everywhere, we want to nest routes:

```java
// Pseudo-code for nested routing
path("/api/v1",
    path("/users",
        GET("", listUsers),
        GET("/{id}", getUser),
        GET("/{id}/orders", getUserOrders)
    ),
    path("/orders", ...),
    path("/products", ...)
)
```

This is composition at work. A nested route:
1. Tests if the path starts with the prefix
2. Strips the prefix from the request
3. Passes the modified request to child routes

```java
public static RouterFunction<ServerResponse> nest(
        RequestPredicate predicate,
        RouterFunction<ServerResponse> routes) {
    return request -> {
        if (predicate.test(request)) {
            // Strip matched path prefix from request
            ServerRequest nestedRequest = /* modify request */;
            return routes.route(nestedRequest);
        }
        return Mono.empty();
    };
}
```

## Step 7: Cross-Cutting Concerns with Filters

Every request needs:
- Logging
- Authentication
- Error handling
- Request tracing

With annotated controllers, we use `@ControllerAdvice`, interceptors, and filters. With functional endpoints, we use **function composition**:

```java
// A filter wraps a handler
Function<HandlerFunction, HandlerFunction> filter;
```

More precisely:

```java
@FunctionalInterface
public interface HandlerFilterFunction<T extends ServerResponse, R extends ServerResponse> {
    Mono<R> filter(ServerRequest request, HandlerFunction<T> next);
}
```

The filter receives the request and the "next" handler. It can:
1. Modify the request before passing to the handler
2. Call the handler (or not)
3. Modify the response after the handler returns
4. Handle errors from the handler

```java
// Authentication filter
HandlerFilterFunction<ServerResponse, ServerResponse> authFilter = (request, next) -> {
    String token = extractToken(request);
    if (token == null) {
        return ServerResponse.status(401).build();
    }
    return validateToken(token)
        .flatMap(user -> {
            ServerRequest authedRequest = addUserToRequest(request, user);
            return next.handle(authedRequest);
        })
        .onErrorResume(AuthException.class, e ->
            ServerResponse.status(401).build());
};
```

Filters compose by wrapping:

```java
RouterFunction<ServerResponse> filtered = route()
    .filter(loggingFilter)      // Outermost
    .filter(authFilter)          // Middle
    .filter(errorHandlingFilter) // Innermost
    .GET("/users", handler)
    .build();
```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    FILTER COMPOSITION                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Request                                                                │
│      │                                                                   │
│      ▼                                                                   │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │                      Logging Filter                               │  │
│   │  ┌────────────────────────────────────────────────────────────┐  │  │
│   │  │                    Auth Filter                              │  │  │
│   │  │  ┌──────────────────────────────────────────────────────┐  │  │  │
│   │  │  │               Error Handling Filter                   │  │  │  │
│   │  │  │  ┌────────────────────────────────────────────────┐  │  │  │  │
│   │  │  │  │                  Handler                        │  │  │  │  │
│   │  │  │  └────────────────────────────────────────────────┘  │  │  │  │
│   │  │  └──────────────────────────────────────────────────────┘  │  │  │
│   │  └────────────────────────────────────────────────────────────┘  │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│      │                                                                   │
│      ▼                                                                   │
│   Response                                                               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Why This Approach?

We've derived functional endpoints from first principles. But why choose this over annotated controllers?

### Explicit Over Implicit

With annotations:
```java
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable String id) { ... }
```

Magic happens at startup: annotation scanning, path parsing, argument resolution.

With functions:
```java
GET("/users/{id}", request -> {
    String id = request.pathVariable("id");
    return findUser(id)
        .flatMap(user -> ServerResponse.ok().bodyValue(user));
})
```

Everything is visible. No startup scanning. No reflection.

### Testability

Testing annotated controllers requires Spring's test infrastructure:

```java
@WebFluxTest(UserController.class)
class UserControllerTest { ... }
```

Testing handler functions is just testing functions:

```java
@Test
void handler_returns_user() {
    ServerRequest request = MockServerRequest.builder()
        .pathVariable("id", "1")
        .build();

    Mono<ServerResponse> response = handler.getUser(request);

    StepVerifier.create(response)
        .assertNext(r -> assertEquals(200, r.statusCode().value()))
        .verifyComplete();
}
```

### Runtime Flexibility

Annotated routes are fixed at startup. Functional routes can be dynamic:

```java
@Bean
public RouterFunction<ServerResponse> dynamicRoutes(FeatureFlags features) {
    RouterFunctions.Builder builder = route();

    if (features.isEnabled("new-api")) {
        builder.GET("/v2/users", newHandler::listUsers);
    }

    return builder.build();
}
```

### Composition

Functional programming excels at composition. Want to add authentication to some routes but not others?

```java
RouterFunction<ServerResponse> publicRoutes = route()
    .GET("/health", healthHandler)
    .GET("/info", infoHandler)
    .build();

RouterFunction<ServerResponse> securedRoutes = route()
    .filter(authFilter)
    .path("/api", apiBuilder -> apiBuilder
        .add(userRoutes)
        .add(orderRoutes)
    )
    .build();

RouterFunction<ServerResponse> all = publicRoutes.and(securedRoutes);
```

## The Trade-Off

Functional endpoints have costs:

1. **More verbose** - You write more code for simple cases
2. **Less familiar** - Most Spring developers know annotations
3. **Less IDE support** - Annotations provide better navigation and refactoring

The decision isn't about which is "better"—it's about which fits your needs.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WHEN TO USE EACH APPROACH                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ANNOTATED CONTROLLERS                 FUNCTIONAL ENDPOINTS             │
│   ─────────────────────                 ────────────────────             │
│                                                                          │
│   • Simple CRUD APIs                    • Complex routing logic          │
│   • Team familiar with Spring MVC       • Microservices with few routes  │
│   • IDE-driven development              • Dynamic route configuration    │
│   • Lots of endpoints                   • Functional programming style   │
│   • Standard request/response           • Testing in isolation           │
│                                                                          │
│   You can even mix both in the same application!                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Complete Picture

Starting from "a handler is just a function," we derived:

1. **HandlerFunction** - `ServerRequest → Mono<ServerResponse>`
2. **RequestPredicate** - `ServerRequest → boolean`
3. **RouterFunction** - `ServerRequest → Mono<HandlerFunction>`
4. **HandlerFilterFunction** - Wraps handlers for cross-cutting concerns
5. **Route composition** - Combine routes with `and()`, `nest()`, `filter()`

These five concepts are all you need. Everything else—the fluent builder API, the convenience methods like `GET()` and `POST()`—is syntactic sugar over these fundamentals.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    FUNCTIONAL ENDPOINTS: CORE CONCEPTS                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                         RouterFunction                                   │
│                              │                                           │
│                    ┌─────────┴─────────┐                                │
│                    │                   │                                 │
│               Predicate            Handler                               │
│                    │                   │                                 │
│            ┌───────┴───────┐          │                                 │
│            │               │          │                                 │
│         Method           Path     Function                               │
│         Content-Type     Headers  returning                              │
│         Accept           Query    Mono<ServerResponse>                   │
│         Custom           Params                                          │
│                                                                          │
│   Compose with: and(), nest(), filter()                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Key Insight

Functional endpoints aren't a "new" feature—they're the **original** abstraction. Annotated controllers are a convenience layer built on top of these functional primitives.

When you understand functional endpoints, you understand what Spring is doing under the hood with your annotations. This knowledge helps you debug issues, extend behavior, and make informed decisions about which approach to use.

The functional model also aligns perfectly with reactive programming. When everything is a function that returns publishers, composition becomes natural. Filters are just function wrappers. Routes are just conditional function dispatch. There's no impedance mismatch between the programming model and the reactive runtime.

## Summary

From first principles, we derived that:

1. A web handler is fundamentally a function from request to response
2. Routing is a function that finds the right handler for a request
3. Predicates are boolean functions that test requests
4. Routes compose through simple functional operations
5. Filters are handler wrappers that add cross-cutting concerns

Functional endpoints expose these primitives directly, giving you explicit control at the cost of some convenience. Annotated controllers hide these primitives behind annotation magic, giving you convenience at the cost of some visibility.

Both approaches are valid. The best choice depends on your specific needs, team preferences, and the complexity of your routing logic.
