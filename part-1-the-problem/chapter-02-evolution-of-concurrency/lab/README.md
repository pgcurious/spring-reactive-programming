# Hands-On Lab 2: Comparing Concurrency Approaches

## Overview

In this lab, you will implement the **same scenario** using each concurrency approach covered in Chapter 2. This hands-on experience will help you:

1. Understand the strengths and weaknesses of each approach
2. Feel the evolution from imperative to declarative
3. Appreciate why reactive programming emerged
4. Build intuition for when to use each approach

**Estimated time**: 45-60 minutes

## Prerequisites

- Java 17+
- Maven or Gradle
- IDE with Java support
- Completion of Chapter 2 reading

## The Scenario

You need to build a **User Dashboard Service** that:

1. Fetches a user by ID
2. Fetches the user's orders
3. Fetches pricing information based on user tier
4. Combines all data into a Dashboard object

This scenario represents a common pattern in microservices: aggregating data from multiple sources.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         USER DASHBOARD SERVICE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Client Request                                                              │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────┐     ┌─────────────┐     ┌──────────────┐                      │
│  │  Fetch  │────▶│   Fetch     │────▶│    Fetch     │                      │
│  │  User   │     │   Orders    │     │   Pricing    │                      │
│  │ (100ms) │     │  (150ms)    │     │   (80ms)     │                      │
│  └─────────┘     └─────────────┘     └──────────────┘                      │
│       │                 │                   │                               │
│       └─────────────────┴───────────────────┘                               │
│                         │                                                   │
│                         ▼                                                   │
│                   ┌──────────┐                                              │
│                   │ Dashboard │                                             │
│                   │  Result   │                                             │
│                   └──────────┘                                              │
│                                                                             │
│  Sequential total: 100 + 150 + 80 = 330ms                                  │
│  Optimal (parallel): 100 + max(150, 80) = 250ms                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Part 1: Setup

### Step 1.1: Review the Example Project

```bash
cd ../examples/concurrency-evolution
```

Examine the structure and run the demo:

```bash
./mvnw spring-boot:run
```

Watch the output to understand how each era works.

### Step 1.2: Understand the Domain Objects

Review `SimulatedService.java` in the `common` package. Note:
- Each method simulates network latency
- Multiple implementation styles are provided (blocking, callback, CF, reactive)

---

## Part 2: Thread Era Implementation

### Exercise 2.1: Implement Dashboard with Threads

Create a new class `ThreadEraDashboard.java` in the `thread_era` package:

```java
package com.example.concurrency.thread_era;

import com.example.concurrency.common.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadEraDashboard {

    /**
     * TODO: Implement using raw threads.
     *
     * Requirements:
     * 1. Create threads to fetch user, orders, and pricing
     * 2. Use AtomicReference or similar to share results
     * 3. Join all threads before returning the dashboard
     *
     * Questions to answer:
     * - How many threads did you create?
     * - Could orders and pricing run in parallel? If so, did you implement it?
     * - How did you handle errors?
     */
    public Dashboard buildDashboard(Long userId) throws InterruptedException {
        // Your implementation here
        return null;
    }
}
```

**Hints:**
- Use `AtomicReference<User>`, `AtomicReference<List<Order>>`, etc. to hold results
- Remember to call `join()` on all threads
- Think about: Can orders and pricing run in parallel? They only need the user...

**Discussion Questions:**
1. How many lines of code did you need?
2. How would you add timeout handling?
3. How would you add retry logic?

---

## Part 3: Executor Era Implementation

### Exercise 3.1: Implement Dashboard with ExecutorService

Create `ExecutorEraDashboard.java` in the `executor_era` package:

```java
package com.example.concurrency.executor_era;

import com.example.concurrency.common.*;
import java.util.List;
import java.util.concurrent.*;

public class ExecutorEraDashboard {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * TODO: Implement using ExecutorService and Future.
     *
     * Requirements:
     * 1. Submit tasks to the executor
     * 2. Get results using Future.get()
     * 3. Return the dashboard
     *
     * Bonus: Can you parallelize orders and pricing fetch?
     *
     * Questions to answer:
     * - Where does blocking occur in your implementation?
     * - What happens if you submit 1000 dashboard requests?
     */
    public Dashboard buildDashboard(Long userId) throws Exception {
        // Your implementation here
        return null;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
```

**Challenge:**
- Try to fetch orders and pricing in parallel after getting the user
- Measure: Does parallel execution improve total time?

---

## Part 4: Callback Implementation

### Exercise 4.1: Implement Dashboard with Callbacks

