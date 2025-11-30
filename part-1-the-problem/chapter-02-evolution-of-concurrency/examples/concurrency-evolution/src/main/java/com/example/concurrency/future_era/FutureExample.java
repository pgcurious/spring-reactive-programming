package com.example.concurrency.future_era;

import com.example.concurrency.common.Order;
import com.example.concurrency.common.SimulatedService;
import com.example.concurrency.common.SimulatedService.Callback;
import com.example.concurrency.common.User;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * THE FUTURE ERA (Java 5-7)
 *
 * Java 5 also introduced Future<T> - a way to get results from async operations.
 * This was a step forward, but had a critical flaw: Future.get() BLOCKS!
 *
 * To avoid blocking, developers used callbacks. This led to "Callback Hell".
 */
public class FutureExample {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * Demonstrates Future and Callable.
     * Shows that while we can submit work asynchronously, getting the result blocks!
     */
    public void demonstrateFuture() {
        System.out.println("1. Future and Callable:");
        System.out.println("   We can submit work async, but Future.get() BLOCKS!\n");

        // Submit async task
        Future<User> userFuture = executor.submit(() -> {
            System.out.println("    [" + Thread.currentThread().getName() +
                "] Fetching user...");
            Thread.sleep(100);
            return new User(1L, "Alice", "PREMIUM");
        });

        System.out.println("    [main] Future returned immediately");
        System.out.println("    [main] Doing other work...");

        // But now we need the result
        try {
            System.out.println("    [main] Calling userFuture.get() - BLOCKING!");
            long start = System.currentTimeMillis();

            User user = userFuture.get(); // THIS BLOCKS!

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("    [main] Got user after blocking for " + elapsed + "ms: " + user);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println();
        System.out.println("    The problem: We moved work to another thread,");
        System.out.println("    but our thread still blocks waiting for the result!\n");
    }

    /**
     * Demonstrates sequential Future.get() calls - the problem compounds.
     */
    public void demonstrateSequentialFutures() {
        System.out.println("2. Sequential Future.get() calls:");
        System.out.println("   Chaining operations requires multiple blocking calls!\n");

        try {
            long start = System.currentTimeMillis();

            // Step 1: Fetch user
            Future<User> userFuture = executor.submit(() -> {
                Thread.sleep(100);
                return new User(1L, "Alice", "PREMIUM");
            });

            User user = userFuture.get(); // BLOCKS!
            System.out.println("    Got user: " + user);

            // Step 2: Fetch orders (depends on user)
            Future<List<Order>> ordersFuture = executor.submit(() -> {
                Thread.sleep(150);
                return List.of(new Order(1L, user.id()), new Order(2L, user.id()));
            });

            List<Order> orders = ordersFuture.get(); // BLOCKS AGAIN!
            System.out.println("    Got orders: " + orders);

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("    Total time: " + elapsed + "ms (spent blocked most of it)\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Demonstrates "Callback Hell" - the workaround for blocking Futures.
     * This avoids blocking but creates deeply nested, hard-to-read code.
     */
    public void demonstrateCallbackHell() {
        System.out.println("3. Callback Hell (the workaround):");
        System.out.println("   Avoiding Future.get() by using callbacks...\n");

        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("    [main] Starting async operations with callbacks...\n");

        // Callback Hell begins...
        SimulatedService.fetchUserAsync(1L, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                System.out.println("    [callback-1] Got user: " + user.name());

                // Nested callback for orders
                SimulatedService.fetchOrdersAsync(user.id(), new Callback<List<Order>>() {
                    @Override
                    public void onSuccess(List<Order> orders) {
                        System.out.println("    [callback-2] Got " + orders.size() + " orders");

                        // Could nest even deeper...
                        // fetchRecommendations(user, new Callback<List<Product>>() {
                        //     public void onSuccess(List<Product> recs) {
                        //         fetchPricing(user.getTier(), new Callback<Pricing>() {
                        //             public void onSuccess(Pricing pricing) {
                        //                 // Finally can do something!
                        //             }
                        //         });
                        //     }
                        // });

                        System.out.println("    [callback-2] Imagine more nesting here...");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        System.err.println("    [callback-2] Error: " + e.getMessage());
                        latch.countDown();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                System.err.println("    [callback-1] Error: " + e.getMessage());
                latch.countDown();
            }
        });

        System.out.println("    [main] Not blocking! But look at that nested code...\n");

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println();
        System.out.println("    Callback Hell problems:");
        System.out.println("    - Hard to read (pyramid of doom)");
        System.out.println("    - Error handling scattered everywhere");
        System.out.println("    - Hard to compose operations");
        System.out.println("    - Hard to add timeout/retry logic\n");
    }

    /**
     * Cleanup resources.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
