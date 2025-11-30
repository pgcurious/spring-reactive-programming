package com.example.concurrency.executor_era;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * THE EXECUTOR ERA (Java 5)
 *
 * Java 5 introduced the java.util.concurrent package, which revolutionized
 * how we think about concurrent programming:
 * - Thread pools (no more creating threads manually!)
 * - ExecutorService for task submission
 * - BlockingQueue for producer-consumer (no more wait/notify!)
 * - Concurrent collections
 *
 * BUT: The fundamental blocking nature remained.
 */
public class ExecutorExample {

    /**
     * Demonstrates thread pool usage with ExecutorService.
     * Notice how threads are reused - much more efficient than creating new ones!
     */
    public void demonstrateThreadPool() {
        System.out.println("1. Thread Pool with ExecutorService:");
        System.out.println("   Threads are now pooled and reused...\n");

        // Create a fixed thread pool with 2 threads
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Submit 5 tasks - they'll be processed by 2 threads
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("    [" + Thread.currentThread().getName() +
                    "] Starting task " + taskId);
                try {
                    Thread.sleep(100); // Simulate work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("    [" + Thread.currentThread().getName() +
                    "] Completed task " + taskId);
            });
        }

        // Shutdown gracefully
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("    All tasks completed with thread reuse!\n");
    }

    /**
     * Demonstrates BlockingQueue - the elegant replacement for wait/notify.
     * Compare this to the ThreadExample producer-consumer!
     */
    public void demonstrateBlockingQueue() {
        System.out.println("2. Producer-Consumer with BlockingQueue:");
        System.out.println("   No more wait/notify - BlockingQueue handles it!\n");

        // BlockingQueue handles all the synchronization
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(3);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Producer
        executor.submit(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    System.out.println("    [producer] Producing: " + i);
                    queue.put(i); // Blocks if full - no explicit wait needed!
                    System.out.println("    [producer] Produced: " + i +
                        " | Queue size: " + queue.size());
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer
        executor.submit(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    Integer value = queue.take(); // Blocks if empty - no explicit wait needed!
                    System.out.println("    [consumer] Consumed: " + value +
                        " | Queue size: " + queue.size());
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("    Compare this simplicity to the wait/notify version!\n");
    }

    /**
     * Demonstrates that despite the improvements, blocking remains.
     * The thread is still blocked during the "database call".
     */
    public void demonstrateStillBlocking() {
        System.out.println("3. But Still Blocking:");
        System.out.println("   The thread sits idle during I/O operations...\n");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            System.out.println("    [" + Thread.currentThread().getName() +
                "] Starting 'database call'...");
            try {
                // Simulating a database call
                // The thread is BLOCKED here, doing nothing!
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("    [" + Thread.currentThread().getName() +
                "] 'Database call' complete. Thread was blocked for 500ms.");
        });

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("    The Executor framework helps manage threads,");
        System.out.println("    but doesn't solve the blocking problem!\n");
    }
}
