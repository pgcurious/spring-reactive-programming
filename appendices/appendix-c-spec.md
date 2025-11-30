# Appendix C: Reactive Streams Specification Details

This appendix provides a detailed look at the Reactive Streams specification that underlies Project Reactor and all reactive libraries in the Java ecosystem.

## The Four Interfaces

The Reactive Streams specification defines four interfaces:

```java
package org.reactivestreams;

public interface Publisher<T> {
    void subscribe(Subscriber<? super T> s);
}

public interface Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T t);
    void onError(Throwable t);
    void onComplete();
}

public interface Subscription {
    void request(long n);
    void cancel();
}

public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

## Signal Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE STREAMS SIGNAL FLOW                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Publisher                              Subscriber                      │
│       │                                      │                           │
│       │◀─────────── subscribe(s) ───────────│                           │
│       │                                      │                           │
│       │───────── onSubscribe(subscription) ─▶│                           │
│       │                                      │                           │
│       │◀─────────── request(n) ─────────────│                           │
│       │                                      │                           │
│       │───────────── onNext(t) ─────────────▶│  ← Repeated up to n times│
│       │───────────── onNext(t) ─────────────▶│                           │
│       │───────────── onNext(t) ─────────────▶│                           │
│       │                                      │                           │
│       │◀─────────── request(m) ─────────────│  ← Request more           │
│       │                                      │                           │
│       │───────────── onNext(t) ─────────────▶│                           │
│       │                                      │                           │
│       │─────── onComplete() OR onError() ───▶│  ← Terminal signal       │
│       │                                      │                           │
│                                                                          │
│   Alternative: Subscriber cancels                                        │
│       │◀─────────── cancel() ───────────────│                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Rules

### Publisher Rules

| Rule | Description |
|------|-------------|
| 1.1 | Total `onNext` signals MUST be ≤ total requested |
| 1.2 | Publisher MAY signal fewer `onNext` than requested |
| 1.3 | `onSubscribe`, `onNext`, `onError`, `onComplete` MUST be signaled sequentially |
| 1.4 | If Publisher fails, it MUST signal `onError` |
| 1.5 | If Publisher terminates successfully, it MUST signal `onComplete` |
| 1.6 | If Publisher signals `onError` or `onComplete`, the Subscription is cancelled |
| 1.7 | After terminal state, no further signals |
| 1.8 | If Subscription is cancelled, Publisher MUST eventually stop signaling |
| 1.9 | `subscribe` MUST call `onSubscribe` on the Subscriber |

### Subscriber Rules

| Rule | Description |
|------|-------------|
| 2.1 | Subscriber MUST signal demand via `request(n)` to receive `onNext` |
| 2.2 | If Subscriber cannot process signals, it SHOULD cancel |
| 2.3 | `onComplete` and `onError` MUST NOT call any methods on Subscription |
| 2.4 | `onSubscribe` and `onNext` MUST consider Subscription cancelled if they throw |
| 2.5 | Subscriber MUST call `cancel()` after `onSubscribe` if already cancelled |
| 2.6 | Subscriber MUST NOT call `cancel()` before `onSubscribe` returns |
| 2.7 | Subscriber MUST ensure that all calls on its `Subscription` are sequential |
| 2.8 | Subscriber MUST be prepared to receive `onComplete`/`onError` after `cancel()` |
| 2.9 | Subscriber MUST be prepared to receive `onNext` after `cancel()` |

### Subscription Rules

| Rule | Description |
|------|-------------|
| 3.1 | `request` and `cancel` MUST only be called inside `onSubscribe`/`onNext`/`onError` |
| 3.2 | `request` MUST be positive (> 0) |
| 3.3 | `request` MAY be called from within `onNext` |
| 3.4 | `request` SHOULD be asynchronous |
| 3.5 | `cancel` MUST be idempotent |
| 3.6 | After cancel, additional `request` MUST be NOPs |
| 3.7 | After cancel, additional `cancel` MUST be NOPs |
| 3.8 | Subscription is not sharable |
| 3.9 | `cancel` MUST request the Publisher to stop signaling |
| 3.10 | `cancel` MUST clear any references to the Subscriber |
| 3.11 | `request` MAY synchronously call `onNext` on Subscriber |
| 3.12 | `cancel` MUST return normally |
| 3.13 | `request` MUST return normally |

### Processor Rules

| Rule | Description |
|------|-------------|
| 4.1 | A Processor represents a processing stage |
| 4.2 | A Processor MUST obey both Publisher and Subscriber rules |

## Implementing a Simple Publisher

```java
public class SimplePublisher implements Publisher<Integer> {

