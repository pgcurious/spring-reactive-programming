package com.example.reactive.pubsub;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Compares three data flow models:
 * 1. PULL (Iterator pattern) - Consumer pulls data
 * 2. PUSH (Observer pattern) - Producer pushes data
 * 3. PUSH-PULL (Reactive Streams) - Hybrid with backpressure
 *
 * This demonstrates why Reactive Streams combines the best of both worlds.
 */
public class PushPullComparison {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        COMPARING: PULL vs PUSH vs PUSH-PULL                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        List<Integer> data = Arrays.asList(1, 2, 3, 4, 5);

        // 1. Pull Model (Iterator)
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  1. PULL MODEL (Iterator Pattern)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        demonstratePull(data);

        Thread.sleep(500);

        // 2. Push Model (Observer)
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  2. PUSH MODEL (Observer Pattern)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        demonstratePush(data);

        Thread.sleep(500);

        // 3. Push-Pull Model (Reactive Streams)
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  3. PUSH-PULL MODEL (Reactive Streams)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        demonstratePushPull(data);

        // Summary
        System.out.println();
        printSummary();
    }

    /**
     * PULL MODEL: Consumer pulls data using Iterator pattern.
     *
     * Characteristics:
     * - Consumer controls the pace (inherent backpressure)
     * - Synchronous and blocking
     * - Cannot handle async data sources
     */
    private static void demonstratePull(List<Integer> data) {
        System.out.println("  Consumer PULLS data:");
        System.out.println();
        System.out.println("    Consumer         Iterator");
        System.out.println("    ────────         ────────");

        Iterator<Integer> iterator = data.iterator();

        while (iterator.hasNext()) {
            System.out.println("    hasNext()? ────► true");
            Integer item = iterator.next();
            System.out.println("    next()     ────► " + item);
            System.out.println("    (process)");
            System.out.println();

            // Simulate slow processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("    hasNext()? ────► false");
        System.out.println("    (done)");
        System.out.println();
        System.out.println("  ✓ Consumer controlled the pace (pulled when ready)");
        System.out.println("  ✗ But: Synchronous only - blocks on each next()");
        System.out.println("  ✗ Cannot handle real-time events or async sources");
        System.out.println();
    }

    /**
     * PUSH MODEL: Producer pushes data using Observer pattern.
     *
     * Characteristics:
     * - Producer controls the pace (no backpressure!)
     * - Asynchronous friendly
     * - Can overwhelm slow consumers
     */
    private static void demonstratePush(List<Integer> data) {
        System.out.println("  Producer PUSHES data:");
        System.out.println();
        System.out.println("    Observable       Observer");
        System.out.println("    ──────────       ────────");

        // Simple observer pattern
        List<Consumer<Integer>> observers = new ArrayList<>();

        // Add an observer (consumer)
        observers.add(item -> {
            System.out.println("    ──────────────► " + item + " (received)");
            System.out.println("                    (must process immediately!)");
            System.out.println();
        });

        // Producer pushes all data immediately
        System.out.println("    (Producer starts pushing...)");
        System.out.println();

        for (Integer item : data) {
            // Push to all observers - no waiting!
            for (Consumer<Integer> observer : observers) {
                observer.accept(item);
            }
            // Producer doesn't wait for consumer
        }

        System.out.println("    (Producer done)");
        System.out.println();
        System.out.println("  ✓ Asynchronous - great for events");
        System.out.println("  ✗ No backpressure - producer fires at will");
        System.out.println("  ✗ Slow consumer would be overwhelmed");
        System.out.println();
    }

    /**
     * PUSH-PULL MODEL: Reactive Streams - best of both worlds.
     *
     * Characteristics:
     * - Consumer controls pace via request(n) - PULL aspect
     * - Producer pushes when items requested - PUSH aspect
     * - Asynchronous with backpressure!
     */
    private static void demonstratePushPull(List<Integer> data) {
        System.out.println("  PUSH-PULL: Consumer requests, Producer delivers:");
        System.out.println();
        System.out.println("    Publisher        Subscriber");
        System.out.println("    ─────────        ──────────");

        // Create a simple publisher
        Flow.Publisher<Integer> publisher = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                private final Iterator<Integer> iterator = data.iterator();
                private final AtomicLong requested = new AtomicLong(0);
                private final AtomicBoolean cancelled = new AtomicBoolean(false);

                @Override
                public void request(long n) {
                    if (cancelled.get()) return;

                    System.out.println("                 ◄── request(" + n + ")  [PULL]");

                    requested.addAndGet(n);

                    // Deliver items up to requested amount
                    while (requested.get() > 0 && iterator.hasNext() && !cancelled.get()) {
                        requested.decrementAndGet();
                        Integer item = iterator.next();
                        System.out.println("    onNext(" + item + ") ──►         [PUSH]");
                        subscriber.onNext(item);
                    }

                    if (!iterator.hasNext() && !cancelled.get()) {
                        System.out.println("    onComplete() ──►");
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };

        // Subscribe with controlled demand
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            private int received = 0;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                System.out.println("    (subscribed)");
                System.out.println();
                // Request just 2 items to start
                s.request(2);
            }

            @Override
            public void onNext(Integer item) {
                received++;
                System.out.println("                     (process: " + item + ")");
                System.out.println();

                // After processing 2 items, request 2 more
                if (received == 2) {
                    System.out.println("    (subscriber ready for more...)");
                    subscription.request(2);
                }
                // After 4, request the rest
                else if (received == 4) {
                    System.out.println("    (subscriber wants remaining...)");
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("                     (done!)");
            }
        });

        System.out.println();
        System.out.println("  ✓ Asynchronous - handles real-time events");
        System.out.println("  ✓ Backpressure - consumer controls pace via request(n)");
        System.out.println("  ✓ Best of both worlds!");
        System.out.println();
    }

    private static void printSummary() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                               ║");
        System.out.println("║  Model      │ Async │ Backpressure │ Real-time Events        ║");
        System.out.println("║  ───────────┼───────┼──────────────┼─────────────────        ║");
        System.out.println("║  Pull       │  No   │  Yes (pull)  │  No                     ║");
        System.out.println("║  Push       │  Yes  │  No          │  Yes                    ║");
        System.out.println("║  Push-Pull  │  Yes  │  Yes         │  Yes                    ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Reactive Streams = Push-Pull = Best of Both Worlds!         ║");
        System.out.println("║                                                               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}
