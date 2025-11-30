# First Principles: Inventing Concurrency Solutions

*You're a Java developer in 1996. Threads exist, but that's about it. How would you evolve the solutions as you encounter each problem?*

---

## Era 0: The Starting Point

You have:
- A CPU that runs one instruction at a time
- The ability to create threads
- Nothing else

**Your first concurrent program:**

```java
// The only tool we have
Thread worker = new Thread(() -> {
    // do work
});
worker.start();
```

Simple. Direct. And immediately problematic.

---

## Problem #1: Thread Creation is Expensive

You discover through measurement:

```
Creating a thread:
├── Allocate 1MB stack memory
├── Register with OS scheduler
├── Initialize thread-local storage
└── Total time: ~1ms

For 1000 requests:
1000 × 1ms = 1 second just creating threads!
```

**The insight**: We're creating and destroying threads constantly. What if we... reused them?

---

## Invention: The Thread Pool

*If you had to invent this yourself:*

```
Idea: Pre-create threads, keep them alive, feed them tasks

┌─────────────────────────────────────────┐
│           THREAD POOL                   │
│                                         │
│   Thread-1 ──┐                         │
│   Thread-2 ──┼── waiting for tasks ─── │◀── Task Queue
│   Thread-3 ──┘                         │
│                                         │
└─────────────────────────────────────────┘
```

**Pseudocode you might write:**

```java
class ThreadPool {
    Queue<Runnable> taskQueue;
    List<Thread> workers;

    ThreadPool(int size) {
        for (int i = 0; i < size; i++) {
            Thread worker = new Thread(() -> {
                while (true) {
                    Runnable task = taskQueue.take(); // blocks if empty
                    task.run();
                }
            });
            workers.add(worker);
            worker.start();
        }
    }

    void submit(Runnable task) {
        taskQueue.add(task);
    }
}
```

**You've invented**: ExecutorService (Java 5, 2004)

---

## Problem #2: I Need the Result Back

Your thread pool works great, but:

```java
pool.submit(() -> computeAnswer());
// How do I get the answer?!
```

**The insight**: Tasks should return values, and we need a "receipt" to claim the result later.

---

## Invention: The Future

*If you had to invent this yourself:*

```
Idea: A placeholder for a result that doesn't exist yet

┌─────────────────────────────────────────┐
│                FUTURE                    │
│                                         │
│   State: PENDING → COMPLETED            │
│   Result: null → "the answer"           │
│                                         │
│   Methods:                              │
│   - get(): blocks until result ready    │
│   - isDone(): check if complete         │
│                                         │
└─────────────────────────────────────────┘
```

**Pseudocode:**

```java
class Future<T> {
    private T result;
    private boolean done = false;

    synchronized T get() {
        while (!done) {
            wait(); // block until ready
        }
        return result;
    }

    synchronized void complete(T value) {
        this.result = value;
        this.done = true;
        notifyAll();
    }
}
```

**You've invented**: java.util.concurrent.Future (Java 5, 2004)

---

## Problem #3: Future.get() Blocks!

```java
Future<User> userFuture = pool.submit(() -> fetchUser());
Future<Orders> ordersFuture = pool.submit(() -> fetchOrders());

User user = userFuture.get();     // blocks here!
Orders orders = ordersFuture.get(); // then blocks here!

// Thread is stuck waiting, even though both tasks could run in parallel
```

**The deeper problem**:

```
Timeline with Future.get():

Thread:  |──fetch user──|──wait──|──fetch orders──|──wait──|──combine──|
                        ↑                         ↑
                  blocked here              blocked here

What we wanted:

Thread:  |──dispatch both──|────wait for both────|──combine──|
                           └─ tasks run in parallel ─┘
```

**The insight**: We need a way to say "when this completes, do that" WITHOUT blocking.

---

## Invention: Callbacks

*If you had to invent this yourself:*

```
Idea: Instead of blocking to get result, provide code to run when ready

┌─────────────────────────────────────────┐
│            CALLBACK MODEL               │
│                                         │
│   asyncOperation(input, (result) -> {   │
│       // this runs when done            │
│   });                                   │
│   // code here runs immediately         │
│                                         │
└─────────────────────────────────────────┘
```

**Pseudocode:**

```java
interface Callback<T> {
    void onComplete(T result);
    void onError(Exception e);
}

void fetchUserAsync(Callback<User> callback) {
    pool.submit(() -> {
        try {
            User user = fetchUser();
            callback.onComplete(user);
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

**Usage:**

```java
fetchUserAsync(user -> {
    // runs when user is ready, thread not blocked!
    processUser(user);
});
```

**You've invented**: The callback pattern (common in JavaScript, Node.js)

---

## Problem #4: Callback Hell

Your callbacks work, but then you need to chain operations:

```java
fetchUserAsync(user -> {
    fetchOrdersAsync(user.getId(), orders -> {
        fetchProductsAsync(orders.getProductIds(), products -> {
            fetchPricingAsync(products, prices -> {
                calculateTotalAsync(prices, total -> {
                    sendEmailAsync(user.getEmail(), total, result -> {
                        // FINALLY DONE
                        // But what if any step fails?
                        // Where do I put the try-catch?
                        // How do I handle timeout?
                    });
                });
            });
        });
    });
});
```

This is the **Pyramid of Doom**. Problems:
1. Code moves rightward indefinitely
2. Error handling is unclear
3. Combining parallel results is awkward
4. Cancellation is nearly impossible

**The insight**: We need callbacks that COMPOSE like regular values.

---

## Invention: CompletableFuture

*If you had to invent this yourself:*

```
Idea: A Future that supports chaining transformations declaratively

