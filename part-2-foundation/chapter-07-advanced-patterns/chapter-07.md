# Chapter 7: Advanced Reactor Patterns

> "Simplicity is the ultimate sophistication." — Leonardo da Vinci

You now understand the fundamentals: Mono, Flux, operators, and error handling. But real-world reactive applications demand more. How do you limit concurrency? Propagate context across async boundaries? Control which threads execute your code? Test pipelines without waiting for real time to pass?

This chapter covers the advanced patterns that separate toy applications from production systems. By the end, you'll control concurrency, propagate request context, master schedulers, and test reactive code with confidence.

---

## 7.1 Controlling Concurrency with flatMap

The `flatMap` operator is powerful—and dangerous. By default, it subscribes to all inner publishers **concurrently**.

### The Problem with Unlimited Concurrency

```java
// Each ID triggers a database call
Flux<User> users = userIds
    .flatMap(id -> userRepository.findById(id));  // How many concurrent calls?
```

If `userIds` has 1,000 elements, this creates 1,000 concurrent database connections. Your database might crash.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                UNLIMITED FLATMAP CONCURRENCY                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Input:     ──[id1]──[id2]──[id3]──[id4]──[id5]──...──[id1000]──          │
│                │       │       │       │       │           │              │
│                ▼       ▼       ▼       ▼       ▼           ▼              │
│  flatMap:   ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐   ┌─────┐           │
│             │ DB  │ │ DB  │ │ DB  │ │ DB  │ │ DB  │   │ DB  │           │
│             │call1│ │call2│ │call3│ │call4│ │call5│...│1000 │           │
│             └─────┘ └─────┘ └─────┘ └─────┘ └─────┘   └─────┘           │
│                │       │       │       │       │           │              │
│                └───────┴───────┴───────┴───────┴───────────┘              │
│                          ALL RUNNING AT ONCE!                             │
│                                                                            │
│  Problem: 1000 concurrent database connections                            │
│  Result: Connection pool exhausted, timeouts, crashes                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Solution: Concurrency Parameter

```java
Flux<User> users = userIds
    .flatMap(id -> userRepository.findById(id), 10);  // Max 10 concurrent
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                CONTROLLED FLATMAP CONCURRENCY                              │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Input:     ──[id1]──[id2]──..──[id10]──[id11]──[id12]──...              │
│                                                                            │
│  flatMap(concurrency=10):                                                 │
│                                                                            │
│  Slot 1:  ──[call1]──────────────[call11]────────────────                │
│  Slot 2:  ──[call2]──────────────────[call12]────────────                │
│  Slot 3:  ──[call3]──────[call13]────────────────────────                │
│  ...                                                                       │
│  Slot 10: ──[call10]─────────────────────[call20]────────                │
│                                                                            │
│  At most 10 concurrent operations at any time.                           │
│  When one completes, the next starts.                                    │
│                                                                            │
│  Benefits:                                                                │
│  • Predictable resource usage                                            │
│  • No connection pool exhaustion                                         │
│  • Controllable backpressure                                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Choosing the Right Concurrency Level

| Scenario | Recommended Concurrency |
|----------|------------------------|
| Database calls | Pool size (e.g., 10-20) |
| HTTP calls | Based on target service capacity |
| CPU-intensive work | Number of cores |
| External API with rate limit | Below the limit |

### concatMap: Sequential Processing

When you need **no** concurrency (strict ordering):

```java
Flux<Result> results = userIds
    .concatMap(id -> processUser(id));  // One at a time, in order
```

---

## 7.2 Batching and Buffering

Sometimes you need to group elements for batch processing.

### buffer: Collect into Lists

```java
Flux<List<Order>> batches = orderFlux
    .buffer(100);  // Collect 100 orders per batch

// Process in batches for efficient DB inserts
batches.flatMap(batch ->
    orderRepository.saveAll(batch),
    2  // 2 concurrent batch saves
);
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BUFFER OPERATION                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Input:   ──[1]──[2]──[3]──[4]──[5]──[6]──[7]──[8]──[9]──|               │
│                                                                            │
│  buffer(3):                                                                │
│                                                                            │
│  Output:  ──────────[1,2,3]──────────[4,5,6]──────────[7,8,9]──|         │
│                                                                            │
│  buffer with time:                                                         │
│  buffer(Duration.ofSeconds(1)):                                            │
│                                                                            │
│  |-------- 1 second --------||-------- 1 second --------|                 │
│  ──[1]──[2]──[3]─────────────[4]──[5]────────────────────                │
│              │                        │                                    │
│              ▼                        ▼                                    │
│  Output: [1,2,3]                   [4,5]                                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Buffer Variants

