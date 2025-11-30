# Lab 3: Building Publisher and Subscriber from Scratch

## Objective

In this lab, you'll implement the core reactive patterns yourself to deeply understand:
1. The Publisher-Subscriber contract
2. How backpressure works at the protocol level
3. Why implementing these correctly is complex
4. Why we use libraries like Project Reactor

By the end, you'll appreciate both the elegance of reactive streams and the wisdom of using battle-tested implementations.

---

## Prerequisites

- Java 17+ installed
- Understanding of Chapter 3 concepts
- Basic familiarity with threads and concurrent programming

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|---------------|
| 1 | Understanding the Reactive Streams Interfaces | 10 min |
| 2 | Implement a Simple Publisher | 25 min |
| 3 | Implement a Controlled Subscriber | 20 min |
| 4 | Wire Them Together and Observe | 15 min |
| 5 | Experience Backpressure | 20 min |
| 6 | Reflection and Key Takeaways | 10 min |

**Total: ~100 minutes**

---

## Part 1: Understanding the Reactive Streams Interfaces (10 min)

Before implementing, let's review the four interfaces. In Java 9+, these are in `java.util.concurrent.Flow`:

### The Interfaces

```java
// 1. Publisher: Source of data
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

// 2. Subscriber: Consumer of data
public interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T item);
    void onError(Throwable throwable);
    void onComplete();
}

// 3. Subscription: The link between Publisher and Subscriber
public interface Subscription {
    void request(long n);
    void cancel();
}

// 4. Processor: Both Subscriber and Publisher (transformation)
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

### The Protocol

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE REACTIVE STREAMS PROTOCOL                           │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Subscriber calls publisher.subscribe(subscriber)                      │
│  2. Publisher calls subscriber.onSubscribe(subscription)                  │
│  3. Subscriber calls subscription.request(n) to request n items           │
│  4. Publisher calls subscriber.onNext(item) up to n times                 │
│  5. Repeat steps 3-4 as needed                                            │
│  6. Publisher calls subscriber.onComplete() when done                     │
│     OR subscriber.onError(e) if something goes wrong                      │
│                                                                            │
│  Rules:                                                                    │
│  • Never send more items than requested                                   │
│  • onComplete/onError are terminal (nothing after)                        │
│  • Must handle concurrent request() calls safely                          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Exercise 1.1: Trace the Protocol

Draw a sequence diagram for this scenario:
- Subscriber subscribes
- Subscriber requests 3 items
- Publisher sends 3 items
- Subscriber requests 2 more items
- Publisher sends 1 item and completes (only 1 left)

---

## Part 2: Implement a Simple Publisher (25 min)

Now let's implement a Publisher that emits integers from a range.

### Exercise 2.1: Complete the Implementation

Create a file `SimplePublisher.java`:

```java
package com.example.lab;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple Publisher that emits integers from start to (start + count - 1).
 *
 * TODO: Complete the implementation following the Reactive Streams spec.
 */
public class SimplePublisher implements Flow.Publisher<Integer> {

    private final int start;
    private final int count;

    public SimplePublisher(int start, int count) {
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        // TODO: Create a SimpleSubscription and pass it to subscriber.onSubscribe()
    }

    /**
     * The Subscription implementation.
     */
    private class SimpleSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super Integer> subscriber;

        // Current position in the range
        private int current;

        // Total items requested but not yet delivered
        private final AtomicLong requested = new AtomicLong(0);

        // Flag to indicate cancellation
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        // Flag to indicate completion
        private final AtomicBoolean completed = new AtomicBoolean(false);

        SimpleSubscription(Flow.Subscriber<? super Integer> subscriber) {
            this.subscriber = subscriber;
            this.current = start;
        }

        @Override
        public void request(long n) {
            // TODO: Implement request logic
            // 1. Validate n > 0 (per spec, must signal error if not)
            // 2. Add n to requested count (handle overflow)
            // 3. Call emit() to deliver items
        }

        @Override
        public void cancel() {
            // TODO: Set cancelled flag
        }

