# Chapter 8: Why WebFlux?

> "The right tool for the right job is a sign of craftsmanship, not limitation." — Unknown

In Parts I and II, we built a deep understanding of why reactive programming exists and mastered Project Reactor. Now comes the pivotal question: **When should you actually use this in Spring?**

This chapter isn't about convincing you that WebFlux is better than Spring MVC. It's about understanding the trade-offs so you can make informed architectural decisions. By the end, you'll know exactly when WebFlux shines, when to stick with MVC, and how to recognize the signs that point to each choice.

---

## 8.1 Spring MVC vs. Spring WebFlux: The Architecture

Let's start with a clear picture of what makes these two frameworks fundamentally different.

### Spring MVC: The Thread-Per-Request Model

Spring MVC is built on the **Servlet API**, which was designed with a simple mental model: one thread handles one request from start to finish.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SPRING MVC: THREAD-PER-REQUEST MODEL                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Incoming Requests                    Tomcat Thread Pool (200 threads)     │
│                                                                            │
│  Request 1 ─────────────────────────► Thread-1 ──────────────────►        │
│                                           │                                │
│  Request 2 ─────────────────────────► Thread-2 ──────────────────►        │
│                                           │                                │
│  Request 3 ─────────────────────────► Thread-3 ──────────────────►        │
│                                           │                                │
│      ...                                 ...                               │
│                                                                            │
│  Request 201 ─────────────────────► [WAITING - no thread available]       │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  What Thread-1 does during a request:                                     │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ Receive  │ Parse │ Controller │ Database │ Build │ Send │        │     │
│  │ Request  │ Body  │ Logic      │ Query    │ JSON  │ Resp │        │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│  0ms        2ms     5ms          │          105ms   110ms  115ms          │
│                                  │                                        │
│                                  └── Thread is BLOCKED for 100ms         │
│                                      waiting for database response       │
│                                      (doing nothing useful)              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

This model is:
- **Simple to understand**: One thread, one request, linear flow
- **Easy to debug**: Stack traces are meaningful
- **Limited by thread count**: 200 threads = ~200 concurrent requests maximum

### Spring WebFlux: The Reactive Model

Spring WebFlux is built on **Reactive Streams** and runs on an **event-loop** architecture (Netty by default).

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SPRING WEBFLUX: EVENT LOOP MODEL                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Incoming Requests                    Netty Event Loop (4-8 threads)       │
│                                                                            │
│  Request 1 ─────┐                                                          │
│  Request 2 ─────┼──────────────────► Event Loop Thread ◄────────────      │
│  Request 3 ─────┤                         │                                │
│  Request 4 ─────┤                         │                                │
│      ...        │                         │                                │
│  Request 10000 ─┘                         ▼                                │
│                                   [Non-blocking operations]                │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  How one thread handles multiple requests:                                │
│                                                                            │
│  Time    0ms    1ms    2ms    3ms    4ms    5ms    ...    100ms          │
│  ────────┼──────┼──────┼──────┼──────┼──────┼──────       ┼──────        │
│          │      │      │      │      │      │             │              │
│  Req 1   [Parse][Start DB call]      .      .      .     [Complete]      │
│          │      │      │      │      │      │             │              │
│  Req 2   .     [Parse][Start DB call].      .      .     [Complete]      │
│          │      │      │      │      │      │             │              │
│  Req 3   .      .     [Parse][Start DB call].      .     [Complete]      │
│          │      │      │      │      │      │             │              │
│                                                                            │
│  The thread is NEVER waiting:                                             │
│  • Starts DB call for Req 1, immediately moves to Req 2                  │
│  • While DB is processing, handles new requests                          │
│  • When DB responds, processes result and sends response                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

This model is:
- **Highly concurrent**: Few threads handle thousands of connections
- **Different mental model**: Operations are callbacks, not blocking calls
- **Requires fully non-blocking code**: One blocking call can stall everything

