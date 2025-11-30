# Lab 7: Advanced Reactor Patterns in Practice

## Objective

In this lab, you'll master the advanced patterns that enable production-ready reactive applications. You'll:

1. Control concurrency with flatMap to prevent resource exhaustion
2. Batch operations with buffer for efficient bulk processing
3. Propagate context through reactive pipelines
4. Master schedulers for thread control
5. Write comprehensive tests with StepVerifier
6. Test time-sensitive code with virtual time
7. Debug reactive pipelines effectively

By the end, you'll have the skills to build and test production-ready reactive systems.

---

## Prerequisites

- Java 17+ installed
- Maven or Gradle installed
- Completed Labs 4, 5, and 6
- Understanding of Chapter 7 concepts

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | Concurrency Control | 20 min |
| 2 | Batching and Buffering | 20 min |
| 3 | Context Propagation | 20 min |
| 4 | Schedulers | 20 min |
| 5 | Testing with StepVerifier | 25 min |
| 6 | Virtual Time Testing | 20 min |
| 7 | Debugging Techniques | 15 min |

**Total: ~140 minutes**

---

## Part 1: Concurrency Control (20 min)

### Exercise 1.1: Understanding Unbounded Concurrency

Create `src/main/java/com/example/lab/ConcurrencyControl.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyControl {

    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    private static final AtomicInteger maxConnections = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Concurrency Control ===\n");

        demonstrateUnboundedConcurrency();
        resetCounters();
        demonstrateBoundedConcurrency();
        resetCounters();
        demonstrateConcatMap();
    }

    private static void demonstrateUnboundedConcurrency() throws InterruptedException {
        System.out.println("--- Unbounded Concurrency (DANGEROUS!) ---");
        System.out.println("Processing 20 items with no concurrency limit:\n");

        Flux.range(1, 20)
            .flatMap(id -> simulateDatabaseCall(id))  // No limit!
            .subscribe(
                result -> System.out.println("Completed: " + result),
                error -> System.err.println("Error: " + error),
                () -> System.out.println("\nAll done! Max concurrent connections: " +
                    maxConnections.get())
            );

        Thread.sleep(2000);
        System.out.println();
    }

    private static void demonstrateBoundedConcurrency() throws InterruptedException {
        System.out.println("--- Bounded Concurrency (SAFE) ---");
        System.out.println("Processing 20 items with concurrency limit of 3:\n");

        Flux.range(1, 20)
            .flatMap(id -> simulateDatabaseCall(id), 3)  // Max 3 concurrent!
            .subscribe(
                result -> System.out.println("Completed: " + result),
                error -> System.err.println("Error: " + error),
                () -> System.out.println("\nAll done! Max concurrent connections: " +
                    maxConnections.get())
            );

        Thread.sleep(8000);
        System.out.println();
    }

    private static void demonstrateConcatMap() throws InterruptedException {
        System.out.println("--- Sequential Processing with concatMap ---");
        System.out.println("Processing 5 items sequentially:\n");

        long start = System.currentTimeMillis();

        Flux.range(1, 5)
            .concatMap(id -> simulateDatabaseCall(id))  // One at a time
            .subscribe(
                result -> System.out.println("Completed: " + result),
                error -> System.err.println("Error: " + error),
                () -> {
                    long elapsed = System.currentTimeMillis() - start;
                    System.out.println("\nAll done in " + elapsed + "ms");
                    System.out.println("Max concurrent connections: " + maxConnections.get());
                }
            );

        Thread.sleep(3000);
        System.out.println();
    }

    private static Mono<String> simulateDatabaseCall(int id) {
        return Mono.fromCallable(() -> {
            int current = activeConnections.incrementAndGet();
            maxConnections.updateAndGet(max -> Math.max(max, current));
            System.out.println("  Starting call " + id + " (active: " + current + ")");
            return id;
        })
        .delayElement(Duration.ofMillis(300))  // Simulate work
        .map(i -> {
            activeConnections.decrementAndGet();
            return "Result-" + i;
        });
    }

    private static void resetCounters() {
        activeConnections.set(0);
        maxConnections.set(0);
    }
}
```

