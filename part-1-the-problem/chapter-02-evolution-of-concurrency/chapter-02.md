# Chapter 2: The Evolution of Concurrency in Java

> "Those who cannot remember the past are condemned to repeat it." — George Santayana

Reactive programming didn't emerge from a vacuum. It's the result of decades of evolution in how we think about concurrent programming in Java. Understanding this evolution isn't just historical trivia—it gives you insight into **why** reactive programming is designed the way it is, and helps you appreciate what problems it solves.

In this chapter, we'll trace Java's journey from raw threads to reactive streams, and you'll see how each step tried to address the limitations of the previous approach.

---

## 2.1 The Thread Era (Java 1.0 - 1.4)

Let's start at the beginning. When Java launched in 1995, it was revolutionary: **threads were built into the language**.

### Direct Thread Management

In the early days, if you wanted concurrent execution, you created and managed threads directly:

```java
public class ThreadEraExample {

    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            System.out.println("Hello from: " + Thread.currentThread().getName());
        });

        thread.start();

        try {
            thread.join(); // Wait for thread to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

This was powerful for its time. But even simple tasks required careful orchestration.

### The Classic Producer-Consumer Problem

One of the canonical examples of early Java concurrency is the producer-consumer pattern. Here's how it looked with `wait()` and `notify()`:

```java
public class ProducerConsumer {
    private final Queue<Integer> buffer = new LinkedList<>();
    private final int capacity = 10;

    public synchronized void produce(int value) throws InterruptedException {
        // Wait while buffer is full
        while (buffer.size() == capacity) {
            wait(); // Release lock and wait
        }

        buffer.add(value);
        System.out.println("Produced: " + value + " | Buffer size: " + buffer.size());

        notify(); // Wake up a waiting consumer
    }

    public synchronized int consume() throws InterruptedException {
        // Wait while buffer is empty
        while (buffer.isEmpty()) {
            wait(); // Release lock and wait
        }

        int value = buffer.poll();
        System.out.println("Consumed: " + value + " | Buffer size: " + buffer.size());

        notify(); // Wake up a waiting producer
        return value;
    }
}
```

### The Problems with Direct Thread Management

While this approach worked, it had significant problems:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   PROBLEMS WITH DIRECT THREAD MANAGEMENT                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. COMPLEXITY                                                             │
│     • Must manually manage thread lifecycle                                │
│     • Easy to create threads without limits                                │
│     • Coordinating multiple threads is error-prone                        │
│                                                                            │
│  2. RESOURCE WASTE                                                         │
│     • Creating threads is expensive (1MB+ stack each)                      │
│     • No thread reuse between tasks                                        │
│     • System can be overwhelmed by too many threads                       │
│                                                                            │
│  3. HARD TO REASON ABOUT                                                   │
│     • Race conditions are easy to introduce                                │
│     • Deadlocks are easy to create                                         │
│     • Debugging is extremely difficult                                     │
│                                                                            │
│  4. SYNCHRONIZATION NIGHTMARES                                             │
│     • synchronized blocks can cause contention                             │
│     • wait/notify semantics are subtle and easy to get wrong              │
│     • Forgetting to notify can cause permanent hangs                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The wait/notify Pitfalls

Consider this subtly broken code:

```java
// BROKEN: notify might happen before wait!
public class BrokenSignaling {
    private boolean ready = false;

    public synchronized void waitForSignal() throws InterruptedException {
        if (!ready) {  // BUG: Should be while, not if!
            wait();    // Spurious wakeup might occur
        }
        // Continue processing...
    }

    public synchronized void signal() {
        ready = true;
        notify();
    }
}
```

Two bugs here:
1. **Should use `while` not `if`**: Threads can wake up spuriously
2. **Lost notification**: If `signal()` is called before `waitForSignal()`, the notification is lost forever

**Key insight**: Direct thread management is powerful but dangerous. Most developers get it wrong.

---

## 2.2 The Executor Era (Java 5)

Java 5 (2004) was a watershed moment for Java concurrency. The `java.util.concurrent` package, based on Doug Lea's work, introduced **thread pools** and **executors**.

### Thread Pools: Reusing Threads

Instead of creating new threads for each task, we could now reuse a pool:

```java
public class ExecutorEraExample {