### The Numbers Tell the Story

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SCALABILITY COMPARISON                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Scenario: 10,000 concurrent connections, each waiting 100ms for I/O      │
│                                                                            │
│  SPRING MVC:                                                               │
│  ─────────────                                                             │
│  Threads needed:     10,000 (one per connection)                          │
│  Memory (1MB/thread): 10 GB just for thread stacks!                       │
│  Result:             Can't scale without huge hardware                    │
│                                                                            │
│  SPRING WEBFLUX:                                                           │
│  ───────────────                                                           │
│  Threads needed:     8 (event loop threads)                               │
│  Memory (threads):   8 MB for thread stacks                               │
│  Memory (total):     ~100-500 MB depending on buffering                   │
│  Result:             Handles 10,000 connections comfortably               │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  The key difference:                                                       │
│  MVC threads WAIT during I/O (wasted resources)                          │
│  WebFlux threads WORK during I/O (processing other requests)             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 8.2 The Event Loop Model

Understanding the event loop is crucial for understanding why WebFlux works the way it does.

### How Netty's Event Loop Works

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE EVENT LOOP EXPLAINED                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  The event loop is like a single very efficient waiter:                   │
│                                                                            │
│  Traditional (Thread-per-request):                                        │
│  ─────────────────────────────────                                         │
│  10 tables, 10 waiters                                                    │
│  Each waiter serves one table, waits for food, brings food               │
│  When food is slow, waiter stands idle at kitchen window                 │
│                                                                            │
│  Event Loop (WebFlux):                                                     │
│  ─────────────────────                                                     │
│  10 tables, 1 very efficient waiter                                       │
│  Takes order from Table 1 → submits to kitchen                           │
│  Takes order from Table 2 → submits to kitchen                           │
│  Takes order from Table 3 → ...                                           │
│  Kitchen bell rings → picks up food for Table 1 → delivers              │
│  Takes order from Table 4 → ...                                           │
│  Kitchen bell rings → picks up food for Table 2 → delivers              │
│                                                                            │
│  The waiter is NEVER waiting. Always moving between tasks.               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Event Loop Cycle

