package com.example.reactive.core;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple Publisher that emits a range of integers.
 *
 * This implementation demonstrates the complexity involved in correctly
 * implementing the Reactive Streams specification. Even for this simple case,
 * we need to handle:
 * - Backpressure (only emit what was requested)
 * - Cancellation (stop when subscriber cancels)
 * - Thread safety (request/cancel can be called from any thread)
 * - Error handling (don't emit after error/complete)
 *
 * This is why we use libraries like Project Reactor instead of implementing
 * Publishers ourselves!
 */
public class SimpleRangePublisher implements Flow.Publisher<Integer> {

    private final int start;
    private final int count;

    public SimpleRangePublisher(int start, int count) {
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        // Create a subscription for this subscriber
        RangeSubscription subscription = new RangeSubscription(subscriber, start, count);

        // Per spec: Must call onSubscribe first, before any other signal
        subscriber.onSubscribe(subscription);
    }

    /**
     * The Subscription implementation - this is where the complexity lives.
     */
    private static class RangeSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super Integer> subscriber;
        private final int end;

        private int current;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);

        RangeSubscription(Flow.Subscriber<? super Integer> subscriber, int start, int count) {
            this.subscriber = subscriber;
            this.current = start;
            this.end = start + count;
        }

        @Override
        public void request(long n) {
            // Per spec: request must be positive
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException(
                    "request amount must be positive, was: " + n));
                return;
            }

            // If already cancelled or completed, do nothing
            if (cancelled.get() || completed.get()) {
                return;
            }

            // Add to the requested count
            // Use getAndAdd with overflow protection
            long currentRequested;
            long newRequested;
            do {
                currentRequested = requested.get();
                newRequested = currentRequested + n;
                // Handle overflow - cap at Long.MAX_VALUE
                if (newRequested < 0) {
                    newRequested = Long.MAX_VALUE;
                }
            } while (!requested.compareAndSet(currentRequested, newRequested));

            // Emit items
            emit();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        private void emit() {
            // This is a simplified implementation
            // A production implementation would handle concurrent request() calls
            // more carefully

            while (true) {
                // Check cancellation
                if (cancelled.get()) {
                    return;
                }

                // Check if we've emitted everything
                if (current >= end) {
                    if (completed.compareAndSet(false, true)) {
                        subscriber.onComplete();
                    }
                    return;
                }

                // Check if there's demand
                long currentRequested = requested.get();
                if (currentRequested == 0) {
                    return;  // No demand, wait for more request() calls
                }

                // Decrement requested count
                if (!requested.compareAndSet(currentRequested, currentRequested - 1)) {
                    continue;  // Someone else modified it, retry
                }

                // Emit the item
                int value = current++;

                try {
                    subscriber.onNext(value);
                } catch (Exception e) {
                    // If subscriber throws, cancel and propagate error
                    cancel();
                    subscriber.onError(e);
                    return;
                }
            }
        }
    }

    /**
     * Demo: Shows the publisher in action
     */
    public static void main(String[] args) {
        System.out.println("=== SimpleRangePublisher Demo ===\n");

        Flow.Publisher<Integer> publisher = new SimpleRangePublisher(1, 10);

        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            private int receivedCount = 0;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                System.out.println("Subscribed! Requesting 3 items...");
                s.request(3);  // Start with 3 items
            }

            @Override
            public void onNext(Integer item) {
                System.out.println("  Received: " + item);
                receivedCount++;

                // After receiving 3 items, request 3 more
                if (receivedCount == 3) {
                    System.out.println("\nPausing... (simulating slow processing)");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Ready for more! Requesting 3 more items...");
                    subscription.request(3);
                }
                // After 6, request the rest
                else if (receivedCount == 6) {
                    System.out.println("\nRequesting remaining items (Long.MAX_VALUE)...");
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("\nStream completed! Received " + receivedCount + " items total.");
            }
        });

        System.out.println("\n=== Key Observation ===");
        System.out.println("Notice how the publisher respects our request() calls.");
        System.out.println("It only sends items when we ask for them - that's backpressure!");
    }
}