Create `CallbackDashboard.java` in the `future_era` package:

```java
package com.example.concurrency.future_era;

import com.example.concurrency.common.*;
import com.example.concurrency.common.SimulatedService.Callback;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CallbackDashboard {

    /**
     * TODO: Implement using callbacks.
     *
     * Requirements:
     * 1. Use SimulatedService.fetchUserAsync with callback
     * 2. Chain to fetchOrdersAsync and fetchPricingAsync
     * 3. Use CountDownLatch to wait for completion
     *
     * Experience the callback hell!
     *
     * Questions to answer:
     * - How deep is your callback nesting?
     * - How would you add error handling for each step?
     * - How readable is your code?
     */
    public Dashboard buildDashboard(Long userId) throws InterruptedException {
        // Your implementation here
        return null;
    }
}
```

**Reflection:**
- Count the levels of nesting in your code
- Imagine adding: timeout, retry, caching, logging at each step
- How maintainable would this become?

---

## Part 5: CompletableFuture Implementation

### Exercise 5.1: Implement Dashboard with CompletableFuture

Create `CompletableFutureDashboard.java` in the `completablefuture_era` package:

```java
package com.example.concurrency.completablefuture_era;

import com.example.concurrency.common.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CompletableFutureDashboard {

    /**
     * TODO: Implement using CompletableFuture.
     *
     * Requirements:
     * 1. Use SimulatedService.fetchUserCF, fetchOrdersCF, fetchPricingCF
     * 2. Chain operations with thenCompose, thenCombine
     * 3. Run orders and pricing in parallel where possible
     * 4. Add error handling with exceptionally
     *
     * This should be MUCH cleaner than callbacks!
     */
    public CompletableFuture<Dashboard> buildDashboard(Long userId) {
        // Your implementation here
        return null;
    }

    /**
     * BONUS: Add timeout handling.
     *
     * Use orTimeout() or completeOnTimeout() to handle slow services.
     */
    public CompletableFuture<Dashboard> buildDashboardWithTimeout(Long userId) {
        // Your implementation here
        return null;
    }
}
```

**Compare:**
- How does the code length compare to the callback version?
- How easy was it to add parallel execution?
- How would you add retry logic?

---

## Part 6: Reactive Implementation

### Exercise 6.1: Implement Dashboard with Reactor

Create `ReactiveDashboard.java` in the `reactive_era` package:

```java
package com.example.concurrency.reactive_era;

import com.example.concurrency.common.*;
import reactor.core.publisher.Mono;
import java.time.Duration;

public class ReactiveDashboard {

    /**
     * TODO: Implement using Project Reactor.
     *
     * Requirements:
     * 1. Use SimulatedService.fetchUserReactive, etc.
     * 2. Chain operations with flatMap
     * 3. Use Mono.zip for parallel execution
     * 4. Add timeout with .timeout()
     * 5. Add error handling with .onErrorResume()
     */
    public Mono<Dashboard> buildDashboard(Long userId) {
        // Your implementation here
        return null;
    }

    /**
     * BONUS: Add retry with exponential backoff.
     *
     * Use retryWhen with Retry.backoff()
     */
    public Mono<Dashboard> buildDashboardWithRetry(Long userId) {
        // Your implementation here
        return null;
    }

    /**
     * BONUS: Add caching.
     *
     * Use .cache() to avoid duplicate fetches.
     */
    public Mono<Dashboard> buildDashboardWithCache(Long userId) {
        // Your implementation here
        return null;
    }
}
```

**Exploration:**
- Try adding: `doOnNext`, `doOnError`, `log()` to see the flow
- Try removing the subscription - does anything happen?
- Try subscribing multiple times - what happens?

---

## Part 7: Comparison

### Exercise 7.1: Create a Comparison Runner

Create `DashboardComparison.java`:

```java
package com.example.concurrency;

import com.example.concurrency.thread_era.ThreadEraDashboard;
import com.example.concurrency.executor_era.ExecutorEraDashboard;
// ... other imports

public class DashboardComparison {

    public static void main(String[] args) throws Exception {
        Long userId = 1L;

        // Thread Era
        long start = System.currentTimeMillis();
        ThreadEraDashboard threadDashboard = new ThreadEraDashboard();
        Dashboard result1 = threadDashboard.buildDashboard(userId);
        System.out.println("Thread Era: " +
            (System.currentTimeMillis() - start) + "ms");

        // Executor Era
        start = System.currentTimeMillis();
        ExecutorEraDashboard executorDashboard = new ExecutorEraDashboard();
        Dashboard result2 = executorDashboard.buildDashboard(userId);
        System.out.println("Executor Era: " +
            (System.currentTimeMillis() - start) + "ms");
        executorDashboard.shutdown();

        // Continue for other eras...

        // Print comparison table
        System.out.println("\n" + "=".repeat(50));
        System.out.println("COMPARISON SUMMARY");
        System.out.println("=".repeat(50));
        // Add your observations here
    }
}
```