```java
// Simplified view of what the event loop does
while (true) {
    // 1. Wait for events (with timeout)
    List<Event> events = selector.select(timeout);

    // 2. Process each event
    for (Event event : events) {
        if (event.isReadable()) {
            // Data arrived from network, process it
            handleRead(event.channel());
        }
        if (event.isWritable()) {
            // Can write to network, flush buffer
            handleWrite(event.channel());
        }
        if (event.isConnectable()) {
            // New connection ready
            handleConnect(event.channel());
        }
    }

    // 3. Run any scheduled tasks
    runScheduledTasks();

    // Loop continues forever
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    EVENT LOOP INTERNALS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│                         ┌─────────────┐                                    │
│                         │  Selector   │                                    │
│                         │  (epoll)    │                                    │
│                         └──────┬──────┘                                    │
│                                │                                           │
│              ┌─────────────────┼─────────────────┐                        │
│              ▼                 ▼                 ▼                        │
│        ┌──────────┐      ┌──────────┐      ┌──────────┐                   │
│        │ Channel 1│      │ Channel 2│      │ Channel N│                   │
│        │ (Conn A) │      │ (Conn B) │      │ (Conn Z) │                   │
│        └──────────┘      └──────────┘      └──────────┘                   │
│                                                                            │
│  One thread monitors ALL channels simultaneously                          │
│  When data arrives on ANY channel, the thread wakes up                   │
│  Processes the event, then goes back to monitoring                       │
│                                                                            │
│  Linux epoll can monitor 100,000+ file descriptors efficiently           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Golden Rule

**Never block the event loop.**

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE GOLDEN RULE OF WEBFLUX                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ❌ BLOCKING CALL (BAD):                                                   │
│                                                                            │
│  @GetMapping("/users/{id}")                                               │
│  public Mono<User> getUser(@PathVariable Long id) {                       │
│      User user = jdbcTemplate.queryForObject(...);  // BLOCKS!            │
│      return Mono.just(user);                                              │
│  }                                                                         │
│                                                                            │
│  What happens:                                                             │
│  Thread is stuck waiting for JDBC                                         │
│  All other connections handled by this thread are STALLED                │
│  With 4 threads, 4 blocking calls = entire server frozen                 │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  ✅ NON-BLOCKING CALL (GOOD):                                              │
│                                                                            │
│  @GetMapping("/users/{id}")                                               │
│  public Mono<User> getUser(@PathVariable Long id) {                       │
│      return r2dbcRepository.findById(id);  // Non-blocking!              │
│  }                                                                         │
│                                                                            │
│  What happens:                                                             │
│  Thread starts database query, immediately moves to next request         │
│  When DB responds, a callback processes the result                       │
│  Thread was never idle                                                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 8.3 When to Choose WebFlux

WebFlux excels in specific scenarios. Here's when to reach for it:

### Scenario 1: High Concurrency with I/O-Bound Operations

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    HIGH CONCURRENCY SCENARIOS                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Ideal for WebFlux:                                                        │
│  ─────────────────                                                         │
│                                                                            │
│  • Gateway services handling 10,000+ connections                          │
│  • Services that call multiple external APIs per request                 │
│  • Long-polling or Server-Sent Events                                    │
│  • WebSocket applications (chat, real-time updates)                      │
│                                                                            │
│  Example: API Gateway                                                      │
│                                                                            │
│  Request ─► Gateway ─┬─► Service A ──┐                                    │
│                      ├─► Service B ──┼─► Aggregate ─► Response            │
│                      └─► Service C ──┘                                    │
│                                                                            │
│  Gateway handles 10,000 concurrent requests                               │
│  Each request waits for 3 services (~100ms each)                         │
│  MVC needs 10,000 threads; WebFlux needs ~8                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

```java
// WebFlux gateway aggregation example
@GetMapping("/dashboard/{userId}")
public Mono<Dashboard> getDashboard(@PathVariable String userId) {
    // All three calls execute in parallel!
    return Mono.zip(
        userService.getProfile(userId),        // ~100ms
        orderService.getRecentOrders(userId),  // ~150ms
        notificationService.getUnread(userId)  // ~80ms
    ).map(tuple -> new Dashboard(
        tuple.getT1(),  // profile
        tuple.getT2(),  // orders
        tuple.getT3()   // notifications
    ));
    // Total time: ~150ms (slowest), not 330ms (sum)
    // Thread is free during all I/O waits
}
```

### Scenario 2: Streaming Data

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    STREAMING SCENARIOS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  WebFlux is natural for:                                                   │
│  ───────────────────────                                                   │
│                                                                            │
│  • Server-Sent Events (SSE)                                               │
│    - Live dashboards                                                      │
│    - Real-time notifications                                              │
│    - Stock price feeds                                                    │
│                                                                            │
│  • WebSockets                                                              │
│    - Chat applications                                                    │
│    - Collaborative editing                                                │
│    - Gaming                                                               │
│                                                                            │
│  • Streaming HTTP responses                                                │
│    - Large file downloads                                                 │
│    - Database result streaming                                            │
│    - Log file tailing                                                     │
│                                                                            │
│  Example: SSE Price Feed                                                   │
│                                                                            │
│  Server ────[AAPL:150.25]────[GOOGL:140.50]────[AAPL:150.30]──►          │
│                │                   │                 │                    │
│              Client             Client            Client                  │
│              receives           receives          receives               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

```java
// SSE streaming example
@GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<StockPrice> streamPrices() {
    return stockService.getPriceStream()  // Returns Flux<StockPrice>
        .delayElements(Duration.ofMillis(100));  // Throttle if needed
}
```

### Scenario 3: Reactive Ecosystem Integration

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE ECOSYSTEM                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  When your stack is already reactive:                                     │
│  ────────────────────────────────────                                      │
│                                                                            │
│  Database:                                                                 │
│  • R2DBC (PostgreSQL, MySQL, SQL Server, H2)                             │
│  • Reactive MongoDB                                                       │
│  • Reactive Redis (Lettuce)                                               │
│  • Reactive Cassandra                                                     │
│  • Reactive Elasticsearch                                                 │
│                                                                            │
│  Messaging:                                                                │
│  • Reactor Kafka                                                          │
│  • Reactor RabbitMQ                                                       │
│  • Spring Cloud Stream (reactive binders)                                │
│                                                                            │
│  External Services:                                                        │
│  • WebClient for HTTP                                                     │
│  • Reactive gRPC                                                          │
│                                                                            │
│  The more reactive your dependencies, the more benefit from WebFlux      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 4: Microservices with External Dependencies

```java
// Service that aggregates data from multiple sources
@Service
public class OrderService {