### Exercise 1.2: Finding the Right Concurrency

Add to the file:

```java
private static void findOptimalConcurrency() throws InterruptedException {
    System.out.println("--- Finding Optimal Concurrency ---\n");

    // TODO: Process 100 items with different concurrency levels
    // Measure total time for each:
    // - concurrency = 1
    // - concurrency = 5
    // - concurrency = 10
    // - concurrency = 20
    // - concurrency = 50

    // Print results showing time vs concurrency
    // Note: There's a sweet spot! Too much concurrency doesn't help.

    int[] concurrencyLevels = {1, 5, 10, 20, 50};

    for (int concurrency : concurrencyLevels) {
        // Your implementation here
    }
}
```

---

## Part 2: Batching and Buffering (20 min)

### Exercise 2.1: Buffer Basics

Create `src/main/java/com/example/lab/BatchingBuffering.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

public class BatchingBuffering {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Batching and Buffering ===\n");

        demonstrateBufferByCount();
        demonstrateBufferByTime();
        demonstrateBufferTimeout();
        demonstrateSlidingWindow();
        demonstrateEfficientBatchInsert();
    }

    private static void demonstrateBufferByCount() {
        System.out.println("--- Buffer by Count ---");

        Flux.range(1, 10)
            .buffer(3)
            .subscribe(batch -> System.out.println("Batch: " + batch));

        System.out.println();
    }

    private static void demonstrateBufferByTime() throws InterruptedException {
        System.out.println("--- Buffer by Time ---");
        System.out.println("Buffering for 500ms windows:\n");

        Flux.interval(Duration.ofMillis(100))
            .take(15)
            .buffer(Duration.ofMillis(500))
            .subscribe(batch -> System.out.println("Batch (size " + batch.size() + "): " + batch));

        Thread.sleep(2000);
        System.out.println();
    }

    private static void demonstrateBufferTimeout() throws InterruptedException {
        System.out.println("--- Buffer with Count OR Time (bufferTimeout) ---");
        System.out.println("Emit when 5 items collected OR 300ms elapsed:\n");

        // Fast emissions followed by slow
        Flux<Integer> variableRate = Flux.concat(
            Flux.range(1, 8).delayElements(Duration.ofMillis(50)),   // Fast
            Flux.range(9, 4).delayElements(Duration.ofMillis(200))   // Slow
        );

        variableRate
            .bufferTimeout(5, Duration.ofMillis(300))
            .subscribe(batch -> System.out.println("Batch: " + batch));

        Thread.sleep(3000);
        System.out.println();
    }

    private static void demonstrateSlidingWindow() {
        System.out.println("--- Sliding Window (Overlapping Buffer) ---");

        Flux.range(1, 7)
            .buffer(3, 1)  // Size 3, skip 1 (overlap of 2)
            .subscribe(window -> System.out.println("Window: " + window));

        System.out.println();
    }

    private static void demonstrateEfficientBatchInsert() throws InterruptedException {
        System.out.println("--- Efficient Batch Insert Pattern ---");

        // Simulate records to insert
        Flux<Record> records = Flux.range(1, 25)
            .map(i -> new Record(i, "data-" + i));

        System.out.println("Inserting 25 records in batches of 10:\n");

        records
            .buffer(10)
            .flatMap(batch -> insertBatch(batch), 2)  // 2 concurrent batch inserts
            .subscribe(
                result -> System.out.println("Batch result: " + result),
                error -> System.err.println("Error: " + error),
                () -> System.out.println("\nAll records inserted!")
            );

        Thread.sleep(2000);
        System.out.println();
    }

    private static Mono<String> insertBatch(List<Record> batch) {
        return Mono.fromCallable(() -> {
            System.out.println("  Inserting batch of " + batch.size() +
                " records: " + batch.get(0).id() + " to " + batch.get(batch.size()-1).id());
            Thread.sleep(200);  // Simulate DB insert time
            return "Inserted " + batch.size() + " records";
        });
    }

    record Record(int id, String data) {}
}
```