### Exercise 7.2: Fill Out the Comparison Table

After implementing all versions, fill out this table:

| Aspect | Thread | Executor | Callback | CF | Reactive |
|--------|--------|----------|----------|--------|----------|
| Lines of code | ___ | ___ | ___ | ___ | ___ |
| Parallel execution? | Y/N | Y/N | Y/N | Y/N | Y/N |
| Execution time (ms) | ___ | ___ | ___ | ___ | ___ |
| Error handling difficulty | 1-5 | 1-5 | 1-5 | 1-5 | 1-5 |
| Adding timeout difficulty | 1-5 | 1-5 | 1-5 | 1-5 | 1-5 |
| Adding retry difficulty | 1-5 | 1-5 | 1-5 | 1-5 | 1-5 |
| Readability | 1-5 | 1-5 | 1-5 | 1-5 | 1-5 |

---

## Part 8: Reflection Questions

Answer these questions after completing the exercises:

### Code Complexity

1. Which approach required the most boilerplate code?
2. Which approach was easiest to understand at first glance?
3. Which approach would be easiest to maintain over time?

### Error Handling

4. How did you handle errors in the Thread approach?
5. How does error handling in CompletableFuture compare to Reactive?
6. Which approach makes it easiest to add retry logic?

### Performance

7. Did any approach achieve better parallelism than others?
8. What's the theoretical minimum time for this scenario?
9. Did any approach get close to the theoretical minimum?

### Real-World Considerations

10. Which approach would you choose for a high-traffic production system?
11. What if you needed to add caching? Which approach makes it easiest?
12. What if the orders service started returning 10,000 orders? Which approach handles this best?

---

## Part 9: Key Takeaways

After completing this lab, you should understand:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                            KEY TAKEAWAYS                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. COMPLEXITY REDUCTION                                                   │
│     Thread Era → Reactive: Massive reduction in boilerplate               │
│                                                                            │
│  2. COMPOSITION                                                            │
│     - Thread/Executor: Hard to compose                                    │
│     - Callbacks: Composable but ugly (callback hell)                      │
│     - CF/Reactive: Clean, natural composition                             │
│                                                                            │
│  3. PARALLELISM                                                            │
│     - Thread/Executor: Manual effort required                             │
│     - CF/Reactive: Natural and easy                                       │
│                                                                            │
│  4. ERROR HANDLING                                                         │
│     - Thread/Executor/Callbacks: Scattered, complex                       │
│     - CF/Reactive: Integrated into the pipeline                          │
│                                                                            │
│  5. ADDITIONAL CONCERNS                                                    │
│     Adding timeout, retry, caching:                                       │
│     - Pre-CF: Significant additional code                                 │
│     - CF: Possible but limited                                            │
│     - Reactive: Often one-liner operators                                 │
│                                                                            │
│  6. BACKPRESSURE                                                           │
│     Only Reactive handles this - critical for streaming data              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

If you have time, try these additional challenges:

### Challenge 1: Add Caching

Implement a caching layer for the user fetch. Compare how easy/hard this is in each approach.

### Challenge 2: Add Circuit Breaker

What if the pricing service is flaky? Add a circuit breaker that returns default pricing after 3 failures.

### Challenge 3: Add Metrics

Add timing metrics to each step. Which approach makes this easiest?

### Challenge 4: Stress Test

Create 100 concurrent dashboard requests. Which approach handles this best?

---

## Summary

You've now implemented the same scenario across 5 different concurrency models:

1. **Thread Era**: Powerful but complex, error-prone
2. **Executor Era**: Better thread management, still blocking
3. **Callbacks**: Non-blocking but "callback hell"
4. **CompletableFuture**: Clean composition, proto-reactive
5. **Reactive**: Full power with backpressure and operators

This evolution represents 25+ years of learning in the Java ecosystem. Reactive programming isn't a fad—it's the culmination of this journey.

## Next Steps

Proceed to **Chapter 3: What is Reactive Programming, Really?** to deepen your understanding of the reactive paradigm before diving into Project Reactor.
