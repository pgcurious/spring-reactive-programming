# Lab 6: Building Resilient Reactive Services

## Objective

In this lab, you'll master error handling and resilience patterns in Project Reactor. You'll:

1. Understand why try-catch doesn't work in reactive code
2. Implement error recovery with `onErrorReturn` and `onErrorResume`
3. Build retry logic with exponential backoff
4. Add timeouts to prevent hanging operations
5. Combine patterns into production-ready resilient pipelines
6. Use `doOn*` operators for logging and debugging

By the end, you'll be able to build reactive services that gracefully handle failures.

---

## Prerequisites

- Java 17+ installed
- Maven or Gradle installed
- Completed Labs 4 and 5 (understanding of Mono, Flux, and operators)
- Understanding of Chapter 6 concepts

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | Setup and Why Try-Catch Fails | 15 min |
| 2 | Basic Error Handling | 20 min |
| 3 | Error Recovery Operators | 25 min |
| 4 | Retry Strategies | 20 min |
| 5 | Timeouts and Fallbacks | 15 min |
| 6 | Building a Resilient Service | 20 min |
| 7 | Reflection and Key Takeaways | 5 min |

**Total: ~120 minutes**

---

## Part 1: Setup and Why Try-Catch Fails (15 min)

### Exercise 1.1: Project Setup

If continuing from Lab 4/5, use the same project. Otherwise, create a new Maven project with this `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>reactor-error-handling-lab</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <reactor.version>3.6.0</reactor.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>${reactor.version}</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <version>${reactor.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>
</project>
```

### Exercise 1.2: The Try-Catch Problem

Create `src/main/java/com/example/lab/WhyTryCatchFails.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;

import java.time.Duration;

public class WhyTryCatchFails {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Why Try-Catch Fails in Reactive Code ===\n");

        demonstrateTryCatchFailure();
        demonstrateCorrectErrorHandling();
    }

    private static void demonstrateTryCatchFailure() throws InterruptedException {
        System.out.println("--- Attempting Try-Catch (This Won't Work!) ---\n");

        try {
            System.out.println("1. Entering try block...");

            Mono<String> mono = Mono.delay(Duration.ofMillis(100))
                .map(l -> {
                    System.out.println("3. Inside map - about to throw!");
                    throw new RuntimeException("Boom!");
                });

            System.out.println("2. Mono created (no error yet!)");

            mono.subscribe(
                value -> System.out.println("Received: " + value),
                error -> System.out.println("4. Error caught in subscriber: " + error.getMessage())
            );

            System.out.println("2.5. Subscribe called (returns immediately!)");
            Thread.sleep(200);  // Wait for async operation

        } catch (Exception e) {
            // THIS WILL NEVER EXECUTE for the Mono error!
            System.out.println("CAUGHT IN TRY-CATCH: " + e.getMessage());
        }

        System.out.println("5. After try-catch block\n");
        System.out.println("Notice: The exception was handled by the subscriber's error handler,");
        System.out.println("NOT by the try-catch block!\n");
    }

    private static void demonstrateCorrectErrorHandling() throws InterruptedException {
        System.out.println("--- Correct Error Handling ---\n");

        Mono<String> mono = Mono.delay(Duration.ofMillis(100))
            .map(l -> {
                throw new RuntimeException("Boom!");
            });

        // Correct: Handle errors in the reactive chain
        mono.doOnError(e -> System.out.println("doOnError: Logging error - " + e.getMessage()))
            .onErrorReturn("Fallback value")
            .subscribe(
                value -> System.out.println("Received: " + value),
                error -> System.out.println("This won't print - error was handled"),
                () -> System.out.println("Completed!")
            );

        Thread.sleep(200);
        System.out.println();
    }
}
```

### Exercise 1.3: Predict the Output

Before running, answer:
1. Will the `catch (Exception e)` block ever execute for the Mono error?
2. In what order will the numbered println statements execute?
3. Where does the error actually get handled?