    private final WebClient inventoryClient;
    private final WebClient pricingClient;
    private final WebClient shippingClient;
    private final ReactiveRedisTemplate<String, Object> redis;

    public Mono<OrderDetails> getOrderDetails(String orderId) {
        return redis.opsForValue().get("order:" + orderId)
            .cast(Order.class)
            .switchIfEmpty(fetchOrderFromDb(orderId))
            .flatMap(order -> Mono.zip(
                inventoryClient.get()
                    .uri("/stock/{sku}", order.getSku())
                    .retrieve()
                    .bodyToMono(Stock.class),
                pricingClient.get()
                    .uri("/price/{sku}", order.getSku())
                    .retrieve()
                    .bodyToMono(Price.class),
                shippingClient.get()
                    .uri("/estimate/{zip}", order.getZipCode())
                    .retrieve()
                    .bodyToMono(ShippingEstimate.class)
            ).map(tuple -> new OrderDetails(
                order,
                tuple.getT1(),  // stock
                tuple.getT2(),  // price
                tuple.getT3()   // shipping
            )));
    }
}
```

---

## 8.4 When to Stick with Spring MVC

WebFlux is not always the answer. Here's when MVC is the better choice:

### Scenario 1: CPU-Bound Workloads

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    CPU-BOUND WORKLOADS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  WebFlux advantage: Threads aren't wasted waiting for I/O                │
│  CPU-bound work: There IS no waiting—CPU is always busy                  │
│                                                                            │
│  Example: Image Processing Service                                         │
│                                                                            │
│  Request ─► [Resize Image] ─► [Apply Filters] ─► [Compress] ─► Response  │
│                  │                  │                │                    │
│                100ms CPU           50ms CPU         30ms CPU              │
│                                                                            │
│  With MVC: 200 threads = 200 concurrent image processing operations      │
│  With WebFlux: 8 threads = 8 concurrent operations (CPU is the limit)    │
│                                                                            │
│  WebFlux doesn't help because:                                            │
│  • Threads are doing real work, not waiting                              │
│  • Event loop can't process other requests while CPU is busy             │
│  • Overhead of reactive wrappers adds latency                            │
│                                                                            │
│  Recommendation: Use MVC + thread pool sized for CPU cores               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 2: Blocking-Only Dependencies

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING DEPENDENCIES                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  If your critical dependencies only support blocking:                     │
│                                                                            │
│  • JDBC (traditional databases)                                           │
│  • JPA/Hibernate                                                          │
│  • Legacy SOAP services                                                   │
│  • Some third-party SDKs                                                  │
│                                                                            │
│  Options with WebFlux:                                                     │
│                                                                            │
│  Option A: Wrap blocking calls in boundedElastic scheduler               │
│  ────────────────────────────────────────────────────────                  │
│  Mono.fromCallable(() -> jdbcTemplate.query(...))                        │
│      .subscribeOn(Schedulers.boundedElastic());                          │
│                                                                            │
│  Problems:                                                                 │
│  • You're back to thread-per-blocking-call                               │
│  • boundedElastic pool can still exhaust                                 │
│  • Added complexity, uncertain benefit                                   │
│                                                                            │
│  Option B: Just use MVC                                                   │
│  ───────────────────────                                                   │
│  • Simpler code                                                           │
│  • Thread pool designed for blocking                                     │
│  • No false sense of being "reactive"                                    │
│                                                                            │
│  Honest recommendation: If your main database is JDBC-only,             │
│  MVC is probably the better choice.                                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 3: Team Experience and Learning Curve

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    TEAM CONSIDERATIONS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Reactive programming has a significant learning curve:                   │
│                                                                            │
│  Week 1:  "What is Mono? Why doesn't my code work?"                       │
│  Week 2:  "Why is nothing happening?" (forgot to subscribe)               │
│  Week 3:  "Why did this run twice?" (cold publisher confusion)            │
│  Week 4:  "This stack trace is incomprehensible"                          │
│  Week 5:  "flatMap vs map? Still confusing"                               │
│  Week 8:  "Starting to feel natural"                                      │
│  Week 12: "Actually productive"                                           │
│                                                                            │
│  Considerations:                                                           │
│  ───────────────                                                           │
│                                                                            │
│  • Team size: Larger teams = more variance in understanding              │
│  • Deadlines: Tight timeline? Maybe not the time to learn new paradigm   │
│  • Hiring: Fewer developers know reactive than MVC                       │
│  • Debugging: Reactive bugs are harder to diagnose                       │
│  • Testing: Different patterns, different tools                          │
│                                                                            │
│  Not a reason to never learn, but a reason to plan the transition        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Decision Framework

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHEN TO CHOOSE WHAT                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Choose WEBFLUX when:                                                      │
│  ────────────────────                                                      │
│  ☑ High concurrency (1000+ concurrent connections)                        │
│  ☑ Lots of I/O waiting (external APIs, slow databases)                   │
│  ☑ Streaming requirements (SSE, WebSockets)                               │
│  ☑ Already using reactive dependencies (R2DBC, reactive Mongo)           │
│  ☑ Team has reactive experience or time to learn                         │
│                                                                            │
│  Choose SPRING MVC when:                                                   │
│  ───────────────────────                                                   │
│  ☑ Moderate concurrency (< 500 concurrent users)                         │
│  ☑ CPU-bound processing                                                   │
│  ☑ JDBC/JPA dependencies                                                  │
│  ☑ Simple request-response patterns                                       │
│  ☑ Team more comfortable with imperative code                            │
│  ☑ Faster time-to-market needed                                          │
│                                                                            │
│  The hybrid approach:                                                      │
│  ────────────────────                                                      │
│  • Use MVC for most services                                              │
│  • Use WebFlux for the gateway/aggregator                                │
│  • Use WebClient even in MVC apps (it's better than RestTemplate)        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 8.5 The Functional vs. Annotated Model

Spring WebFlux offers two programming models. Both are fully reactive; they differ in style.

### Annotated Controllers

The familiar `@RestController` style you know from Spring MVC:

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }

    @GetMapping
    public Flux<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody Mono<User> user) {
        return user.flatMap(userService::save);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteUser(@PathVariable String id) {
        return userService.deleteById(id);
    }
}
```