### Exercise 2.2: Build a Rate Limiter

Add to the file:

```java
private static void buildRateLimiter() throws InterruptedException {
    System.out.println("--- Rate Limiter with Buffer ---\n");

    // TODO: Build a rate limiter that:
    // 1. Accepts requests at any rate
    // 2. Processes at most 5 requests per second
    // 3. Uses buffer with time to batch requests
    // 4. Processes each batch sequentially

    // Simulate 20 requests arriving quickly
    Flux<String> requests = Flux.range(1, 20)
        .map(i -> "Request-" + i)
        .doOnNext(r -> System.out.println("Received: " + r));

    // Your rate limiter implementation here
}
```

---

## Part 3: Context Propagation (20 min)

### Exercise 3.1: Understanding Context

Create `src/main/java/com/example/lab/ContextPropagation.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class ContextPropagation {

    public static void main(String[] args) {
        System.out.println("=== Context Propagation ===\n");

        demonstrateBasicContext();
        demonstrateContextFlow();
        demonstrateContextModification();
        demonstrateRequestTracing();
    }

    private static void demonstrateBasicContext() {
        System.out.println("--- Basic Context Usage ---");

        Mono<String> mono = Mono.deferContextual(ctx -> {
            String userId = ctx.get("userId");
            String traceId = ctx.get("traceId");
            return Mono.just("Processing for user " + userId + " [trace: " + traceId + "]");
        });

        mono.contextWrite(Context.of("userId", "user-123", "traceId", "trace-abc"))
            .subscribe(System.out::println);

        System.out.println();
    }

    private static void demonstrateContextFlow() {
        System.out.println("--- Context Flows Upstream ---");

        Mono<String> result = Mono.just("data")
            .flatMap(d -> {
                return Mono.deferContextual(ctx -> {
                    System.out.println("  In flatMap, userId: " + ctx.getOrDefault("userId", "none"));
                    return Mono.just(d + "-processed");
                });
            })
            .map(d -> {
                System.out.println("  In map (context not directly accessible here)");
                return d.toUpperCase();
            });

        System.out.println("Without context:");
        result.subscribe(System.out::println);

        System.out.println("\nWith context:");
        result
            .contextWrite(Context.of("userId", "user-456"))
            .subscribe(System.out::println);

        System.out.println();
    }

    private static void demonstrateContextModification() {
        System.out.println("--- Context Modification (Immutable!) ---");

        Mono<String> inner = Mono.deferContextual(ctx -> {
            String value = ctx.getOrDefault("key", "not-found");
            return Mono.just("Inner sees: " + value);
        });

        Mono<String> outer = Mono.deferContextual(ctx -> {
            String value = ctx.getOrDefault("key", "not-found");
            return inner.map(innerResult -> "Outer sees: " + value + ", " + innerResult);
        });

        // Inner context adds a new key-value
        outer
            .contextWrite(ctx -> ctx.put("key", "outer-value"))
            .flatMap(outerResult ->
                inner.contextWrite(ctx -> ctx.put("key", "inner-value"))
                    .map(innerResult -> outerResult + " | Separate: " + innerResult)
            )
            .subscribe(System.out::println);

        System.out.println();
    }

    private static void demonstrateRequestTracing() {
        System.out.println("--- Request Tracing Pattern ---\n");

        // Simulate a request with trace ID
        processRequest("req-001")
            .subscribe(System.out::println);

        System.out.println();
    }

    private static Mono<String> processRequest(String requestId) {
        return Mono.just("Starting request")
            .flatMap(s -> {
                return fetchUser()
                    .flatMap(user -> fetchOrders(user))
                    .flatMap(orders -> calculateTotal(orders));
            })
            .contextWrite(Context.of(
                "requestId", requestId,
                "startTime", System.currentTimeMillis()
            ));
    }

    private static Mono<String> fetchUser() {
        return Mono.deferContextual(ctx -> {
            logWithContext(ctx, "Fetching user");
            return Mono.just("User-123");
        });
    }

    private static Mono<String> fetchOrders(String user) {
        return Mono.deferContextual(ctx -> {
            logWithContext(ctx, "Fetching orders for " + user);
            return Mono.just("Orders-ABC");
        });
    }

    private static Mono<String> calculateTotal(String orders) {
        return Mono.deferContextual(ctx -> {
            logWithContext(ctx, "Calculating total for " + orders);
            long startTime = ctx.getOrDefault("startTime", 0L);
            long elapsed = System.currentTimeMillis() - startTime;
            return Mono.just("Total: $100.00 (processed in " + elapsed + "ms)");
        });
    }

    private static void logWithContext(reactor.util.context.ContextView ctx, String message) {
        String requestId = ctx.getOrDefault("requestId", "unknown");
        System.out.println("  [" + requestId + "] " + message);
    }
}
```