Run the code and verify your predictions.

---

## Part 2: Basic Error Handling (20 min)

### Exercise 2.1: Error Signals

Create `src/main/java/com/example/lab/ErrorSignals.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ErrorSignals {

    public static void main(String[] args) {
        System.out.println("=== Error Signals ===\n");

        demonstrateErrorPropagation();
        demonstrateErrorTerminatesStream();
        demonstrateMonoError();
    }

    private static void demonstrateErrorPropagation() {
        System.out.println("--- Error Propagation Through Pipeline ---");

        Flux<Integer> flux = Flux.range(1, 5)
            .doOnNext(i -> System.out.println("Source emitting: " + i))
            .map(i -> {
                if (i == 3) {
                    throw new RuntimeException("Error at element 3!");
                }
                return i * 10;
            })
            .doOnNext(i -> System.out.println("After map: " + i))
            .filter(i -> i > 15)
            .doOnNext(i -> System.out.println("After filter: " + i));

        flux.subscribe(
            value -> System.out.println("  Subscriber received: " + value),
            error -> System.out.println("  Subscriber error: " + error.getMessage()),
            () -> System.out.println("  Subscriber complete")
        );

        System.out.println("\nNotice: Elements 4 and 5 were never emitted!");
        System.out.println("Error at element 3 terminated the stream.\n");
    }

    private static void demonstrateErrorTerminatesStream() {
        System.out.println("--- Error Terminates Stream ---");

        Flux<String> flux = Flux.create(sink -> {
            sink.next("First");
            sink.next("Second");
            sink.error(new RuntimeException("Something went wrong"));
            sink.next("Third");  // This will never be emitted!
            sink.complete();      // This will never be called!
        });

        flux.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> System.out.println("  Error: " + error.getMessage()),
            () -> System.out.println("  Complete (won't print)")
        );

        System.out.println("\nNotice: 'Third' was never received, completion never signaled.\n");
    }

    private static void demonstrateMonoError() {
        System.out.println("--- Mono Error Behavior ---");

        // Mono that immediately errors
        Mono<String> errorMono = Mono.error(new IllegalArgumentException("Invalid input"));

        errorMono.subscribe(
            value -> System.out.println("  Value: " + value),
            error -> System.out.println("  Error type: " + error.getClass().getSimpleName()
                + ", message: " + error.getMessage())
        );

        // Mono that errors during processing
        Mono<Integer> processingError = Mono.just(10)
            .map(i -> i / 0);  // ArithmeticException

        processingError.subscribe(
            value -> System.out.println("  Value: " + value),
            error -> System.out.println("  Error type: " + error.getClass().getSimpleName())
        );

        System.out.println();
    }
}
```

### Exercise 2.2: Complete the Handler

Add a method that handles different error types differently:

```java
private static void handleDifferentErrors() {
    System.out.println("--- Handling Different Error Types ---");

    // TODO: Create a Flux that processes strings
    // - If the string is "error", throw RuntimeException
    // - If the string is "invalid", throw IllegalArgumentException
    // - Otherwise, return the string uppercase

    // TODO: Subscribe with an error handler that:
    // - Prints "Runtime error occurred" for RuntimeException
    // - Prints "Invalid argument: <message>" for IllegalArgumentException
    // - Prints "Unknown error: <class name>" for other exceptions

    Flux<String> flux = Flux.just("hello", "error", "world", "invalid", "test");

    // Your implementation here
}
```

---

## Part 3: Error Recovery Operators (25 min)

### Exercise 3.1: onErrorReturn