**Pros:**
- Familiar to Spring MVC developers
- Annotations are declarative and readable
- Easy to understand at a glance

**Cons:**
- Less flexible for complex routing
- Can't compose routes dynamically

### Functional Endpoints

A more functional approach using `RouterFunction` and `HandlerFunction`:

```java
@Configuration
public class UserRouter {

    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
            .path("/users", builder -> builder
                .GET("/{id}", handler::getUser)
                .GET("", handler::getAllUsers)
                .POST("", handler::createUser)
                .DELETE("/{id}", handler::deleteUser)
            )
            .build();
    }
}

@Component
public class UserHandler {

    private final UserService userService;

    public Mono<ServerResponse> getUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok().bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getAllUsers(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(userService.findAll(), User.class);
    }

    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(User.class)
            .flatMap(userService::save)
            .flatMap(saved -> ServerResponse.created(
                URI.create("/users/" + saved.getId())
            ).bodyValue(saved));
    }

    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.deleteById(id)
            .then(ServerResponse.noContent().build());
    }
}
```

**Pros:**
- Routes are data (can be manipulated programmatically)
- More explicit control over request/response
- Better for complex conditional routing

**Cons:**
- More verbose
- Less familiar to Spring MVC developers
- Slightly steeper learning curve

### Choosing Between Them

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ANNOTATED vs FUNCTIONAL                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Use ANNOTATED when:                                                       │
│  ──────────────────                                                        │
│  • Team knows Spring MVC                                                  │
│  • Routes are straightforward                                             │
│  • You want quick development                                             │
│  • Standard CRUD operations                                               │
│                                                                            │
│  Use FUNCTIONAL when:                                                      │
│  ───────────────────                                                       │
│  • Routes need to be dynamic (loaded from config)                        │
│  • Complex routing logic (custom predicates)                             │
│  • You prefer functional programming style                               │
│  • Building a framework on top of WebFlux                                │
│                                                                            │
│  Can you mix them?                                                         │
│  ─────────────────                                                         │
│  YES! Use annotated controllers for simple endpoints                     │
│  and functional for special cases. They coexist perfectly.               │
│                                                                            │
│  Key insight: Both are EQUALLY reactive under the hood.                  │
│  The choice is about style, not performance.                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 8.6 Non-Blocking All the Way Down