        /**
         * Emits items to the subscriber based on demand.
         */
        private void emit() {
            // TODO: Implement emission logic
            // While there's demand and items to emit:
            // 1. Check if cancelled
            // 2. Check if we've emitted everything
            // 3. Check if there's demand (requested > 0)
            // 4. Decrement requested, increment current
            // 5. Call subscriber.onNext(value)
            // 6. If done, call subscriber.onComplete()
        }
    }

    public static void main(String[] args) {
        // Test your implementation
        SimplePublisher publisher = new SimplePublisher(1, 5);

        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                System.out.println("Subscribed! Requesting 2 items...");
                s.request(2);
            }

            @Override
            public void onNext(Integer item) {
                System.out.println("Received: " + item);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("Complete!");
            }
        });
    }
}
```

### Hints

1. In `subscribe()`:
   ```java
   SimpleSubscription subscription = new SimpleSubscription(subscriber);
   subscriber.onSubscribe(subscription);
   ```

2. In `request(long n)`:
   ```java
   if (n <= 0) {
       cancel();
       subscriber.onError(new IllegalArgumentException("request must be > 0"));
       return;
   }
   // Add to requested (with overflow protection)
   // Call emit()
   ```

3. In `emit()`:
   ```java
   while (!cancelled.get() && requested.get() > 0 && current < start + count) {
       requested.decrementAndGet();
       subscriber.onNext(current++);
   }
   if (!cancelled.get() && current >= start + count && !completed.getAndSet(true)) {
       subscriber.onComplete();
   }
   ```

### Expected Output

```
Subscribed! Requesting 2 items...
Received: 1
Received: 2
```

Notice that only 2 items are received because we only requested 2!

---

## Part 3: Implement a Controlled Subscriber (20 min)

Now let's implement a Subscriber that demonstrates backpressure.

### Exercise 3.1: Complete the Implementation

Create a file `ControlledSubscriber.java`:

```java
package com.example.lab;

import java.util.concurrent.Flow;

/**
 * A Subscriber that requests items in batches, demonstrating backpressure.
 *
 * TODO: Complete the implementation.
 */
public class ControlledSubscriber<T> implements Flow.Subscriber<T> {

    private final String name;
    private final int batchSize;
    private final long processingTimeMs;

    private Flow.Subscription subscription;
    private int receivedInBatch = 0;
    private int totalReceived = 0;

    /**
     * Creates a controlled subscriber.
     *
     * @param name            Identifier for logging
     * @param batchSize       How many items to request at a time
     * @param processingTimeMs Simulated processing time per item
     */
    public ControlledSubscriber(String name, int batchSize, long processingTimeMs) {
        this.name = name;
        this.batchSize = batchSize;
        this.processingTimeMs = processingTimeMs;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        // TODO: Store the subscription and request the first batch
        System.out.println("[" + name + "] Subscribed. Requesting " + batchSize + " items.");
    }