### Exercise 3.2: Build a Logging Interceptor

Add to the file:

```java
private static void buildLoggingInterceptor() {
    System.out.println("--- Logging Interceptor ---\n");

    // TODO: Create a reusable logging decorator that:
    // 1. Reads trace ID from context
    // 2. Logs entry with method name and trace ID
    // 3. Logs exit with duration and trace ID
    // 4. Works with any Mono

    // Example usage:
    // Mono<String> result = withLogging("fetchUser", fetchUser());
}
```

---

## Part 4: Schedulers (20 min)

### Exercise 4.1: Understanding Schedulers

Create `src/main/java/com/example/lab/SchedulerControl.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

public class SchedulerControl {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Scheduler Control ===\n");

        demonstrateDefaultScheduler();
        demonstrateSubscribeOn();
        demonstratePublishOn();
        demonstrateCombined();
        demonstrateBlockingOnElastic();
    }

    private static void demonstrateDefaultScheduler() {
        System.out.println("--- Default Scheduler (Caller's Thread) ---");

        System.out.println("Main thread: " + Thread.currentThread().getName());

        Flux.range(1, 3)
            .doOnNext(i -> System.out.println("  Processing " + i + " on: " +
                Thread.currentThread().getName()))
            .subscribe();

        System.out.println();
    }

    private static void demonstrateSubscribeOn() throws InterruptedException {
        System.out.println("--- subscribeOn: Subscription on Different Thread ---");

        System.out.println("Main thread: " + Thread.currentThread().getName());

        Flux.range(1, 3)
            .doOnSubscribe(s -> System.out.println("  Subscription on: " +
                Thread.currentThread().getName()))
            .doOnNext(i -> System.out.println("  Processing " + i + " on: " +
                Thread.currentThread().getName()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        Thread.sleep(100);
        System.out.println();
    }

    private static void demonstratePublishOn() throws InterruptedException {
        System.out.println("--- publishOn: Switch Thread Mid-Pipeline ---");

        Flux.range(1, 3)
            .doOnNext(i -> System.out.println("  Before publishOn (" + i + "): " +
                Thread.currentThread().getName()))
            .publishOn(Schedulers.parallel())
            .doOnNext(i -> System.out.println("  After publishOn (" + i + "): " +
                Thread.currentThread().getName()))
            .subscribe();

        Thread.sleep(100);
        System.out.println();
    }

    private static void demonstrateCombined() throws InterruptedException {
        System.out.println("--- Combined subscribeOn and publishOn ---");

        Flux.range(1, 3)
            .doOnNext(i -> System.out.println("  Source (" + i + "): " +
                Thread.currentThread().getName()))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(i -> System.out.println("  After subscribeOn (" + i + "): " +
                Thread.currentThread().getName()))
            .publishOn(Schedulers.parallel())
            .doOnNext(i -> System.out.println("  After publishOn (" + i + "): " +
                Thread.currentThread().getName()))
            .publishOn(Schedulers.single())
            .doOnNext(i -> System.out.println("  After second publishOn (" + i + "): " +
                Thread.currentThread().getName()))
            .subscribe();

        Thread.sleep(200);
        System.out.println();
    }

    private static void demonstrateBlockingOnElastic() throws InterruptedException {
        System.out.println("--- Blocking Operations on Elastic Scheduler ---");

        // DON'T block on parallel scheduler - use boundedElastic!
        Mono.fromCallable(() -> {
            System.out.println("  Blocking operation on: " +
                Thread.currentThread().getName());
            Thread.sleep(100);  // Simulating blocking I/O
            return "Result from blocking call";
        })
        .subscribeOn(Schedulers.boundedElastic())  // Safe for blocking!
        .subscribe(System.out::println);

        Thread.sleep(200);
        System.out.println();
    }
}
```

