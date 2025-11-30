# First Principles: Deriving Reactive Testing

## Forget That StepVerifier Exists

You've written some reactive code. Now you need to test it. But when you try your usual testing approach, something feels wrong. Let's understand why and derive the solution.

## Step 1: The Fundamental Problem

Traditional testing works like this:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SYNCHRONOUS TESTING MODEL                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   @Test                                                                  │
│   void traditionalTest() {                                              │
│       // 1. Call method                                                  │
│       String result = calculator.add(2, 3);                             │
│                                                                          │
│       // 2. Assert on result                                             │
│       assertEquals("5", result);                                        │
│   }                                                                      │
│                                                                          │
│   Timeline:                                                              │
│   ─────┬─────────────────────────────┬──────────────────────────────    │
│        │                             │                                   │
│     Call method                  Get result                             │
│     (sync, blocks               (immediately                            │
│      until done)                 available)                             │
│                                                                          │
│   The result is THERE when the method returns.                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

But reactive code doesn't work this way:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE: WHAT GOES WRONG                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   @Test                                                                  │
│   void brokenReactiveTest() {                                           │
│       // 1. Call method                                                  │
│       Mono<String> result = service.getData();                          │
│                                                                          │
│       // 2. Assert... but on what?                                       │
│       assertEquals("expected", result);  // WRONG!                      │
│   }                                                                      │
│                                                                          │
│   Problem: 'result' is not the data—it's a RECIPE for getting data.    │
│                                                                          │
│   Timeline:                                                              │
│   ─────┬───────────────────────────────────────────────────────────     │
│        │                                                                 │
│     Call method                                                          │
│     (returns immediately                                                │
│      with a Mono, NOT                                                   │
│      the actual data!)                                                  │
│                                                                          │
│   The data hasn't been fetched yet—nothing has even started!            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: What Makes a Mono Different?

A `Mono<String>` is not a String. It's a **publisher**—a description of how to get a String.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    MONO IS A BLUEPRINT                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Think of it like this:                                                │
│                                                                          │
│   Synchronous:                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  String getData() {                                              │   │
│   │      return database.query("...");  // Executes NOW             │   │
│   │  }                                                               │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Reactive:                                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Mono<String> getData() {                                        │   │
│   │      return Mono.fromCallable(() -> database.query("..."));     │   │
│   │      // Returns a RECIPE, not the result                        │   │
│   │      // Nothing executes until someone subscribes               │   │
│   │  }                                                               │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   The Mono is like a cooking recipe:                                    │
│   - Having the recipe doesn't give you food                            │
│   - You need to follow the recipe (subscribe) to get food              │
│   - Multiple people can follow the same recipe (multiple subscriptions) │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: How Do We Get the Actual Value?

First instinct: just call `.block()` to wait for the result!

```java
@Test
void testWithBlock() {
    Mono<String> mono = service.getData();
    String result = mono.block();  // Wait for result
    assertEquals("expected", result);
}
```

This works for simple cases, but has problems:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PROBLEMS WITH .block()                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. Can't test errors properly:                                        │
│      mono.block() throws the exception—hard to assert on it            │
│                                                                          │
│   2. Can't test sequences:                                              │
│      Flux.just(1, 2, 3).blockFirst() only gives you the first element  │
│                                                                          │
│   3. Can't test completion:                                             │
│      How do you verify an empty Mono completed (vs errored)?            │
│                                                                          │
│   4. Can't test timing:                                                 │
│      Mono.delay(Duration.ofHours(1)).block() waits an actual hour!     │
│                                                                          │
│   5. Can't test signals:                                                │
│      Reactive streams have more than just values—they have signals     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 4: What Are We Really Testing?

