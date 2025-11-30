# First Principles: Designing Reactive Streams from Scratch

*Forget that reactive programming exists. Let's derive it from fundamental requirements.*

---

## The Problem Statement

You need to build a system that:
1. Handles **streams of data** (not single values)
2. Is **non-blocking** (no thread sits waiting)
3. Handles **fast producer, slow consumer** (backpressure)
4. Supports **composition** (transformations chain nicely)
5. Has **clear error handling** and **completion signals**

How would you design this?

---

## Step 1: The Data Source

We need something that produces data. Let's call it a **Publisher**.

```
What does a data source need to do?
1. Allow someone to receive data from it

┌─────────────────────────────────────────┐
│  Publisher                              │
│                                         │
│  + subscribe(receiver): void            │
│                                         │
└─────────────────────────────────────────┘
```

That's it for now. A publisher knows how to connect to someone who wants data.

---

## Step 2: The Data Receiver

We need something that receives data. Let's call it a **Subscriber**.

What events might a subscriber care about?

```
1. "Here's an item"       → onNext(item)
2. "Something went wrong" → onError(error)
3. "No more items"        → onComplete()

┌─────────────────────────────────────────┐
│  Subscriber<T>                          │
│                                         │
│  + onNext(T item): void                 │
│  + onError(Throwable): void             │
│  + onComplete(): void                   │
│                                         │
└─────────────────────────────────────────┘
```

**Design decision**: onError and onComplete are **terminal**. After either, no more onNext.

```
Valid sequences:
onNext* onComplete       (zero or more items, then done)
onNext* onError          (zero or more items, then error)

Invalid:
onComplete onNext        ❌ (nothing after terminal)
onError onComplete       ❌ (only one terminal signal)
```

---

## Step 3: The Problem We Haven't Solved

Let's wire them together:

```java
publisher.subscribe(new Subscriber<Data>() {
    void onNext(Data item) {
        // process item (takes 100ms)
    }
});
```

What happens if publisher emits 1000 items/second but processing takes 100ms each?

```
Second 1:
  Publisher emits: 1000 items
  Subscriber processes: 10 items
  Buffer: 990 items (where do they go?!)

Second 2:
  Publisher emits: 1000 more items
  Subscriber processes: 10 items
  Buffer: 1980 items (GROWING!)

This is unsustainable.
```

---

## Step 4: Inventing Backpressure

**Key insight**: The subscriber must control the flow.

How? The subscriber tells the publisher: "Send me N items. I'll ask for more when ready."

But we need a **communication channel** between them. Let's call it a **Subscription**.

```
┌─────────────────────────────────────────┐
│  Subscription                           │
│                                         │
│  + request(long n): void   ← DEMAND     │
│  + cancel(): void          ← STOP       │
│                                         │
└─────────────────────────────────────────┘
```

The subscriber needs to receive this subscription. Let's add a method:

```
┌─────────────────────────────────────────┐
│  Subscriber<T>                          │
│                                         │
│  + onSubscribe(Subscription): void  NEW │
│  + onNext(T item): void                 │
│  + onError(Throwable): void             │
│  + onComplete(): void                   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Step 5: The Complete Protocol

Now let's see how they interact:

```
┌──────────────┐                    ┌──────────────┐
│   Publisher  │                    │  Subscriber  │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │ ─────── subscribe(subscriber) ──▶ │
       │                                   │
       │ ◀─────── onSubscribe(sub) ─────── │
       │                                   │
       │ ◀─────── sub.request(10) ──────── │  "I want 10"
       │                                   │
       │ ─────────── onNext(1) ──────────▶ │
       │ ─────────── onNext(2) ──────────▶ │
       │           ... (10 items)          │
       │                                   │
       │ ◀─────── sub.request(10) ──────── │  "I want 10 more"
       │                                   │
       │ ─────────── onNext(11) ─────────▶ │
       │           ...                     │
       │ ─────────── onComplete() ───────▶ │  "No more data"
       │                                   │
```

**The magic**: Publisher CANNOT emit more than requested. This prevents overflow.

---

## Step 6: The Rules We Must Enforce

From this design, certain rules emerge:

### Rule 1: Request Before Receive
```
Publisher must NOT emit items before subscriber requests.
onNext can only be called after request(n) and within that n.
```

### Rule 2: One at a Time
```
onNext must be called sequentially, not concurrently.
Thread safety is subscriber's responsibility otherwise.
```

### Rule 3: Terminal is Final
```
After onComplete or onError:
- No more onNext
- No more terminal signals
- Subscription is effectively cancelled
```

### Rule 4: Request Accumulation
```
request(5) + request(3) = request for 8 items total
Demand accumulates until satisfied.
```

### Rule 5: Non-Negative
```
request(n) where n <= 0 must signal onError
(What would negative demand even mean?)
```

---

## Step 7: Why This Design Works

### Problem: Fast Producer, Slow Consumer
**Solution**: Consumer requests only what it can handle.

```
Producer speed: 1000 items/sec (potential)
Consumer speed: 10 items/sec (actual processing)

Without backpressure:
  Buffer grows unbounded → OOM

With backpressure:
  Consumer requests 10 → gets 10 → processes → requests 10 more
  Producer waits between batches → no buffer growth
```

### Problem: Memory Safety
**Solution**: Bounded demand means bounded buffers.

```
If subscriber always requests exactly what it can buffer:
  Buffer size is predictable and bounded
  No surprise OOM
```

### Problem: Cancel Mid-Stream
**Solution**: subscription.cancel()

```java
Subscription sub;

onSubscribe(Subscription s) {
    this.sub = s;
    s.request(10);
}