### Exercise 4.2: Scheduler Selection Challenge

Add to the file:

```java
private static void schedulerSelectionChallenge() throws InterruptedException {
    System.out.println("--- Scheduler Selection Challenge ---\n");

    // TODO: Build a pipeline that:
    // 1. Reads from a "file" (simulated blocking operation) - use boundedElastic
    // 2. Parses the data (CPU-intensive) - use parallel
    // 3. Saves to database (blocking I/O) - use boundedElastic
    // 4. Sends notification (non-blocking) - use single

    // Verify by printing thread names at each stage

    // Simulated operations:
    Mono<String> readFile = Mono.fromCallable(() -> {
        System.out.println("  Reading file on: " + Thread.currentThread().getName());
        Thread.sleep(50);
        return "raw-data";
    });

    // Your implementation here
}
```

---

## Part 5: Testing with StepVerifier (25 min)

### Exercise 5.1: StepVerifier Basics

Create `src/test/java/com/example/lab/StepVerifierTest.java`:

```java
package com.example.lab;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class StepVerifierTest {

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
    void testFluxEmissionAlternative() {
        Flux<Integer> flux = Flux.just(1, 2, 3);

        StepVerifier.create(flux)
            .expectNext(1, 2, 3)  // Multiple in one call
            .verifyComplete();
    }

    @Test
    void testMonoSuccess() {
        Mono<String> mono = Mono.just("hello");

        StepVerifier.create(mono)
            .expectNext("hello")
            .verifyComplete();
    }

    @Test
    void testMonoEmpty() {
        Mono<String> mono = Mono.empty();

        StepVerifier.create(mono)
            .verifyComplete();  // No elements, just complete
    }

    @Test
    void testError() {
        Mono<String> mono = Mono.error(new RuntimeException("Boom!"));

        StepVerifier.create(mono)
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void testErrorMessage() {
        Mono<String> mono = Mono.error(new IllegalArgumentException("Invalid input"));

        StepVerifier.create(mono)
            .expectErrorMessage("Invalid input")
            .verify();
    }

    @Test
    void testWithMatchers() {
        Flux<String> flux = Flux.just("apple", "banana", "cherry");

        StepVerifier.create(flux)
            .expectNextMatches(s -> s.startsWith("a"))
            .expectNextMatches(s -> s.length() == 6)
            .expectNextMatches(s -> s.contains("err"))
            .verifyComplete();
    }

    @Test
    void testNextCount() {
        Flux<Integer> flux = Flux.range(1, 100);

        StepVerifier.create(flux)
            .expectNextCount(100)
            .verifyComplete();
    }

    @Test
    void testConsumeWhile() {
        Flux<Integer> flux = Flux.range(1, 10);

        StepVerifier.create(flux)
            .thenConsumeWhile(i -> i <= 5)  // Consume 1-5
            .expectNext(6, 7, 8, 9, 10)      // Then expect 6-10
            .verifyComplete();
    }

    @Test
    void testAssertNext() {
        Flux<User> users = Flux.just(
            new User("alice", 25),
            new User("bob", 30)
        );

        StepVerifier.create(users)
            .assertNext(user -> {
                assert user.name().equals("alice");
                assert user.age() == 25;
            })
            .assertNext(user -> {
                assert user.name().equals("bob");
                assert user.age() == 30;
            })
            .verifyComplete();
    }

    record User(String name, int age) {}
}
```