```java
// By count
flux.buffer(10);                           // Every 10 elements

// By time
flux.buffer(Duration.ofSeconds(1));        // Every second

// By count OR time (whichever first)
flux.bufferTimeout(100, Duration.ofSeconds(1));

// With overlap (sliding window)
flux.buffer(3, 1);  // [1,2,3], [2,3,4], [3,4,5]...
```

### window: Split into Sub-Fluxes

Like buffer, but emits Flux instead of List—useful for streaming processing:

```java
Flux<Flux<Order>> windows = orderFlux
    .window(100);  // Each window is a Flux of up to 100 orders

windows.flatMap(window ->
    window.collectList()
        .flatMap(batch -> processInParallel(batch)),
    2
);
```

---

## 7.3 Grouping with groupBy

Split a stream based on a key:

```java
Flux<GroupedFlux<String, Order>> grouped = orderFlux
    .groupBy(Order::getStatus);

grouped.flatMap(group -> {
    String status = group.key();  // "PENDING", "SHIPPED", etc.
    return group
        .collectList()
        .doOnNext(orders -> log.info("{}: {} orders", status, orders.size()));
});
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    GROUPBY OPERATION                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Input:  ──[A1]──[B1]──[A2]──[A3]──[B2]──[C1]──[A4]──|                    │
│            │      │      │      │      │      │      │                    │
│            │      │      │      │      │      │      │                    │
│  groupBy(firstChar):                                                       │
│            │      │      │      │      │      │      │                    │
│            ▼      │      ▼      ▼      │      │      ▼                    │
│  Group A: [A1]────────[A2]───[A3]────────────────[A4]──|                  │
│                   │                     │      │                          │
│                   ▼                     ▼      │                          │
│  Group B:       [B1]──────────────────[B2]────────────|                  │
│                                                │                          │
│                                                ▼                          │
│  Group C:                                    [C1]─────────────|          │
│                                                                            │
│  Each GroupedFlux:                                                        │
│  • Has a key() method returning the group key                            │
│  • Is a full Flux you can transform and subscribe to                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Common Gotcha: Consuming GroupedFlux

**GroupedFlux MUST be consumed or cancelled.** Ignoring groups causes memory leaks.

```java
// WRONG - Groups are created but not consumed!
orderFlux.groupBy(Order::getStatus);  // Memory leak!

// CORRECT - Every group is processed
orderFlux.groupBy(Order::getStatus)
    .flatMap(group -> group.collectList())  // Consume each group
    .subscribe(list -> process(list));
```

---

## 7.4 Context Propagation

In traditional Java, `ThreadLocal` stores request-scoped data (user ID, trace ID, etc.). This breaks in reactive code because operations hop between threads.

### The Problem

```java
// Traditional approach - BROKEN in reactive!
public class SecurityContext {
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    public static void setUser(User user) { currentUser.set(user); }
    public static User getUser() { return currentUser.get(); }
}

// In reactive code:
webFilter.setCurrentUser(user);  // Thread A
return service.getData()          // Might run on Thread B - no user!
    .map(data -> {
        User user = SecurityContext.getUser();  // NULL!
        return data;
    });
```

### Reactor Context: The Solution

Reactor provides `Context`—an immutable key-value store that flows through the reactive chain.

```java
// Writing context
Mono<String> dataWithContext = service.getData()
    .contextWrite(Context.of("userId", "user-123", "traceId", "abc-456"));

