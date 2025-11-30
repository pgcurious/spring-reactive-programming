# First Principles: Designing Error Handling for Async Streams

*Forget that Reactor's error operators exist. Let's derive what error handling should look like in a reactive system from fundamental requirements.*

---

## The Starting Point

We have asynchronous streams of data. Things go wrong. Networks fail. Services crash. Invalid data arrives. How should errors be handled?

Let's derive the answer from first principles.

---

## Step 1: The Problem with Traditional Error Handling

In synchronous code, errors are handled with try-catch:

```java
try {
    String data = fetchData();      // Blocks, returns, or throws
    String result = process(data);  // Blocks, returns, or throws
    save(result);                   // Blocks, returns, or throws
} catch (Exception e) {
    handleError(e);
}
```

This works because:
1. Each call **blocks** until complete
2. Exceptions propagate up the **call stack**
3. The catch block is on the **same stack**

Now consider async:

```java
void doWork() {
    try {
        asyncFetchData()                  // Returns immediately (Future, Mono, etc.)
            .thenApply(data -> process(data))
            .thenAccept(result -> save(result));
    } catch (Exception e) {
        // USELESS! The actual work hasn't started yet.
        // Errors will occur later, on a different thread.
    }
}
```

```
┌─────────────────────────────────────────────────────────────────┐
│  THE FUNDAMENTAL PROBLEM                                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Synchronous:                                                    │
│  ────────────                                                    │
│                                                                 │
│  Thread A:  [try]───[call]───[call]───[call]───[catch if error] │
│                        │        │        │           │           │
│                        ▼        ▼        ▼           │           │
│                      work     work     work          │           │
│                        │        │        │           │           │
│                      done     done     done          │           │
│                        │        │        │           │           │
│                        ▼        ▼        ▼           ▼           │
│             Same stack, same thread, exceptions propagate up     │
│                                                                 │
│  Asynchronous:                                                   │
│  ─────────────                                                   │
│                                                                 │
│  Thread A:  [try]───[schedule work]───[return]───[catch]───[end]│
│                           │                          │           │
│                           │              (catch scope ended,     │
│                           │               no active exception)  │
│                           ▼                                      │
│  Thread B:             [work]───[ERROR!]                        │
│                                    │                             │
│                                    ▼                             │
│                          Where does this go?                    │
│                          No catch block here!                   │
│                                                                 │
│  The error occurs AFTER the try-catch has ended,                │
│  on a DIFFERENT thread, with a DIFFERENT call stack.            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Conclusion**: Traditional try-catch cannot work for async errors. We need something different.

---

## Step 2: Errors as Data (The Channel Approach)

If exceptions can't propagate through the call stack, they must travel through **the same channel as the data**.

```
Synchronous model:
─────────────────
  Return value ─────► caller (via return statement)
  Exception ────────► caller (via throw, caught by try-catch)
  Two separate paths!

Asynchronous model:
─────────────────────
  Data ─────────────► subscriber (via callback/signal)
  Error ────────────► subscriber (via same callback mechanism)
  Same path!
```

This is our first insight:

**In async systems, errors must be first-class values that travel through the same channel as data.**

---

## Step 3: Designing the Error Channel

Let's design from scratch. We have a data producer and consumer:

```java
interface DataProducer<T> {
    void produceTo(DataConsumer<T> consumer);
}

interface DataConsumer<T> {
    void onData(T data);
    // What about errors?
}
```

### Option A: Return an Either Type

```java
interface DataConsumer<T> {
    void onData(Either<Error, T> data);  // Every item could be error or value
}
```

Problems:
- Every element requires error checking
- Multiple errors could occur
- Doesn't distinguish "error happened" from "error is the data"

### Option B: Separate Error Signal

```java
interface DataConsumer<T> {
    void onData(T data);          // Normal data
    void onError(Throwable error); // Error occurred
}
```

Better! But what about completion?

```java
interface DataConsumer<T> {
    void onData(T data);            // Normal data
    void onError(Throwable error);  // Error occurred
    void onComplete();              // No more data
}
```

This is exactly what Reactive Streams defined (onNext, onError, onComplete).

---

## Step 4: Error Signal Semantics

What should happen when onError is called?

### Option A: Non-terminal (Stream Continues)

```
──[1]──[2]──[error]──[3]──[4]──|
                │
                └── Error happened, but stream continues
