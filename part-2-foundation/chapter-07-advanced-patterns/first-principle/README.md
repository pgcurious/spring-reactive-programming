# First Principles: Deriving Advanced Reactive Patterns

*Forget that Reactor's advanced features exist. Let's derive what a production-ready reactive system needs from fundamental requirements.*

---

## The Starting Point

We have a reactive system with Mono and Flux. We understand operators and error handling. But as we build real applications, we face new challenges:

1. How do we prevent resource exhaustion from too much parallelism?
2. How do we pass request context through async operations?
3. How do we control which threads run our code?
4. How do we test async code without waiting?
5. How do we debug when stack traces are useless?

Let's derive solutions from first principles.

---

## Problem 1: Concurrency Control

### The Scenario

```java
// Process 10,000 user IDs
Flux<Result> results = userIds
    .flatMap(id -> externalService.call(id));
```

What happens here? Let's trace through:

```
┌─────────────────────────────────────────────────────────────────┐
│  UNBOUNDED CONCURRENCY PROBLEM                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  userIds:    [1, 2, 3, 4, ... 10000]                           │
│                │  │  │  │      │                                │
│  flatMap:     │  │  │  │      │                                │
│               ▼  ▼  ▼  ▼      ▼                                │
│             [call][call][call][call]...[call]                  │
│               │    │    │    │        │                        │
│               └────┴────┴────┴────────┘                        │
│                 ALL 10,000 AT ONCE!                            │
│                                                                 │
│  Impact:                                                        │
│  • 10,000 concurrent HTTP connections                          │
│  • External service overwhelmed (rate limited or crashes)      │
│  • Connection pool exhausted                                   │
│  • Memory for 10,000 in-flight requests                        │
│  • Timeouts cascade                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### First Insight: We Need Bounded Concurrency

We need to limit how many inner publishers are subscribed simultaneously.

```
Design requirement:
─────────────────────
flatMap should accept a concurrency limit.
"Process at most N items concurrently."
```

### Implementation Approach

```
┌─────────────────────────────────────────────────────────────────┐
│  BOUNDED CONCURRENCY DESIGN                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  flatMap with concurrency = 3:                                  │
│                                                                 │
│  Queue:      [4, 5, 6, 7, 8, 9, 10, ...]                       │
│                │                                                │
│  Active:    [1] [2] [3]   (max 3 concurrent)                   │
│              │   │   │                                          │
│              ▼   ▼   ▼                                          │
│            call call call                                       │
│              │   │   │                                          │
│              ▼   │   │                                          │
│           done  │   │                                           │
│              │   │   │                                          │
│  Active:    [4] [2] [3]   (slot freed, next item starts)       │
│              │   │   │                                          │
│              │   ▼   │                                          │
│              │ done  │                                          │
│                                                                 │
│  Invariant: Active.size() <= concurrency limit                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why This Works

1. **Predictable resource usage** - Never more than N connections/threads
2. **Backpressure propagation** - Upstream slows down naturally
3. **Graceful degradation** - System doesn't collapse under load

This is exactly what `flatMap(mapper, concurrency)` provides.

---

## Problem 2: Batching for Efficiency

### The Scenario

```java
// Insert 10,000 records
Flux<Record> records = ...;
records.flatMap(record -> repository.insert(record), 10);
```

Even with concurrency control, this is 10,000 database round trips!

### First Insight: Batch Operations Are More Efficient

```
Single inserts:
────────────────
insert(r1) → [network RTT] → done
insert(r2) → [network RTT] → done
...
insert(r10000) → [network RTT] → done

Total: 10,000 × RTT

Batch inserts:
───────────────
insertAll([r1...r100]) → [network RTT] → done
insertAll([r101...r200]) → [network RTT] → done
...
Total: 100 × RTT (100x faster!)
```

### Design: The Buffer Operator

We need an operator that collects elements into groups:

```
┌─────────────────────────────────────────────────────────────────┐
│  BUFFER DESIGN                                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Input:   ──[1]──[2]──[3]──[4]──[5]──[6]──[7]──[8]──[9]──|     │
│                                                                 │
│  buffer(3):                                                     │
│                                                                 │
│  Internal buffer: [1]                                           │
│  Internal buffer: [1, 2]                                        │
│  Internal buffer: [1, 2, 3] → FULL, emit!                      │
│                                                                 │
│  Output:  ────────────[1,2,3]────────────[4,5,6]──[7,8,9]──|   │
│                                                                 │
│  Variations needed:                                             │
│  • buffer(count): Emit every N elements                        │
│  • buffer(duration): Emit every T time                         │
│  • buffer(count, duration): Emit on count OR time              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Time-Based Buffering?

```
Scenario: Stream with variable rate