// Reading context
Mono<String> result = Mono.deferContextual(ctx -> {
    String userId = ctx.get("userId");
    String traceId = ctx.get("traceId");
    log.info("[{}] Processing for user {}", traceId, userId);
    return fetchData(userId);
});
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTOR CONTEXT                                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Context flows UPSTREAM during subscription:                              │
│                                                                            │
│  source ──► map ──► flatMap ──► contextWrite({userId}) ──► subscriber    │
│    │         │         │                  │                   │           │
│    │         │         │                  │                   │           │
│    │         │         │     Context flows backwards          │           │
│    │◄────────┴─────────┴──────────────────┘                   │           │
│    │                                                          │           │
│    │  All operators can now access context via                │           │
│    │  Mono.deferContextual() or transformDeferredContextual() │           │
│                                                                            │
│  Key insight: Context is immutable.                                       │
│  contextWrite creates a NEW context for upstream operators.              │
│                                                                            │
│  Common use cases:                                                        │
│  • Request/correlation IDs for tracing                                   │
│  • User authentication info                                              │
│  • Locale/timezone                                                       │
│  • Feature flags                                                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Context in Practice

```java
@RestController
public class OrderController {

    @GetMapping("/orders/{id}")
    public Mono<Order> getOrder(@PathVariable String id,
                                @RequestHeader("X-Trace-Id") String traceId,
                                @AuthenticationPrincipal User user) {
        return orderService.findById(id)
            .contextWrite(ctx -> ctx
                .put("traceId", traceId)
                .put("userId", user.getId())
            );
    }
}

@Service
public class OrderService {

    public Mono<Order> findById(String id) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", "no-trace");
            log.info("[{}] Finding order {}", traceId, id);
            return orderRepository.findById(id);
        });
    }
}
```

---

## 7.5 Schedulers: Controlling Thread Execution

By default, reactive code runs on the subscribing thread. Schedulers let you control this.

### The Two Key Operators

```java
// subscribeOn: Where the SUBSCRIPTION happens (usually the source)
Flux.range(1, 10)
    .subscribeOn(Schedulers.boundedElastic())  // Subscription on elastic pool
    .map(i -> i * 2)  // Also runs on elastic pool
    .subscribe();

// publishOn: Where DOWNSTREAM operators run
Flux.range(1, 10)
    .publishOn(Schedulers.parallel())  // Downstream on parallel pool
    .map(i -> i * 2)  // Runs on parallel pool
    .subscribe();
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    subscribeOn vs publishOn                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  subscribeOn: Affects WHERE subscription/source runs                      │
│  ────────────────────────────────────────────────                          │
│                                                                            │
│  main thread: subscribe() ─┐                                              │
│                            │ subscribeOn(elastic)                         │
│  elastic pool:             └──► source ──► map ──► filter ──► result     │
│                                                                            │
│  No matter where in chain, affects subscription point.                    │
│                                                                            │
│  publishOn: Affects WHERE downstream operators run                        │
│  ────────────────────────────────────────────                              │
│                                                                            │
│  main thread:   source ──► map ──┐                                        │
│                                  │ publishOn(parallel)                    │
│  parallel pool:                  └──► filter ──► result                  │
│                                                                            │
│  Position in chain matters - affects everything after it.                │
│                                                                            │
│  Combined:                                                                │
│  ───────────                                                               │
│                                                                            │
│  flux                                                                      │
│    .subscribeOn(elastic)        // Source on elastic                     │
│    .map(...)                    // On elastic                            │
│    .publishOn(parallel)         // Switch to parallel                    │
│    .filter(...)                 // On parallel                           │
│    .publishOn(single)           // Switch to single                      │
│    .subscribe()                 // Final on single                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Available Schedulers

| Scheduler | Description | Use Case |
|-----------|-------------|----------|
| `immediate()` | Current thread | Testing, synchronous execution |
| `single()` | Single reusable thread | Sequential background work |
| `parallel()` | Fixed pool (CPU cores) | CPU-bound work |
| `boundedElastic()` | Bounded pool (grows) | Blocking I/O |
| `fromExecutor(...)` | Custom executor | Special requirements |

### Common Patterns

```java
// Blocking operation on elastic scheduler
Mono.fromCallable(() -> blockingDatabaseCall())
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe();

// CPU-intensive on parallel, then publish results
Flux.range(1, 1000)
    .publishOn(Schedulers.parallel())
    .map(i -> cpuIntensiveOperation(i))
    .publishOn(Schedulers.single())
    .subscribe(result -> updateUI(result));