┌─────────────────────────────────────────────────────────────┐
│              COMPLETABLE FUTURE                             │
│                                                             │
│   future                                                    │
│     .thenApply(x -> transform(x))    // chain               │
│     .thenCompose(x -> anotherAsync(x)) // chain async       │
│     .exceptionally(e -> handleError(e)) // error handling   │
│     .thenCombine(other, (a,b) -> merge(a,b)) // parallel    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**The mental model:**

```
Instead of:                     We write:

callback(a ->                   futureA
  callback(b ->                   .thenCompose(a -> getB(a))
    callback(c ->                 .thenCompose(b -> getC(b))
      use(a,b,c)                  .thenApply(c -> use(c))
    )
  )
)
```

**Key operations you'd invent:**

```java
// Transform result (sync)
<U> CompletableFuture<U> thenApply(Function<T, U> fn)

// Chain to another async operation
<U> CompletableFuture<U> thenCompose(Function<T, CompletableFuture<U>> fn)

// Handle errors
CompletableFuture<T> exceptionally(Function<Throwable, T> fn)

// Combine parallel results
<U, V> CompletableFuture<V> thenCombine(
    CompletableFuture<U> other,
    BiFunction<T, U, V> fn
)

// Wait for all
static CompletableFuture<Void> allOf(CompletableFuture<?>... futures)
```

**You've invented**: CompletableFuture (Java 8, 2014)

---

## Problem #5: What About Streams of Data?

CompletableFuture is perfect for **single async values**. But what about:

- Stream of stock prices (continuous)
- Stream of user events (continuous)
- Stream of database results (might be 1 million rows)

```java
// This doesn't work well:
CompletableFuture<List<StockPrice>> prices = getAllPrices();
// Must wait for ALL prices before processing ANY
// What if there are millions?
// What if they come over hours?
```

**The insight**: We need something like CompletableFuture, but for **multiple values over time**.

---

## Problem #6: The Producer-Consumer Speed Mismatch

Consider:

```
Producer: Database returning 1 million rows at 100,000 rows/second
Consumer: Your code processing at 1,000 rows/second

What happens?

Second 1:  Producer: 100,000 rows → Buffer
           Consumer: 1,000 rows processed
           Buffer: 99,000 rows (and growing!)

Second 10: Buffer: 990,000 rows
           Memory: EXPLODING
           System: CRASHING
```

**The insight**: The consumer needs a way to tell the producer "SLOW DOWN!"

---

## Invention: Backpressure

*If you had to invent this yourself:*

```
Idea: Consumer tells producer how much it can handle

┌──────────────────────────────────────────────────┐
│                                                  │
│   PRODUCER                      CONSUMER         │
│                                                  │
│   "I have data"  ───────────▶                   │
│                  ◀───────────  "Send me 10"      │
│   [10 items]     ───────────▶                   │
│                  ◀───────────  "Send me 10"      │
│   [10 items]     ───────────▶                   │
│                                                  │
└──────────────────────────────────────────────────┘
```

**The key insight**: Invert control. Producer doesn't push blindly; consumer REQUESTS what it can handle.

---

## Invention: Reactive Streams

*Combining all our inventions:*

```
What we need:
1. Multiple values over time (not just one like Future)
2. Composable transformations (like CompletableFuture)
3. Backpressure (consumer controls flow)
4. Async (non-blocking)

The interfaces we'd design:

┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Publisher<T>                                               │
│    └── subscribe(Subscriber)                                │
│                                                             │
│  Subscriber<T>                                              │
│    ├── onSubscribe(Subscription)                            │
│    ├── onNext(T item)                                       │
│    ├── onError(Throwable)                                   │
│    └── onComplete()                                         │
│                                                             │
│  Subscription                                               │
│    ├── request(long n)    ← BACKPRESSURE!                   │
│    └── cancel()                                             │
│                                                             │
│  Processor<T,R> extends Subscriber<T>, Publisher<R>         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**You've invented**: The Reactive Streams Specification (2015)

---

## The Evolution Pattern

Looking back, each invention solved the previous problem:

```
Problem                          Solution                    Year
───────────────────────────────────────────────────────────────────
Thread creation expensive    →   Thread Pool (Executor)      2004
Need result from thread      →   Future                      2004
Future.get() blocks          →   Callbacks                   ~2005
Callback hell                →   CompletableFuture           2014
Single value only            →   Streams/Observable          ~2010
No backpressure              →   Reactive Streams            2015
```

Each solution is a **logical consequence** of the previous problem.

---

## The Fundamental Patterns (Derived)

### Pattern 1: Decoupling

```
Era 1: Thread tightly coupled to task
Era 2: Thread pool decouples thread from task
Era 3: Future decouples submission from result retrieval
Era 4: Callbacks decouple execution from waiting
Era 5: Reactive decouples producer speed from consumer speed
```

### Pattern 2: From Imperative to Declarative

```
Early:  for (item : items) { process(item); }     // HOW
Late:   items.map(this::process).filter(...)      // WHAT
```

### Pattern 3: Composition Over Nesting

```
Early:  callback(a -> callback(b -> callback(c -> ...)))
Late:   op1.then(op2).then(op3).then(...)
```

---

## Key Takeaway

Each concurrency abstraction in Java wasn't invented arbitrarily. Each was a **necessary response** to limitations in the previous approach:

1. Raw threads → Too expensive to create → **Thread pools**
2. Thread pools → Can't get results → **Future**
3. Future → Blocks waiting → **Callbacks**
4. Callbacks → Don't compose → **CompletableFuture**
5. CompletableFuture → Single values only → **Reactive Streams**
6. Streams → Producer floods consumer → **Backpressure**

If you understand WHY each was invented, you understand WHEN to use each.

---

*Next: Chapter 3 dives deep into reactive programming - the final form of this evolution.*