### Exercise 5.2: Testing Error Handling

Add to the test class:

```java
@Test
void testOnErrorReturn() {
    Mono<String> mono = Mono.<String>error(new RuntimeException("Failed"))
        .onErrorReturn("fallback");

    StepVerifier.create(mono)
        .expectNext("fallback")
        .verifyComplete();
}

@Test
void testOnErrorResume() {
    Mono<String> primary = Mono.error(new RuntimeException("Primary failed"));
    Mono<String> fallback = Mono.just("from fallback");

    Mono<String> result = primary.onErrorResume(e -> fallback);

    StepVerifier.create(result)
        .expectNext("from fallback")
        .verifyComplete();
}

@Test
void testRetry() {
    // TODO: Test a Mono that fails twice, then succeeds
    // Use an AtomicInteger to track attempts
    // Verify the final value is emitted
}

@Test
void testContext() {
    Mono<String> mono = Mono.deferContextual(ctx ->
        Mono.just("User: " + ctx.get("userId"))
    );

    StepVerifier.create(
        mono.contextWrite(ctx -> ctx.put("userId", "test-user"))
    )
        .expectNext("User: test-user")
        .verifyComplete();
}
```

---

## Part 6: Virtual Time Testing (20 min)

### Exercise 6.1: Testing Delays

Create `src/test/java/com/example/lab/VirtualTimeTest.java`:

```java
package com.example.lab;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

public class VirtualTimeTest {

    @Test
    void testDelayWithoutVirtualTime_SLOW() {
        // This test takes 3 ACTUAL seconds!
        // DON'T DO THIS in real tests!
        Mono<String> delayed = Mono.just("hello")
            .delayElement(Duration.ofSeconds(3));

        StepVerifier.create(delayed)
            .expectNext("hello")
            .verifyComplete();
    }

    @Test
    void testDelayWithVirtualTime_FAST() {
        // This test completes instantly!
        StepVerifier.withVirtualTime(() ->
            Mono.just("hello").delayElement(Duration.ofSeconds(3))
        )
            .thenAwait(Duration.ofSeconds(3))  // Virtual time advance
            .expectNext("hello")
            .verifyComplete();
    }

    @Test
    void testInterval() {
        // Testing Flux.interval that would take 10 hours!
        StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofHours(1)).take(10)
        )
            .thenAwait(Duration.ofHours(10))
            .expectNextCount(10)
            .verifyComplete();
    }

    @Test
    void testTimeout() {
        StepVerifier.withVirtualTime(() ->
            Mono.never().timeout(Duration.ofSeconds(5))
        )
            .thenAwait(Duration.ofSeconds(5))
            .expectError(java.util.concurrent.TimeoutException.class)
            .verify();
    }

    @Test
    void testTimeoutWithFallback() {
        StepVerifier.withVirtualTime(() ->
            Mono.never()
                .timeout(Duration.ofSeconds(5), Mono.just("fallback"))
        )
            .thenAwait(Duration.ofSeconds(5))
            .expectNext("fallback")
            .verifyComplete();
    }

    @Test
    void testRetryWithBackoff() {
        java.util.concurrent.atomic.AtomicInteger attempts =
            new java.util.concurrent.atomic.AtomicInteger(0);

        // Fails twice, succeeds on third attempt
        StepVerifier.withVirtualTime(() ->
            Mono.fromCallable(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new RuntimeException("Fail");
                }
                return "Success";
            })
            .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(1)))
        )
            .thenAwait(Duration.ofSeconds(3))  // Allow for backoff delays
            .expectNext("Success")
            .verifyComplete();
    }

    @Test
    void testDelayBetweenElements() {
        StepVerifier.withVirtualTime(() ->
            Flux.just("a", "b", "c")
                .delayElements(Duration.ofMinutes(1))
        )
            .expectSubscription()
            .thenAwait(Duration.ofMinutes(1))
            .expectNext("a")
            .thenAwait(Duration.ofMinutes(1))
            .expectNext("b")
            .thenAwait(Duration.ofMinutes(1))
            .expectNext("c")
            .verifyComplete();
    }

    @Test
    void testExpectNoEvent() {
        StepVerifier.withVirtualTime(() ->
            Mono.just("hello").delayElement(Duration.ofSeconds(10))
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(9))  // Nothing for 9 seconds
            .thenAwait(Duration.ofSeconds(1))      // Then the 10th second
            .expectNext("hello")
            .verifyComplete();
    }
}
```