    public static void main(String[] args) {
        // Create a pool of 4 threads
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Submit tasks - threads are reused
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("Task " + taskId + " running on: " +
                    Thread.currentThread().getName());
                simulateWork(100);
            });
        }

        // Shutdown gracefully
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### The Producer-Consumer with BlockingQueue

Java 5 also brought `BlockingQueue`, which dramatically simplified producer-consumer:

```java
public class ModernProducerConsumer {
    private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(10);

    public void produce(int value) throws InterruptedException {
        queue.put(value); // Blocks if queue is full
        System.out.println("Produced: " + value);
    }

    public int consume() throws InterruptedException {
        int value = queue.take(); // Blocks if queue is empty
        System.out.println("Consumed: " + value);
        return value;
    }
}
```

**Look at the difference!** No synchronized blocks, no wait/notify, no checking conditions. The complexity is hidden inside the BlockingQueue.

### The Executor Types

Java 5 provided several executor implementations:

```java
// Fixed number of threads - predictable resource usage
ExecutorService fixed = Executors.newFixedThreadPool(10);

// Grows as needed, reuses idle threads
ExecutorService cached = Executors.newCachedThreadPool();

// Single thread - tasks execute sequentially
ExecutorService single = Executors.newSingleThreadExecutor();

// For scheduled/periodic tasks
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(4);
```

### What the Executor Framework Solved

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHAT EXECUTORS IMPROVED                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ✓ Thread Reuse                                                            │
│    • Threads are pooled and reused                                         │
│    • No overhead of creating threads for each task                        │
│                                                                            │
│  ✓ Resource Management                                                     │
│    • Pool size limits prevent system overload                             │
│    • Tasks queue up when all threads are busy                             │
│                                                                            │
│  ✓ Separation of Concerns                                                  │
│    • Task submission separated from execution policy                       │
│    • Can change thread pool without changing tasks                        │
│                                                                            │
│  ✓ Better Coordination                                                     │
│    • BlockingQueue handles producer-consumer                               │
│    • CountDownLatch, CyclicBarrier for coordination                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### But Still Fundamentally Blocking

Here's the catch: **Executors helped manage threads, but the blocking nature remained.**

```java
public class StillBlocking {
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public void processRequest(Request request) {
        executor.submit(() -> {
            // Thread is blocked for the entire duration of these calls
            User user = userRepository.findById(request.getUserId()); // BLOCKS
            List<Order> orders = orderRepository.findByUser(user);    // BLOCKS
            sendNotification(user, orders);                            // BLOCKS
            return new Response(user, orders);
        });
    }
}
```

The thread is sitting idle during each of those blocking calls. We're better at managing threads, but we're not using them more efficiently.

---

## 2.3 The Future Era (Java 5-7)

Also in Java 5, the `Future` interface was introduced. Finally, we could get results back from asynchronous operations!

### Callable and Future

```java
public class FutureExample {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public Future<User> fetchUser(Long id) {
        return executor.submit(() -> {
            // Simulating database call
            Thread.sleep(100);
            return new User(id, "User " + id);
        });
    }

    public Future<List<Order>> fetchOrders(Long userId) {
        return executor.submit(() -> {
            // Simulating database call
            Thread.sleep(150);
            return List.of(new Order(1L, userId), new Order(2L, userId));
        });
    }
}
```

### The Problem: Future.get() Still Blocks!

Here's where things get frustrating:

