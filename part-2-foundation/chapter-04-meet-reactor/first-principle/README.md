# First Principles: Designing a Reactive Library from Scratch

*Forget that Project Reactor exists. Let's derive what a reactive library should look like from fundamental requirements.*

---

## The Starting Point

We've already derived the Reactive Streams specification (four interfaces: Publisher, Subscriber, Subscription, Processor). Now we face a practical problem:

**Implementing these interfaces correctly is extremely difficult.**

Let's discover what a library should provide by trying to build one ourselves.

---

## Step 1: The Pain of Raw Implementation

Imagine you need to create a publisher that emits integers 1 to N:

```java
public class RangePublisher implements Publisher<Integer> {
    private final int start;
    private final int count;

    public RangePublisher(int start, int count) {
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        // Now what? We need to:
        // 1. Create a Subscription
        // 2. Handle request(n) correctly
        // 3. Track current position
        // 4. Handle cancellation
        // 5. Be thread-safe
        // 6. Never emit more than requested
        // 7. Handle reentrant request() calls
        // ...this is getting complicated
    }
}
```

Every time you want a new data source, you rewrite all this logic. That's not sustainable.

---

## Step 2: The Factory Pattern Emerges

**Key insight**: Instead of implementing Publisher directly, provide factory methods.

```
What if we had:

Publisher<Integer> range = SomeFactory.range(1, 10);
Publisher<String> just = SomeFactory.just("Hello");
Publisher<Long> interval = SomeFactory.interval(1, TimeUnit.SECONDS);
```

The factory handles the complex implementation. You just describe WHAT you want.

---

## Step 3: What Should the Factory Produce?

We need a type that:
1. Implements Publisher (for compatibility)
2. Provides fluent methods for transformation
3. Is easy to work with

Let's call it... `Flux` (for multiple elements):

```
┌─────────────────────────────────────────────────────────────────┐
│  Flux<T> extends Publisher<T>                                   │
│                                                                 │
│  Factory methods:                                               │
│  • Flux.just(T... values)                                      │
│  • Flux.range(int start, int count)                            │
│  • Flux.fromIterable(Iterable<T>)                              │
│  • Flux.interval(Duration period)                              │
│  • Flux.empty()                                                │
│  • Flux.error(Throwable)                                       │
│                                                                 │
│  Transformation methods (we'll derive these in Chapter 5):     │
│  • .map(Function<T, R>)                                        │
│  • .filter(Predicate<T>)                                       │
│  • .flatMap(Function<T, Publisher<R>>)                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 4: The 0-or-1 Element Case

Many operations return a single value:
- `findById(id)` → One user or none
- `save(entity)` → The saved entity
- `count()` → A single number

Using `Flux<T>` for these is semantically wrong. We need a specialized type.

Let's call it... `Mono` (for single/mono):

```
┌─────────────────────────────────────────────────────────────────┐
│  Mono<T> extends Publisher<T>                                   │
│                                                                 │
│  Cardinality: 0 or 1 element                                   │
│                                                                 │
│  Factory methods:                                               │
│  • Mono.just(T value)                                          │
│  • Mono.empty()                                                │
│  • Mono.error(Throwable)                                       │
│  • Mono.fromSupplier(Supplier<T>)                              │
│  • Mono.fromFuture(CompletableFuture<T>)                       │
│                                                                 │
│  Semantic clarity:                                              │
│  • Mono<User> findById(id)   - clearly 0 or 1                  │
│  • Flux<User> findAll()      - clearly 0 to N                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Two Types?