This is perhaps the most important section of the chapter.

### The Problem with Partial Reactivity

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE CHAIN MUST BE COMPLETE                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  A reactive chain is only as non-blocking as its weakest link.            │
│                                                                            │
│  Fully Reactive Chain:                                                     │
│  ─────────────────────                                                     │
│  HTTP Request                                                              │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  WebFlux Controller                                                        │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  Service Layer                                                             │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  R2DBC Repository                                                          │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  Database                                                                  │
│                                                                            │
│  Result: Excellent scalability                                            │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  Broken Chain:                                                             │
│  ─────────────                                                             │
│  HTTP Request                                                              │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  WebFlux Controller                                                        │
│      │                                                                     │
│      ▼ (non-blocking)                                                      │
│  Service Layer                                                             │
│      │                                                                     │
│      ▼ (BLOCKING!) ◄─── ONE blocking call                                 │
│  JDBC Repository                                                           │
│      │                                                                     │
│      ▼                                                                     │
│  Database                                                                  │
│                                                                            │
│  Result: Event loop threads blocked → degraded performance                │
│          Possibly WORSE than plain Spring MVC                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Common Sources of Blocking

```java
// ❌ BLOCKING: JDBC
User user = jdbcTemplate.queryForObject(
    "SELECT * FROM users WHERE id = ?",
    User.class,
    id
);

// ❌ BLOCKING: Thread.sleep
Thread.sleep(1000);  // Never do this!

// ❌ BLOCKING: Synchronous HTTP
String response = restTemplate.getForObject(url, String.class);

// ❌ BLOCKING: File I/O (traditional)
String content = Files.readString(Path.of("file.txt"));

// ❌ BLOCKING: Waiting on a Future
Future<User> future = executor.submit(() -> fetchUser());
User user = future.get();  // Blocks!

// ❌ BLOCKING: Mono.block()
User user = userMono.block();  // Emergency escape hatch ONLY

// ❌ BLOCKING: Synchronized blocks with contention
synchronized (lock) {
    // Waiting for lock blocks the thread
}
```

### Detecting Blocking Calls

Spring provides tools to detect blocking calls in reactive code:

```java
// Enable BlockHound in tests
@BeforeAll
static void setUp() {
    BlockHound.install();
}

// BlockHound will throw an exception if blocking is detected
@Test
void shouldNotBlock() {
    Mono<User> userMono = userService.findById("123");
    StepVerifier.create(userMono)
        .expectNextCount(1)
        .verifyComplete();
    // If findById() blocks anywhere, test fails
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BLOCKHOUND DETECTION                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Add to pom.xml:                                                           │
│                                                                            │
│  <dependency>                                                              │
│      <groupId>io.projectreactor.tools</groupId>                           │
│      <artifactId>blockhound</artifactId>                                  │
│      <version>1.0.8.RELEASE</version>                                     │
│      <scope>test</scope>                                                  │
│  </dependency>                                                             │
│                                                                            │
│  Example output when blocking detected:                                   │
│                                                                            │
│  java.lang.Error: Blocking call! java.lang.Thread.sleep                   │
│      at reactor.blockhound.BlockHound$Builder.lambda$install$8(...)      │
│      at com.example.UserService.findById(UserService.java:42)            │
│                                                                            │
│  Use BlockHound in CI to catch blocking calls before production          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### When Blocking is Unavoidable

Sometimes you must call blocking code. Here's the proper pattern:

```java
@Service
public class LegacyIntegrationService {

    private final LegacyBlockingClient legacyClient;  // No reactive version