```

---

## 7.6 Testing Reactive Code with StepVerifier

Testing reactive code requires special tools. `StepVerifier` lets you assert on elements, errors, and completion.

### Basic Testing

```java
@Test
void testFluxEmission() {
    Flux<Integer> flux = Flux.just(1, 2, 3);

    StepVerifier.create(flux)
        .expectNext(1)
        .expectNext(2)
        .expectNext(3)
        .verifyComplete();
}

@Test
void testMonoError() {
    Mono<String> mono = Mono.error(new RuntimeException("Boom!"));

    StepVerifier.create(mono)
        .expectErrorMessage("Boom!")
        .verify();
}
```

### Testing with Matchers

```java
@Test
void testWithMatchers() {
    Flux<User> users = userService.findAll();

    StepVerifier.create(users)
        .expectNextMatches(user -> user.getName().startsWith("A"))
        .expectNextCount(5)
        .expectNextMatches(user -> user.isActive())
        .verifyComplete();
}
```

### Virtual Time: Testing Delays Without Waiting

```java
@Test
void testDelayWithVirtualTime() {
    // This would take 10 hours without virtual time!
    StepVerifier.withVirtualTime(() ->
        Flux.interval(Duration.ofHours(1))
            .take(10)
    )
    .thenAwait(Duration.ofHours(10))  // Simulated, instant!
    .expectNextCount(10)
    .verifyComplete();
}