```
Design decision: Two types (Mono, Flux) vs One type (Observable/Flowable)

Option A: Single type for everything
─────────────────────────────────────
  Observable<User> findById(id);    // Is this 0-1 or 0-N?
  Observable<User> findAll();       // Same type, different semantics

  Problems:
  • Unclear cardinality
  • Must read documentation to understand
  • Some operators don't make sense (e.g., reduce on a single)

Option B: Two specialized types
─────────────────────────────────────
  Mono<User> findById(id);          // Clearly 0 or 1
  Flux<User> findAll();             // Clearly 0 to N

  Benefits:
  • Type signature tells the story
  • Compile-time clarity
  • Operators tailored to cardinality
  • Mono.block() is safer (only one element)

Reactor chose Option B. RxJava has multiple types (Single, Maybe, etc.)
```

---

## Step 5: The Laziness Requirement

Now a critical design decision: When should computation happen?

```
Option A: Eager (immediate execution)
─────────────────────────────────────
Flux<Integer> flux = Flux.range(1, 1000000)
    .map(expensiveComputation);
// All 1 million computations happen NOW

Problems:
• Can't compose without executing
• Can't cancel mid-computation
• Memory usage spikes immediately
• What if no one needs the result?

Option B: Lazy (deferred execution)
─────────────────────────────────────
Flux<Integer> flux = Flux.range(1, 1000000)
    .map(expensiveComputation);
// Nothing happens yet - just a blueprint

flux.subscribe(consumer);
// NOW computation happens

Benefits:
• Compose freely without side effects
• Cancel anytime
• Compute only what's needed
• Backpressure works naturally
```

**Decision**: Reactive types should be **lazy**. Building a pipeline is just creating a blueprint. Subscription triggers execution.

---

## Step 6: The Cold/Hot Distinction

Consider this scenario:

```java
Mono<User> userMono = httpClient.get("/users/123").bodyToMono(User.class);

userMono.subscribe(user -> display(user));
userMono.subscribe(user -> log(user));
```

Question: How many HTTP calls are made?

```
Option A: One call, shared result (Hot)
─────────────────────────────────────────
• First subscribe triggers the call
• Second subscribe gets the same result
• Efficient for expensive operations
• But: What if we WANT fresh data each time?

Option B: One call per subscriber (Cold)
─────────────────────────────────────────
• Each subscribe triggers a new call
• Fresh data for each subscriber
• But: Wasteful if we don't need fresh data
```

**Decision**: Default to **cold** (one execution per subscriber). Provide explicit operators to make hot when needed.

```java
// Cold (default) - two HTTP calls
Mono<User> cold = httpClient.get(...);
cold.subscribe(...);  // HTTP call #1
cold.subscribe(...);  // HTTP call #2

// Hot (explicit) - one HTTP call, shared
Mono<User> hot = httpClient.get(...).cache();
hot.subscribe(...);   // HTTP call #1
hot.subscribe(...);   // Reuses result from #1
```

---

## Step 7: The Subscription Trigger

We've established laziness. How should subscription work?

```java
// What happens when you call subscribe?
Flux.range(1, 10)
    .map(i -> i * 2)
    .filter(i -> i > 10)
    .subscribe(System.out::println);
```

```
┌─────────────────────────────────────────────────────────────────┐
│  THE SUBSCRIPTION FLOW                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Assembly Phase (building the pipeline):                        │
│  ────────────────────────────────────────                       │
│                                                                 │
│  Flux<Integer> step1 = Flux.range(1, 10);                      │
│         │                                                       │
│         ▼                                                       │
│  Flux<Integer> step2 = step1.map(i -> i * 2);                  │
│         │                                                       │
│         ▼                                                       │
│  Flux<Integer> step3 = step2.filter(i -> i > 10);              │
│         │                                                       │
│         ▼                                                       │
│  [Pipeline complete, no execution yet]                          │
│                                                                 │
│  Subscription Phase (triggering execution):                     │
│  ──────────────────────────────────────────                     │
│                                                                 │
│  step3.subscribe(consumer);                                     │
│         │                                                       │
│         │  subscribe propagates UPSTREAM                        │
│         ▼                                                       │
│  step2.subscribe(filterSubscriber)                             │
│         │                                                       │
│         ▼                                                       │
│  step1.subscribe(mapSubscriber)                                │
│         │                                                       │
│         ▼                                                       │
│  [Source begins emitting]                                       │
│                                                                 │
│  Data flows DOWNSTREAM:                                         │
│  ──────────────────────────                                     │
│                                                                 │
│  Source: onNext(1) ─► Map: onNext(2) ─► Filter: [dropped]      │
│  Source: onNext(2) ─► Map: onNext(4) ─► Filter: [dropped]      │
│  ...                                                            │
│  Source: onNext(6) ─► Map: onNext(12) ─► Filter: ─► Consumer   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 8: Operator Implementation Strategy

How should operators like `map` and `filter` be implemented?

```
Naive approach: Each operator creates a full Publisher implementation
─────────────────────────────────────────────────────────────────────
map() → MapPublisher (implements all Publisher rules)
filter() → FilterPublisher (implements all Publisher rules)
flatMap() → FlatMapPublisher (implements all Publisher rules)