onNext(Data item) {
    if (shouldStop(item)) {
        sub.cancel();  // No more items will come
    }
}
```

---

## Step 8: Adding Transformation (Processor)

What if we want to transform data between publisher and subscriber?

```
Publisher<Integer> ──▶ [double it] ──▶ Subscriber<Integer>
```

We need something that is BOTH a subscriber (receives) AND a publisher (emits).

```
┌─────────────────────────────────────────┐
│  Processor<T, R>                        │
│                                         │
│  extends Subscriber<T>                  │
│  extends Publisher<R>                   │
│                                         │
│  Receives T, transforms, emits R        │
│                                         │
└─────────────────────────────────────────┘
```

Now we can chain:

```
Publisher<A> ──▶ Processor<A,B> ──▶ Processor<B,C> ──▶ Subscriber<C>
```

This is **composition**.

---

## Step 9: The Complete Interface Design

We've derived the Reactive Streams specification:

```java
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

**That's it.** Four interfaces. Everything else is implementation.

---

## Step 10: Why Not Just Use These Directly?

You could implement these interfaces directly. But consider:

```java
// To double every number and filter evens:
new Processor<Integer, Integer>() {
    private Subscription upstream;
    private Subscriber<? super Integer> downstream;

    public void onSubscribe(Subscription s) {
        this.upstream = s;
        // need to handle downstream subscription too
    }

    public void subscribe(Subscriber<? super Integer> s) {
        this.downstream = s;
        // need to create subscription for downstream
    }

    public void onNext(Integer n) {
        Integer doubled = n * 2;
        if (doubled % 2 == 0) {
            downstream.onNext(doubled);
        }
        // need to manage demand properly
    }

    // ... handle errors, completion, cancellation, thread safety...
}
```

This is **tedious and error-prone**. You need:
- Correct demand accounting
- Thread safety
- Error propagation
- Cancellation handling
- Upstream/downstream coordination

---

## Step 11: The Need for a Library

**Key insight**: The interfaces define the CONTRACT. A library provides IMPLEMENTATION.

What a library (like Reactor) gives you:

```java
// Instead of implementing Processor manually:
Flux.range(1, 100)
    .map(n -> n * 2)
    .filter(n -> n % 2 == 0)
    .subscribe(System.out::println);
```

The library handles:
- ✅ Backpressure propagation
- ✅ Thread safety
- ✅ Error handling
- ✅ Cancellation
- ✅ Operator fusion (optimization)
- ✅ Debugging support

You just describe WHAT you want, not HOW to achieve it.

---

## The Mental Model

Think of reactive streams as **plumbing**:

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   [Water Source] ──┬── Pipe ──┬── Pipe ──┬── [Faucet]      │
│    (Publisher)     │          │          │   (Subscriber)   │
│                    │          │          │                  │
│                 [Valve]    [Filter]   [Meter]              │
│               (Processor) (Processor) (Processor)           │
│                                                             │
│   Water only flows when faucet is open (demand)            │
│   Pressure is controlled (backpressure)                    │
│   Each pipe segment transforms/filters                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Principles Derived

### Principle 1: Consumer Controls Flow
The subscriber, not the publisher, determines pace. This is **demand-driven**.

### Principle 2: Bounded Resources
With controlled demand, buffers are bounded, memory is predictable.

### Principle 3: Composition
Processors let you build complex pipelines from simple transformations.

### Principle 4: Async-Agnostic
Nothing in the interfaces requires threading. Async is an implementation choice.

### Principle 5: Contract Over Implementation
The specification defines behavior rules. Libraries provide implementation.

---

## What We've Invented

From first principles, we derived:

1. **Publisher** - Source of data
2. **Subscriber** - Consumer of data with lifecycle callbacks
3. **Subscription** - The backpressure control handle
4. **Processor** - Transformation between streams
5. **The Protocol** - Rules governing their interaction

This IS the Reactive Streams specification. Published in 2015, adopted into Java 9 as `java.util.concurrent.Flow`.

---

## The Reactive Manifesto Connection

Our technical solution supports higher-level goals:

```
┌─────────────────────────────────────────────────────────────┐
│                  REACTIVE SYSTEMS                           │
│                                                             │
│   Responsive ←── Resilient ←── Elastic ←── Message-Driven  │
│       ↑             ↑            ↑              ↑          │
│       │             │            │              │          │
│   "Always      "Recover      "Scale up      "Async         │
│    answers"    from failure"   and down"    communication" │
│                                                             │
│   Backpressure keeps system responsive under load          │
│   Error signals enable resilience                          │
│   Demand-based flow enables elasticity                     │
│   Non-blocking enables message-driven architecture         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Summary: The Derivation Path

```
Problem: Handle data streams
    ↓
Invention: Publisher/Subscriber
    ↓
Problem: Fast producer overwhelms slow consumer
    ↓
Invention: Backpressure via Subscription.request(n)
    ↓
Problem: Need lifecycle signals
    ↓
Invention: onSubscribe, onError, onComplete
    ↓
Problem: Need transformations
    ↓
Invention: Processor
    ↓
Problem: Manual implementation is error-prone
    ↓
Invention: Libraries (Reactor, RxJava)
```

Each step is a **logical necessity**, not arbitrary design.

---

## What Comes Next

Now that we understand reactive streams from first principles:

- **Part II**: Project Reactor - The library that implements these interfaces
- **Part III**: Spring WebFlux - Building web applications with reactive streams
- **Part IV**: Reactive data access - Databases and messaging
- **Part V**: Production concerns - Testing, debugging, monitoring

The foundation is set. Now we build on it.

---

*"The best way to understand something is to derive it yourself."*
