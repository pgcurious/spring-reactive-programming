package com.example.concurrency.reactive_era;

import com.example.concurrency.common.Order;
import com.example.concurrency.common.SimulatedService;
import com.example.concurrency.common.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE REACTIVE ERA (Java 9+)
 *
 * Reactive Streams (standardized in Java 9's Flow API) and libraries like
 * Project Reactor represent the current state of the art in async programming.
 *
 * Key advantages over CompletableFuture:
 * - BACKPRESSURE: Subscriber controls the flow rate
 * - STREAMING: Handle 0 to N values (not just 0 or 1)
 * - LAZY: Nothing happens until you subscribe
 * - OPERATORS: 400+ operators for complex transformations
 * - SCHEDULERS: Fine-grained control over execution context
 */
public class ReactiveExample {

    /**
     * Demonstrates basic Flux and Mono operations.
     */
    public void demonstrateFluxBasics() throws InterruptedException {
        System.out.println("1. Flux and Mono Basics:");
        System.out.println("   Mono = 0 or 1 element, Flux = 0 to N elements\n");

        // Mono: single value
        System.out.println("    Mono (single value):");
        Mono.just("Hello, Reactive!")
            .map(String::toUpperCase)
            .subscribe(value -> System.out.println("    Received: " + value));

        System.out.println();

        // Flux: multiple values
        System.out.println("    Flux (multiple values):");
        CountDownLatch latch = new CountDownLatch(1);

        Flux.just("Apple", "Banana", "Cherry")
            .map(String::toUpperCase)
            .filter(s -> s.startsWith("B") || s.startsWith("C"))
            .subscribe(
                value -> System.out.println("    Received: " + value),
                error -> System.err.println("    Error: " + error),
                () -> {
                    System.out.println("    Complete!");
                    latch.countDown();
                }
            );

        latch.await(1, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * Demonstrates chaining operations similar to CompletableFuture,
     * but with more flexibility and operators.
     */
    public void demonstrateChaining() throws InterruptedException {
        System.out.println("2. Chaining Operations:");
        System.out.println("   Similar to CompletableFuture but more powerful\n");

        CountDownLatch latch = new CountDownLatch(1);

        SimulatedService.fetchUserReactive(1L)
            .doOnNext(user -> System.out.println("    Got user: " + user.name()))
            .flatMap(user -> SimulatedService.fetchOrdersReactive(user.id())
                .collectList()
                .map(orders -> "User " + user.name() + " has " + orders.size() + " orders"))
            .subscribe(
                result -> System.out.println("    Result: " + result),
                error -> System.err.println("    Error: " + error),
                latch::countDown
            );

        System.out.println("    [main] Not blocking - doing other work...\n");
        latch.await(5, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * Demonstrates BACKPRESSURE - the killer feature of Reactive Streams.
     * The subscriber controls how fast data flows.
     */
    public void demonstrateBackpressure() throws InterruptedException {
        System.out.println("3. Backpressure (The Killer Feature):");
        System.out.println("   Subscriber controls the flow rate!\n");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger(0);

        // Create a fast producer (1000 items)
        Flux<Integer> fastProducer = Flux.range(1, 1000)
            .doOnNext(i -> {
                if (i <= 5 || i == 1000) {
                    System.out.println("    [producer] Emitting: " + i);
                } else if (i == 6) {
                    System.out.println("    [producer] ... (emitting 6-999) ...");
                }
            });

        // Slow consumer with backpressure
        fastProducer
            .onBackpressureBuffer(10) // Buffer up to 10 items
            .limitRate(5) // Request only 5 at a time
            .delayElements(Duration.ofMillis(1)) // Simulate slow processing
            .take(20) // Only take first 20 for demo
            .subscribe(
                value -> {
                    received.incrementAndGet();
                    if (value <= 5 || value == 20) {
                        System.out.println("    [consumer] Processing: " + value);
                    } else if (value == 6) {
                        System.out.println("    [consumer] ... (processing 6-19) ...");
                    }
                },
                error -> {
                    System.err.println("    Error: " + error);
                    latch.countDown();
                },
                () -> {
                    System.out.println("    [consumer] Done! Processed " +
                        received.get() + " items");
                    latch.countDown();
                }
            );

        latch.await(5, TimeUnit.SECONDS);
        System.out.println();
        System.out.println("    With backpressure, fast producers can't overwhelm");
        System.out.println("    slow consumers - no OutOfMemoryError!\n");
    }

    /**
     * Demonstrates LAZINESS - nothing happens until you subscribe.
     * This allows building reusable pipelines.
     */
    public void demonstrateLaziness() throws InterruptedException {
        System.out.println("4. Laziness (Nothing Happens Until Subscribe):");
        System.out.println("   Build pipelines that can be reused\n");

        // Create a pipeline - NO EXECUTION YET
        Mono<String> pipeline = Mono.fromCallable(() -> {
                System.out.println("    [pipeline] Executing expensive operation...");
                Thread.sleep(100);
                return "RESULT";
            })
            .map(s -> {
                System.out.println("    [pipeline] Transforming...");
                return s.toLowerCase();
            });

        System.out.println("    Pipeline created, but NOTHING executed yet!");
        System.out.println("    (Compare to CompletableFuture which executes immediately)\n");

        Thread.sleep(500);

        System.out.println("    Now subscribing...\n");

        CountDownLatch latch = new CountDownLatch(1);
        pipeline.subscribe(
            result -> {
                System.out.println("    Got result: " + result);
                latch.countDown();
            }
        );

        latch.await(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("    Laziness enables:");
        System.out.println("    - Reusable pipeline definitions");
        System.out.println("    - Multiple subscriptions (each gets fresh execution)");
        System.out.println("    - Conditional execution (subscribe only when needed)\n");
    }

    /**
     * Demonstrates the rich operator library.
     */
    public void demonstrateOperators() throws InterruptedException {
        System.out.println("5. Rich Operator Library:\n");

        CountDownLatch latch = new CountDownLatch(1);

        // Just a few examples of the 400+ operators
        Flux.range(1, 100)
            .filter(n -> n % 2 == 0)           // Keep even numbers
            .take(10)                           // First 10
            .buffer(3)                          // Group into batches of 3
            .map(batch -> "Batch: " + batch)    // Transform
            .delayElements(Duration.ofMillis(50)) // Add delay
            .timeout(Duration.ofSeconds(2))     // Timeout after 2s
            .onErrorResume(e -> Flux.just("Fallback")) // Error recovery
            .subscribe(
                result -> System.out.println("    " + result),
                error -> System.err.println("    Error: " + error),
                () -> {
                    System.out.println("    Complete!");
                    latch.countDown();
                }
            );

        latch.await(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("    Other notable operators:");
        System.out.println("    - zip, merge, concat: Combining streams");
        System.out.println("    - retry, retryWhen: Retry logic");
        System.out.println("    - window, groupBy: Stream splitting");
        System.out.println("    - cache, share: Multicast");
        System.out.println("    - And 380+ more!\n");
    }

    /**
     * Demonstrates parallel processing with Schedulers.
     */
    public void demonstrateSchedulers() throws InterruptedException {
        System.out.println("6. Schedulers (Execution Control):\n");

        CountDownLatch latch = new CountDownLatch(1);

        Flux.range(1, 4)
            .doOnNext(i -> System.out.println("    [" +
                Thread.currentThread().getName() + "] Starting item " + i))
            .parallel(2) // Split into 2 rails
            .runOn(reactor.core.scheduler.Schedulers.parallel()) // Run on parallel scheduler
            .map(i -> {
                System.out.println("    [" + Thread.currentThread().getName() +
                    "] Processing item " + i);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return i * 2;
            })
            .sequential() // Merge back
            .subscribe(
                result -> System.out.println("    [" +
                    Thread.currentThread().getName() + "] Result: " + result),
                error -> System.err.println("    Error: " + error),
                () -> {
                    System.out.println("    Complete!");
                    latch.countDown();
                }
            );

        latch.await(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("    Schedulers give you control over WHERE code runs,");
        System.out.println("    without manually managing threads!\n");
    }
}