Let's think about what a reactive stream does:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE STREAM SIGNALS                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Publisher                                              Subscriber     │
│       │                                                      │          │
│       │──── onSubscribe(subscription) ─────────────────────▶│          │
│       │                                                      │          │
│       │◀─── request(n) ─────────────────────────────────────│          │
│       │                                                      │          │
│       │──── onNext(value1) ────────────────────────────────▶│          │
│       │──── onNext(value2) ────────────────────────────────▶│          │
│       │──── onNext(value3) ────────────────────────────────▶│          │
│       │                                                      │          │
│       │──── onComplete() ──────────────────────────────────▶│          │
│       │     (OR)                                             │          │
│       │──── onError(exception) ────────────────────────────▶│          │
│                                                                          │
│   A complete test should verify:                                        │
│   • Subscription happened                                               │
│   • Correct values emitted (in order)                                  │
│   • Correct terminal signal (complete OR error)                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 5: Designing a Testing Tool

We need a tool that:
1. Subscribes to the publisher
2. Records all signals
3. Lets us assert on those signals
4. Handles the async nature

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONCEPTUAL TEST SUBSCRIBER                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   class TestSubscriber<T> implements Subscriber<T> {                    │
│       List<T> values = new ArrayList<>();                               │
│       Throwable error = null;                                           │
│       boolean completed = false;                                        │
│       boolean subscribed = false;                                       │
│                                                                          │
│       void onSubscribe(Subscription s) {                                │
│           subscribed = true;                                            │
│           s.request(Long.MAX_VALUE);  // Request all                   │
│       }                                                                  │
│                                                                          │
│       void onNext(T value) {                                            │
│           values.add(value);                                            │
│       }                                                                  │
│                                                                          │
│       void onComplete() {                                                │
│           completed = true;                                             │
│       }                                                                  │
│                                                                          │
│       void onError(Throwable e) {                                       │
│           error = e;                                                    │
│       }                                                                  │
│   }                                                                      │
│                                                                          │
│   // Test usage                                                          │
│   TestSubscriber<String> subscriber = new TestSubscriber<>();           │
│   mono.subscribe(subscriber);                                           │
│   // Wait for completion...                                             │
│   assertEquals(List.of("expected"), subscriber.values);                 │
│   assertTrue(subscriber.completed);                                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: Making It Fluent

Manual subscriber management is tedious. Let's make it fluent:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DERIVING StepVerifier                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Instead of:                                                           │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  TestSubscriber<Integer> sub = new TestSubscriber<>();          │   │
│   │  flux.subscribe(sub);                                            │   │
│   │  waitForCompletion(sub);                                         │   │
│   │  assertEquals(1, sub.values.get(0));                            │   │
│   │  assertEquals(2, sub.values.get(1));                            │   │
│   │  assertEquals(3, sub.values.get(2));                            │   │
│   │  assertTrue(sub.completed);                                      │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Fluent API:                                                           │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  StepVerifier.create(flux)                                       │   │
│   │      .expectNext(1)          // Assert first value              │   │
│   │      .expectNext(2)          // Assert second value             │   │
│   │      .expectNext(3)          // Assert third value              │   │
│   │      .verifyComplete();      // Assert completion + trigger     │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Each step is an EXPECTATION about the next signal.                   │
│   verify() subscribes and checks all expectations.                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 7: The Virtual Time Insight

What about testing time-based operations?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE TIME PROBLEM                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Real-world scenario:                                                   │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Flux.interval(Duration.ofHours(1))  // Emit every hour         │   │
│   │      .take(24)                        // For 24 hours           │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Testing with real time: Test takes 24 HOURS!                          │
│                                                                          │
│   Insight: We don't care about REAL time, just LOGICAL time.           │
│                                                                          │
│   Solution: Virtual time scheduler                                       │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Virtual Scheduler                                               │   │
│   │                                                                   │   │
│   │  Time: 0:00                                                      │   │
│   │  Pending: [emit at 1:00, emit at 2:00, ...]                     │   │
│   │                                                                   │   │
│   │  advanceTimeBy(1 hour):                                          │   │
│   │  Time: 1:00                                                      │   │
│   │  → Execute "emit at 1:00" immediately                           │   │
│   │                                                                   │   │
│   │  advanceTimeBy(23 hours):                                        │   │
│   │  Time: 24:00                                                     │   │
│   │  → Execute all remaining emits immediately                      │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Test runs in milliseconds, not hours!                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Testing HTTP Endpoints

