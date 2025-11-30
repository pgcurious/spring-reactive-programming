# First Principles: Deriving WebClient

## Forget That WebClient Exists

Imagine you've built a reactive web application with WebFlux. Your endpoints return Mono and Flux, and the event loop handles thousands of concurrent requests efficiently. But now you need to call an external service.

The question: **How do you make HTTP calls without blocking?**

Let's derive the reactive HTTP client from first principles.

## Step 1: The Problem with Blocking HTTP Clients

Traditional HTTP clients like `RestTemplate` work like this:

```java
User user = restTemplate.getForObject("http://service/users/1", User.class);
// Thread is BLOCKED here until response arrives
System.out.println(user.getName());
```

What happens during the wait:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING HTTP CALL TIMELINE                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1                                                               │
│   ════════                                                               │
│                                                                          │
│   0ms     10ms    20ms    30ms    40ms    50ms    60ms    70ms          │
│   │       │       │       │       │       │       │       │             │
│   ├───────┴───────┴───────┴───────┴───────┴───────┴───────┤             │
│   │                                                       │             │
│   │  ┌───────┐   ┌─────────────────────────────┐   ┌───────┐           │
│   │  │ Send  │   │         WAITING             │   │Process│           │
│   │  │Request│   │  Thread does NOTHING        │   │Result │           │
│   │  └───────┘   └─────────────────────────────┘   └───────┘           │
│   │                                                       │             │
│                                                                          │
│   The thread is occupied but idle for ~50ms                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

In a reactive application with few threads, this is catastrophic. If you have 4 event loop threads and make 4 blocking HTTP calls, your entire application freezes.

## Step 2: What Does "Non-Blocking" Mean for HTTP?

For a non-blocking HTTP client, we need:

1. **Send the request** - Quick, don't wait for response
2. **Register a callback** - "Call me when response arrives"
3. **Free the thread** - Let it do other work
4. **Process response** - When it arrives, via the callback

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    NON-BLOCKING HTTP CALL TIMELINE                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1 (Event Loop)                                                  │
│   ══════════════════════                                                 │
│                                                                          │
│   0ms     10ms    20ms    30ms    40ms    50ms    60ms    70ms          │
│   │       │       │       │       │       │       │       │             │
│   ├───────┼───────┼───────┼───────┼───────┼───────┼───────┤             │
│   │       │       │       │       │       │       │       │             │
│   │┌─────┐│┌─────┐│┌─────┐│┌─────┐│┌─────┐│┌─────┐│┌─────┐│             │
│   ││Send ││Handle││Handle││Handle││Handle││Handle││Process│             │
│   ││Req  ││Req B ││Req C ││Req D ││Req E ││Req F ││Resp A│              │
│   │└─────┘│└─────┘│└─────┘│└─────┘│└─────┘│└─────┘│└─────┘│             │
│   │       │       │       │       │       │       │       │             │
│                                                                          │
│   Same thread handles 6 other requests while waiting!                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: Representing "Future Response" as a Type

In reactive terms, an HTTP response that will arrive eventually is a perfect fit for `Mono`:

```java
// Blocking: Response IS here, right now
User user = blockingClient.get("/users/1");

// Non-blocking: Response WILL BE here, eventually
Mono<User> userMono = reactiveClient.get("/users/1");
```

The `Mono<User>` represents:
- A single response that will arrive (or fail)
- The ability to transform the response when it arrives
- Error handling for connection failures, timeouts, etc.

## Step 4: Building the Abstraction

What operations do we need on a reactive HTTP client?

### Making Requests

```java
// Specify the HTTP method and URL
Mono<Response> response = client.get("/users/1");
Mono<Response> response = client.post("/users");
Mono<Response> response = client.put("/users/1");
Mono<Response> response = client.delete("/users/1");
```

### Setting Headers and Body

```java
// Add headers
client.get("/users/1")
    .header("Authorization", "Bearer token")
    .header("Accept", "application/json");

// Add request body
client.post("/users")
    .body(new User("Alice", "alice@example.com"));
```

### Extracting Response

```java
// Get just the body
Mono<User> user = client.get("/users/1")
    .bodyToMono(User.class);

// Get multiple items
Flux<User> users = client.get("/users")
    .bodyToFlux(User.class);

// Get full response with headers
Mono<ResponseEntity<User>> response = client.get("/users/1")
    .toEntity(User.class);
```

## Step 5: The Flow of a Non-Blocking Request