```java
public void processUserOrders(Long userId) throws Exception {
    // Start async operations
    Future<User> userFuture = fetchUser(userId);
    Future<List<Order>> ordersFuture = fetchOrders(userId);

    // But now... how do we get the results?
    User user = userFuture.get();        // BLOCKS until complete!
    List<Order> orders = ordersFuture.get(); // BLOCKS until complete!

    // Process...
    System.out.println("User: " + user + ", Orders: " + orders.size());
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE FUTURE.GET() PROBLEM                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Timeline of execution:                                                    │
│                                                                            │
│  Thread 1: ──▶ fetchUser()  ──────────────────────▶ returns Future         │
│                    │                                      │                │
│                    ▼                                      │                │
│  Thread 2: ◀──── executes user fetch ─────────────────────│                │
│                                                           │                │
│  Thread 1: ──▶ fetchOrders() ────────────────────▶ returns Future          │
│                    │                                      │                │
│                    ▼                                      │                │
│  Thread 3: ◀──── executes order fetch ────────────────────│                │
│                                                           │                │
│  Thread 1: ──▶ userFuture.get() ──────────────────────────┤                │
│                    │                                      │                │
│                    ▼                                      │                │
│               BLOCKS HERE!                                │                │
│               Thread 1 is waiting, doing nothing          │                │
│                    │                                      │                │
│                    ▼                                      │                │
│               ordersFuture.get() ──────────────────────────                │
│                    │                                                       │
│                    ▼                                                       │
│               BLOCKS AGAIN!                                                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

We've moved the work to other threads, but our main thread is still blocked waiting for results.

### Composing Futures is Painful

What if we need to chain operations? It gets ugly:

```java
public void fetchUserThenOrders(Long userId) throws Exception {
    Future<User> userFuture = fetchUser(userId);

    // We MUST block to get the user before fetching orders
    User user = userFuture.get(); // BLOCKS!

    Future<List<Order>> ordersFuture = fetchOrdersForUser(user);

    // And block again
    List<Order> orders = ordersFuture.get(); // BLOCKS!

    // Finally can process
    process(user, orders);
}
```

**There's no way to say**: "When the user is ready, THEN fetch orders."

### The Callback Approach (Pre-Java 8)

One workaround was callbacks:

```java
public interface Callback<T> {
    void onSuccess(T result);
    void onError(Exception e);
}

