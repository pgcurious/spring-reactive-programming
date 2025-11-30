# Chapter 6: Error Handling and Resilience

> "The best way to predict the future is to implement it." — David Heinemeier Hansson

Real applications fail. Networks timeout. Services crash. Databases become unavailable. The difference between a fragile system and a resilient one isn't the absence of failures—it's how gracefully failures are handled.

In reactive programming, error handling works fundamentally differently than traditional try-catch blocks. Errors are **first-class citizens**, signals that flow through your pipeline just like data. By the end of this chapter, you'll understand why try-catch doesn't work in async contexts and master Reactor's powerful error handling operators.

---

## 6.1 Errors as First-Class Citizens

In imperative Java, we handle errors with try-catch:

```java
try {
    User user = userService.findById(id);
    sendEmail(user.getEmail());
} catch (UserNotFoundException e) {
    log.error("User not found", e);
    sendDefaultEmail();
}
```

This doesn't work in reactive code. Here's why:

```java
// This try-catch NEVER catches the error!
try {
    Mono<User> userMono = userService.findById(id);  // Returns immediately
    userMono.subscribe(user -> sendEmail(user.getEmail()));
} catch (Exception e) {
    // NEVER REACHED - the error happens later, on a different thread
    log.error("This never prints", e);
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                WHY TRY-CATCH FAILS IN REACTIVE                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  IMPERATIVE (Synchronous):                                                 │
│  ─────────────────────────                                                 │
│  Thread A: try { operation() } catch { handle() }                         │
│            │                                                               │
│            ▼                                                               │
│            [operation runs]                                                │
│            │                                                               │
│            ▼ (error!)                                                      │
│            [catch block executes] ← Same thread, same stack               │
│                                                                            │
│  REACTIVE (Asynchronous):                                                  │
│  ────────────────────────                                                  │
│  Thread A: mono.subscribe()     Thread B: [operation runs]                │
│            │                              │                                │
│            ▼                              ▼ (error!)                       │
│            [returns immediately]          [error has no catch block!]     │
│            │                              │                                │
│            ▼                              ▼                                │
│            try-catch scope ended          Where does the error go?        │
│                                                                            │
│  The error occurs AFTER the try-catch scope has ended,                    │
│  on a DIFFERENT thread with a DIFFERENT call stack.                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Reactive Solution: Error as Signal

In reactive streams, every pipeline has three possible signals:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE THREE SIGNALS                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. onNext(T)    - Data arrived, here's an element                        │
│  2. onError(E)   - Something went wrong, here's the exception             │
│  3. onComplete() - Stream finished successfully, no more data             │
│                                                                            │
│  Timeline examples:                                                        │
│                                                                            │
│  Success: ──[1]──[2]──[3]──|                                              │
│                            └── onComplete                                  │
│                                                                            │
│  Error:   ──[1]──[2]──X                                                   │
│                       └── onError (stream terminates)                      │
│                                                                            │
│  Key insight: onError and onComplete are TERMINAL.                        │
│  After either one, no more signals flow.                                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6.2 The Error Channel

Every subscriber can provide an error handler:

```java
flux.subscribe(
    element -> processElement(element),      // onNext
    error -> handleError(error),              // onError
    () -> onComplete()                        // onComplete
);
```

### Error Propagation

When an error occurs anywhere in the pipeline, it propagates downstream:

```java
Flux.range(1, 5)
    .map(i -> {
        if (i == 3) throw new RuntimeException("Boom at 3!");
        return i * 10;
    })
    .filter(i -> i > 10)
    .subscribe(
        i -> System.out.println("Received: " + i),
        e -> System.out.println("Error: " + e.getMessage()),
        () -> System.out.println("Complete")
    );

// Output:
// Received: 20
// Error: Boom at 3!
// (No more elements, no completion - error is terminal)
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ERROR PROPAGATION                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Flux.range(1,5) ──► map() ──► filter() ──► Subscriber                    │
│       │                 │                                                  │
│       │ onNext(1)       │                                                  │
│       ├─────────────────┼──►[10]──►[passes]──► onNext(10)? No, <10       │
│       │                 │                                                  │
│       │ onNext(2)       │                                                  │
│       ├─────────────────┼──►[20]──►[passes]──► onNext(20) ✓              │
│       │                 │                                                  │
│       │ onNext(3)       │                                                  │
│       ├─────────────────┼──► THROW! ──────────► onError(e) ✗             │
│       │                 │                                                  │
│       │ (cancelled)     │    Stream terminated                            │
│       │                 │    No more processing                           │
│       │                 │                                                  │
│       └─────────────────┘                                                  │
│                                                                            │
│  Once onError fires, the subscription is cancelled upstream.              │
│  Elements 4 and 5 are never processed.                                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6.3 Recovering from Errors