    private final int count;

    public SimplePublisher(int count) {
        this.count = count;
    }

    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        // Rule 1.9: Must call onSubscribe
        subscriber.onSubscribe(new SimpleSubscription(subscriber, count));
    }

    private static class SimpleSubscription implements Subscription {
        private final Subscriber<? super Integer> subscriber;
        private final int count;
        private int current = 0;
        private long requested = 0;
        private volatile boolean cancelled = false;

        SimpleSubscription(Subscriber<? super Integer> subscriber, int count) {
            this.subscriber = subscriber;
            this.count = count;
        }

        @Override
        public void request(long n) {
            // Rule 3.2: Must be positive
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException(
                    "Rule 3.2: request must be positive, got " + n));
                return;
            }

            // Rule 3.6: After cancel, request is NOP
            if (cancelled) {
                return;
            }

            // Add to requested (handle overflow)
            long newRequested = requested + n;
            if (newRequested < 0) {
                newRequested = Long.MAX_VALUE;
            }
            requested = newRequested;

            // Emit elements
            while (requested > 0 && current < count && !cancelled) {
                // Rule 1.1: Only emit up to requested
                subscriber.onNext(current++);
                requested--;
            }

            // Rule 1.5: Signal complete when done
            if (current >= count && !cancelled) {
                subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {
            // Rule 3.5: Idempotent
            cancelled = true;
        }
    }
}
```

## Implementing a Simple Subscriber

```java
public class SimpleSubscriber implements Subscriber<Integer> {

    private Subscription subscription;
    private final int batchSize;

    public SimpleSubscriber(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        // Rule 2.1: Signal demand
        subscription.request(batchSize);
    }