@Test
void testTimeout() {
    StepVerifier.withVirtualTime(() ->
        Mono.delay(Duration.ofSeconds(5))
            .timeout(Duration.ofSeconds(3))
    )
    .thenAwait(Duration.ofSeconds(3))
    .expectError(TimeoutException.class)
    .verify();
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    VIRTUAL TIME TESTING                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Real time:                                                                │
│  ──────────                                                                │
│  Flux.interval(1 hour).take(10)                                           │
│  Test would take 10 ACTUAL hours to run!                                  │
│                                                                            │
│  Virtual time:                                                             │
│  ─────────────                                                             │
│  StepVerifier.withVirtualTime(() -> Flux.interval(1 hour).take(10))      │
│  .thenAwait(Duration.ofHours(10))  // Simulated instantly                │
│  .expectNextCount(10)                                                     │
│  .verifyComplete();                                                       │
│  Test runs in milliseconds!                                               │
│                                                                            │
│  How it works:                                                            │
│  • StepVerifier installs a VirtualTimeScheduler                          │
│  • thenAwait() advances the virtual clock                                │
│  • Delayed operations fire immediately when their time comes             │
│                                                                            │
│  Caveats:                                                                 │
│  • Source must be created inside withVirtualTime() lambda                │
│  • All time-based operations must use the default scheduler              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 7.7 Debugging Reactive Code

Reactive stack traces are notoriously difficult to read. Here's how to debug effectively.

### The Problem: Lost Stack Traces

```java
// Where did this error originate?
Flux.range(1, 10)
    .map(i -> i * 2)
    .flatMap(this::process)
    .filter(x -> x > 0)
    .map(this::transform)  // Error here!
    .subscribe();

// Stack trace shows: transform() threw NPE
// But WHY was the input null? The trace doesn't show the reactive chain!
```

### Solution 1: checkpoint()

Add checkpoints to identify pipeline stages:

```java
Flux.range(1, 10)
    .checkpoint("after range")
    .map(i -> i * 2)
    .checkpoint("after doubling")
    .flatMap(this::process)
    .checkpoint("after process")
    .filter(x -> x > 0)
    .checkpoint("after filter")
    .map(this::transform)
    .checkpoint("after transform")
    .subscribe();
```

Error output now shows: `Assembly trace from checkpoint [after process]`

### Solution 2: Hooks.onOperatorDebug()

Enable full assembly tracing (expensive, dev only):

```java
// At application startup
Hooks.onOperatorDebug();

// Now all reactive chains capture assembly info
// Stack traces show full chain
```

### Solution 3: log() Operator

```java
Flux.range(1, 5)
    .log("before-map")
    .map(i -> i * 2)
    .log("after-map")
    .subscribe();

// Output:
// [ INFO] before-map: onSubscribe(...)
// [ INFO] before-map: request(unbounded)
// [ INFO] before-map: onNext(1)
// [ INFO] after-map: onNext(2)
// ...
```

### Debugging Tips

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    DEBUGGING TIPS                                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Use checkpoint() at critical points                                   │
│     Add descriptive names for easy identification.                        │
│                                                                            │
│  2. Enable Hooks.onOperatorDebug() in development                        │
│     NEVER in production - significant overhead.                          │
│                                                                            │
│  3. Use log() liberally during development                               │
│     Shows all signals: subscribe, request, next, error, complete.        │
│                                                                            │
│  4. Use doOnNext/doOnError for targeted debugging                        │
│     Print specific values at specific points.                            │
│                                                                            │
│  5. Test components in isolation                                          │
│     StepVerifier makes this easy.                                        │
│                                                                            │
│  6. Think about data flow                                                 │
│     Trace where null/invalid values could originate.                     │
│                                                                            │
│  7. Check subscription                                                    │
│     "Nothing happens" often means missing subscribe().                   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 7.8 Summary

In this chapter, we covered the advanced patterns that enable production-ready reactive applications:

**Controlling Concurrency:**
- `flatMap` concurrency parameter limits parallel operations
- `concatMap` for sequential processing
- Choose concurrency based on target resource capacity

**Batching:**
- `buffer` collects into Lists
- `window` creates sub-Fluxes
- Essential for bulk operations

**Grouping:**
- `groupBy` splits by key
- GroupedFlux must be consumed

**Context:**
- Replaces ThreadLocal for reactive code
- `contextWrite` adds, `deferContextual` reads
- Flows upstream during subscription

**Schedulers:**
- `subscribeOn` for subscription/source
- `publishOn` for downstream operations
- Choose scheduler based on work type

**Testing:**
- StepVerifier for assertions
- Virtual time for delay testing
- Test without waiting

**Debugging:**
- checkpoint() for pipeline identification
- Hooks.onOperatorDebug() for full traces
- log() for signal visibility

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Control flatMap Concurrency                                           │
│     Unlimited concurrency is dangerous. Always consider resource limits. │
│                                                                            │
│  2. Batch for Efficiency                                                  │
│     Individual operations are expensive. Buffer and batch.               │
│                                                                            │
│  3. Context Replaces ThreadLocal                                          │
│     Thread-hopping breaks ThreadLocal. Use Reactor Context.              │
│                                                                            │
│  4. subscribeOn vs publishOn                                              │
│     subscribeOn: where subscription happens (position doesn't matter)    │
│     publishOn: where downstream runs (position matters)                  │
│                                                                            │
│  5. Test with StepVerifier                                                │
│     Don't test by subscribing and waiting. Use StepVerifier.            │
│                                                                            │
│  6. Virtual Time is Essential                                             │
│     Testing delays without virtual time is impractical.                  │
│                                                                            │
│  7. Debug Proactively                                                     │
│     Add checkpoints before bugs happen, not after.                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

Part II is complete! You now have a solid foundation in Project Reactor. In Part III, we'll apply these concepts to **Spring WebFlux**—building reactive web applications, handling HTTP requests reactively, and integrating with the Spring ecosystem.

---

## Hands-On Lab 7: Advanced Patterns in Practice

Now it's time to apply these advanced patterns. In this lab, you'll:

1. Control concurrency in a batch processor
2. Use buffer for efficient bulk operations
3. Propagate context through a request pipeline
4. Master schedulers for thread control
5. Write comprehensive tests with StepVerifier
6. Debug a broken reactive pipeline

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Reactor Context Documentation](https://projectreactor.io/docs/core/release/reference/#context) - Official guide to Context
- [StepVerifier Guide](https://projectreactor.io/docs/core/release/reference/#testing) - Testing reference
- [Debugging Reactor](https://projectreactor.io/docs/core/release/reference/#debugging) - Debugging techniques

---

## Discussion Questions

1. Why is unlimited flatMap concurrency dangerous? What resources could be exhausted?

2. How does Reactor Context differ from ThreadLocal? Why was a new mechanism needed?

3. When would you use `subscribeOn` vs `publishOn`? Give examples of each.

4. Why is virtual time testing important? What would happen without it?

5. A colleague adds `Hooks.onOperatorDebug()` to production code for debugging. Why is this problematic?