Reactor provides several operators for error recovery. Each has its use case.

### onErrorReturn: Provide a Fallback Value

When an error occurs, emit a default value and complete:

```java
Mono<User> getUserOrDefault(String id) {
    return userRepository.findById(id)
        .onErrorReturn(new User("anonymous", "guest@example.com"));
}

// With predicate - only catch specific errors
Mono<User> getUserOrDefault(String id) {
    return userRepository.findById(id)
        .onErrorReturn(
            e -> e instanceof NotFoundException,  // Only for NotFound
            new User("anonymous", "guest@example.com")
        );
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    onErrorReturn                                           │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Before: ──[1]──[2]──X (error)                                            │
│                                                                            │
│  After:  ──[1]──[2]──[default]──|                                         │
│                      └── Fallback value, then complete                    │
│                                                                            │
│  Use when: You have a sensible default value                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### onErrorResume: Switch to a Fallback Publisher

When an error occurs, switch to an alternative source:

```java
Mono<User> getUserWithFallback(String id) {
    return primaryDatabase.findById(id)
        .onErrorResume(e -> {
            log.warn("Primary DB failed, trying backup", e);
            return backupDatabase.findById(id);
        });
}

// Common pattern: Cache fallback to database
Mono<Config> getConfig(String key) {
    return cache.get(key)
        .onErrorResume(e -> database.findConfig(key));
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    onErrorResume                                           │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Primary:  ──[1]──[2]──X (error)                                          │
│                        │                                                   │
│                        └──► Switch to fallback                            │
│                                                                            │
│  Fallback:                  ──[A]──[B]──[C]──|                            │
│                                                                            │
│  Result:   ──[1]──[2]────────[A]──[B]──[C]──|                             │
│                                                                            │
│  Use when: You have an alternative data source                            │
│  Use when: Different error types need different handling                   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### onErrorMap: Transform the Error

Change the error type without recovering:

```java
Mono<User> getUser(String id) {
    return repository.findById(id)
        .onErrorMap(SQLException.class,
            e -> new ServiceException("Database error", e));
}
```

### The Decision Tree

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHICH ERROR OPERATOR?                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  On error, do you want to:                                                │
│                                                                            │
│  ┌──────────────────────────┐                                              │
│  │ Recover with a value?    │───Yes──► onErrorReturn(value)               │
│  └──────────────────────────┘                                              │
│            │ No                                                            │
│            ▼                                                               │
│  ┌──────────────────────────┐                                              │
│  │ Switch to another source?│───Yes──► onErrorResume(e -> altSource)      │
│  └──────────────────────────┘                                              │
│            │ No                                                            │
│            ▼                                                               │
│  ┌──────────────────────────┐                                              │
│  │ Change the error type?   │───Yes──► onErrorMap(e -> newError)          │
│  └──────────────────────────┘                                              │
│            │ No                                                            │
│            ▼                                                               │
│  ┌──────────────────────────┐                                              │
│  │ Just log and propagate?  │───Yes──► doOnError(e -> log.error(...))     │
│  └──────────────────────────┘                                              │
│            │ No                                                            │
│            ▼                                                               │
│  Let the error propagate unchanged to subscriber's onError handler        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6.4 Retry Strategies

Sometimes the best recovery is trying again. Network glitches, temporary unavailability, and race conditions often resolve themselves.

### Simple Retry

```java
Mono<Response> callExternalService() {
    return webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(Response.class)
        .retry(3);  // Retry up to 3 times on ANY error
}
```

### Retry with Backoff

Hammering a failing service is bad. Use exponential backoff:

```java
Mono<Response> callExternalServiceWithBackoff() {
    return webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(Response.class)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(10))
            .jitter(0.5)  // Add randomness to prevent thundering herd
            .filter(e -> e instanceof WebClientResponseException.ServiceUnavailable)
            .onRetryExhaustedThrow((spec, signal) ->
                new ServiceException("Service unavailable after retries", signal.failure()))
        );
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    EXPONENTIAL BACKOFF                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Attempt 1: ──[call]──X (fail)                                            │
│                       │                                                    │
│                       └── Wait ~1 second                                   │
│                                                                            │
│  Attempt 2:                 ──[call]──X (fail)                            │
│                                       │                                    │
│                                       └── Wait ~2 seconds                  │
│                                                                            │
│  Attempt 3:                                   ──[call]──X (fail)          │
│                                                         │                  │
│                                                         └── Wait ~4 sec    │
│                                                                            │
│  Attempt 4:                                                 ──[call]──✓   │
│                                                                            │
│  With jitter: Actual wait times vary randomly to prevent                  │
│  multiple clients retrying at the exact same moment.                      │
│                                                                            │
│  Timeline: ───[X]─1s─[X]─2s─[X]──4s──[✓]                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Retry Budgets

Don't retry forever. Set limits:

```java
.retryWhen(Retry.backoff(3, Duration.ofMillis(100))
    .maxBackoff(Duration.ofSeconds(2))       // Cap the wait time
    .filter(this::isRetryable)               // Only retry certain errors
    .transientErrors(true)                   // Reset count on success
)
```

---

## 6.5 Timeouts

In reactive systems, operations without timeouts are bugs waiting to happen.

### Setting Timeouts

```java
Mono<User> getUserWithTimeout(String id) {
    return userService.findById(id)
        .timeout(Duration.ofSeconds(5));  // Throws TimeoutException
}

// With fallback
Mono<User> getUserWithTimeoutAndFallback(String id) {
    return userService.findById(id)
        .timeout(Duration.ofSeconds(5), Mono.just(User.anonymous()));
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    TIMEOUT BEHAVIOR                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Without timeout:                                                          │
│  ──[waiting...]──[waiting...]──[waiting...]──[waiting...]──►               │
│  (Could wait forever if service is unresponsive)                          │
│                                                                            │
│  With timeout(5s):                                                         │
│  ──[waiting...]──[waiting...]──X TimeoutException                         │
│                               │                                            │
│                               └── After 5 seconds                          │
│                                                                            │
│  With timeout(5s, fallback):                                              │
│  ──[waiting...]──[waiting...]──[fallback value]──|                        │
│                               │                                            │
│                               └── Switch to fallback on timeout            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Combining Timeout with Retry

```java
Mono<Response> resilientCall() {
    return webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(Response.class)
        .timeout(Duration.ofSeconds(3))          // Each attempt has 3s
        .retryWhen(Retry.backoff(2, Duration.ofMillis(500)))  // Retry twice
        .onErrorResume(e -> Mono.just(Response.fallback())); // Final fallback
}
```

---

## 6.6 doOn* Operators for Debugging

The `doOn*` family lets you observe signals without affecting them—perfect for logging and metrics.

```java
Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .doOnSubscribe(s -> log.debug("Looking up user {}", id))
        .doOnNext(user -> log.debug("Found user: {}", user.getName()))
        .doOnError(e -> log.error("Failed to find user {}", id, e))
        .doOnSuccess(user -> metrics.recordUserLookup(user != null))
        .doOnTerminate(() -> log.debug("User lookup completed"))
        .doFinally(signal -> cleanup());
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    doOn* OPERATORS                                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  doOnSubscribe  - When subscription occurs                                 │
│  doOnNext       - For each element                                         │
│  doOnError      - When an error signal arrives                            │
│  doOnComplete   - When onComplete signal arrives                          │
│  doOnSuccess    - (Mono) When element or empty completes                  │
│  doOnTerminate  - When stream ends (either complete or error)             │
│  doFinally      - Always runs, receives signal type                       │
│  doOnCancel     - When subscription is cancelled                          │
│                                                                            │
│  Key insight: These are SIDE EFFECTS.                                     │
│  They don't change the stream, just observe it.                           │
│  Perfect for logging, metrics, debugging.                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6.7 Building Resilient Pipelines

Let's combine everything into a production-ready pattern:

```java
public Mono<OrderResult> processOrder(Order order) {
    return validateOrder(order)
        .flatMap(this::checkInventory)
        .flatMap(this::reserveItems)
        .flatMap(this::processPayment)
        .flatMap(this::createShipment)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
            .filter(e -> e instanceof TransientException))
        .onErrorResume(PaymentException.class, e -> {
            log.error("Payment failed for order {}", order.getId(), e);
            return compensate(order).then(Mono.error(e));
        })
        .onErrorResume(e -> {
            log.error("Order processing failed", e);
            return Mono.just(OrderResult.failed(e.getMessage()));
        })
        .doOnSuccess(result -> metrics.recordOrder(result))
        .doFinally(signal -> log.info("Order {} finished: {}", order.getId(), signal));
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    RESILIENCE PATTERN                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  validate ──► inventory ──► reserve ──► payment ──► shipment              │
│     │             │           │           │           │                    │
│     └─────────────┴───────────┴───────────┴───────────┘                    │
│                               │                                            │
│                               ▼                                            │
│                          [timeout 30s]                                     │
│                               │                                            │
│                               ▼                                            │
│                    [retry 2x with backoff]                                │
│                       (transient errors)                                  │
│                               │                                            │
│                               ▼                                            │
│               ┌───────────────┴───────────────┐                           │
│               │                               │                            │
│         PaymentError?                   Other errors?                      │
│               │                               │                            │
│               ▼                               ▼                            │
│     [compensate + rethrow]          [return failed result]                │
│                                                                            │
│  The pipeline:                                                            │
│  1. Handles specific errors (payment) differently                         │
│  2. Has overall timeout protection                                        │
│  3. Retries transient failures                                           │
│  4. Falls back gracefully for unrecoverable errors                       │
│  5. Logs and records metrics throughout                                   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6.8 Summary

In this chapter, we learned that error handling in reactive programming is fundamentally different:

**Errors as Signals:**
- onError is a first-class signal like onNext
- Try-catch doesn't work in async contexts
- Errors terminate the stream

**Recovery Operators:**
- `onErrorReturn`: Substitute a default value
- `onErrorResume`: Switch to alternative source
- `onErrorMap`: Transform the error type

**Retry Strategies:**
- Simple retry with count limit
- Exponential backoff with jitter
- Filter to retry only specific errors

**Timeouts:**
- Always set timeouts on external calls
- Combine with fallback for graceful degradation

**Observability:**
- Use doOn* for logging and metrics
- Side effects don't change the stream

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Errors are Signals                                                    │
│     Don't use try-catch for reactive errors. Subscribe with an           │
│     error handler or use error operators.                                 │
│                                                                            │
│  2. Recover at the Right Level                                            │
│     Handle errors as close to the source as makes sense.                 │
│     Don't let errors bubble up if you can recover.                       │
│                                                                            │
│  3. Always Set Timeouts                                                   │
│     An operation without a timeout is a potential system hang.           │
│     Default to failing fast.                                             │
│                                                                            │
│  4. Retry Intelligently                                                   │
│     Use backoff, jitter, and filtering. Don't hammer failing services.   │
│                                                                            │
│  5. Log with doOnError, Handle with onError*                             │
│     Observation (logging) is separate from handling (recovery).          │
│                                                                            │
│  6. Plan for Failure                                                      │
│     Design your pipelines assuming things will fail.                     │
│     What's the fallback? What gets logged? What about compensation?      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 7, we'll explore **advanced Reactor patterns**—controlling concurrency, batching operations, propagating context, managing schedulers, and testing reactive code. These patterns separate hobbyist reactive code from production-ready systems.

---

## Hands-On Lab 6: Building Resilient Services

Now it's time to put resilience into practice. In this lab, you'll:

1. Handle errors from failing services
2. Implement retry with exponential backoff
3. Add timeouts and fallbacks
4. Build a multi-step pipeline with proper error handling

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Reactor Reference: Handling Errors](https://projectreactor.io/docs/core/release/reference/#error.handling) - Official documentation
- [Resilience4j](https://resilience4j.readme.io/) - Circuit breakers and more
- [Release It!](https://pragprog.com/titles/mnee2/release-it-second-edition/) - Patterns for production resilience

---

## Discussion Questions

1. Why doesn't try-catch work for reactive error handling? What fundamental difference causes this?

2. When would you choose `onErrorReturn` vs `onErrorResume`?

3. A service is having intermittent failures. You add `retry(10)`. Why might this make things worse?

4. How would you implement a circuit breaker pattern using Reactor's operators?

5. You have a pipeline: A → B → C. An error in B should trigger compensation in A, then fail. How would you structure this?