Let's trace what happens when we make a request:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    NON-BLOCKING REQUEST LIFECYCLE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. BUILD REQUEST                                                       │
│      ┌──────────────────────────────────────────────────────────────┐   │
│      │ WebClient.get("/users/1").header("Auth", "token")            │   │
│      │ Returns: RequestSpec (builder, no network activity yet)      │   │
│      └──────────────────────────────────────────────────────────────┘   │
│                                │                                         │
│                                ▼                                         │
│   2. SPECIFY RESPONSE TYPE                                               │
│      ┌──────────────────────────────────────────────────────────────┐   │
│      │ .retrieve().bodyToMono(User.class)                           │   │
│      │ Returns: Mono<User> (cold - nothing sent yet)                │   │
│      └──────────────────────────────────────────────────────────────┘   │
│                                │                                         │
│                                ▼                                         │
│   3. SUBSCRIBE (TRIGGER)                                                 │
│      ┌──────────────────────────────────────────────────────────────┐   │
│      │ mono.subscribe(user -> ...)                                  │   │
│      │ NOW: Connection made, request sent, callback registered      │   │
│      └──────────────────────────────────────────────────────────────┘   │
│                                │                                         │
│                                ▼                                         │
│   4. WAIT (NON-BLOCKING)                                                │
│      ┌──────────────────────────────────────────────────────────────┐   │
│      │ Thread released. OS/Netty notify when data arrives.          │   │
│      └──────────────────────────────────────────────────────────────┘   │
│                                │                                         │
│                                ▼                                         │
│   5. RESPONSE ARRIVES                                                    │
│      ┌──────────────────────────────────────────────────────────────┐   │
│      │ Event loop picks up response, deserializes to User           │   │
│      │ Callback invoked with the User object                        │   │
│      └──────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

The key insight: **Nothing happens until subscription**. The Mono is "cold" until someone subscribes.

## Step 6: Why Return Mono/Flux Instead of Callbacks?

We could use callbacks:

```java
// Callback style (what we're avoiding)
client.get("/users/1", user -> {
    System.out.println(user.getName());
}, error -> {
    System.err.println("Failed: " + error);
});
```

But Mono/Flux give us:

### Composition

```java
// Chain operations elegantly
Mono<UserProfile> profile = client.get("/users/1")
    .bodyToMono(User.class)
    .flatMap(user -> client.get("/profiles/" + user.getProfileId())
        .bodyToMono(Profile.class)
        .map(p -> new UserProfile(user, p)));
```

### Parallel Execution

```java
// Execute in parallel and combine
Mono<Dashboard> dashboard = Mono.zip(
    client.get("/users/1").bodyToMono(User.class),
    client.get("/orders?userId=1").bodyToFlux(Order.class).collectList(),
    client.get("/notifications?userId=1").bodyToFlux(Notification.class).collectList(),
    (user, orders, notifications) -> new Dashboard(user, orders, notifications)
);
```

### Error Handling

```java
// Declarative error handling
Mono<User> user = client.get("/users/1")
    .bodyToMono(User.class)
    .retry(3)
    .timeout(Duration.ofSeconds(5))
    .onErrorResume(e -> Mono.just(User.anonymous()));
```

### Backpressure

```java
// Control the rate of processing
client.get("/large-dataset")
    .bodyToFlux(Record.class)
    .limitRate(100)  // Process 100 at a time
    .flatMap(record -> processRecord(record), 10)  // 10 concurrent
    .subscribe();
```

## Step 7: Handling HTTP Semantics

HTTP has specific semantics we need to handle:

### Status Codes

```java
Mono<User> user = client.get("/users/1")
    .retrieve()
    .onStatus(status -> status.value() == 404,
        response -> Mono.error(new UserNotFoundException()))
    .onStatus(HttpStatusCode::is5xxServerError,
        response -> Mono.error(new ServiceUnavailableException()))
    .bodyToMono(User.class);
```

### Response Headers

```java
client.get("/users/1")
    .exchangeToMono(response -> {
        String rateLimitRemaining = response.headers()
            .header("X-RateLimit-Remaining")
            .get(0);
        // Use header information
        return response.bodyToMono(User.class);
    });
```

### Streaming Responses

```java
// Server-Sent Events
Flux<Event> events = client.get("/events/stream")
    .accept(MediaType.TEXT_EVENT_STREAM)
    .retrieve()
    .bodyToFlux(Event.class);
```

## Step 8: Connection Management

Non-blocking doesn't mean unlimited connections. We still need:

