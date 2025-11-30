package com.example.reactive.backpressure;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates the difference between systems WITH and WITHOUT backpressure.
 *
 * This is a critical concept in reactive programming!
 *
 * Scenario: A fast producer (1000 items/second) and a slow consumer (10 items/second)
 *
 * Without backpressure: Items pile up in a buffer until memory runs out
 * With backpressure: Consumer controls the flow, producer slows down
 */
public class BackpressureDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           BACKPRESSURE DEMONSTRATION                          ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Producer: 100 items/second (fast)                           ║");
        System.out.println("║  Consumer: 10 items/second (slow)                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Demo 1: Without backpressure (buffer grows unbounded)
        System.out.println("▼ WITHOUT BACKPRESSURE (Unbounded Buffer)");
        System.out.println("─".repeat(50));
        runWithoutBackpressure();

        System.out.println();
        Thread.sleep(1000);

        // Demo 2: With backpressure (consumer controls flow)
        System.out.println("▼ WITH BACKPRESSURE (Controlled Flow)");
        System.out.println("─".repeat(50));
        runWithBackpressure();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                      KEY TAKEAWAYS                            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Without backpressure:                                        ║");
        System.out.println("║    • Buffer grows without limit                              ║");
        System.out.println("║    • Memory usage increases over time                        ║");
        System.out.println("║    • Eventually leads to OutOfMemoryError                    ║");
        System.out.println("║                                                               ║");
        System.out.println("║  With backpressure:                                           ║");
        System.out.println("║    • Buffer stays bounded                                    ║");
        System.out.println("║    • Memory usage is predictable                             ║");
        System.out.println("║    • System remains stable under load                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    /**
     * Demonstrates what happens WITHOUT backpressure.
     * The producer fires at will, and items pile up in a buffer.
     */
    private static void runWithoutBackpressure() throws InterruptedException {
        Queue<Integer> unboundedBuffer = new ConcurrentLinkedQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong produced = new AtomicLong(0);
        AtomicLong consumed = new AtomicLong(0);

        // Fast producer - 100 items per second
        Thread producer = new Thread(() -> {
            int item = 0;
            while (running.get() && item < 500) {
                unboundedBuffer.offer(item++);
                produced.incrementAndGet();
                try {
                    Thread.sleep(10);  // 100 items/second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Producer");

        // Slow consumer - 10 items per second
        Thread consumer = new Thread(() -> {
            while (running.get()) {
                Integer item = unboundedBuffer.poll();
                if (item != null) {
                    consumed.incrementAndGet();
                    // Simulate slow processing
                    try {
                        Thread.sleep(100);  // 10 items/second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "Consumer");

        // Monitor thread
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 10 && running.get(); i++) {
                System.out.printf("  [%ds] Produced: %4d | Consumed: %4d | Buffer size: %4d%n",
                    i, produced.get(), consumed.get(), unboundedBuffer.size());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Monitor");

        producer.start();
        consumer.start();
        monitor.start();

        monitor.join();
        running.set(false);
        producer.join();
        consumer.join();

        System.out.println("  [end] Final buffer size: " + unboundedBuffer.size());
        System.out.println("  ⚠️  Buffer grew to " + unboundedBuffer.size() + " items!");
        System.out.println("  ⚠️  In a real system, this would eventually cause OutOfMemoryError");
    }

    /**
     * Demonstrates what happens WITH backpressure.
     * The consumer controls the flow - producer only sends when consumer requests.
     */
    private static void runWithBackpressure() throws InterruptedException {
        // Using a semaphore to implement backpressure
        // This simulates the request(n) mechanism
        final int MAX_IN_FLIGHT = 5;  // Consumer says "I can handle 5 at a time"
        Semaphore permits = new Semaphore(MAX_IN_FLIGHT);
        BlockingQueue<Integer> boundedBuffer = new ArrayBlockingQueue<>(MAX_IN_FLIGHT);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong produced = new AtomicLong(0);
        AtomicLong consumed = new AtomicLong(0);

        // Producer - wants to be fast but must respect backpressure
        Thread producer = new Thread(() -> {
            int item = 0;
            while (running.get() && item < 500) {
                try {
                    // Wait for permit (backpressure!)
                    // This is like waiting for request(n)
                    permits.acquire();

                    boundedBuffer.put(item++);
                    produced.incrementAndGet();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Producer");

        // Slow consumer - 10 items per second
        Thread consumer = new Thread(() -> {
            while (running.get()) {
                try {
                    Integer item = boundedBuffer.poll(100, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        consumed.incrementAndGet();

                        // Simulate slow processing
                        Thread.sleep(100);  // 10 items/second

                        // Release permit - like calling request(1)
                        permits.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Consumer");

        // Monitor thread
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 10 && running.get(); i++) {
                System.out.printf("  [%ds] Produced: %4d | Consumed: %4d | Buffer size: %4d | Permits: %d%n",
                    i, produced.get(), consumed.get(), boundedBuffer.size(),
                    permits.availablePermits());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Monitor");

        producer.start();
        consumer.start();
        monitor.start();

        monitor.join();
        running.set(false);
        permits.release(100);  // Release to unblock producer
        producer.join();
        consumer.join();

        System.out.println("  [end] Final buffer size: " + boundedBuffer.size());
        System.out.println("  ✓ Buffer stayed bounded at max " + MAX_IN_FLIGHT + " items!");
        System.out.println("  ✓ Memory usage was predictable and controlled");
    }
}