Problem: Massive code duplication, hard to maintain

Better approach: Operators wrap and delegate
─────────────────────────────────────────────────────────────────────
Each operator wraps the upstream Publisher and adds its behavior.

flux.map(fn) returns a new Flux that:
  • When subscribed, subscribes to upstream
  • When it receives onNext(x), calls consumer.onNext(fn(x))
  • Delegates onError and onComplete unchanged

This is the DECORATOR pattern applied to Publishers.
```

```
┌────────────────────────────────────────────────────────────────┐
│  OPERATOR AS DECORATOR                                         │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Original:  [Source] ───────────────────────► [Consumer]      │
│                                                                │
│  With map:  [Source] ──► [MapOperator] ─────► [Consumer]      │
│                              │                                 │
│                              │ Transforms each element        │
│                              │ using the provided function    │
│                                                                │
│  Chained:   [Source] ──► [Map] ──► [Filter] ──► [Consumer]   │
│                                                                │
│  Each operator is a wrapper that:                             │
│  • Subscribes to upstream when subscribed to                  │
│  • Transforms/filters/combines elements                       │
│  • Passes results downstream                                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## Step 9: Handling Backpressure Transparently

The library should handle backpressure so users don't have to think about it:

```java
// User writes this:
Flux.range(1, 1000000)
    .map(i -> i * 2)
    .subscribe(System.out::println);

// Behind the scenes:
// • Subscriber requests initial batch (e.g., 256)
// • Source emits 256 elements
// • Map processes and forwards them
// • As consumer processes, it requests more
// • Cycle continues until complete

// User never sees request(n) calls!
```

**Design decision**: Provide a default request strategy (usually prefetch + replenish), but allow customization when needed.

---

## Step 10: Error Handling Strategy

Errors in async code are tricky. The library needs a consistent strategy:

```
Traditional try-catch doesn't work:
────────────────────────────────────
try {
    flux.subscribe(item -> process(item));  // Non-blocking
} catch (Exception e) {
    // This NEVER catches errors from the stream!
    // The error happens later, on a different thread
}

Solution: Error is a first-class signal:
────────────────────────────────────────
flux.subscribe(
    item -> process(item),
    error -> handleError(error),    // Errors arrive here
    () -> onComplete()
);

// Or use operators:
flux.onErrorResume(error -> fallbackFlux)
    .onErrorReturn(defaultValue)
    .doOnError(error -> log(error))
    .subscribe(...);
```

---

## Step 11: Thread Management

Who controls which thread runs the code?

```
By default: The subscribing thread
──────────────────────────────────
Flux.range(1, 10)
    .map(i -> i * 2)
    .subscribe(System.out::println);

// All of this runs on the current thread
// (unless the source is inherently async, like interval)

To change threads: Explicit operators
──────────────────────────────────────
Flux.range(1, 10)
    .publishOn(Schedulers.parallel())    // Downstream runs on parallel pool
    .map(i -> i * 2)
    .subscribeOn(Schedulers.boundedElastic())  // Subscription runs on elastic
    .subscribe(System.out::println);
```