### Connection Pooling

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONNECTION POOL                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Request Queue                    Connection Pool                       │
│   ═════════════                    ═══════════════                       │
│                                                                          │
│   ┌─────────┐                      ┌─────────────────────┐              │
│   │Request 1│────────────────────▶│ Connection 1 (busy) │              │
│   └─────────┘                      └─────────────────────┘              │
│   ┌─────────┐                      ┌─────────────────────┐              │
│   │Request 2│────────────────────▶│ Connection 2 (busy) │              │
│   └─────────┘                      └─────────────────────┘              │
│   ┌─────────┐                      ┌─────────────────────┐              │
│   │Request 3│─── waiting ────────▶│ Connection 3 (idle) │              │
│   └─────────┘                      └─────────────────────┘              │
│   ┌─────────┐                      ┌─────────────────────┐              │
│   │Request 4│                      │ Connection 4 (idle) │              │
│   └─────────┘                      └─────────────────────┘              │
│       :                                                                  │
│                                                                          │
│   When a connection frees up, next queued request uses it               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Timeout Management

```java
// Different timeouts for different scenarios
HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)  // Connect timeout
    .responseTimeout(Duration.ofSeconds(10));            // Response timeout

WebClient client = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .build();
```

## Step 9: Error Semantics

HTTP errors need different handling than connection errors:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ERROR CATEGORIES                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   CONNECTION ERRORS                HTTP ERRORS                           │
│   ═════════════════                ═══════════════                       │
│                                                                          │
│   • Connection refused             • 4xx Client errors                   │
│   • DNS resolution failed          • 5xx Server errors                   │
│   • Connection timeout             • Unexpected status codes             │
│   • SSL/TLS errors                                                       │
│   • Read timeout                                                         │
│                                                                          │
│   Usually: retry appropriate       Usually: inspect body for details     │
│                                                                          │
│   WebClientRequestException        WebClientResponseException            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 10: The Complete Picture

Putting it all together, a reactive HTTP client needs:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WEBCLIENT ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                        User Code                                         │
│                           │                                              │
│                           ▼                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    WebClient API                                 │   │
│   │  • Fluent builder for requests                                  │   │
│   │  • Returns Mono/Flux (cold publishers)                          │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                           │                                              │
│                           ▼                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    Exchange Functions                            │   │
│   │  • Request/response filters                                     │   │
│   │  • Logging, auth, metrics                                       │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                           │                                              │
│                           ▼                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    ClientHttpConnector                           │   │
│   │  • Manages actual connections                                   │   │
│   │  • Default: ReactorClientHttpConnector (Netty)                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                           │                                              │
│                           ▼                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    Netty / Connection Pool                       │   │
│   │  • Non-blocking I/O                                             │   │
│   │  • Connection reuse                                             │   │
│   │  • Timeout handling                                             │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                           │                                              │
│                           ▼                                              │
│                        Network                                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Why This Design?

The WebClient design follows from our constraints:

1. **Non-blocking I/O** → Return Mono/Flux, not raw values
2. **Composable** → Use the same abstractions as the rest of reactive code
3. **HTTP semantics** → Handle status codes, headers, streaming
4. **Resilient** → Support timeouts, retries, circuit breakers
5. **Efficient** → Connection pooling, request pipelining

Each design decision flows from the fundamental requirement: **make HTTP calls without blocking threads**.

## The Key Insight

WebClient isn't just "RestTemplate but async." It's a fundamentally different approach:

| RestTemplate | WebClient |
|--------------|-----------|
| Returns values | Returns publishers |
| Thread waits for response | Thread continues immediately |
| Sequential by default | Parallel-friendly |
| Error = exception thrown | Error = signal in stream |
| One request at a time per thread | Many requests per thread |

When you understand that WebClient returns a **description of work to be done** (a Mono) rather than **doing the work immediately**, the entire API makes sense.

## Summary

From first principles, we derived that a reactive HTTP client must:

1. **Return publishers** (Mono/Flux) instead of blocking for responses
2. **Be lazy** - nothing happens until subscription
3. **Integrate with reactive operators** - map, flatMap, zip, retry
4. **Handle HTTP semantics** - status codes, headers, streaming
5. **Manage connections** - pooling, timeouts, lifecycle
6. **Distinguish error types** - connection vs HTTP errors

WebClient is the natural result of applying reactive principles to HTTP communication. It's not just a different syntax—it's a different paradigm that enables massive concurrency without massive thread counts.
