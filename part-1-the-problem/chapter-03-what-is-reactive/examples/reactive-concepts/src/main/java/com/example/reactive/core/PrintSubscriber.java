package com.example.reactive.core;

import java.util.concurrent.Flow;

/**
 * A simple Subscriber that prints received items.
 *
 * This demonstrates the Subscriber contract:
 * - onSubscribe: Called first with a Subscription
 * - onNext: Called for each item (0 to N times)
 * - onError: Called if an error occurs (terminates the stream)
 * - onComplete: Called when the stream is done (terminates the stream)
 *
 * Key insight: The subscriber controls the pace through request(n).
 */
public class PrintSubscriber<T> implements Flow.Subscriber<T> {

    private final String name;
    private final long batchSize;
    private final long delayMillis;

    private Flow.Subscription subscription;
    private long received = 0;
    private long totalReceived = 0;

    /**
     * Creates a subscriber that requests items in batches.
     *
     * @param name       Name for logging
     * @param batchSize  How many items to request at a time
     * @param delayMillis Simulated processing delay per item (to show backpressure)
     */
    public PrintSubscriber(String name, long batchSize, long delayMillis) {
        this.name = name;
        this.batchSize = batchSize;
        this.delayMillis = delayMillis;
    }

    /**
     * Creates a subscriber with default settings.
     */
    public PrintSubscriber(String name) {
        this(name, Long.MAX_VALUE, 0);
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        this.subscription = s;
        System.out.println("[" + name + "] Subscribed. Requesting " +
            (batchSize == Long.MAX_VALUE ? "unlimited" : batchSize) + " items.");

        // Initial request - tell the publisher how many items we want
        s.request(batchSize);
    }

    @Override
    public void onNext(T item) {
        totalReceived++;
        received++;

        // Simulate processing time
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                subscription.cancel();
                return;
            }
        }

        System.out.println("[" + name + "] Received (#" + totalReceived + "): " + item);

        // If we're using batches and have received a batch, request more
        if (batchSize != Long.MAX_VALUE && received >= batchSize) {
            received = 0;
            System.out.println("[" + name + "] Batch complete. Requesting " + batchSize + " more...");
            subscription.request(batchSize);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        System.err.println("[" + name + "] ERROR: " + throwable.getMessage());
    }

    @Override
    public void onComplete() {
        System.out.println("[" + name + "] COMPLETE. Total received: " + totalReceived);
    }

    /**
     * Cancel the subscription.
     */
    public void cancel() {
        if (subscription != null) {
            System.out.println("[" + name + "] Cancelling subscription...");
            subscription.cancel();
        }
    }

    /**
     * Demo: Shows different subscriber behaviors
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== PrintSubscriber Demo ===\n");

        // Demo 1: Fast subscriber with unlimited demand
        System.out.println("--- Demo 1: Unlimited Demand ---");
        Flow.Publisher<Integer> publisher1 = new SimpleRangePublisher(1, 5);
        publisher1.subscribe(new PrintSubscriber<>("FastSubscriber"));

        Thread.sleep(100);
        System.out.println();

        // Demo 2: Slow subscriber with batch demand (backpressure in action)
        System.out.println("--- Demo 2: Batch Demand (Backpressure) ---");
        Flow.Publisher<Integer> publisher2 = new SimpleRangePublisher(1, 6);
        publisher2.subscribe(new PrintSubscriber<>("SlowSubscriber", 2, 100));

        Thread.sleep(1000);
        System.out.println();

        // Demo 3: Cancellation
        System.out.println("--- Demo 3: Cancellation ---");
        Flow.Publisher<Integer> publisher3 = new SimpleRangePublisher(1, 100);
        PrintSubscriber<Integer> cancellingSubscriber = new PrintSubscriber<>("CancellingSubscriber", 3, 50);
        publisher3.subscribe(cancellingSubscriber);

        Thread.sleep(200);
        cancellingSubscriber.cancel();
        System.out.println("Cancelled! No more items should be received.");

        Thread.sleep(500);
        System.out.println("\n=== Demo Complete ===");
    }
}