For HTTP testing, we need to:
1. Make an HTTP request
2. Verify the response (status, headers, body)
3. Do it non-blocking

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WEB TEST CLIENT                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Conceptual flow:                                                       │
│                                                                          │
│   webTestClient.get()           // Build request                        │
│       .uri("/api/users/123")    // Set path                             │
│       .exchange()               // Execute request (returns Publisher)  │
│       .expectStatus().isOk()    // Assert on status                     │
│       .expectBody(User.class)   // Assert on body                       │
│       .value(user -> {                                                  │
│           assertEquals("123", user.getId());                            │
│       });                                                                │
│                                                                          │
│   Under the hood:                                                        │
│   1. webTestClient creates an HTTP request                              │
│   2. exchange() sends it and subscribes to response                     │
│   3. expectXxx() methods verify response parts                          │
│   4. Everything is non-blocking                                         │
│                                                                          │
│   Key insight: It's StepVerifier for HTTP!                              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: Integration Testing Philosophy

Unit tests mock dependencies. But do those mocks behave like the real thing?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE MOCK PROBLEM                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Unit test with mocks:                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  when(userRepository.findById("123"))                            │   │
│   │      .thenReturn(Mono.just(mockUser));                          │   │
│   │                                                                   │   │
│   │  // Test passes! But...                                          │   │
│   │  // What if real Postgres has different NULL handling?          │   │
│   │  // What if real query is subtly wrong?                         │   │
│   │  // What if connection pooling causes issues?                   │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Solution: Test with REAL dependencies                                 │
│                                                                          │
│   But how? Can't require everyone to install PostgreSQL.               │
│                                                                          │
│   Answer: Testcontainers                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  @Container                                                      │   │
│   │  static PostgreSQLContainer<?> postgres =                        │   │
│   │      new PostgreSQLContainer<>("postgres:15");                  │   │
│   │                                                                   │   │
│   │  // Docker container starts automatically for test              │   │
│   │  // Real PostgreSQL, no mocks                                   │   │
│   │  // Isolated, reproducible, portable                            │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 10: The Complete Testing Picture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE TESTING STRATEGY                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Layer 1: Unit Tests (StepVerifier)                                    │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  • Test individual Mono/Flux operations                         │   │
│   │  • Mock dependencies                                             │   │
│   │  • Use virtual time for delays                                   │   │
│   │  • Fast, isolated, many of them                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Layer 2: Integration Tests (WebTestClient)                            │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  • Test HTTP endpoints end-to-end                               │   │
│   │  • Verify serialization, headers, status codes                  │   │
│   │  • Test with mock services or real containers                   │   │
│   │  • Medium speed, test request/response contracts                │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Layer 3: System Tests (Testcontainers)                                │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  • Test with real databases, message brokers, caches            │   │
│   │  • Verify actual behavior matches expectations                  │   │
│   │  • Slower, but high confidence                                  │   │
│   │  • Catch integration issues early                               │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Each layer catches different bugs:                                    │
│   • Unit: Logic errors, operator misuse                                │
│   • Integration: API contract violations                               │
│   • System: Infrastructure incompatibilities                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Key Insight

Testing reactive code requires thinking about **signals over time**, not just final values:

| Synchronous Testing | Reactive Testing |
|---------------------|------------------|
| Call method, get result | Subscribe, receive signals |
| Assert on return value | Assert on each signal |
| Exception = test failure | Error signal = expected outcome |
| Time doesn't matter | Time is part of behavior |

The tools we derived:
- **StepVerifier**: Subscribe and assert on signals
- **Virtual Time**: Control time without waiting
- **WebTestClient**: StepVerifier for HTTP
- **Testcontainers**: Real dependencies in isolation

## Summary

From first principles, we derived that:

1. **Reactive types are lazy**—you must subscribe to get values
2. **Testing means verifying signals**—onNext, onComplete, onError
3. **Time can be virtualized**—no need to wait for real delays
4. **Integration testing needs real dependencies**—mocks hide bugs
5. **Each layer catches different bugs**—unit, integration, system

The reactive testing tools aren't arbitrary—they emerge naturally from understanding what reactive streams actually do and what we need to verify about them.