High load:   [1][2][3][4][5][6]...[100] in 1 second
Low load:    [1].........[2]..... in 1 minute

With count-only buffer(100):
  High load: Emits every 1 second ✓
  Low load:  Takes 50 minutes to emit! ✗

With time-based buffer(100, 10s):
  High load: Emits every ~1 second (hits count first)
  Low load:  Emits every 10 seconds (hits time first)
```

This is exactly what `bufferTimeout(count, duration)` provides.

---

## Problem 3: Context Propagation

### The Scenario

```java
// Traditional approach
public void processRequest(HttpRequest request) {
    String userId = request.getHeader("X-User-Id");
    ThreadLocal.set("userId", userId);

    User user = userService.getUser();        // Reads ThreadLocal
    Order order = orderService.getOrder();     // Reads ThreadLocal

    ThreadLocal.remove("userId");
}
```

This breaks in reactive:

```
┌─────────────────────────────────────────────────────────────────┐
│  THREADLOCAL FAILURE IN REACTIVE                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Thread A (Netty event loop):                                   │
│  ─────────────────────────────                                   │
│  1. Request arrives                                              │
│  2. ThreadLocal.set("userId", "user-123")                       │
│  3. userService.getUser() → returns Mono, subscribes           │
│  4. ThreadLocal.get("userId") → "user-123" ✓                   │
│  5. Async operation starts, Thread A returns to pool           │
│                                                                 │
│  Thread B (different thread from pool):                         │
│  ──────────────────────────────────────                          │
│  6. Async operation completes                                   │
│  7. ThreadLocal.get("userId") → NULL! ✗                        │
│                                                                 │
│  Why? Thread B never had the ThreadLocal set.                  │
│  Thread-hopping breaks ThreadLocal.                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### First Insight: Context Must Travel With Data

Since execution hops between threads, context can't be stored in threads. It must travel **with the reactive chain itself**.

### Design: Immutable Context Attached to Subscription

```
┌─────────────────────────────────────────────────────────────────┐
│  REACTOR CONTEXT DESIGN                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Requirements:                                                   │
│  1. Context travels with the subscription                       │
│  2. Any operator can read context                               │
│  3. Context can be modified (added to)                          │
│  4. Thread-safe (immutable)                                     │
│                                                                 │
│  Solution: Immutable map attached to Subscription               │
│                                                                 │
│  subscribe() with context:                                      │
│                                                                 │
│  source ──► map ──► flatMap ──► contextWrite({userId}) ──► sub │
│                                        │                        │
│                                        │                        │
│  During subscription (flows UPSTREAM):                          │
│                                        │                        │
│  source ◄── map ◄── flatMap ◄────────┘                        │
│    │                                                            │
│    │ "Here's a context with userId for you to use"             │
│    │                                                            │
│    ▼                                                            │
│  Context available to ALL upstream operators                   │
│                                                                 │
│  Why upstream flow?                                             │
│  • Subscription propagates upstream                            │
│  • Context is part of subscription                             │
│  • Natural to flow with subscription                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Immutable?

```
Mutable context problems:
─────────────────────────
Thread A: context.put("key", "value1")
Thread B: context.put("key", "value2")  // Race condition!
Thread A: context.get("key")  // What value?

Immutable context solution:
───────────────────────────
Thread A: newContext = context.put("key", "value1")  // New instance
Thread B: newContext = context.put("key", "value2")  // New instance
No race conditions - each has its own view.
```

This is exactly what Reactor's `Context` provides.

---

## Problem 4: Thread Control

### The Scenario

```java
// Where does this code run?
Flux.range(1, 100)
    .map(i -> i * 2)           // Which thread?
    .flatMap(this::dbCall)     // Which thread?
    .filter(x -> x > 0)        // Which thread?
    .subscribe(System.out::println);
```

### First Insight: Default Is Caller's Thread

By default, everything runs on the thread that called `subscribe()`.

```
┌─────────────────────────────────────────────────────────────────┐
│  DEFAULT THREADING                                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  main thread:                                                    │
│  ─────────────                                                   │
│  flux.subscribe()                                                │
│       │                                                         │
│       ▼                                                         │
│  subscribe propagates up, then data flows down                  │
│  ALL on main thread (unless source is async)                   │
│                                                                 │
│  Problem: Blocking the main thread!                             │
│                                                                 │
│  With Flux.interval() or async source:                         │
│  ────────────────────────────────────────                        │
│  flux = Flux.interval(1 second)                                 │
│       │                                                         │
│       ▼                                                         │
│  Scheduler: parallel (default for interval)                    │
│  Data flows on parallel scheduler thread                       │
│                                                                 │
│  Each source has its own default scheduler                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Design Need: Explicit Thread Control

We need two controls:
1. Where does subscription/source work happen?
2. Where does downstream processing happen?