Create `src/main/java/com/example/lab/ErrorRecovery.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

public class ErrorRecovery {

    public static void main(String[] args) {
        System.out.println("=== Error Recovery Operators ===\n");

        demonstrateOnErrorReturn();
        demonstrateOnErrorReturnWithPredicate();
        demonstrateOnErrorResume();
        demonstrateOnErrorResumeWithPredicate();
        demonstrateOnErrorMap();
    }

    private static void demonstrateOnErrorReturn() {
        System.out.println("--- onErrorReturn: Provide Default Value ---");

        Mono<String> mono = simulateServiceCall(true)  // true = will fail
            .onErrorReturn("default-value");

        mono.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> System.out.println("  Error (won't print): " + error.getMessage()),
            () -> System.out.println("  Completed!")
        );

        System.out.println();
    }

    private static void demonstrateOnErrorReturnWithPredicate() {
        System.out.println("--- onErrorReturn with Predicate ---");

        // Only catch IllegalStateException, let others propagate
        Mono<String> mono1 = Mono.<String>error(new IllegalStateException("Service unavailable"))
            .onErrorReturn(
                e -> e instanceof IllegalStateException,
                "service-unavailable-default"
            );

        Mono<String> mono2 = Mono.<String>error(new IllegalArgumentException("Bad input"))
            .onErrorReturn(
                e -> e instanceof IllegalStateException,  // Won't match!
                "service-unavailable-default"
            );

        System.out.println("IllegalStateException (matched):");
        mono1.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> System.out.println("  Error: " + error.getMessage())
        );

        System.out.println("IllegalArgumentException (not matched):");
        mono2.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> System.out.println("  Error propagated: " + error.getMessage())
        );

        System.out.println();
    }

    private static void demonstrateOnErrorResume() {
        System.out.println("--- onErrorResume: Switch to Fallback Source ---");

        // Simulating: Primary DB fails, fallback to cache
        Mono<String> primaryDb = Mono.error(new RuntimeException("DB connection failed"));
        Mono<String> cache = Mono.just("cached-value");

        Mono<String> result = primaryDb
            .doOnError(e -> System.out.println("  Primary failed: " + e.getMessage()))
            .onErrorResume(e -> {
                System.out.println("  Switching to cache...");
                return cache;
            });

        result.subscribe(
            value -> System.out.println("  Final value: " + value),
            error -> System.out.println("  Error: " + error.getMessage()),
            () -> System.out.println("  Completed!")
        );

        System.out.println();
    }

    private static void demonstrateOnErrorResumeWithPredicate() {
        System.out.println("--- onErrorResume with Predicate ---");

        Mono<String> mono = Mono.<String>error(new ServiceUnavailableException("Service down"))
            .onErrorResume(
                ServiceUnavailableException.class,
                e -> {
                    System.out.println("  Service unavailable, returning fallback");
                    return Mono.just("fallback-for-service-down");
                }
            )
            .onErrorResume(
                IllegalArgumentException.class,
                e -> {
                    System.out.println("  Bad input, returning empty");
                    return Mono.empty();
                }
            );

        mono.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> System.out.println("  Unhandled error: " + error.getMessage()),
            () -> System.out.println("  Completed!")
        );

        System.out.println();
    }

    private static void demonstrateOnErrorMap() {
        System.out.println("--- onErrorMap: Transform the Error ---");

        Mono<String> mono = Mono.<String>error(new RuntimeException("Low-level DB error"))
            .onErrorMap(e -> new ServiceException("Failed to fetch data", e));

        mono.subscribe(
            value -> System.out.println("  Received: " + value),
            error -> {
                System.out.println("  Error type: " + error.getClass().getSimpleName());
                System.out.println("  Message: " + error.getMessage());
                System.out.println("  Cause: " + error.getCause().getMessage());
            }
        );

        System.out.println();
    }

    // Helper methods and classes
    private static Mono<String> simulateServiceCall(boolean shouldFail) {
        if (shouldFail) {
            return Mono.error(new RuntimeException("Service call failed"));
        }
        return Mono.just("success-response");
    }

    static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    static class ServiceException extends RuntimeException {
        public ServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

### Exercise 3.2: Build a Resilient Data Fetcher

Add to the file:

```java
private static void buildResilientFetcher() {
    System.out.println("--- Resilient Data Fetcher ---");

    // TODO: Implement a method that:
    // 1. First tries to get data from cache (simulateCache)
    // 2. If cache fails, tries primary database (simulatePrimaryDb)
    // 3. If primary fails, tries replica database (simulateReplicaDb)
    // 4. If all fail, returns a default value

    // Use these helper methods:
    // simulateCache(fail: boolean)
    // simulatePrimaryDb(fail: boolean)
    // simulateReplicaDb(fail: boolean)

    // Test case 1: All succeed (cache returns data)
    // Test case 2: Cache fails, primary succeeds
    // Test case 3: Cache and primary fail, replica succeeds
    // Test case 4: All fail, default returned
}

