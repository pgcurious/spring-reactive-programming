# First Principles: Deriving Stream Operators from Necessity

*Forget that operators exist. Let's discover why we need them and how they must work.*

---

## The Problem

We have a reactive library with Publishers and Subscribers. Data flows. But raw data is rarely useful—we need to transform it.

Without operators, every transformation requires implementing custom Publishers and Subscribers. Let's see why that's painful and derive a better way.

---

## Step 1: The Raw Approach

Suppose we have a `Flux<Integer>` and want to double each number.

```java
// Without operators, we'd write a custom subscriber:
Flux<Integer> source = Flux.range(1, 5);

source.subscribe(new BaseSubscriber<Integer>() {
    @Override
    protected void hookOnNext(Integer value) {
        int doubled = value * 2;  // Transform here
        System.out.println(doubled);
    }
});
```

This works for simple cases, but what if we want to:
1. Double each number, THEN
2. Filter to keep only those > 5, THEN
3. Convert to strings?

```java
// Messy: All logic crammed into one subscriber
source.subscribe(new BaseSubscriber<Integer>() {
    @Override
    protected void hookOnNext(Integer value) {
        int doubled = value * 2;
        if (doubled > 5) {
            String result = String.valueOf(doubled);
            System.out.println(result);
        }
    }
});
```

This doesn't compose. We can't reuse the "double" logic separately from the "filter" logic.

---

## Step 2: The Decorator Insight

**Key insight**: What if each transformation was a Publisher that wraps another Publisher?

```
┌─────────────────────────────────────────────────────────────────┐
│  DECORATOR PATTERN FOR STREAMS                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Instead of:                                                   │
│  [Source] ──────────────────────────────────────► [Consumer]  │
│                      (all logic here)                          │
│                                                                 │
│  We have:                                                      │
│  [Source] ──► [Decorator A] ──► [Decorator B] ──► [Consumer]  │
│               (double)          (filter)                       │
│                                                                 │
│  Each decorator:                                               │
│  • Is a Publisher (downstream subscribes to it)                │
│  • Is a Subscriber (it subscribes to upstream)                 │
│  • Adds its specific transformation                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 3: Deriving the map Operator

Let's invent `map` from scratch.

### Requirements
1. Take a function `T → R`
2. Apply it to each element
3. Pass transformed elements downstream

### Pseudo-implementation

```java
class MapOperator<T, R> implements Publisher<R> {
    private final Publisher<T> upstream;
    private final Function<T, R> mapper;

    MapOperator(Publisher<T> upstream, Function<T, R> mapper) {
        this.upstream = upstream;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Subscriber<? super R> downstream) {
        // When someone subscribes to us, we subscribe to upstream
        upstream.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                // Pass subscription through
                downstream.onSubscribe(s);
            }

            @Override
            public void onNext(T item) {
                // TRANSFORM and forward
                R result = mapper.apply(item);
                downstream.onNext(result);
            }

            @Override
            public void onError(Throwable t) {
                downstream.onError(t);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        });
    }
}
```

### Making it Fluent

Add a method to Flux:

```java
class Flux<T> implements Publisher<T> {
    public <R> Flux<R> map(Function<T, R> mapper) {
        return new MapOperator<>(this, mapper);
    }
}
```

Now we can write:

```java
Flux.range(1, 5)
    .map(i -> i * 2)
    .map(i -> "Value: " + i);
```

Each `map()` creates a new MapOperator wrapping the previous Flux.

---

## Step 4: Deriving the filter Operator

### Requirements
1. Take a predicate `T → boolean`
2. Forward elements that match
3. Drop elements that don't match

### Key Insight
Filter doesn't transform elements—it **selectively forwards** them.

```java
class FilterOperator<T> implements Publisher<T> {
    private final Publisher<T> upstream;
    private final Predicate<T> predicate;

    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        upstream.subscribe(new Subscriber<T>() {
            @Override
            public void onNext(T item) {
                if (predicate.test(item)) {
                    downstream.onNext(item);  // Forward if matches
                }
                // Silently drop if doesn't match
                // But wait... backpressure!
            }
            // ... other methods pass through
        });
    }
}
```

### The Backpressure Problem

If downstream requests 1 item, and we drop the first 9 items, we've consumed 10 from upstream but delivered only 1.

**Solution**: Filter must request more from upstream when it drops elements.

```java
@Override
public void onNext(T item) {
    if (predicate.test(item)) {
        downstream.onNext(item);
    } else {
        // Item dropped, request another from upstream
        subscription.request(1);
    }
}
```

This is why implementing operators correctly is complex!

---

## Step 5: Deriving flatMap

This is the trickiest but most powerful operator.

### The Problem
Sometimes each element needs to be transformed into **multiple elements** (or an async operation that produces elements).

```java
// For each user, get their orders (returns Flux<Order>)
users.flatMap(user -> orderService.findByUser(user.getId()));
```

### Requirements
1. Take a function `T → Publisher<R>`
2. Subscribe to each inner Publisher
3. Merge all results into a single stream

### The Complexity

```
┌─────────────────────────────────────────────────────────────────┐
│  FLATMAP VISUAL                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Input:    ──(A)────────(B)────────(C)──|                      │
│               │           │           │                         │
│               ▼           ▼           ▼                         │
│            [Inner 1]   [Inner 2]   [Inner 3]                   │
│            ──(a1)(a2)  ──(b1)(b2)(b3)  ──(c1)                  │
│               │   │      │   │   │      │                      │
│               └───┼──────┼───┼───┼──────┘                      │
│                   │      │   │   │                              │
│                   ▼      ▼   ▼   ▼                              │
│  Output:   ──(a1)(a2)(b1)(b2)(b3)(c1)──|                       │
│                     (interleaved)                               │
│                                                                 │
│  Challenges:                                                   │
│  • Multiple concurrent subscriptions (A, B, C run in parallel) │
│  • Results interleave (order not guaranteed)                   │
│  • Must handle completion of ALL inner publishers              │
│  • Backpressure from downstream to ALL upstreams               │
│  • Error from any inner should propagate                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Simplified Pseudo-implementation