### Design: subscribeOn vs publishOn

```
┌─────────────────────────────────────────────────────────────────┐
│  SCHEDULER OPERATORS DESIGN                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  subscribeOn: WHERE SUBSCRIPTION HAPPENS                        │
│  ─────────────────────────────────────────                       │
│                                                                 │
│  flux.subscribeOn(elastic)                                      │
│       │                                                         │
│       │ subscribe() called on main thread                      │
│       │ but actual subscription work runs on elastic           │
│       ▼                                                         │
│  elastic: source ──► map ──► filter ──► [result]              │
│                                                                 │
│  Position in chain DOESN'T MATTER for subscribeOn              │
│  (only one subscription, always at the beginning)              │
│                                                                 │
│  publishOn: WHERE DOWNSTREAM RUNS                               │
│  ─────────────────────────────────                               │
│                                                                 │
│  flux.map(...)                                                  │
│      .publishOn(parallel)  ← Switch point                      │
│      .filter(...)                                               │
│      .map(...)                                                  │
│       │                                                         │
│       │                                                         │
│       ▼                                                         │
│  caller: map                                                    │
│  parallel: filter, map, subscriber                             │
│                                                                 │
│  Position MATTERS - affects everything after it                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Two Operators?

Different use cases:

```
subscribeOn use case:
─────────────────────
"My source is blocking (file read, blocking DB call).
 I want subscription/reading to happen on a different thread."

Mono.fromCallable(() -> blockingFileRead())
    .subscribeOn(Schedulers.boundedElastic())

publishOn use case:
────────────────────
"My processing is CPU-intensive.
 I want to move it to a parallel scheduler."

flux.map(this::parse)
    .publishOn(Schedulers.parallel())
    .map(this::heavyComputation)
```

---

## Problem 5: Testing Without Waiting

### The Scenario

```java
@Test
void testDelayedEmission() {
    Flux<Long> flux = Flux.interval(Duration.ofHours(1))
        .take(10);

    // How do we test this?
    // Actually waiting 10 hours is not acceptable!
}
```

### First Insight: Time Can Be Virtualized

Schedulers control when delayed events fire. If we control the scheduler, we control time.

```
┌─────────────────────────────────────────────────────────────────┐
│  VIRTUAL TIME DESIGN                                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Real scheduler:                                                 │
│  ─────────────────                                               │
│  schedule(task, 1 hour) → Wait 1 hour → Run task               │
│                                                                 │
│  Virtual scheduler:                                              │
│  ──────────────────                                              │
│  schedule(task, 1 hour) → Record: "task at t+1h"               │
│  advanceTimeBy(1 hour) → Find tasks at t+1h → Run immediately  │
│                                                                 │
│  No actual waiting! Time advances instantly.                   │
│                                                                 │
│  Implementation:                                                 │
│  • VirtualTimeScheduler tracks pending tasks                   │
│  • Tasks sorted by scheduled time                              │
│  • advanceTime runs all tasks up to new time                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Design: StepVerifier with Virtual Time

```java
StepVerifier.withVirtualTime(() -> {
    // This lambda is important!
    // Source must be created INSIDE so it uses virtual scheduler
    return Flux.interval(Duration.ofHours(1)).take(10);
})
.thenAwait(Duration.ofHours(10))  // Instant!
.expectNextCount(10)
.verifyComplete();
```

Why the lambda? The source must be created *after* virtual time is installed.

---

## Problem 6: Debugging Async Chains

### The Scenario

```java
flux.map(this::step1)
    .flatMap(this::step2)
    .map(this::step3)     // NPE here!
    .subscribe();

// Stack trace shows: NPE in step3
// But WHY was the input null? Trace is useless!
```

### The Problem: Lost Context

```
┌─────────────────────────────────────────────────────────────────┐
│  REACTIVE STACK TRACE PROBLEM                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Traditional synchronous:                                        │
│  ─────────────────────────                                       │
│  step1() calls step2() calls step3()                           │
│  Stack trace shows: step1 → step2 → step3                     │
│  Easy to trace!                                                 │
│                                                                 │
│  Reactive async:                                                 │
│  ─────────────────                                               │
│  step1 runs, returns Mono, completes                           │
│  (time passes, different thread)                               │
│  step2 runs, returns Mono, completes                           │
│  (time passes, different thread)                               │
│  step3 runs → NPE!                                             │
│                                                                 │
│  Stack trace shows: NPE in step3                               │
│  Doesn't show: How we got here, what pipeline                 │
│                                                                 │
│  Why? Each step runs in its own context.                       │
│  No call stack connects them.                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Solution 1: Checkpoint Operator

Manually mark pipeline stages:

```java
flux.map(this::step1)
    .checkpoint("after step1")
    .flatMap(this::step2)
    .checkpoint("after step2")
    .map(this::step3)
    .checkpoint("after step3")
    .subscribe();

