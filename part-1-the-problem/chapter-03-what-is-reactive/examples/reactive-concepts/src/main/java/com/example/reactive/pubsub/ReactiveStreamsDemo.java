package com.example.reactive.pubsub;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Demonstrates the Reactive Streams API using Java 9+ Flow API.
 *
 * The java.util.concurrent.Flow class contains the Reactive Streams interfaces:
 * - Flow.Publisher<T>
 * - Flow.Subscriber<T>
 * - Flow.Subscription
 * - Flow.Processor<T, R>
 *
 * This example uses SubmissionPublisher, which is a JDK-provided Publisher implementation.
 * It shows how the four interfaces work together.
 */
public class ReactiveStreamsDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           REACTIVE STREAMS SPECIFICATION DEMO                 ║");
        System.out.println("║                                                               ║");
        System.out.println("║   Using java.util.concurrent.Flow (Java 9+)                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Demo 1: Basic Publisher-Subscriber interaction
        System.out.println("─── Demo 1: Basic Publisher-Subscriber ───");
        basicDemo();

        Thread.sleep(1000);
        System.out.println();

        // Demo 2: Multiple Subscribers
        System.out.println("─── Demo 2: Multiple Subscribers ───");
        multipleSubscribersDemo();

        Thread.sleep(1000);
        System.out.println();

        // Demo 3: The Signal Flow
        System.out.println("─── Demo 3: Signal Flow Visualization ───");
        signalFlowDemo();

        Thread.sleep(1000);
    }

    /**
     * Basic demo of Publisher-Subscriber interaction.
     */
    private static void basicDemo() throws InterruptedException {
        // SubmissionPublisher is a JDK-provided Publisher implementation
        // It handles all the complexity of backpressure, thread-safety, etc.
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

        // Create a subscriber
        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                System.out.println("  [Subscriber] Received subscription");
                this.subscription = s;
                s.request(1);  // Request first item
            }

            @Override
            public void onNext(String item) {
                System.out.println("  [Subscriber] Received: " + item);
                subscription.request(1);  // Request next item
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("  [Subscriber] Error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("  [Subscriber] Complete!");
            }
        };

        // Subscribe
        publisher.subscribe(subscriber);

        // Publish some items
        System.out.println("  [Publisher] Submitting items...");
        publisher.submit("Hello");
        publisher.submit("Reactive");
        publisher.submit("World");

        // Close the publisher (signals onComplete to subscribers)
        Thread.sleep(100);
        publisher.close();

        Thread.sleep(500);
    }

    /**
     * Demo showing multiple subscribers receiving the same data.
     */
    private static void multipleSubscribersDemo() throws InterruptedException {
        SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>();

        // Create two subscribers
        publisher.subscribe(createNamedSubscriber("Subscriber-A"));
        publisher.subscribe(createNamedSubscriber("Subscriber-B"));

        System.out.println("  [Publisher] Publishing to multiple subscribers...");

        publisher.submit(1);
        Thread.sleep(50);
        publisher.submit(2);
        Thread.sleep(50);
        publisher.submit(3);

        Thread.sleep(100);
        publisher.close();

        Thread.sleep(500);
    }

    /**
     * Demo showing the complete signal flow in Reactive Streams.
     */
    private static void signalFlowDemo() throws InterruptedException {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │                 REACTIVE STREAMS SIGNALS                 │");
        System.out.println("  │                                                          │");
        System.out.println("  │  Publisher                          Subscriber           │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      │◄────── subscribe() ──────────────┤                │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      ├─────── onSubscribe(sub) ────────►│                │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      │◄────── request(n) ───────────────┤                │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      ├─────── onNext(item) ────────────►│                │");
        System.out.println("  │      ├─────── onNext(item) ────────────►│                │");
        System.out.println("  │      │         ...                      │                │");
        System.out.println("  │      │◄────── request(n) ───────────────┤                │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      ├─────── onNext(item) ────────────►│                │");
        System.out.println("  │      │                                  │                │");
        System.out.println("  │      ├─────── onComplete() ────────────►│                │");
        System.out.println("  │      │            OR                    │                │");
        System.out.println("  │      ├─────── onError(err) ────────────►│                │");
        System.out.println("  │                                                          │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  Live demonstration:");
        System.out.println();

        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            private int requestCount = 0;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                System.out.println("  ├──────► onSubscribe() called");
                this.subscription = s;
                System.out.println("  │◄────── request(2)");
                s.request(2);
            }

            @Override
            public void onNext(String item) {
                requestCount++;
                System.out.println("  ├──────► onNext(\"" + item + "\")");

                if (requestCount == 2) {
                    System.out.println("  │◄────── request(2)");
                    subscription.request(2);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("  ├──────► onError(" + t.getMessage() + ")");
            }

            @Override
            public void onComplete() {
                System.out.println("  └──────► onComplete()");
            }
        });

        Thread.sleep(50);

        System.out.println("  [Publisher submitting items...]");
        publisher.submit("First");
        Thread.sleep(50);
        publisher.submit("Second");
        Thread.sleep(50);
        publisher.submit("Third");
        Thread.sleep(50);
        publisher.submit("Fourth");
        Thread.sleep(50);

        System.out.println("  [Publisher closing...]");
        publisher.close();

        Thread.sleep(500);
    }

    private static Flow.Subscriber<Integer> createNamedSubscriber(String name) {
        return new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);  // Unbounded demand
            }

            @Override
            public void onNext(Integer item) {
                System.out.println("  [" + name + "] Received: " + item);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("  [" + name + "] Error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("  [" + name + "] Complete");
            }
        };
    }
}