```java
class FlatMapOperator<T, R> implements Publisher<R> {
    private final Publisher<T> upstream;
    private final Function<T, Publisher<R>> mapper;
    private final int concurrency;  // How many inner subscriptions at once

    @Override
    public void subscribe(Subscriber<? super R> downstream) {
        upstream.subscribe(new Subscriber<T>() {
            private final Set<InnerSubscriber> active = ConcurrentHashMap.newKeySet();
            private volatile boolean upstreamComplete = false;

            @Override
            public void onNext(T item) {
                // Create inner Publisher from item
                Publisher<R> inner = mapper.apply(item);

                // Subscribe to inner
                InnerSubscriber innerSub = new InnerSubscriber();
                active.add(innerSub);
                inner.subscribe(innerSub);
            }

            @Override
            public void onComplete() {
                upstreamComplete = true;
                maybeComplete();
            }

            void maybeComplete() {
                if (upstreamComplete && active.isEmpty()) {
                    downstream.onComplete();
                }
            }

            class InnerSubscriber implements Subscriber<R> {
                @Override
                public void onNext(R item) {
                    downstream.onNext(item);  // Forward inner's items
                }

                @Override
                public void onComplete() {
                    active.remove(this);
                    maybeComplete();
                }
                // ... error handling, backpressure, etc.
            }
        });
    }
}
```

This is simplified—real flatMap handles:
- Concurrency limits
- Backpressure coordination
- Error modes (fail-fast vs error-delayed)
- Cancellation propagation
- Thread safety

---

## Step 6: Why map vs flatMap?

```
┌─────────────────────────────────────────────────────────────────┐
│  THE FUNDAMENTAL DIFFERENCE                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  map:                                                          │
│  • Function: T → R                                             │
│  • Synchronous transformation                                  │
│  • One input → one output                                      │
│  • No nested Publishers                                        │
│                                                                 │
│  flatMap:                                                      │
│  • Function: T → Publisher<R>                                  │
│  • Async transformation (or sync one-to-many)                  │
│  • One input → zero or more outputs                            │
│  • Flattens (unwraps) the inner Publishers                     │
│                                                                 │
│  The "flat" in flatMap means: flatten the nested structure.    │
│                                                                 │
│  Without flat:  Flux<Flux<R>>  (nested)                        │
│  With flat:     Flux<R>         (flattened)                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 7: Deriving Combining Operators

We often have multiple streams that need to be combined.

### zip: Wait and Pair

Requirement: Pair the Nth element of stream A with the Nth element of stream B.

```
Stream A: ──(A1)────(A2)────(A3)──|
Stream B: ──(B1)──(B2)────(B3)──|

Must wait:
- A1 arrives, wait for B1
- B1 arrives, emit (A1, B1)
- A2 arrives, B2 already here, emit (A2, B2)
- etc.
```

Key insight: Need buffers for each stream to hold elements waiting for their pair.

### merge: Interleave by Time

Requirement: Emit elements from multiple streams as they arrive.

```
Stream A: ──(A1)────────(A2)────(A3)──|
Stream B: ────────(B1)────────(B2)──|

Output:   ──(A1)──(B1)──(A2)──(B2)──(A3)──|
          (order depends on arrival time)
```

Key insight: Subscribe to all streams, forward elements as they come.

### concat: Sequential

Requirement: Subscribe to streams one after another.

```
Stream A: ──(A1)──(A2)──|
Stream B:                 ──(B1)──(B2)──|

Output:   ──(A1)──(A2)────(B1)──(B2)──|
                      ↑
                      Wait for A to complete, then start B