// Error now shows: "Assembly trace from checkpoint [after step2]"
// Tells you the error came from the pipeline, after step2
```

### Solution 2: Assembly-Time Capture

Capture the assembly stack when operators are created:

```java
Hooks.onOperatorDebug();  // Enable globally

// Now every operator records where it was created
// Stack traces show the full assembly chain
```

Why optional? Capturing stacks for every operator is expensive.

```
┌─────────────────────────────────────────────────────────────────┐
│  ASSEMBLY-TIME CAPTURE                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Normal mode:                                                    │
│  ─────────────                                                   │
│  flux.map(...) → Creates MapOperator, no extra info            │
│  Cost: minimal                                                  │
│                                                                 │
│  Debug mode:                                                     │
│  ─────────────                                                   │
│  flux.map(...) → Creates MapOperator                           │
│                → Captures stack trace (expensive!)             │
│                → Stores "assembled at line 42 of Foo.java"    │
│  Cost: High! Stack capture is slow.                            │
│                                                                 │
│  Trade-off:                                                     │
│  • Development: Enable debug mode, accept slowdown             │
│  • Production: Disable, use checkpoints strategically          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Summary: What We've Derived

Starting from production requirements, we derived:

### 1. Concurrency Control
- **Problem**: Unbounded parallelism exhausts resources
- **Solution**: Concurrency limit parameter on flatMap
- **Implementation**: Internal queue with N active slots

### 2. Batching
- **Problem**: Individual operations are inefficient
- **Solution**: Buffer operator to collect into batches
- **Variations**: By count, by time, or both

### 3. Context Propagation
- **Problem**: ThreadLocal breaks with thread-hopping
- **Solution**: Immutable context attached to subscription
- **Flow**: Travels upstream during subscription

### 4. Thread Control
- **Problem**: Need control over execution threads
- **Solution**: subscribeOn (source) and publishOn (downstream)
- **Insight**: Position matters for publishOn

### 5. Virtual Time Testing
- **Problem**: Can't wait hours in tests
- **Solution**: Virtual scheduler advances time instantly
- **Key**: Source must be created inside virtual time scope

### 6. Debugging
- **Problem**: Stack traces don't show reactive chain
- **Solution**: checkpoint() and assembly-time capture
- **Trade-off**: Performance vs debuggability

---

## The Design Philosophy

```
┌─────────────────────────────────────────────────────────────────┐
│  REACTOR'S ADVANCED FEATURES PHILOSOPHY                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Explicit over implicit                                      │
│     Concurrency limits, schedulers, context - all explicit.    │
│     No magic. You decide.                                       │
│                                                                 │
│  2. Composition                                                  │
│     Complex behavior from simple operators.                     │
│     buffer() + flatMap(batch, 2) = efficient batch processing. │
│                                                                 │
│  3. Safe defaults, powerful overrides                           │
│     Default concurrency is safe (256 for flatMap).             │
│     Override when you know better.                             │
│                                                                 │
│  4. Development vs production modes                             │
│     Debug features (onOperatorDebug) for development.          │
│     Lightweight checkpoints for production.                     │
│                                                                 │
│  5. Testability                                                  │
│     Virtual time makes reactive testing practical.             │
│     StepVerifier for declarative assertions.                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Why This Understanding Matters

```
┌─────────────────────────────────────────────────────────────────┐
│  UNDERSTANDING THE DESIGN HELPS YOU:                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Choose appropriate concurrency                             │
│     Know that flatMap(fn, N) limits active subscriptions.     │
│     Size N based on downstream resource capacity.              │
│                                                                 │
│  2. Design for efficiency                                      │
│     Batch where possible. Understand the trade-offs.           │
│                                                                 │
│  3. Use context correctly                                      │
│     Know context flows upstream. Write at the end,             │
│     read at the beginning.                                     │
│                                                                 │
│  4. Debug effectively                                          │
│     Add checkpoints proactively. Enable debug in dev.          │
│                                                                 │
│  5. Write robust tests                                         │
│     Use virtual time. Don't test timing with Thread.sleep.    │
│                                                                 │
│  6. Reason about threads                                       │
│     Know subscribeOn vs publishOn. Predict execution.         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## The Path Forward

With these patterns:
- **Part III**: Spring WebFlux - reactive web applications
- **Part IV**: Data & Integration - R2DBC, reactive messaging
- **Part V**: Production - monitoring, deployment, tuning

The foundation is complete. You understand not just *what* Reactor provides, but *why*.

---

*"Make it work, make it right, make it fast." — Kent Beck*

*You now know how to make it work AND right. Part III will show you how to make it real.*