private static Mono<String> simulateCache(boolean fail) {
    if (fail) return Mono.error(new RuntimeException("Cache miss"));
    return Mono.just("data-from-cache");
}

private static Mono<String> simulatePrimaryDb(boolean fail) {
    if (fail) return Mono.error(new RuntimeException("Primary DB unavailable"));
    return Mono.just("data-from-primary-db");
}

private static Mono<String> simulateReplicaDb(boolean fail) {
    if (fail) return Mono.error(new RuntimeException("Replica DB unavailable"));
    return Mono.just("data-from-replica-db");
}
```

---

## Part 4: Retry Strategies (20 min)

### Exercise 4.1: Basic Retry

Create `src/main/java/com/example/lab/RetryStrategies.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryStrategies {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Retry Strategies ===\n");

        demonstrateSimpleRetry();
        demonstrateRetryWithBackoff();
        demonstrateRetryWithFilter();
        demonstrateRetryExhausted();
    }

    private static void demonstrateSimpleRetry() {
        System.out.println("--- Simple Retry ---");

        AtomicInteger attempts = new AtomicInteger(0);

        Mono<String> unreliableService = Mono.fromSupplier(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("  Attempt " + attempt);
            if (attempt < 3) {
                throw new RuntimeException("Failed on attempt " + attempt);
            }
            return "Success on attempt " + attempt;
        });

        unreliableService
            .retry(3)  // Retry up to 3 times
            .subscribe(
                value -> System.out.println("  Result: " + value),
                error -> System.out.println("  Final error: " + error.getMessage())
            );

        System.out.println();
    }

    private static void demonstrateRetryWithBackoff() throws InterruptedException {
        System.out.println("--- Retry with Exponential Backoff ---");

        AtomicInteger attempts = new AtomicInteger(0);

        Mono<String> unreliableService = Mono.fromSupplier(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("  Attempt " + attempt + " at " + System.currentTimeMillis() % 10000 + "ms");
            if (attempt < 4) {
                throw new RuntimeException("Failed on attempt " + attempt);
            }
            return "Success!";
        });

        System.out.println("Starting at " + System.currentTimeMillis() % 10000 + "ms");
        System.out.println("(Watch the timestamps - notice the increasing delays)\n");

        unreliableService
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.1)  // 10% random variation
                .doBeforeRetry(signal ->
                    System.out.println("  Retrying after failure: " + signal.failure().getMessage())
                )
            )
            .subscribe(
                value -> System.out.println("  Result: " + value),
                error -> System.out.println("  Final error: " + error.getMessage())
            );

        Thread.sleep(3000);  // Wait for retries
        System.out.println();
    }

    private static void demonstrateRetryWithFilter() {
        System.out.println("--- Retry with Error Filter ---");

        AtomicInteger attempts = new AtomicInteger(0);

        // Only retry TransientException, not ValidationException
        Mono<String> service = Mono.fromSupplier(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("  Attempt " + attempt);

            if (attempt == 1) {
                throw new TransientException("Network glitch");
            }
            if (attempt == 2) {
                throw new TransientException("Temporary unavailable");
            }
            if (attempt == 3) {
                throw new ValidationException("Invalid input");  // Should NOT retry
            }
            return "Success";
        });

        service
            .retryWhen(Retry.max(5)
                .filter(e -> e instanceof TransientException)
                .doBeforeRetry(signal ->
                    System.out.println("  Retrying transient error...")
                )
            )
            .subscribe(
                value -> System.out.println("  Result: " + value),
                error -> System.out.println("  Final error: " + error.getClass().getSimpleName()
                    + " - " + error.getMessage())
            );

        System.out.println("\nNotice: ValidationException was NOT retried.\n");
    }

    private static void demonstrateRetryExhausted() throws InterruptedException {
        System.out.println("--- Retry Exhausted Handling ---");

        AtomicInteger attempts = new AtomicInteger(0);

        Mono<String> alwaysFails = Mono.fromSupplier(() -> {
            System.out.println("  Attempt " + attempts.incrementAndGet());
            throw new RuntimeException("Always fails");
        });

        alwaysFails
            .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
                .onRetryExhaustedThrow((spec, signal) ->
                    new ServiceException("Service unavailable after " +
                        signal.totalRetries() + " retries", signal.failure())
                )
            )
            .subscribe(
                value -> System.out.println("  Result: " + value),
                error -> {
                    System.out.println("  Error type: " + error.getClass().getSimpleName());
                    System.out.println("  Message: " + error.getMessage());
                }
            );

        Thread.sleep(500);
        System.out.println();
    }

    // Exception classes
    static class TransientException extends RuntimeException {
        public TransientException(String message) { super(message); }
    }

    static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    static class ServiceException extends RuntimeException {
        public ServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
```

### Exercise 4.2: Implement Smart Retry

Add to the file:

```java
private static void implementSmartRetry() throws InterruptedException {
    System.out.println("--- Smart Retry Implementation ---");

    // TODO: Implement a retry strategy that:
    // 1. Retries up to 5 times
    // 2. Uses exponential backoff starting at 100ms
    // 3. Caps backoff at 2 seconds
    // 4. Only retries IOException and TimeoutException (simulated)
    // 5. Does NOT retry IllegalArgumentException
    // 6. Logs each retry attempt with the error message
    // 7. On exhaustion, throws a custom "MaxRetriesExceededException"

    // Create a service that fails with different exceptions based on attempt number
}
```

---

## Part 5: Timeouts and Fallbacks (15 min)

### Exercise 5.1: Timeout Handling

Create `src/main/java/com/example/lab/TimeoutHandling.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;

import java.time.Duration;

public class TimeoutHandling {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Timeout Handling ===\n");

        demonstrateSimpleTimeout();
        demonstrateTimeoutWithFallback();
        demonstrateTimeoutWithRetry();
        demonstrateCompleteResilientPipeline();
    }

    private static void demonstrateSimpleTimeout() throws InterruptedException {
        System.out.println("--- Simple Timeout ---");

        // This will timeout
        Mono<String> slowService = Mono.delay(Duration.ofSeconds(2))
            .map(l -> "Response from slow service");

        System.out.println("Calling slow service with 500ms timeout...");
        slowService
            .timeout(Duration.ofMillis(500))
            .subscribe(
                value -> System.out.println("  Received: " + value),
                error -> System.out.println("  Error: " + error.getClass().getSimpleName())
            );

        Thread.sleep(1000);
        System.out.println();

        // This will succeed
        Mono<String> fastService = Mono.delay(Duration.ofMillis(100))
            .map(l -> "Response from fast service");

        System.out.println("Calling fast service with 500ms timeout...");
        fastService
            .timeout(Duration.ofMillis(500))
            .subscribe(
                value -> System.out.println("  Received: " + value),
                error -> System.out.println("  Error: " + error.getClass().getSimpleName())
            );

        Thread.sleep(500);
        System.out.println();
    }

    private static void demonstrateTimeoutWithFallback() throws InterruptedException {
        System.out.println("--- Timeout with Fallback ---");

        Mono<String> slowService = Mono.delay(Duration.ofSeconds(2))
            .map(l -> "Primary response");

        Mono<String> fallback = Mono.just("Fallback response (cached data)");

        slowService
            .timeout(Duration.ofMillis(500), fallback)
            .subscribe(
                value -> System.out.println("  Received: " + value),
                error -> System.out.println("  Error: " + error.getMessage()),
                () -> System.out.println("  Completed!")
            );

        Thread.sleep(1000);
        System.out.println();
    }

    private static void demonstrateTimeoutWithRetry() throws InterruptedException {
        System.out.println("--- Timeout with Retry ---");

        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger(0);

        Mono<String> unreliableSlowService = Mono.fromSupplier(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("  Attempt " + attempt + " starting...");
            return attempt;
        })
        .delayElement(attempt -> {
            // First 2 attempts are slow (will timeout), 3rd is fast
            return attempts.get() < 3 ? Duration.ofSeconds(2) : Duration.ofMillis(100);
        })
        .map(attempt -> "Success on attempt " + attempt);

        unreliableSlowService
            .timeout(Duration.ofMillis(500))
            .retry(3)
            .subscribe(
                value -> System.out.println("  Result: " + value),
                error -> System.out.println("  Final error: " + error.getMessage())
            );

        Thread.sleep(5000);
        System.out.println();
    }

    private static void demonstrateCompleteResilientPipeline() throws InterruptedException {
        System.out.println("--- Complete Resilient Pipeline ---");

        System.out.println("Scenario: Service that may be slow OR fail, with full resilience\n");

        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger(0);

        Mono<String> unreliableService = Mono.fromSupplier(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("  Attempt " + attempt);

            // Simulate different failure modes
            if (attempt == 1) {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                return "Too slow";  // Will timeout
            }
            if (attempt == 2) {
                throw new RuntimeException("Service error");
            }
            return "Success!";
        });

        unreliableService
            .timeout(Duration.ofMillis(500))       // Each attempt has 500ms timeout
            .doOnError(e -> System.out.println("    Error: " + e.getClass().getSimpleName()))
            .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofMillis(100)))
            .onErrorResume(e -> {
                System.out.println("    All retries failed, using fallback");
                return Mono.just("Fallback value");
            })
            .doOnSuccess(v -> System.out.println("  Final result: " + v))
            .subscribe();

        Thread.sleep(3000);
        System.out.println();
    }

    // Helper for variable delay
    private static <T> java.util.function.Function<T, Duration> delayElement(
            java.util.function.Function<T, Duration> delayFunction) {
        return delayFunction;
    }
}
```

### Exercise 5.2: Design Your Timeout Strategy

Add a method that implements:

```java
private static void designTimeoutStrategy() {
    System.out.println("--- Custom Timeout Strategy ---");

    // TODO: Build a pipeline that calls 3 services in parallel (using Mono.zip)
    // - Each service may be slow or fail
    // - Each service has its own timeout (different values)
    // - If a service times out, use its fallback
    // - Combine all 3 results into a single response

    // Services:
    // - User Service: May take 0-2s, timeout at 500ms, fallback: anonymous user
    // - Product Service: May take 0-1s, timeout at 300ms, fallback: empty product
    // - Price Service: May take 0-3s, timeout at 1s, fallback: price=0

    // Expected: Even if some services timeout, get a combined result
}
```

---

## Part 6: Building a Resilient Service (20 min)

### Exercise 6.1: Complete Resilient Service

Create `src/main/java/com/example/lab/ResilientOrderService.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ResilientOrderService {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Resilient Order Service ===\n");

        ResilientOrderService service = new ResilientOrderService();

        // Test different scenarios
        System.out.println("Scenario 1: Everything works");
        service.processOrder("order-1", false, false, false).block();

        System.out.println("\nScenario 2: Inventory check fails initially, then succeeds");
        service.resetCounters();
        service.processOrder("order-2", true, false, false).block();

        System.out.println("\nScenario 3: Payment times out, fallback to pending status");
        service.resetCounters();
        service.processOrder("order-3", false, true, false).block();

        System.out.println("\nScenario 4: Notification fails, order still succeeds");
        service.resetCounters();
        service.processOrder("order-4", false, false, true).block();
    }

    private AtomicInteger inventoryAttempts = new AtomicInteger(0);
    private AtomicInteger paymentAttempts = new AtomicInteger(0);

    public Mono<OrderResult> processOrder(String orderId,
                                          boolean inventoryFlaky,
                                          boolean paymentSlow,
                                          boolean notificationFails) {
        return validateOrder(orderId)
            .doOnNext(order -> System.out.println("  1. Order validated: " + order.id()))
            .flatMap(order -> checkInventory(order, inventoryFlaky))
            .doOnNext(order -> System.out.println("  2. Inventory checked"))
            .flatMap(order -> processPayment(order, paymentSlow))
            .doOnNext(order -> System.out.println("  3. Payment processed: " + order.paymentStatus()))
            .flatMap(order -> sendNotification(order, notificationFails)
                .onErrorResume(e -> {
                    System.out.println("  4. Notification failed (non-critical): " + e.getMessage());
                    return Mono.just(order);  // Continue without notification
                }))
            .doOnNext(order -> System.out.println("  4. Notification sent (or skipped)"))
            .map(order -> new OrderResult(order.id(), order.paymentStatus(), "COMPLETED"))
            .doOnSuccess(result -> System.out.println("  Order completed: " + result))
            .doOnError(e -> System.out.println("  Order failed: " + e.getMessage()));
    }

    private Mono<Order> validateOrder(String orderId) {
        // Simple validation - always succeeds
        return Mono.just(new Order(orderId, "PENDING"));
    }

    private Mono<Order> checkInventory(Order order, boolean flaky) {
        return Mono.fromSupplier(() -> {
            int attempt = inventoryAttempts.incrementAndGet();
            if (flaky && attempt < 3) {
                throw new RuntimeException("Inventory service temporarily unavailable");
            }
            return order;
        })
        .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
            .doBeforeRetry(signal ->
                System.out.println("    Retrying inventory check... (attempt " +
                    (signal.totalRetries() + 2) + ")")
            ))
        .timeout(Duration.ofSeconds(2))
        .onErrorMap(e -> new OrderException("Inventory check failed", e));
    }

    private Mono<Order> processPayment(Order order, boolean slow) {
        Mono<Order> payment = slow
            ? Mono.delay(Duration.ofSeconds(5)).map(l -> order.withPaymentStatus("PAID"))
            : Mono.just(order.withPaymentStatus("PAID"));

        return payment
            .timeout(Duration.ofSeconds(1), Mono.just(order.withPaymentStatus("PENDING")))
            .doOnNext(o -> {
                if ("PENDING".equals(o.paymentStatus())) {
                    System.out.println("    Payment timed out, marked as pending for later processing");
                }
            });
    }

    private Mono<Order> sendNotification(Order order, boolean fails) {
        if (fails) {
            return Mono.error(new RuntimeException("Notification service unavailable"));
        }
        return Mono.just(order);
    }

    public void resetCounters() {
        inventoryAttempts.set(0);
        paymentAttempts.set(0);
    }

    // Domain objects
    record Order(String id, String paymentStatus) {
        Order withPaymentStatus(String status) {
            return new Order(id, status);
        }
    }

    record OrderResult(String orderId, String paymentStatus, String status) {}

    static class OrderException extends RuntimeException {
        public OrderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

### Exercise 6.2: Extend the Service

Add the following capabilities:

```java
// TODO: Add these features to the order service:

// 1. Add a circuit breaker pattern:
//    - Track consecutive failures
//    - After 3 consecutive failures, immediately fail for 10 seconds
//    - Then allow one request through (half-open state)

// 2. Add compensation logic:
//    - If payment succeeds but notification fails 3 times,
//      mark the order for manual review instead of completing

// 3. Add metrics collection using doOn* operators:
//    - Count successful orders
//    - Count failed orders
//    - Track average processing time
//    - Log all errors with context

// Hint: You might need AtomicInteger counters and some state management
```

---

## Part 7: Reflection and Key Takeaways (5 min)

### Discussion Questions

1. **Why can't try-catch work for reactive error handling?**
   - Think about the timing and thread context.

2. **When would you use `onErrorReturn` vs `onErrorResume`?**
   - What's the fundamental difference?

3. **What's dangerous about `retry(100)`?**
   - How does backoff help?

4. **Why is timeout important in reactive systems?**
   - What happens without timeouts?

5. **How do you decide where in the pipeline to handle errors?**
   - Close to the source? At the end? Both?

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Errors are Signals, Not Exceptions                                    │
│     Handle them with onError* operators, not try-catch.                  │
│     Errors are terminal - they end the stream.                           │
│                                                                            │
│  2. Recovery Operators                                                    │
│     • onErrorReturn: Default value                                       │
│     • onErrorResume: Alternative source                                  │
│     • onErrorMap: Transform error type                                   │
│                                                                            │
│  3. Retry Intelligently                                                   │
│     • Limit retry count                                                  │
│     • Use exponential backoff                                            │
│     • Filter retryable errors                                            │
│     • Don't hammer failing services                                      │
│                                                                            │
│  4. Always Set Timeouts                                                   │
│     • Every external call needs a timeout                                │
│     • Combine with fallback for graceful degradation                    │
│     • timeout + retry = per-attempt timeout                             │
│                                                                            │
│  5. doOn* for Observation                                                 │
│     • doOnError for logging                                              │
│     • Doesn't change the stream                                          │
│     • Separate observation from handling                                 │
│                                                                            │
│  6. Design for Failure                                                    │
│     • What's the fallback?                                               │
│     • What should be logged?                                             │
│     • What errors are transient vs permanent?                            │
│     • What operations need compensation?                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

### Challenge 1: Circuit Breaker

Implement a simple circuit breaker that:
- Opens after 3 consecutive failures
- Stays open for 5 seconds
- Moves to half-open state (allows 1 request)
- Closes on success, reopens on failure

### Challenge 2: Bulkhead Pattern

Implement isolation using `flatMap` concurrency limits:
- Different services get different concurrency limits
- Track and report when limits are reached

### Challenge 3: Metrics Dashboard

Create a reactive metrics collector that:
- Counts successes, failures, timeouts, retries
- Calculates success rate
- Emits metrics every second as a Flux

---

## Running the Examples

```bash
# Compile
mvn compile

# Run specific class
mvn exec:java -Dexec.mainClass="com.example.lab.WhyTryCatchFails"
mvn exec:java -Dexec.mainClass="com.example.lab.ErrorSignals"
mvn exec:java -Dexec.mainClass="com.example.lab.ErrorRecovery"
mvn exec:java -Dexec.mainClass="com.example.lab.RetryStrategies"
mvn exec:java -Dexec.mainClass="com.example.lab.TimeoutHandling"
mvn exec:java -Dexec.mainClass="com.example.lab.ResilientOrderService"
```

---

## What's Next?

In Chapter 7, we'll explore **advanced Reactor patterns**—controlling concurrency with flatMap parameters, batching with buffer and window, context propagation for tracing, schedulers for thread control, and testing with StepVerifier. These patterns complete your reactive toolkit.

---

## Summary

In this lab, you:

1. ✅ Understood why try-catch fails in reactive code
2. ✅ Implemented error recovery with `onErrorReturn` and `onErrorResume`
3. ✅ Built retry logic with exponential backoff
4. ✅ Added timeouts and fallbacks
5. ✅ Combined patterns into production-ready resilient pipelines
6. ✅ Used `doOn*` operators for logging and debugging

**Congratulations!** You now have practical experience building resilient reactive services that gracefully handle failures.