### Exercise 6.2: Test a Complex Timing Scenario

Add to the test class:

```java
@Test
void testRateLimiter() {
    // TODO: Test a rate limiter that allows 3 requests per second
    // 1. Send 10 requests quickly
    // 2. Verify only 3 are processed in the first second
    // 3. Advance time by 1 second
    // 4. Verify next 3 are processed
    // Continue until all 10 are processed
}

@Test
void testCacheExpiration() {
    // TODO: Test a caching Mono that:
    // 1. Caches the result for 5 minutes
    // 2. Returns cached value on subsequent calls
    // 3. Refreshes after cache expires
    // Use Mono.cache(Duration.ofMinutes(5))
}
```

---

## Part 7: Debugging Techniques (15 min)

### Exercise 7.1: Debugging Tools

Create `src/main/java/com/example/lab/DebuggingTechniques.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

public class DebuggingTechniques {

    public static void main(String[] args) {
        System.out.println("=== Debugging Techniques ===\n");

        demonstrateLog();
        demonstrateCheckpoint();
        demonstrateDebugWithDoOn();
        // demonstrateOperatorDebug();  // Expensive, only enable when needed
    }

    private static void demonstrateLog() {
        System.out.println("--- Using log() ---\n");

        Flux.range(1, 3)
            .log("before-map")
            .map(i -> i * 2)
            .log("after-map")
            .subscribe();

        System.out.println();
    }

    private static void demonstrateCheckpoint() {
        System.out.println("--- Using checkpoint() ---\n");

        try {
            Flux.range(1, 5)
                .checkpoint("after range")
                .map(i -> i * 2)
                .checkpoint("after doubling")
                .filter(i -> i > 100)  // Filters out everything
                .checkpoint("after filter")
                .single()  // Error: expected 1 element, got 0
                .checkpoint("after single")
                .block();
        } catch (Exception e) {
            System.out.println("Error caught: " + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            // The checkpoint info is in the suppressed exceptions
        }

        System.out.println();
    }

    private static void demonstrateDebugWithDoOn() {
        System.out.println("--- Using doOn* for Debugging ---\n");

        Flux.range(1, 5)
            .doOnSubscribe(s -> System.out.println("Subscribed!"))
            .doOnNext(i -> System.out.println("  Processing: " + i))
            .map(i -> i * 2)
            .doOnNext(i -> System.out.println("  After map: " + i))
            .filter(i -> i > 4)
            .doOnNext(i -> System.out.println("  After filter: " + i))
            .doOnComplete(() -> System.out.println("Completed!"))
            .subscribe();

        System.out.println();
    }

    private static void demonstrateOperatorDebug() {
        System.out.println("--- Using Hooks.onOperatorDebug() ---\n");
        System.out.println("WARNING: This is expensive! Only use in development.\n");

        // Enable full assembly tracking
        Hooks.onOperatorDebug();

        try {
            Flux.range(1, 5)
                .map(i -> i * 2)
                .filter(i -> i > 100)
                .single()
                .block();
        } catch (Exception e) {
            System.out.println("Full stack trace with assembly info:");
            e.printStackTrace();
        }

        // Don't forget to disable!
        Hooks.resetOnOperatorDebug();

        System.out.println();
    }
}
```