public void fetchUserAsync(Long id, Callback<User> callback) {
    executor.submit(() -> {
        try {
            Thread.sleep(100);
            callback.onSuccess(new User(id, "User " + id));
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

This avoids blocking, but introduces a new problem: **Callback Hell**.

### Callback Hell

```java
public void fetchUserDashboard(Long userId) {
    fetchUserAsync(userId, new Callback<User>() {
        @Override
        public void onSuccess(User user) {
            fetchOrdersAsync(user.getId(), new Callback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> orders) {
                    fetchRecommendationsAsync(user, new Callback<List<Product>>() {
                        @Override
                        public void onSuccess(List<Product> recommendations) {
                            // Finally! But look at this nesting...
                            renderDashboard(user, orders, recommendations);
                        }
                        @Override
                        public void onError(Exception e) {
                            handleError(e);
                        }
                    });
                }
                @Override
                public void onError(Exception e) {
                    handleError(e);
                }
            });
        }
        @Override
        public void onError(Exception e) {
            handleError(e);
        }
    });
}
```

This is:
- Hard to read (the "pyramid of doom")
- Hard to maintain
- Error handling is scattered everywhere
- Hard to compose or modify

**Key insight**: Future gave us a handle to async results, but no good way to compose them.

---

## 2.4 The CompletableFuture Era (Java 8)

Java 8 (2014) brought `CompletableFuture`, and everything changed. This is **proto-reactive programming**.

### The Revolution: Composition Without Blocking

```java
public class CompletableFutureExample {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public CompletableFuture<User> fetchUser(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(100);
            return new User(id, "User " + id);
        }, executor);
    }

    public CompletableFuture<List<Order>> fetchOrders(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(150);
            return List.of(new Order(1L, userId), new Order(2L, userId));
        }, executor);
    }

    public CompletableFuture<PricingInfo> fetchPricing(User user) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(80);
            return new PricingInfo(user.getTier(), 0.10);
        }, executor);
    }
}
```

### Chaining Operations: thenApply, thenCompose

Now we can chain without blocking:

```java
// Sequential chain - flat, readable code!
CompletableFuture<Dashboard> dashboard = fetchUser(userId)
    .thenCompose(user -> fetchOrders(user.getId())   // flatMap equivalent
        .thenApply(orders -> new Dashboard(user, orders))); // map equivalent

// No blocking! This returns immediately.
// The chain executes when results are ready.
```

Compare to the callback hell earlier:

```java
// Before (Callback Hell):
fetchUserAsync(userId, new Callback<User>() {
    public void onSuccess(User user) {
        fetchOrdersAsync(user.getId(), new Callback<List<Order>>() {
            public void onSuccess(List<Order> orders) {
                // nested deeper...
            }
        });
    }
});

// After (CompletableFuture):
fetchUser(userId)
    .thenCompose(user -> fetchOrders(user.getId()))
    .thenApply(orders -> process(orders));

// Same behavior, dramatically cleaner!
```

### Parallel Composition: allOf, anyOf

Even better—we can run things in parallel naturally:

```java
public CompletableFuture<Dashboard> buildDashboard(Long userId) {
    CompletableFuture<User> userFuture = fetchUser(userId);

    // These run in PARALLEL!
    CompletableFuture<List<Order>> ordersFuture =
        userFuture.thenCompose(user -> fetchOrders(user.getId()));

    CompletableFuture<PricingInfo> pricingFuture =
        userFuture.thenCompose(user -> fetchPricing(user));

    CompletableFuture<List<Product>> recommendationsFuture =
        userFuture.thenCompose(user -> fetchRecommendations(user));

    // Combine when ALL are complete
    return userFuture.thenCombine(ordersFuture, (user, orders) ->
        new Pair<>(user, orders))
        .thenCombine(pricingFuture, (pair, pricing) ->
            new Triple<>(pair.user(), pair.orders(), pricing))
        .thenCombine(recommendationsFuture, (triple, recommendations) ->
            new Dashboard(triple.user(), triple.orders(),
                         triple.pricing(), recommendations));
}
```

Or using `allOf`:

```java
public CompletableFuture<Dashboard> buildDashboardWithAllOf(Long userId) {
    CompletableFuture<User> userFuture = fetchUser(userId);
    CompletableFuture<List<Order>> ordersFuture = fetchOrders(userId);
    CompletableFuture<PricingInfo> pricingFuture = fetchPricing(userId);

    return CompletableFuture.allOf(userFuture, ordersFuture, pricingFuture)
        .thenApply(v -> new Dashboard(
            userFuture.join(),
            ordersFuture.join(),
            pricingFuture.join()
        ));
}
```

### Error Handling: exceptionally, handle

Error handling is finally integrated:

```java
fetchUser(userId)
    .thenCompose(user -> fetchOrders(user.getId()))
    .thenApply(orders -> processOrders(orders))
    .exceptionally(error -> {
        log.error("Failed to process orders", error);
        return Collections.emptyList(); // Fallback
    });

// Or with handle for both success and error:
fetchUser(userId)
    .handle((user, error) -> {
        if (error != null) {
            return new GuestUser();
        }
        return user;
    });
```

### The Key Insight: This is Proto-Reactive!

Look at what CompletableFuture gives us:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                  COMPLETABLEFUTURE: PROTO-REACTIVE                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ✓ Non-blocking Composition                                                │
│    • Chain operations without blocking                                     │
│    • Results flow through the pipeline                                     │
│                                                                            │
│  ✓ Declarative Style                                                       │
│    • Describe WHAT should happen, not HOW                                  │
│    • Similar to Stream API for async                                       │
│                                                                            │
│  ✓ Parallel Execution                                                      │
│    • Easy to run independent operations in parallel                        │
│    • Combine results when ready                                            │
│                                                                            │
│  ✓ Integrated Error Handling                                               │
│    • Errors propagate through the chain                                    │
│    • Can be handled at any point                                           │
│                                                                            │
│  This is the REACTIVE MINDSET:                                             │
│  • Don't wait for results                                                  │
│  • Describe transformations                                                │
│  • React when data arrives                                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### But CompletableFuture Has Limitations

Despite its power, CompletableFuture falls short in several areas:

**1. No Backpressure**

```java
// What if the producer is faster than the consumer?
for (int i = 0; i < 1_000_000; i++) {
    CompletableFuture.supplyAsync(() -> generateData())
        .thenAccept(data -> processData(data)); // Memory explosion!
}
// No way for processData to slow down generateData
```

**2. Limited Operators**

```java
// CompletableFuture has: thenApply, thenCompose, thenCombine, exceptionally...
// Reactive libraries have: map, flatMap, filter, buffer, window, merge,
//    concat, zip, retry, timeout, debounce, throttle, sample, groupBy...
//    ...over 400 operators!
```

**3. Single Value Only**

```java
// CompletableFuture<T> represents ONE future value
// What about streams of values over time?
// - Stock price updates (continuous)
// - User events (ongoing)
// - Server-sent events (infinite)
// CompletableFuture can't model these
```

**4. No Lazy Evaluation**

```java
// This executes IMMEDIATELY:
CompletableFuture<User> cf = CompletableFuture.supplyAsync(() -> {
    System.out.println("Executing!"); // Runs right away!
    return fetchUser();
});

// In reactive, nothing happens until you subscribe
// Allows building pipelines that can be reused
```

---

## 2.5 The Reactive Era (Java 9+)

The limitations of CompletableFuture led to the creation of the **Reactive Streams** specification, which was incorporated into Java 9 as `java.util.concurrent.Flow`.

### The Reactive Streams Specification

The spec defines four simple interfaces:

```java
public final class Flow {

    public interface Publisher<T> {
        void subscribe(Subscriber<? super T> subscriber);
    }

    public interface Subscriber<T> {
        void onSubscribe(Subscription subscription);
        void onNext(T item);
        void onError(Throwable throwable);
        void onComplete();
    }

    public interface Subscription {
        void request(long n);  // BACKPRESSURE!
        void cancel();
    }

    public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
    }
}
```

### The Key Innovation: Backpressure

The `Subscription.request(n)` method is revolutionary:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         BACKPRESSURE MECHANISM                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Publisher                                      Subscriber                 │
│  ──────────                                     ──────────                 │
│                                                                            │
│  [Has data] ◄─────── request(5) ───────────── [Ready for 5 items]         │
│     │                                                                      │
│     ├──────────────► onNext(1) ────────────► [Process item 1]             │
│     ├──────────────► onNext(2) ────────────► [Process item 2]             │
│     ├──────────────► onNext(3) ────────────► [Process item 3]             │
│     ├──────────────► onNext(4) ────────────► [Process item 4]             │
│     └──────────────► onNext(5) ────────────► [Process item 5]             │
│                                                     │                      │
│  [Waiting...] ◄────── request(3) ───────────────────┘                     │
│     │                                                                      │
│     ├──────────────► onNext(6) ────────────► [Process item 6]             │
│     ├──────────────► onNext(7) ────────────► [Process item 7]             │
│     └──────────────► onNext(8) ────────────► [Process item 8]             │
│                                                                            │
│  SUBSCRIBER CONTROLS THE FLOW RATE!                                        │
│  Publisher cannot overwhelm subscriber.                                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Backpressure Matters

```java
// Without backpressure (CompletableFuture style):
producer.generateData()  // 10,000 items/second
    .forEach(consumer::process); // 100 items/second capacity
// Result: Memory fills up, OutOfMemoryError, crash

// With backpressure (Reactive Streams style):
publisher.subscribe(new Subscriber<Data>() {
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(10); // I can handle 10 items
    }

    @Override
    public void onNext(Data item) {
        process(item);
        subscription.request(1); // Ready for one more
    }
    // ... other methods
});
// Result: Producer sends at rate consumer can handle
```

### Why a Specification?

Multiple reactive libraries existed (RxJava, Reactor, Akka Streams), each with their own API. The specification allows interoperability:

```java
// RxJava Flowable can be converted to...
// Reactor Flux, which can be converted to...
// Akka Streams Source

// All implement the same underlying contract
// Libraries can interoperate seamlessly
```

### The Flow API in Java 9

Java 9 included the interfaces but **not** an implementation. You need a library like Project Reactor:

```java
// Java Flow API (just interfaces)
Flow.Publisher<String> publisher; // Can't instantiate directly

// Project Reactor (actual implementation)
Flux<String> flux = Flux.just("hello", "world")
    .map(String::toUpperCase)
    .filter(s -> s.length() > 3);

// Reactor's Flux implements Flow.Publisher
Flow.Publisher<String> asFlow = flux;
```

This is intentional: the JDK provides the contract, libraries provide the implementation.

---

## 2.6 The Pattern Emerges

Let's step back and see the pattern that emerged across 25 years of evolution:

### From Imperative to Declarative

```java
// 1995: Imperative thread management
Thread t = new Thread(() -> doWork());
t.start();
t.join();

// 2004: Imperative task submission
Future<Result> f = executor.submit(() -> doWork());
Result r = f.get(); // Still imperative: "give me the result NOW"

// 2014: Declarative composition
CompletableFuture.supplyAsync(() -> doWork())
    .thenApply(this::transform)
    .thenAccept(this::consume);
// Declarative: "WHEN work is done, THEN transform, THEN consume"

// 2017+: Reactive streams
Flux.defer(() -> doWork())
    .map(this::transform)
    .subscribe(this::consume);
// Declarative + backpressure + operators
```

### The Paradigm Shift

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        THE PARADIGM SHIFT                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  BEFORE (Pull-based):                                                      │
│  ─────────────────────                                                     │
│                                                                            │
│  I ask for data ──────────────────────────────────────────► Get data       │
│                       (wait...)                                            │
│  I ask for more data ─────────────────────────────────────► Get data       │
│                       (wait...)                                            │
│                                                                            │
│  "Give me the data."                                                       │
│                                                                            │
│  ──────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  AFTER (Push-based with backpressure):                                     │
│  ─────────────────────────────────────                                     │
│                                                                            │
│  I subscribe ──────────────────────────────────────────────►               │
│  I'm ready for 5 items ────────────────────────────────────►               │
│                                                                            │
│  ◄──────────────────────────────────────────── Here's item 1               │
│  ◄──────────────────────────────────────────── Here's item 2               │
│  ◄──────────────────────────────────────────── Here's item 3               │
│  ◄──────────────────────────────────────────── Here's item 4               │
│  ◄──────────────────────────────────────────── Here's item 5               │
│                                                                            │
│  I'm ready for 3 more ─────────────────────────────────────►               │
│  ◄──────────────────────────────────────────── Here's item 6               │
│  ...                                                                       │
│                                                                            │
│  "Push data to me when I'm ready."                                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Mental Model Shift

**Old thinking:**
```
1. Start operation
2. Wait for result
3. Process result
4. Repeat
```

**New thinking:**
```
1. Define what should happen when data arrives
2. Define what should happen on error
3. Define what should happen on completion
4. Subscribe (start the flow)
5. Let data push through the pipeline
```

This is the **Hollywood Principle**: "Don't call us, we'll call you."

---

## 2.7 Comparing the Approaches

Let's implement the same scenario across all eras: fetch a user, then fetch their orders.

### Thread Era

```java
public void threadEra(Long userId) {
    Thread thread = new Thread(() -> {
        try {
            User user = fetchUserBlocking(userId);
            List<Order> orders = fetchOrdersBlocking(user.getId());
            processResults(user, orders);
        } catch (Exception e) {
            handleError(e);
        }
    });
    thread.start();
    // Can't easily get the result back!
}
```

### Executor Era

```java
public Future<Void> executorEra(Long userId) {
    return executor.submit(() -> {
        try {
            User user = fetchUserBlocking(userId);
            List<Order> orders = fetchOrdersBlocking(user.getId());
            processResults(user, orders);
        } catch (Exception e) {
            handleError(e);
        }
        return null;
    });
    // Caller still blocks on future.get() to know when done
}
```

### Future Era (with Callbacks)

```java
public void futureEra(Long userId) {
    fetchUserAsync(userId, new Callback<User>() {
        @Override
        public void onSuccess(User user) {
            fetchOrdersAsync(user.getId(), new Callback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> orders) {
                    processResults(user, orders);
                }
                @Override
                public void onError(Exception e) {
                    handleError(e);
                }
            });
        }
        @Override
        public void onError(Exception e) {
            handleError(e);
        }
    });
    // Callback hell!
}
```

### CompletableFuture Era

```java
public CompletableFuture<Void> completableFutureEra(Long userId) {
    return fetchUserAsync(userId)
        .thenCompose(user -> fetchOrdersAsync(user.getId())
            .thenApply(orders -> new Pair<>(user, orders)))
        .thenAccept(pair -> processResults(pair.user(), pair.orders()))
        .exceptionally(e -> {
            handleError(e);
            return null;
        });
    // Clean, composable, non-blocking
}
```

### Reactive Era

```java
public Mono<Void> reactiveEra(Long userId) {
    return fetchUser(userId)
        .flatMap(user -> fetchOrders(user.getId())
            .map(orders -> new Pair<>(user, orders)))
        .doOnNext(pair -> processResults(pair.user(), pair.orders()))
        .doOnError(this::handleError)
        .then();
    // Clean, composable, non-blocking, with backpressure, lazy!
}
```

### Summary Comparison

| Aspect | Thread | Executor | Future | CompletableFuture | Reactive |
|--------|--------|----------|--------|-------------------|----------|
| Thread reuse | No | Yes | Yes | Yes | Yes |
| Non-blocking | No | No | Kind of | Yes | Yes |
| Composable | No | No | No | Yes | Yes |
| Error handling | Try-catch | Try-catch | Callbacks | Integrated | Integrated |
| Backpressure | No | No | No | No | **Yes** |
| Lazy evaluation | No | No | No | No | **Yes** |
| Operators | None | None | None | Few | **Many** |
| Streams of values | No | No | No | No | **Yes** |

---

## 2.8 Summary

In this chapter, we traced Java's concurrency evolution:

**The Thread Era (1995-2004):**
- Direct thread management
- Powerful but dangerous
- wait/notify synchronization nightmares

**The Executor Era (2004+):**
- Thread pools and reuse
- Better resource management
- But still fundamentally blocking

**The Future Era (2004-2014):**
- Handles to async results
- Future.get() still blocks
- Callback hell as a workaround

**The CompletableFuture Era (2014+):**
- Non-blocking composition
- Proto-reactive programming
- But no backpressure, limited operators, single values only

**The Reactive Era (2017+):**
- Standardized Reactive Streams
- Backpressure built-in
- Rich operator libraries
- Streams of values over time

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Each evolution addressed real pain points                              │
│     • Executors → thread management                                        │
│     • Futures → getting results from async work                           │
│     • CompletableFuture → composition without blocking                    │
│     • Reactive → backpressure and streaming                               │
│                                                                            │
│  2. The trend is clear: from imperative to declarative                    │
│     • From "how to do it" to "what should happen"                         │
│     • From pull to push                                                    │
│     • From blocking to reacting                                           │
│                                                                            │
│  3. Reactive programming is the logical next step                         │
│     • It's not a fad—it's the culmination of 25 years of evolution        │
│     • It solves real problems that previous approaches couldn't           │
│                                                                            │
│  4. CompletableFuture is a gateway drug                                   │
│     • If you understand thenApply/thenCompose, you're 80% there          │
│     • Reactive adds: backpressure, streaming, operators, laziness        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 3, we'll dive deeper into what reactive programming really is—stripping away the frameworks to understand the core concepts. You'll see that reactive programming is more than just a library; it's a way of thinking about data flow.

---

## Hands-On Lab 2: Comparing Concurrency Approaches

Now it's time to experience the evolution firsthand. In this lab, you'll:

1. Implement the same scenario using each approach
2. Compare the code complexity and readability
3. Observe the behavior differences
4. Understand why each evolution happened

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [JSR 166: Concurrency Utilities](https://jcp.org/en/jsr/detail?id=166) - Doug Lea's original proposal
- [Reactive Streams Specification](https://www.reactive-streams.org/) - The standard
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/) - Deep dive into Reactor
- [CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) - Official documentation
- [Java Concurrency in Practice](https://jcip.net/) - The classic book by Brian Goetz

---

## Discussion Questions

1. In your current codebase, which era's patterns do you see most often?

2. Have you experienced callback hell? How did you deal with it?

3. If CompletableFuture solves composition, why do we need reactive streams?

4. What would happen in your system if a downstream service became 10x slower? Which approach would handle it best?

5. When would you NOT want backpressure?