```

Problems:
- Consumer must handle interleaved errors
- How many errors are allowed?
- What if the error makes subsequent data invalid?

### Option B: Terminal (Stream Ends)

```
──[1]──[2]──[error]
              │
              └── Stream terminates, no more signals
```

Benefits:
- Clear state: stream is done
- Consumer knows no more data coming
- Matches how most errors work (they're fatal to the operation)

**Decision**: Errors are **terminal**. Once onError fires, the stream is done.

```
┌─────────────────────────────────────────────────────────────────┐
│  THE TERMINAL ERROR PRINCIPLE                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  A stream can end in exactly one of two ways:                   │
│                                                                 │
│  1. onComplete() - Normal completion, no more data              │
│     ──[1]──[2]──[3]──|                                         │
│                      └── "I'm done, everything went well"       │
│                                                                 │
│  2. onError(e) - Abnormal completion, something went wrong      │
│     ──[1]──[2]──X                                              │
│                 └── "I failed, here's why"                      │
│                                                                 │
│  After either signal:                                           │
│  • No more onNext calls                                         │
│  • No more onError calls                                        │
│  • No more onComplete calls                                     │
│  • Subscription should be considered cancelled                  │
│                                                                 │
│  This is a RULE, not a suggestion. Libraries enforce it.       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 5: What About Error Recovery?

If errors are terminal, how do we recover? We can't "un-terminate" a stream.

**Insight**: Recovery happens by **transforming** the stream before termination.

### The Interception Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│  ERROR INTERCEPTION                                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Without recovery:                                               │
│                                                                 │
│  Source ──► [operator] ──► [operator] ──► Subscriber            │
│     │            │              │              │                 │
│     └────────────┴──────────────┴──────────────┘                │
│                   Error propagates straight through              │
│                                                                 │
│  With recovery operator:                                         │
│                                                                 │
│  Source ──► [operator] ──► [recovery] ──► Subscriber            │
│     │            │              │              │                 │
│     │            │       ┌──────┤              │                 │
│     │            │       │      │              │                 │
│     └────────────┴───[X]─┘      │              │                 │
│                         │       │              │                 │
│                    Error caught │              │                 │
│                         │       ▼              │                 │
│                         └──► [fallback] ──────┘                 │
│                                                                 │
│  The recovery operator:                                          │
│  1. Intercepts onError                                          │
│  2. Instead of propagating, substitutes something else          │
│  3. Downstream never sees the error                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 6: Designing Recovery Operators

What recovery behaviors do we need?

### Behavior 1: Substitute a Value

"If there's an error, emit this value instead."

```java
// Design
flux.onError(substitute: T)

// Semantics
──[1]──[2]──[X error]     becomes    ──[1]──[2]──[substitute]──|
```

Useful when: You have a sensible default.

### Behavior 2: Switch to Another Source

"If there's an error, get data from somewhere else."

```java
// Design
flux.onError(switchTo: Publisher<T>)

// Semantics
──[1]──[2]──[X error]     becomes    ──[1]──[2]────────────────
                                                    │
                                              [A]──[B]──[C]──|
                                                    ↑
                                              Fallback source
```

Useful when: You have a backup data source (cache, secondary DB, etc.)

### Behavior 3: Transform the Error

"Change the error type but still fail."

```java
// Design
flux.onError(transform: Exception -> Exception)

// Semantics
──[1]──[2]──[X SQLException]  becomes  ──[1]──[2]──[X ServiceException]
```

Useful when: Wrapping low-level errors in domain exceptions.

---

## Step 7: The Retry Pattern

What if the error is temporary? Network blips, transient failures?

**Insight**: Retry is really "subscribe again."

```
┌─────────────────────────────────────────────────────────────────┐
│  RETRY AS RE-SUBSCRIPTION                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  First attempt:                                                  │
│  Source.subscribe() ──► [1] ──► [2] ──► [X error]              │
│                                              │                   │
│                                              ▼                   │
│                                    Should we retry?             │
│                                              │                   │
│                                          Yes │                   │
│                                              ▼                   │
│  Second attempt:                                                 │
│  Source.subscribe() ──► [1] ──► [2] ──► [3] ──► |              │
│         ↑                                                       │
│         └── Same source, fresh subscription                     │
│                                                                 │
│  Remember: Cold publishers re-execute on each subscription.     │
│  Retry leverages this property!                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Retry Design Questions

1. **How many times?** Need a limit to prevent infinite loops.

2. **Which errors?** Not all errors should be retried. Don't retry `InvalidInputException`.

3. **How fast?** Immediate retry might hammer a failing service.

This leads to:

```java
flux.retry(3)  // Simple: retry 3 times