### Exercise 7.2: Debug a Broken Pipeline

Add to the file:

```java
private static void debugBrokenPipeline() {
    System.out.println("--- Debug Challenge: Find the Bug ---\n");

    // This pipeline has a bug. Use debugging techniques to find it.
    // The pipeline should:
    // 1. Process user IDs
    // 2. Fetch user data (simulated)
    // 3. Filter active users
    // 4. Get their email addresses
    // 5. Return a list of emails

    // But it's returning an empty list when it shouldn't!

    Flux<String> userIds = Flux.just("1", "2", "3", "4", "5");

    Mono<java.util.List<String>> emails = userIds
        .flatMap(id -> fetchUser(id))
        .filter(user -> user.active())
        .map(user -> user.email())
        .filter(email -> email != null)
        .collectList();

    System.out.println("Emails: " + emails.block());

    // TODO: Use log(), checkpoint(), or doOnNext() to find where data is lost
    // Then fix the bug
}

private static Mono<User> fetchUser(String id) {
    // Simulated user data - some users are active, some aren't
    return Mono.just(switch (id) {
        case "1" -> new User("1", "alice@example.com", true);
        case "2" -> new User("2", "bob@example.com", false);
        case "3" -> new User("3", null, true);  // Bug: null email
        case "4" -> new User("4", "david@example.com", true);
        case "5" -> new User("5", "eve@example.com", false);
        default -> new User(id, "unknown@example.com", false);
    });
}

record User(String id, String email, boolean active) {}
```

---

## Summary

In this lab, you:

1. ✅ Controlled concurrency with flatMap parameters
2. ✅ Used buffer for efficient batch operations
3. ✅ Propagated context through reactive pipelines
4. ✅ Mastered schedulers for thread control
5. ✅ Wrote tests with StepVerifier
6. ✅ Used virtual time for delay testing
7. ✅ Applied debugging techniques

---

## Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Control Concurrency Explicitly                                        │
│     Use flatMap(fn, concurrency) to prevent resource exhaustion.         │
│                                                                            │
│  2. Batch for Efficiency                                                  │
│     buffer() turns individual operations into bulk operations.           │
│                                                                            │
│  3. Context Replaces ThreadLocal                                          │
│     Use contextWrite() and deferContextual() for request-scoped data.   │
│                                                                            │
│  4. Know Your Schedulers                                                  │
│     subscribeOn: source thread | publishOn: downstream thread            │
│     boundedElastic for blocking, parallel for CPU work                   │
│                                                                            │
│  5. Test with StepVerifier                                                │
│     Never test reactive code with Thread.sleep()!                        │
│                                                                            │
│  6. Virtual Time is Essential                                             │
│     withVirtualTime() + thenAwait() for delay testing                   │
│                                                                            │
│  7. Debug Proactively                                                     │
│     Add checkpoints() before you need them.                              │
│     Use log() liberally during development.                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## What's Next?

**Congratulations!** You've completed Part II: Foundation. You now have a solid understanding of Project Reactor.

In **Part III: Spring WebFlux**, you'll apply these skills to build reactive web applications. You'll learn:
- WebFlux controllers and functional endpoints
- Reactive HTTP clients with WebClient
- Server-sent events and WebSockets
- Integration with the Spring ecosystem

The foundation you've built will make WebFlux feel natural and intuitive.