    public Mono<LegacyData> fetchData(String id) {
        // Wrap blocking call in a Mono, schedule on boundedElastic
        return Mono.fromCallable(() -> legacyClient.getData(id))
            .subscribeOn(Schedulers.boundedElastic());  // Dedicated thread pool for blocking
    }
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    HANDLING UNAVOIDABLE BLOCKING                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Schedulers.boundedElastic():                                              │
│  ────────────────────────────                                              │
│  • Creates threads as needed (up to a limit)                              │
│  • Threads are reused for 60 seconds                                      │
│  • Designed for blocking I/O                                              │
│  • Doesn't block the event loop threads                                   │
│                                                                            │
│  Pattern:                                                                  │
│                                                                            │
│  Mono.fromCallable(() -> blockingOperation())                             │
│      .subscribeOn(Schedulers.boundedElastic())                            │
│      .flatMap(result -> reactiveProcessing(result));                      │
│                                                                            │
│  This isolates blocking to a separate thread pool                         │
│  Event loop threads stay free for non-blocking work                       │
│                                                                            │
│  Warning: This is a workaround, not a solution                            │
│  If most of your calls are blocking, just use Spring MVC                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 8.7 Summary

In this chapter, we explored the strategic decision of when to use Spring WebFlux:

**Architecture Differences:**
- Spring MVC: Thread-per-request, Servlet API
- Spring WebFlux: Event loop, Reactive Streams
- WebFlux excels when threads would otherwise be idle waiting for I/O

**When to Choose WebFlux:**
- High concurrency (thousands of connections)
- I/O-bound operations (external APIs, slow databases)
- Streaming requirements (SSE, WebSockets)
- Already using reactive dependencies

**When to Stick with MVC:**
- CPU-bound workloads
- JDBC/JPA dependencies
- Simpler request-response patterns
- Team unfamiliar with reactive programming

**Programming Models:**
- Annotated controllers: Familiar, declarative
- Functional endpoints: Flexible, compositional
- Both are equally reactive—choose based on preference

**The Critical Rule:**
- Reactive chains must be non-blocking end-to-end
- One blocking call can undermine the entire architecture
- Use BlockHound to detect blocking calls

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. WebFlux is not "better" than MVC                                       │
│     It's a different tool for different problems.                         │
│                                                                            │
│  2. Event loops work because threads don't wait                           │
│     I/O-bound apps benefit; CPU-bound apps don't.                         │
│                                                                            │
│  3. Partial reactivity is worse than no reactivity                        │
│     If you can't go fully non-blocking, consider MVC.                     │
│                                                                            │
│  4. The programming model is a style choice                               │
│     Annotated and functional are both reactive.                           │
│                                                                            │
│  5. Blocking detection is essential                                        │
│     Use BlockHound in tests to catch blocking calls.                      │
│                                                                            │
│  6. The hybrid approach works                                              │
│     WebClient in MVC apps, or WebFlux for specific services.              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 9, we'll dive into **annotated controllers in WebFlux**. If you're a Spring MVC developer, you'll find most of your knowledge transfers directly—but with some important differences in return types and request handling.

---

## Hands-On Lab 8: WebFlux Fundamentals

Now it's time to experience WebFlux firsthand. In this lab, you'll:

1. Create a WebFlux project from scratch
2. Build endpoints with both programming models
3. Compare behavior with Spring MVC
4. Detect blocking calls with BlockHound

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Spring WebFlux Reference Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Reactor Netty Reference Guide](https://projectreactor.io/docs/netty/release/reference/)
- [BlockHound GitHub Repository](https://github.com/reactor/BlockHound)
- [Understanding Reactive Types (Spring Blog)](https://spring.io/blog/2016/06/07/notes-on-reactive-programming-part-i-the-reactive-landscape)

---

## Discussion Questions

1. Your application has 100 concurrent users and uses PostgreSQL with JPA. Should you use WebFlux? Why or why not?

2. You're building an API gateway that aggregates responses from 5 downstream services. Each service call takes ~100ms. What's the expected response time with MVC vs. WebFlux?

3. A teammate suggests wrapping all JDBC calls in `Schedulers.boundedElastic()` to make the app "reactive." What would you say?

4. How would you evaluate whether your team is ready to adopt WebFlux?

5. Your WebFlux app performs well in testing but degrades in production. What might be happening?