flux.retryWhen(config -> config
    .maxAttempts(3)
    .filter(e -> e instanceof TransientException)
    .backoff(Duration.ofSeconds(1))
    .jitter(0.5)
)
```

---

## Step 8: The Backoff Pattern

Why is immediate retry bad?

```
┌─────────────────────────────────────────────────────────────────┐
│  THE THUNDERING HERD PROBLEM                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Scenario: Service goes down at t=0, comes back at t=10s        │
│                                                                 │
│  Without backoff (1000 clients):                                │
│                                                                 │
│  t=0:   1000 requests ──► [X fail]                             │
│  t=0.1: 1000 requests ──► [X fail]  (immediate retry)          │
│  t=0.2: 1000 requests ──► [X fail]                             │
│  ...                                                            │
│  t=10:  Service comes back                                      │
│  t=10:  1000 requests ──► [OVERLOAD]  Service can't handle!    │
│                                                                 │
│  With exponential backoff + jitter:                             │
│                                                                 │
│  t=0:   1000 requests ──► [X fail]                             │
│  t=1-2: ~500 retry (randomized delay)                          │
│  t=3-6: ~500 retry (randomized delay)                          │
│  ...                                                            │
│  t=10:  Service comes back                                      │
│  t=10+: Requests trickle in gradually, service handles load    │
│                                                                 │
│  Exponential backoff: Wait 1s, 2s, 4s, 8s...                   │
│  Jitter: Add random variation so clients don't sync up         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Backoff Formula

```
wait_time = min(base * 2^attempt + random_jitter, max_backoff)

Example with base=1s, jitter=0.5, max=30s:
  Attempt 1: 1s * 2^0 + rand(0, 0.5) = ~1.0-1.5s
  Attempt 2: 1s * 2^1 + rand(0, 1.0) = ~2.0-3.0s
  Attempt 3: 1s * 2^2 + rand(0, 2.0) = ~4.0-6.0s
  Attempt 4: 1s * 2^3 + rand(0, 4.0) = ~8.0-12.0s
```

---

## Step 9: The Timeout Requirement

What if the source never emits? Never completes? Never errors?

```
──[waiting...]──[waiting...]──[waiting...]──[forever...]
```

We need a timeout mechanism:

```java
flux.timeout(Duration.ofSeconds(5))

// If no element/completion within 5s, emit TimeoutException
```

```
┌─────────────────────────────────────────────────────────────────┐
│  TIMEOUT DESIGN                                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Option A: Timeout from subscription start                      │
│  ─────────────────────────────────────────────                   │
│  Start timer when subscribed. Fire error if stream not          │
│  complete within duration.                                      │
│                                                                 │
│  Problem: Long-running streams with many elements timeout.      │
│                                                                 │
│  Option B: Timeout between elements (idle timeout)              │
│  ─────────────────────────────────────────────────              │
│  Reset timer on each element. Fire error if no element          │
│  within duration.                                               │
│                                                                 │
│  Better for: Streams where elements keep arriving               │
│                                                                 │
│  Both are useful! Reactor provides both:                        │
│  • timeout(Duration) - from start                               │
│  • timeout(Duration, Publisher) - from start, with fallback    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 10: Observability (Side Effects)

We need to **observe** errors without **handling** them. For logging, metrics, debugging.

```java
// I want to LOG the error but still let it propagate
flux.logOnError(e -> log.error("Failed", e))  // hypothetical
    .onErrorResume(e -> fallback());           // actual handling
