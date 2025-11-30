package com.example.concurrency.completablefuture_era;

import com.example.concurrency.common.Order;
import com.example.concurrency.common.SimulatedService;
import com.example.concurrency.common.User;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * THE COMPLETABLEFUTURE ERA (Java 8)
 *
 * Java 8's CompletableFuture was revolutionary. It enabled:
 * - Non-blocking composition with thenApply, thenCompose
 * - Parallel execution with thenCombine, allOf
 * - Integrated error handling with exceptionally, handle
 *
 * This is "proto-reactive" - the mindset shift that led to reactive programming.
 *
 * But it still has limitations:
 * - No backpressure
 * - Single value only (not streams)
 * - Limited operators compared to reactive libraries
 * - Not lazy (executes immediately)
 */
public class CompletableFutureExample {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * Demonstrates chaining with thenApply and thenCompose.
     * Compare this to the callback hell in FutureExample!
     */
    public void demonstrateChaining() {
        System.out.println("1. Chaining with thenApply/thenCompose:");
        System.out.println("   Flat, readable code without blocking!\n");

        CompletableFuture<Void> chain = SimulatedService.fetchUserCF(1L)
            .thenCompose(user -> {
                // thenCompose is like flatMap - returns another CompletableFuture
                System.out.println("    Got user: " + user.name() + ", fetching orders...");
                return SimulatedService.fetchOrdersCF(user.id());
            })
            .thenApply(orders -> {
                // thenApply is like map - transforms the value
                System.out.println("    Got " + orders.size() + " orders");
                return orders.size();
            })
            .thenAccept(count -> {
                // thenAccept consumes the value
                System.out.println("    Processed " + count + " orders total");
            });

        System.out.println("    [main] Chain returned immediately (non-blocking)!");
        System.out.println("    [main] Waiting for chain to complete...\n");

        // Wait for completion (just for demo purposes)
        chain.join();

        System.out.println();
        System.out.println("    Compare to callback hell:");
        System.out.println("    - Flat structure (no pyramid)");
        System.out.println("    - Operations chain naturally");
        System.out.println("    - Easy to read and modify\n");
    }

    /**
     * Demonstrates parallel composition with thenCombine and allOf.
     * Independent operations can run in parallel automatically!
     */
    public void demonstrateParallelComposition() {
        System.out.println("2. Parallel Composition:");
        System.out.println("   Independent operations run in parallel!\n");

        long start = System.currentTimeMillis();

        // Fetch user first (others depend on it)
        CompletableFuture<User> userFuture = SimulatedService.fetchUserCF(1L);

        // Then fetch orders and pricing IN PARALLEL
        CompletableFuture<String> result = userFuture.thenCompose(user -> {
            System.out.println("    Got user, starting parallel fetches...");

            // These run in PARALLEL!
            CompletableFuture<List<Order>> ordersFuture =
                SimulatedService.fetchOrdersCF(user.id());
            CompletableFuture<Double> pricingFuture =
                SimulatedService.fetchPricingCF(user.tier());

            // Combine when both are ready
            return ordersFuture.thenCombine(pricingFuture, (orders, discount) -> {
                String summary = String.format(
                    "User: %s, Orders: %d, Discount: %.0f%%",
                    user.name(), orders.size(), discount * 100
                );
                return summary;
            });
        });

        System.out.println("    [main] Not blocking, waiting for result...\n");

        String summary = result.join();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("    Result: " + summary);
        System.out.println("    Total time: " + elapsed + "ms");
        System.out.println("    (Orders and pricing fetched in parallel, saving ~80ms)\n");
    }

    /**
     * Demonstrates integrated error handling with exceptionally and handle.
     */
    public void demonstrateErrorHandling() {
        System.out.println("3. Integrated Error Handling:");
        System.out.println("   Errors flow through the chain naturally\n");

        // Create a chain that might fail
        CompletableFuture<String> chain = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("    Step 1: Starting...");
                return "data";
            }, executor)
            .thenApply(data -> {
                System.out.println("    Step 2: Processing...");
                if (Math.random() > 0.5) {
                    throw new RuntimeException("Random failure!");
                }
                return data.toUpperCase();
            })
            .thenApply(data -> {
                System.out.println("    Step 3: More processing...");
                return data + "!";
            })
            .exceptionally(error -> {
                // This catches errors from ANY step above
                System.out.println("    Error caught: " + error.getMessage());
                return "FALLBACK";
            })
            .thenApply(result -> {
                System.out.println("    Step 4: Final processing with: " + result);
                return "Final: " + result;
            });

        String result = chain.join();
        System.out.println("    Result: " + result);
        System.out.println();
        System.out.println("    Error handling is integrated into the chain!");
        System.out.println("    No scattered try-catch blocks needed.\n");
    }

    /**
     * Demonstrates the limitations of CompletableFuture.
     */
    public void demonstrateLimitations() {
        System.out.println("4. CompletableFuture Limitations:\n");

        // Limitation 1: No backpressure
        System.out.println("    a) No backpressure:");
        System.out.println("       What if producer is faster than consumer?");
        System.out.println("       CompletableFuture can't handle this.\n");

        // Limitation 2: Single value only
        System.out.println("    b) Single value only:");
        System.out.println("       Can't model streams of values over time.");
        System.out.println("       Stock prices, user events, SSE - need Flux!\n");

        // Limitation 3: Not lazy
        System.out.println("    c) Not lazy:");
        CompletableFuture<String> eager = CompletableFuture.supplyAsync(() -> {
            System.out.println("       [Executed immediately!]");
            return "result";
        }, executor);
        System.out.println("       CF executes as soon as created.");
        System.out.println("       Can't build reusable pipelines.\n");

        eager.join();

        // Limitation 4: Limited operators
        System.out.println("    d) Limited operators:");
        System.out.println("       CF has ~30 methods.");
        System.out.println("       Reactor Flux has 400+ operators!");
        System.out.println("       filter, buffer, window, retry, timeout, etc.\n");
    }

    /**
     * Cleanup resources.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