```

Key insight: Only subscribe to next stream after current completes.

---

## Step 8: Deriving Aggregating Operators

Sometimes we need to reduce a stream to a single value.

### reduce: Accumulate

```
Input:  ──(1)──(2)──(3)──(4)──(5)──|

With reduce(0, (acc, x) -> acc + x):

Step 1: acc=0, x=1 → acc=1
Step 2: acc=1, x=2 → acc=3
Step 3: acc=3, x=3 → acc=6
Step 4: acc=6, x=4 → acc=10
Step 5: acc=10, x=5 → acc=15
(onComplete)

Output: ─────────────────────────(15)──|
```

Key insight: reduce changes cardinality: Flux → Mono

### collect: Gather

collect is reduce with a mutable accumulator:

```java
// Internally:
reduce(
    ArrayList::new,           // Initial value
    (list, item) -> {
        list.add(item);
        return list;
    }
)
```

---

## Step 9: The Operator Pattern

All operators follow the same pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│  THE OPERATOR PATTERN                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Every operator:                                               │
│                                                                 │
│  1. Wraps an upstream Publisher                                │
│  2. Implements Publisher itself                                │
│  3. When subscribed:                                           │
│     a. Creates internal Subscriber                             │
│     b. Subscribes to upstream with it                          │
│     c. Internal subscriber applies operator logic              │
│     d. Forwards results to downstream subscriber               │
│                                                                 │
│  Benefits:                                                     │
│  • Composable: .op1().op2().op3() creates a chain             │
│  • Reusable: Each operator is independent                     │
│  • Testable: Operators can be tested in isolation             │
│  • Lazy: Chain is just blueprints until subscribed            │
│                                                                 │
│  Complexity hidden:                                            │
│  • Backpressure propagation                                   │
│  • Error handling                                             │
│  • Thread safety                                              │
│  • Resource management                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 10: Why Operator Libraries?

We could implement operators ourselves, but:

### The Complexity

```java
// A PROPER map implementation needs:
// 1. Backpressure handling
// 2. Error handling (what if mapper throws?)
// 3. Cancellation propagation
// 4. Thread safety
// 5. Null handling
// 6. Fusion optimization (merging with adjacent operators)
// 7. Debugging hooks
// 8. Context propagation

// Real Reactor MapSubscriber is ~200 lines of careful code
```

### The Wisdom

```
Reactor provides ~400 operators, each:
• Correctly implemented
• Battle-tested in production
• Optimized for performance
• Handling edge cases
• Supporting debugging

You get to focus on WHAT you want (declarative),
not HOW to implement it (imperative).
```

---

## The Mental Model We've Derived

```
┌─────────────────────────────────────────────────────────────────┐
│  OPERATOR MENTAL MODEL                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. OPERATORS ARE DECORATORS                                   │
│     Each wraps the previous Publisher, adding behavior.        │
│                                                                 │
│  2. SUBSCRIPTION FLOWS UPSTREAM                                │
│     subscribe() propagates up the chain.                       │
│                                                                 │
│  3. DATA FLOWS DOWNSTREAM                                      │
│     Elements flow down through operator transformations.       │
│                                                                 │
│  4. DEMAND FLOWS UPSTREAM                                      │
│     request(n) propagates up to control flow.                  │
│                                                                 │
│  5. ERRORS FLOW DOWNSTREAM                                     │
│     Errors propagate to the final subscriber.                  │
│                                                                 │
│  6. EACH OPERATOR IS INDEPENDENT                               │
│     Knows only about upstream and downstream.                  │
│     No global state.                                           │
│                                                                 │
│  Visualization:                                                │
│                                                                 │
│  [Source] ─data→ [Op1] ─data→ [Op2] ─data→ [Subscriber]       │
│           ←req─        ←req─        ←req─                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## What We've Learned

Starting from raw Publishers and Subscribers, we discovered:

1. **Decorator Pattern** - Operators wrap and delegate
2. **map** - Transform each element synchronously
3. **filter** - Selectively forward elements (with backpressure awareness)
4. **flatMap** - Transform to Publisher, manage multiple subscriptions, flatten
5. **Combining operators** - zip (pair), merge (interleave), concat (sequence)
6. **Aggregating operators** - reduce (accumulate), collect (gather)
7. **The complexity** - Backpressure, errors, threading, cancellation
8. **The value of libraries** - 400 correct implementations vs rolling your own

---

## The Shift

```
Before understanding operators:
─────────────────────────────────
"How do I loop through elements and transform them?"

After understanding operators:
─────────────────────────────────
"What transformation do I want? There's an operator for that."

This is the shift from HOW to WHAT.
From imperative to declarative.
From managing mechanics to expressing intent.
```

---

*"The art of programming is the art of organizing complexity." — Edsger W. Dijkstra*