**Design decision**: No implicit threading. User explicitly controls thread pools with `subscribeOn` and `publishOn`.

---

## Step 12: The Final Architecture

Putting it all together:

```
┌─────────────────────────────────────────────────────────────────┐
│                    REACTOR ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Layer 1: Reactive Streams Interfaces                          │
│  ─────────────────────────────────────                          │
│  Publisher, Subscriber, Subscription, Processor                │
│  (The contract, standardized)                                   │
│                                                                 │
│  Layer 2: Core Types                                           │
│  ─────────────────────                                          │
│  Mono<T> - 0 or 1 element                                      │
│  Flux<T> - 0 to N elements                                     │
│  (User-facing API)                                              │
│                                                                 │
│  Layer 3: Factory Methods                                      │
│  ─────────────────────────                                      │
│  Mono.just(), Flux.range(), Flux.create(), etc.               │
│  (Easy creation without implementing Publisher)                │
│                                                                 │
│  Layer 4: Operators                                            │
│  ────────────────────                                           │
│  map, filter, flatMap, zip, merge, concat, etc.               │
│  (Transformations as decorators)                               │
│                                                                 │
│  Layer 5: Schedulers                                           │
│  ─────────────────────                                          │
│  subscribeOn, publishOn, Schedulers.*                          │
│  (Thread pool management)                                       │
│                                                                 │
│  Layer 6: Error Handling                                       │
│  ─────────────────────────                                      │
│  onErrorReturn, onErrorResume, retry, etc.                    │
│  (Resilience patterns)                                         │
│                                                                 │
│  Layer 7: Testing Utilities                                    │
│  ───────────────────────────                                    │
│  StepVerifier, TestPublisher, virtual time                     │
│  (Verification tools)                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## What We've Derived

Starting from "implementing Reactive Streams is hard," we've derived:

1. **Factory Pattern** - Don't implement Publisher; use factory methods
2. **Two Types** - Mono for 0-1, Flux for 0-N (semantic clarity)
3. **Laziness** - Build blueprints, execute on subscribe
4. **Cold Default** - Each subscriber gets fresh execution
5. **Operator Decoration** - Wrap and delegate for transformations
6. **Transparent Backpressure** - Library handles request logic
7. **Error as Signal** - First-class error channel
8. **Explicit Threading** - User controls schedulers

This IS Project Reactor. The design decisions are logical consequences of the requirements.

---

## Why This Understanding Matters

```
┌─────────────────────────────────────────────────────────────────┐
│  UNDERSTANDING THE DESIGN HELPS YOU:                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Debug effectively                                          │
│     Know that subscribe triggers execution, so trace from      │
│     there. Know that cold means fresh execution.               │
│                                                                 │
│  2. Choose correctly                                           │
│     Mono vs Flux is semantic, not just about count.            │
│     Cold vs hot matters for performance and correctness.       │
│                                                                 │
│  3. Extend confidently                                         │
│     Create custom operators knowing they're decorators.        │
│     Use create/generate for custom sources.                    │
│                                                                 │
│  4. Reason about behavior                                      │
│     Laziness explains why nothing happens without subscribe.   │
│     Decoration explains why operators return new Flux.         │
│                                                                 │
│  5. Appreciate the library                                     │
│     All this complexity is handled FOR you.                    │
│     You just compose operations declaratively.                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## The Path Forward

With this foundation:
- **Chapter 5**: Operators and thinking in streams
- **Chapter 6**: Error handling and resilience patterns
- **Chapter 7**: Advanced patterns (context, schedulers, testing)

We'll build on these principles, using Reactor's implementations of the concepts we've derived.

---

*"The purpose of abstraction is not to be vague, but to create a new semantic level in which one can be absolutely precise." — Edsger W. Dijkstra*