    @Override
    public void onNext(T item) {
        // TODO: Process the item
        // 1. Increment counters
        // 2. Simulate processing time
        // 3. If batch is complete, request next batch

        System.out.println("[" + name + "] Processing: " + item);

        // Simulate slow processing
        if (processingTimeMs > 0) {
            try {
                Thread.sleep(processingTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                subscription.cancel();
                return;
            }
        }

        // TODO: Check if batch is complete and request more
    }

    @Override
    public void onError(Throwable t) {
        System.err.println("[" + name + "] Error: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        System.out.println("[" + name + "] Complete! Total received: " + totalReceived);
    }

    /**
     * Allows external cancellation.
     */
    public void cancel() {
        if (subscription != null) {
            System.out.println("[" + name + "] Cancelling...");
            subscription.cancel();
        }
    }
}
```

### Expected Behavior

When you use batch size of 3 with a publisher of 10 items:
```
[Subscriber] Subscribed. Requesting 3 items.
[Subscriber] Processing: 1
[Subscriber] Processing: 2
[Subscriber] Processing: 3
[Subscriber] Batch complete. Requesting 3 more.
[Subscriber] Processing: 4
...
```

---

## Part 4: Wire Them Together (15 min)

### Exercise 4.1: Connect Publisher and Subscriber

Create a file `ReactiveDemo.java`:

```java
package com.example.lab;

/**
 * Demonstrates Publisher and Subscriber working together.
 */
public class ReactiveDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Reactive Streams Demo ===\n");

        // Create a publisher that emits 1-10
        SimplePublisher publisher = new SimplePublisher(1, 10);

        // Create a controlled subscriber (batch size 3, 100ms processing)
        ControlledSubscriber<Integer> subscriber =
            new ControlledSubscriber<>("SlowConsumer", 3, 100);

        // Connect them
        publisher.subscribe(subscriber);

        // Wait for processing
        Thread.sleep(2000);

        System.out.println("\n=== Demo Complete ===");
    }
}
```

### Observation Questions

1. How many items are delivered in each batch?
2. What controls the pace of delivery?
3. What would happen if processing time was 0?

---

## Part 5: Experience Backpressure (20 min)

### Exercise 5.1: Without Backpressure

Modify your subscriber to request `Long.MAX_VALUE` (unbounded demand):

```java
@Override
public void onSubscribe(Flow.Subscription s) {
    this.subscription = s;
    s.request(Long.MAX_VALUE);  // No backpressure!
}
```

Run the demo. What happens?

### Exercise 5.2: Simulate Fast Producer, Slow Consumer

Create a scenario where:
- Publisher has 100 items
- Subscriber processes 1 item per 100ms
- Compare with and without batched requests

### Exercise 5.3: Measure the Difference

Create a test that measures:
1. Memory usage (buffer size) with unbounded demand
2. Memory usage with batched demand

```java
public class BackpressureTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Backpressure Impact Demo ===\n");

        // Scenario: Fast publisher (1000 items), slow subscriber (10 items/sec)

        // Test 1: Unbounded demand (no backpressure)
        System.out.println("--- Without Backpressure ---");
        testWithoutBackpressure();

        Thread.sleep(1000);

        // Test 2: Batched demand (with backpressure)
        System.out.println("\n--- With Backpressure ---");
        testWithBackpressure();
    }

    private static void testWithoutBackpressure() {
        // TODO: Implement - subscriber requests Long.MAX_VALUE
        // Track how many items are "in flight" (delivered but not processed)
    }

    private static void testWithBackpressure() {
        // TODO: Implement - subscriber requests in small batches
        // Track how many items are "in flight"
    }
}
```

---

## Part 6: Reflection and Key Takeaways (10 min)

### Discussion Questions

1. **What was the hardest part of implementing the Publisher?**
   - Thread safety?
   - Tracking requested count?
   - Handling edge cases?

2. **Why do you think the spec uses `long` for request count instead of `int`?**

3. **What would happen if you called `onNext` without checking `requested > 0`?**

4. **In a real system, where would the complexity come from?**
   - Async execution?
   - Error handling?
   - Resource cleanup?

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. The Protocol is Simple, Implementation is Complex                     │
│     • Four interfaces, many rules                                          │
│     • Thread safety is critical                                            │
│     • Edge cases are subtle                                                │
│                                                                            │
│  2. Backpressure is the Key Innovation                                    │
│     • request(n) controls the flow                                        │
│     • Prevents memory overflow                                            │
│     • Consumer drives the pace                                            │
│                                                                            │
│  3. This is Why We Use Libraries                                          │
│     • Project Reactor, RxJava implement this correctly                    │
│     • Battle-tested, optimized, full-featured                             │
│     • We can focus on business logic                                      │
│                                                                            │
│  4. Understanding the Fundamentals Helps                                  │
│     • Debug issues more effectively                                       │
│     • Make better design decisions                                        │
│     • Appreciate what the library does for us                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

If you finish early, try these:

### Challenge 1: Implement a Transformation Processor

Create a `Processor<Integer, String>` that converts integers to strings:

```java
public class ToStringProcessor implements Flow.Processor<Integer, String> {
    // TODO: Implement
}
```

### Challenge 2: Implement Error Handling

Modify your publisher to:
1. Throw an error after emitting 5 items
2. Ensure `onError` is called and no more items are emitted

### Challenge 3: Implement Cancellation

Create a subscriber that cancels after receiving 3 items. Verify that:
1. No more items are delivered after cancellation
2. `onComplete` is not called

---

## Running the Examples

From the `examples/reactive-concepts` directory:

```bash
# Compile
mvn compile

# Run a specific example
mvn exec:java -Dexec.mainClass="com.example.reactive.core.SimpleRangePublisher"
mvn exec:java -Dexec.mainClass="com.example.reactive.backpressure.BackpressureDemo"
mvn exec:java -Dexec.mainClass="com.example.reactive.pubsub.PushPullComparison"
mvn exec:java -Dexec.mainClass="com.example.reactive.pubsub.ReactiveStreamsDemo"
```

---

## Solution Files

Complete solutions are available in the `examples/reactive-concepts` directory:
- `SimpleRangePublisher.java` - Complete Publisher implementation
- `PrintSubscriber.java` - Complete Subscriber implementation
- `BackpressureDemo.java` - Backpressure demonstration
- `PushPullComparison.java` - Comparison of data flow models
- `ReactiveStreamsDemo.java` - Using Java's Flow API

---

## What's Next?

In Part II of the book, we'll use **Project Reactor**, which provides:
- `Flux<T>` and `Mono<T>` - powerful Publisher implementations
- 400+ operators for transformation, filtering, combining
- Built-in error handling and retry mechanisms
- Context propagation
- Testing utilities

You'll never implement Publisher/Subscriber yourself in production—but now you understand what's happening under the hood!

---

## Summary

In this lab, you:

1. ✅ Understood the Reactive Streams interfaces
2. ✅ Implemented a simple Publisher
3. ✅ Implemented a controlled Subscriber
4. ✅ Wired them together
5. ✅ Experienced backpressure firsthand
6. ✅ Appreciated why we use libraries

**Congratulations!** You now have a deep understanding of reactive programming fundamentals. This knowledge will serve you well as you move into Project Reactor and Spring WebFlux in the upcoming chapters.