```

This is the `doOnError` pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│  OBSERVATION vs HANDLING                                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  doOnError - OBSERVE                                            │
│  ─────────────────────                                           │
│  Error passes through unchanged.                                │
│  Side effect executes (logging, metrics).                       │
│  Downstream still sees the error.                               │
│                                                                 │
│  ──[1]──[2]──[X]                                               │
│              │                                                  │
│         doOnError(e -> log(e))                                 │
│              │                                                  │
│              ▼                                                  │
│  ──[1]──[2]──[X]  (error continues)                            │
│                                                                 │
│  onErrorResume - HANDLE                                         │
│  ──────────────────────                                          │
│  Error is intercepted.                                          │
│  Fallback executes.                                             │
│  Downstream sees fallback, not error.                           │
│                                                                 │
│  ──[1]──[2]──[X]                                               │
│              │                                                  │
│         onErrorResume(e -> fallback)                           │
│              │                                                  │
│              ▼                                                  │
│  ──[1]──[2]──[fallback values]──|  (error absorbed)            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 11: The Complete Error Handling Model

Putting it all together:

```
┌─────────────────────────────────────────────────────────────────┐
│  REACTIVE ERROR HANDLING MODEL                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Errors are signals, not exceptions                          │
│     • Travel through the same channel as data                   │
│     • Delivered via onError callback                            │
│     • Terminal (end the stream)                                 │
│                                                                 │
│  2. Recovery is transformation                                   │
│     • onErrorReturn: substitute a value                         │
│     • onErrorResume: switch to fallback source                  │
│     • onErrorMap: transform the error type                      │
│                                                                 │
│  3. Retry is re-subscription                                     │
│     • Leverages cold publisher behavior                         │
│     • Configurable: count, filter, backoff                      │
│     • Protects downstream from transient failures               │
│                                                                 │
│  4. Timeout is error generation                                  │
│     • Converts "nothing happening" to explicit error            │
│     • Essential for resilient systems                           │
│                                                                 │
│  5. Observation is separate from handling                        │
│     • doOnError: observe without changing                       │
│     • onError*: handle and transform                            │
│                                                                 │
│  6. Composition                                                  │
│     • Chain operators for complex strategies                    │
│     • timeout + retry + fallback = resilient pipeline          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 12: Why This Design Works

```
┌─────────────────────────────────────────────────────────────────┐
│  DESIGN PRINCIPLES MET                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Async-compatible                                             │
│     Errors don't rely on call stack. They travel through        │
│     the same async channel as data.                             │
│                                                                 │
│  2. Composable                                                   │
│     Error operators chain like data operators.                  │
│     Complex error handling from simple pieces.                  │
│                                                                 │
│  3. Explicit                                                     │
│     No hidden error swallowing. Errors must be explicitly       │
│     handled or they propagate to subscriber.                    │
│                                                                 │
│  4. Flexible                                                     │
│     Different strategies for different errors.                  │
│     Recovery, retry, transform, or propagate.                   │
│                                                                 │
│  5. Observable                                                   │
│     Can observe errors without handling them.                   │
│     Critical for logging and metrics.                           │
│                                                                 │
│  6. Consistent                                                   │
│     Same model for all reactive types (Mono, Flux).            │
│     Same operators, same semantics.                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## What We've Derived

Starting from "try-catch doesn't work in async," we've derived:

1. **Error as Signal** - Errors must travel through the data channel
2. **Terminal Errors** - onError ends the stream
3. **Interception Pattern** - Recovery operators intercept before propagation
4. **Value Substitution** - onErrorReturn for default values
5. **Source Switching** - onErrorResume for fallback sources
6. **Error Transformation** - onErrorMap for wrapping
7. **Retry as Re-subscription** - Leveraging cold publisher semantics
8. **Backoff** - Protecting systems from retry storms
9. **Timeout** - Converting silence to explicit errors
10. **Observation vs Handling** - doOnError vs onError*

This IS Reactor's error handling model. The operators exist because the async model requires them.

---

## Why This Understanding Matters

```
┌─────────────────────────────────────────────────────────────────┐
│  UNDERSTANDING THE DESIGN HELPS YOU:                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Stop using try-catch instinctively                         │
│     Recognize that async requires different patterns.          │
│                                                                 │
│  2. Choose the right operator                                  │
│     onErrorReturn vs onErrorResume isn't arbitrary—            │
│     each solves a specific problem.                            │
│                                                                 │
│  3. Design for failure                                         │
│     Understanding terminal errors means planning               │
│     recovery at the right pipeline stage.                      │
│                                                                 │
│  4. Implement retry correctly                                  │
│     Knowing retry is re-subscription explains why              │
│     cold publishers are required.                              │
│                                                                 │
│  5. Debug error flows                                          │
│     Understanding propagation helps trace where                │
│     errors originate and where they're handled.                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## The Path Forward

With this foundation:
- **Chapter 7**: Advanced patterns (context, schedulers, testing)
- **Chapter 8+**: WebFlux, where error handling meets HTTP

The patterns we've derived here apply everywhere in the reactive ecosystem.

---

*"Exception handling is not an afterthought; it's a fundamental part of the design." — Brian Kernighan*