    @Override
    public void onNext(Integer item) {
        System.out.println("Received: " + item);

        // Request more in batches
        // Rule 3.3: May call request from onNext
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {
        // Rule 2.3: Must not call Subscription methods
        System.err.println("Error: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        // Rule 2.3: Must not call Subscription methods
        System.out.println("Complete!");
    }
}
```

## Backpressure Strategies

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BACKPRESSURE STRATEGIES                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. REQUEST-BASED (Specification default)                              │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Subscriber controls rate via request(n)                     │      │
│   │  Publisher only sends what is requested                      │      │
│   │                                                               │      │
│   │  Subscriber ──request(10)──▶ Publisher                       │      │
│   │  Subscriber ◀──10 items──── Publisher                        │      │
│   │  Subscriber ──request(5)───▶ Publisher                       │      │
│   │  Subscriber ◀──5 items───── Publisher                        │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   2. BUFFERING                                                          │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Buffer items when consumer is slow                          │      │
│   │  Risks: Memory overflow if buffer unbounded                  │      │
│   │                                                               │      │
│   │  Fast Producer ──▶ [Buffer] ──▶ Slow Consumer                │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   3. DROPPING                                                           │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Drop items when consumer can't keep up                      │      │
│   │  Options: Drop oldest, drop newest, drop incoming            │      │
│   │                                                               │      │
│   │  Fast Producer ──▶ [Drop] ──▶ Slow Consumer                  │      │
│   │                      │                                        │      │
│   │                      └──▶ 🗑️ Dropped items                   │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   4. ERROR                                                              │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Signal error when backpressure occurs                       │      │
│   │  Used when data loss is unacceptable                        │      │
│   │                                                               │      │
│   │  Fast Producer ──▶ [Error] ──▶ onError(Overflow)             │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   5. LATEST                                                             │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Keep only the latest item                                   │      │
│   │  Good for "current state" scenarios                          │      │
│   │                                                               │      │
│   │  Fast Producer ──▶ [Latest: X] ──▶ Slow Consumer             │      │
│   │                                   (only gets latest X)       │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Signal Ordering Guarantees

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SIGNAL ORDERING                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Valid signal sequences:                                                │
│                                                                          │
│   onSubscribe → (onNext)* → onComplete                                  │
│   onSubscribe → (onNext)* → onError                                     │
│   onSubscribe → (onNext)* [cancelled]                                   │
│                                                                          │
│   Where (onNext)* means zero or more onNext calls                       │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   Thread Safety:                                                         │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   All signals to a Subscriber MUST be sequential             │      │
│   │   No concurrent calls to onNext, onError, onComplete         │      │
│   │                                                               │      │
│   │   BUT: request() and cancel() may be called from any thread │      │
│   │                                                               │      │
│   │   Publisher is responsible for ensuring signal serialization │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   Invalid sequences (will break the spec):                              │
│                                                                          │
│   ✗ onNext → onSubscribe                                                │
│   ✗ onComplete → onNext                                                 │
│   ✗ onError → onNext                                                    │
│   ✗ onComplete → onComplete                                             │
│   ✗ onError → onError                                                   │
│   ✗ Concurrent onNext calls                                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Reactor's Extensions

Reactor extends the base specification with additional features:

### Fusion

```java
// Macro-fusion: Combine operators into one
Flux.just(1, 2, 3)
    .map(x -> x * 2)
    .filter(x -> x > 2)
// Internally optimized to single traversal

// Micro-fusion: Optimized queue implementations
// Operators can share underlying data structures
```

### Context

```java
// Reactor adds Context for request-scoped data
Mono.deferContextual(ctx -> {
    String traceId = ctx.get("traceId");
    return processWithTrace(traceId);
}).contextWrite(ctx -> ctx.put("traceId", "abc123"));
```

### Additional Signals

```java
// doOnSubscribe, doOnNext, doOnError, doOnComplete
// doOnCancel, doOnTerminate, doFinally
// These are Reactor extensions, not in the spec
```

## Compliance Testing

The Reactive Streams TCK (Technology Compatibility Kit) provides tests:

```java
public class MyPublisherVerificationTest
        extends PublisherVerification<Integer> {

    public MyPublisherVerificationTest() {
        super(new TestEnvironment());
    }

    @Override
    public Publisher<Integer> createPublisher(long elements) {
        return new MyPublisher(elements);
    }

    @Override
    public Publisher<Integer> createFailedPublisher() {
        return new MyFailedPublisher();
    }
}
```

## java.util.concurrent.Flow

Java 9+ includes the specification in the JDK:

```java
// Java 9+ java.util.concurrent.Flow
public final class Flow {
    public static interface Publisher<T> { ... }
    public static interface Subscriber<T> { ... }
    public static interface Subscription { ... }
    public static interface Processor<T,R> extends Subscriber<T>, Publisher<R> { ... }
}

// Adapters to convert between org.reactivestreams and java.util.concurrent.Flow
FlowAdapters.toPublisher(reactiveStreamsPublisher);
FlowAdapters.toFlowPublisher(reactiveStreamsPublisher);
```

## Key Takeaways

1. **Backpressure is built-in**: Subscriber controls the flow rate
2. **Sequential signals**: No concurrent signals to Subscriber
3. **Terminal is terminal**: After onComplete/onError, no more signals
4. **Cancel is eventual**: Publisher should eventually stop after cancel
5. **Request must be positive**: Zero or negative breaks the contract
6. **onSubscribe is mandatory**: Publisher must always call it first

The specification is simple but the guarantees it provides enable building complex, composable, and interoperable reactive systems.
