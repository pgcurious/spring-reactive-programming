# Lab 4: Your First Reactor Project

## Objective

In this lab, you'll get hands-on experience with Project Reactor's core types: Mono and Flux. You'll:

1. Set up a Reactor project from scratch
2. Create Mono and Flux using various factory methods
3. Understand cold vs hot publisher behavior
4. Prove to yourself that nothing happens until you subscribe
5. Practice with different subscription patterns

By the end, Mono and Flux will feel familiar, and you'll have internalized the "nothing happens until subscribe" principle.

---

## Prerequisites

- Java 17+ installed
- Maven or Gradle installed
- IDE of your choice (IntelliJ IDEA recommended)
- Understanding of Chapter 4 concepts

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | Project Setup | 10 min |
| 2 | Creating Mono | 15 min |
| 3 | Creating Flux | 15 min |
| 4 | Nothing Happens Until Subscribe | 20 min |
| 5 | Cold vs Hot Behavior | 20 min |
| 6 | Subscription Patterns | 15 min |
| 7 | Reflection and Key Takeaways | 5 min |

**Total: ~100 minutes**

---

## Part 1: Project Setup (10 min)

### Exercise 1.1: Create a New Maven Project

Create a new directory for your project and add the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>reactor-lab</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <reactor.version>3.6.0</reactor.version>
    </properties>

    <dependencies>
        <!-- Reactor Core -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>${reactor.version}</version>
        </dependency>

        <!-- Reactor Test (for StepVerifier) -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <version>${reactor.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- SLF4J for logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Exercise 1.2: Verify Setup

Create a file `src/main/java/com/example/lab/ReactorSetupTest.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorSetupTest {
    public static void main(String[] args) {
        // Test Mono
        Mono<String> mono = Mono.just("Hello, Reactor!");
        mono.subscribe(System.out::println);

        // Test Flux
        Flux<Integer> flux = Flux.range(1, 5);
        flux.subscribe(System.out::println);

        System.out.println("\nReactor is working correctly!");
    }
}
```

Run the program. Expected output:

```
Hello, Reactor!
1
2
3
4
5

Reactor is working correctly!
```

---

## Part 2: Creating Mono (15 min)

### Exercise 2.1: Mono Factory Methods

Create `src/main/java/com/example/lab/MonoCreation.java`:

```java
package com.example.lab;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MonoCreation {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Mono Factory Methods ===\n");

        // 1. Mono.just() - from a known value
        System.out.println("--- Mono.just() ---");
        Mono<String> just = Mono.just("Hello");
        just.subscribe(s -> System.out.println("Received: " + s));

        // 2. Mono.empty() - completes without emitting
        System.out.println("\n--- Mono.empty() ---");
        Mono<String> empty = Mono.empty();
        empty.subscribe(
            s -> System.out.println("Received: " + s),
            e -> System.out.println("Error: " + e),
            () -> System.out.println("Completed (empty)")
        );

        // 3. Mono.error() - emits an error
        System.out.println("\n--- Mono.error() ---");
        Mono<String> error = Mono.error(new RuntimeException("Something went wrong"));
        error.subscribe(
            s -> System.out.println("Received: " + s),
            e -> System.out.println("Error: " + e.getMessage())
        );

        // 4. Mono.fromSupplier() - lazy computation
        System.out.println("\n--- Mono.fromSupplier() ---");
        Mono<Long> fromSupplier = Mono.fromSupplier(() -> {
            System.out.println("Computing timestamp...");
            return System.currentTimeMillis();
        });
        System.out.println("Mono created, not yet subscribed");
        Thread.sleep(100);
        fromSupplier.subscribe(ts -> System.out.println("Timestamp: " + ts));

        // 5. Mono.fromCallable() - can throw checked exceptions
        System.out.println("\n--- Mono.fromCallable() ---");
        Mono<String> fromCallable = Mono.fromCallable(() -> {
            // Simulating IO operation that could throw
            return "Result from callable";
        });
        fromCallable.subscribe(System.out::println);

        // 6. Mono.fromFuture() - from CompletableFuture
        System.out.println("\n--- Mono.fromFuture() ---");
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Result from future";
        });
        Mono<String> fromFuture = Mono.fromFuture(future);
        fromFuture.subscribe(System.out::println);
        Thread.sleep(200);  // Wait for async completion

        // 7. Mono.justOrEmpty() - from Optional
        System.out.println("\n--- Mono.justOrEmpty() ---");
        Optional<String> presentOptional = Optional.of("Present value");
        Optional<String> emptyOptional = Optional.empty();

        Mono.justOrEmpty(presentOptional)
            .subscribe(
                s -> System.out.println("Present: " + s),
                e -> {},
                () -> System.out.println("Completed")
            );

        Mono.justOrEmpty(emptyOptional)
            .subscribe(
                s -> System.out.println("Empty: " + s),
                e -> {},
                () -> System.out.println("Completed (was empty)")
            );

        // 8. Mono.defer() - create fresh Mono on each subscription
        System.out.println("\n--- Mono.defer() ---");
        Mono<Long> deferred = Mono.defer(() -> Mono.just(System.currentTimeMillis()));
        deferred.subscribe(ts -> System.out.println("First subscription: " + ts));
        Thread.sleep(100);
        deferred.subscribe(ts -> System.out.println("Second subscription: " + ts));
    }
}
```

### Exercise 2.2: Predict the Output

Before running the code, predict:
1. When will "Computing timestamp..." be printed?
2. What's the difference between the two defer() subscription timestamps?

Run the code and verify your predictions.

---

## Part 3: Creating Flux (15 min)

### Exercise 3.1: Flux Factory Methods

Create `src/main/java/com/example/lab/FluxCreation.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FluxCreation {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Flux Factory Methods ===\n");

        // 1. Flux.just() - from known values
        System.out.println("--- Flux.just() ---");
        Flux<String> just = Flux.just("A", "B", "C");
        just.subscribe(s -> System.out.print(s + " "));
        System.out.println();

        // 2. Flux.fromIterable() - from collection
        System.out.println("\n--- Flux.fromIterable() ---");
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
        Flux<Integer> fromList = Flux.fromIterable(list);
        fromList.subscribe(i -> System.out.print(i + " "));
        System.out.println();

        // 3. Flux.fromArray() - from array
        System.out.println("\n--- Flux.fromArray() ---");
        String[] array = {"X", "Y", "Z"};
        Flux<String> fromArray = Flux.fromArray(array);
        fromArray.subscribe(s -> System.out.print(s + " "));
        System.out.println();

        // 4. Flux.range() - generate sequence
        System.out.println("\n--- Flux.range() ---");
        Flux<Integer> range = Flux.range(10, 5);  // 10, 11, 12, 13, 14
        range.subscribe(i -> System.out.print(i + " "));
        System.out.println();

        // 5. Flux.empty() - no elements
        System.out.println("\n--- Flux.empty() ---");
        Flux<String> empty = Flux.empty();
        empty.subscribe(
            s -> System.out.println("Received: " + s),
            e -> System.out.println("Error"),
            () -> System.out.println("Completed (empty flux)")
        );

        // 6. Flux.interval() - emit every duration
        System.out.println("\n--- Flux.interval() ---");
        System.out.println("Emitting every 200ms for 1 second:");
        Flux<Long> interval = Flux.interval(Duration.ofMillis(200));
        var disposable = interval.subscribe(i -> System.out.print(i + " "));
        Thread.sleep(1000);
        disposable.dispose();  // Stop the interval
        System.out.println("\n(disposed)");

        // 7. Flux.generate() - programmatic generation with state
        System.out.println("\n--- Flux.generate() ---");
        Flux<String> generated = Flux.generate(
            () -> 0,  // Initial state
            (state, sink) -> {
                sink.next("Value-" + state);
                if (state >= 4) sink.complete();
                return state + 1;
            }
        );
        generated.subscribe(System.out::println);

        // 8. Flux.create() - push-style with backpressure
        System.out.println("\n--- Flux.create() ---");
        Flux<String> created = Flux.create(sink -> {
            sink.next("First");
            sink.next("Second");
            sink.next("Third");
            sink.complete();
        });
        created.subscribe(System.out::println);

        // 9. Flux.concat() - sequential combination
        System.out.println("\n--- Flux.concat() ---");
        Flux<Integer> concat = Flux.concat(
            Flux.range(1, 3),
            Flux.range(10, 3)
        );
        concat.subscribe(i -> System.out.print(i + " "));
        System.out.println();

        // 10. Flux.merge() - interleaved combination
        System.out.println("\n--- Flux.merge() (simulated with delayElements) ---");
        // Note: merge interleaves based on timing; with immediate sources it may look like concat
        Flux<String> merged = Flux.merge(
            Flux.just("A1", "A2", "A3"),
            Flux.just("B1", "B2", "B3")
        );
        merged.subscribe(s -> System.out.print(s + " "));
        System.out.println();
    }
}
```

### Exercise 3.2: Create Your Own Flux

Add to the file:

```java
// TODO: Create a Flux that emits the first 10 Fibonacci numbers
// Hint: Use Flux.generate() with state as a Pair/Array of last two numbers

// TODO: Create a Flux from a Stream (careful - streams can only be consumed once!)

// TODO: Create a Flux that emits "tick" every 500ms but stops after 5 ticks
```

---

## Part 4: Nothing Happens Until Subscribe (20 min)

This is the most critical concept. Let's prove it thoroughly.

### Exercise 4.1: Demonstrating Laziness

Create `src/main/java/com/example/lab/NothingHappensUntilSubscribe.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class NothingHappensUntilSubscribe {

    public static void main(String[] args) {
        System.out.println("=== Nothing Happens Until Subscribe ===\n");

        demonstrateLaziness();
        demonstrateVoidMethodBug();
        demonstrateSideEffects();
    }

    private static void demonstrateLaziness() {
        System.out.println("--- Demonstrating Laziness ---");

        System.out.println("1. Creating Flux...");
        Flux<Integer> flux = Flux.range(1, 5)
            .doOnNext(i -> System.out.println("   Processing: " + i))
            .map(i -> {
                System.out.println("   Mapping: " + i);
                return i * 10;
            });

        System.out.println("2. Flux created. Has anything been processed?");
        System.out.println("   (If you see 'Processing' or 'Mapping', something is wrong!)");
        System.out.println();

        System.out.println("3. Now subscribing...");
        flux.subscribe(i -> System.out.println("   Received: " + i));

        System.out.println("4. Subscription complete.\n");
    }

    private static void demonstrateVoidMethodBug() {
        System.out.println("--- The Void Method Bug ---");

        // This is a COMMON BUG!
        System.out.println("Calling buggyMethod()...");
        buggyMethod();
        System.out.println("buggyMethod() returned. Was the email sent?");
        System.out.println("(Check: Did you see 'Sending email'?)\n");

        // This is the CORRECT approach
        System.out.println("Calling correctMethod()...");
        Mono<Void> result = correctMethod();
        System.out.println("correctMethod() returned Mono. Email not sent yet.");
        result.subscribe();
        System.out.println("After subscribe: Email was sent.\n");
    }

    // BUG: This method does NOTHING!
    private static void buggyMethod() {
        Mono.fromRunnable(() -> {
            System.out.println("   Sending email...");
            // Imagine actual email sending here
        });
        // No subscribe! Email is NEVER sent!
    }

    // CORRECT: Return the Mono, let caller subscribe
    private static Mono<Void> correctMethod() {
        return Mono.fromRunnable(() -> {
            System.out.println("   Sending email...");
        });
    }

    private static void demonstrateSideEffects() {
        System.out.println("--- Side Effects and Subscription Count ---");

        Flux<String> flux = Flux.just("A", "B", "C")
            .doOnNext(s -> System.out.println("   Side effect for: " + s));

        System.out.println("Subscribing first time:");
        flux.subscribe(s -> System.out.println("   Sub1 received: " + s));

        System.out.println("\nSubscribing second time:");
        flux.subscribe(s -> System.out.println("   Sub2 received: " + s));

        System.out.println("\nNotice: Side effects ran TWICE (once per subscription)");
        System.out.println("This is COLD publisher behavior.\n");
    }
}
```

### Exercise 4.2: Find the Bug

The following code has a bug. What's wrong?

```java
@Service
public class OrderService {

    private final OrderRepository repository;
    private final NotificationService notifications;

    public void processOrder(Order order) {
        repository.save(order)
            .flatMap(saved -> notifications.sendConfirmation(saved))
            .doOnSuccess(v -> log.info("Order processed: {}", order.getId()));
    }
}
```

Write down the bug and how you would fix it.

---

## Part 5: Cold vs Hot Behavior (20 min)

### Exercise 5.1: Cold Publisher Demonstration

Create `src/main/java/com/example/lab/ColdVsHot.java`:

```java
package com.example.lab;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

public class ColdVsHot {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Cold vs Hot Publishers ===\n");

        demonstrateCold();
        demonstrateHot();
        demonstrateColdToHotCache();
        demonstrateColdToHotShare();
    }

    private static void demonstrateCold() {
        System.out.println("--- Cold Publisher (Default) ---");
        System.out.println("Each subscriber gets fresh data from the beginning.\n");

        Flux<Integer> cold = Flux.range(1, 3)
            .doOnSubscribe(s -> System.out.println("New subscription started!"))
            .doOnNext(i -> System.out.println("Generating: " + i));

        System.out.println("First subscriber:");
        cold.subscribe(i -> System.out.println("  Sub1 received: " + i));

        System.out.println("\nSecond subscriber:");
        cold.subscribe(i -> System.out.println("  Sub2 received: " + i));

        System.out.println("\nNotice: Both subscribers see ALL elements.");
        System.out.println("Notice: 'Generating' printed 6 times (3 x 2 subscribers).\n");
    }

    private static void demonstrateHot() throws InterruptedException {
        System.out.println("--- Hot Publisher (Sinks) ---");
        System.out.println("Subscribers only see data from when they subscribed.\n");

        // Create a hot source using Sinks
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> hot = sink.asFlux();

        // Emit before any subscribers
        System.out.println("Emitting 'Message 1' and 'Message 2' (no subscribers yet)");
        sink.tryEmitNext("Message 1");
        sink.tryEmitNext("Message 2");

        // First subscriber joins
        System.out.println("\nFirst subscriber joins:");
        hot.subscribe(msg -> System.out.println("  Sub1: " + msg));

        // Emit more
        System.out.println("\nEmitting 'Message 3':");
        sink.tryEmitNext("Message 3");

        // Second subscriber joins
        System.out.println("\nSecond subscriber joins:");
        hot.subscribe(msg -> System.out.println("  Sub2: " + msg));

        // Emit more
        System.out.println("\nEmitting 'Message 4':");
        sink.tryEmitNext("Message 4");

        sink.tryEmitComplete();

        System.out.println("\nNotice: Sub1 missed Messages 1-2, saw 3-4");
        System.out.println("Notice: Sub2 missed Messages 1-3, saw only 4\n");
    }

    private static void demonstrateColdToHotCache() throws InterruptedException {
        System.out.println("--- Cold to Hot with cache() ---");
        System.out.println("First subscriber triggers execution, others reuse result.\n");

        Flux<Integer> cold = Flux.range(1, 3)
            .doOnSubscribe(s -> System.out.println("Subscription triggered!"))
            .doOnNext(i -> System.out.println("Computing: " + i));

        Flux<Integer> cached = cold.cache();

        System.out.println("First subscriber:");
        cached.subscribe(i -> System.out.println("  Sub1 received: " + i));

        System.out.println("\nSecond subscriber (should reuse cached values):");
        cached.subscribe(i -> System.out.println("  Sub2 received: " + i));

        System.out.println("\nNotice: 'Computing' only printed 3 times (not 6).");
        System.out.println("The second subscriber got cached results.\n");
    }

    private static void demonstrateColdToHotShare() throws InterruptedException {
        System.out.println("--- Cold to Hot with share() ---");
        System.out.println("Multiple subscribers share the same execution.\n");

        Flux<Long> cold = Flux.interval(Duration.ofMillis(200))
            .doOnNext(i -> System.out.println("Emitting: " + i))
            .take(5);

        Flux<Long> shared = cold.share();

        System.out.println("First subscriber joins immediately:");
        shared.subscribe(i -> System.out.println("  Sub1: " + i));

        Thread.sleep(500);

        System.out.println("\nSecond subscriber joins after 500ms:");
        shared.subscribe(i -> System.out.println("  Sub2: " + i));

        Thread.sleep(1500);

        System.out.println("\nNotice: Sub2 missed some early values.");
        System.out.println("Both subscribers shared the same stream.\n");
    }
}
```

### Exercise 5.2: When to Use Cold vs Hot

For each scenario, decide if you need cold or hot:

1. **Database query for user profile**: Cold or Hot?
2. **Real-time stock price stream**: Cold or Hot?
3. **HTTP call to external API that costs money per call**: Cold or Hot?
4. **WebSocket message stream**: Cold or Hot?
5. **Reading a file and returning its contents**: Cold or Hot?

Write your answers and reasoning.

---

## Part 6: Subscription Patterns (15 min)

### Exercise 6.1: Different Subscribe Methods

Create `src/main/java/com/example/lab/SubscriptionPatterns.java`:

```java
package com.example.lab;

import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class SubscriptionPatterns {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Subscription Patterns ===\n");

        basicSubscribe();
        subscribeWithHandlers();
        subscribeWithBackpressure();
        cancelableSubscription();
    }

    private static void basicSubscribe() {
        System.out.println("--- Basic Subscribe ---");

        Flux<Integer> flux = Flux.range(1, 3);

        // Simplest form - just trigger execution
        System.out.println("1. subscribe() with no args:");
        flux.subscribe();
        System.out.println("   (elements processed but not printed)\n");

        // Consumer for each element
        System.out.println("2. subscribe(consumer):");
        flux.subscribe(i -> System.out.println("   Received: " + i));
        System.out.println();
    }

    private static void subscribeWithHandlers() {
        System.out.println("--- Subscribe with Handlers ---");

        Flux<Integer> successFlux = Flux.range(1, 3);
        Flux<Integer> errorFlux = Flux.range(1, 3)
            .map(i -> {
                if (i == 2) throw new RuntimeException("Error at 2!");
                return i;
            });

        System.out.println("Success case:");
        successFlux.subscribe(
            i -> System.out.println("   onNext: " + i),
            e -> System.out.println("   onError: " + e.getMessage()),
            () -> System.out.println("   onComplete!")
        );

        System.out.println("\nError case:");
        errorFlux.subscribe(
            i -> System.out.println("   onNext: " + i),
            e -> System.out.println("   onError: " + e.getMessage()),
            () -> System.out.println("   onComplete!")
        );
        System.out.println();
    }

    private static void subscribeWithBackpressure() {
        System.out.println("--- Subscribe with Backpressure Control ---");

        Flux<Integer> flux = Flux.range(1, 10);

        System.out.println("Requesting only 3 elements at a time:");
        flux.subscribe(new BaseSubscriber<Integer>() {
            private int count = 0;

            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                System.out.println("   Subscribed! Requesting 3...");
                request(3);
            }

            @Override
            protected void hookOnNext(Integer value) {
                System.out.println("   Received: " + value);
                count++;
                if (count % 3 == 0 && count < 9) {
                    System.out.println("   Requesting 3 more...");
                    request(3);
                }
            }

            @Override
            protected void hookOnComplete() {
                System.out.println("   Complete!");
            }
        });
        System.out.println();
    }

    private static void cancelableSubscription() throws InterruptedException {
        System.out.println("--- Cancelable Subscription ---");

        Flux<Long> infinite = Flux.interval(Duration.ofMillis(200))
            .doOnCancel(() -> System.out.println("   Subscription cancelled!"));

        System.out.println("Starting infinite flux, will cancel after 1 second:");
        var disposable = infinite.subscribe(i -> System.out.println("   Tick: " + i));

        Thread.sleep(1000);
        System.out.println("   Cancelling...");
        disposable.dispose();

        System.out.println("   Is disposed? " + disposable.isDisposed());
        Thread.sleep(500);  // Prove no more ticks after dispose
        System.out.println();
    }
}
```

### Exercise 6.2: Implement Custom Backpressure

Modify the backpressure example to:
1. Request only 2 elements at a time
2. Add a 500ms delay between batches
3. Stop after receiving 6 elements (cancel the subscription)

---

## Part 7: Reflection and Key Takeaways (5 min)

### Discussion Questions

1. **What surprised you most about Reactor's behavior?**
   - The laziness?
   - The cold/hot distinction?
   - The need to explicitly subscribe?

2. **How does understanding "nothing happens until subscribe" change how you'll write code?**

3. **In what situations would you use:**
   - `cache()` vs `share()`?
   - `Mono.fromSupplier()` vs `Mono.just()`?
   - `Flux.generate()` vs `Flux.create()`?

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Mono for 0-1, Flux for 0-N                                            │
│     The type signature communicates cardinality.                          │
│                                                                            │
│  2. Reactive types are LAZY                                               │
│     Building a pipeline creates a blueprint, not execution.               │
│     subscribe() triggers execution.                                       │
│                                                                            │
│  3. Cold by default                                                        │
│     Each subscriber gets fresh execution.                                 │
│     Use cache(), share(), or Sinks for hot behavior.                     │
│                                                                            │
│  4. Return, don't subscribe                                               │
│     In most cases, return Mono/Flux from your methods.                   │
│     Let the framework or caller handle subscription.                      │
│                                                                            │
│  5. Factory methods hide complexity                                       │
│     Use Mono.just(), Flux.range(), etc. instead of                       │
│     implementing Publisher yourself.                                      │
│                                                                            │
│  6. Subscription gives you control                                        │
│     Cancel anytime, control backpressure with request(n).                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

If you finish early:

### Challenge 1: HTTP Simulation

Create a `Mono<User>` that simulates an HTTP call:
- Takes 500ms to complete
- Returns a User object
- Demonstrate that subscribing twice makes two "calls"
- Then use `cache()` to ensure only one "call"

### Challenge 2: Event Stream

Create a hot `Flux<Event>` that:
- Emits events when you call a method
- Supports multiple subscribers
- Late subscribers don't see past events
- Use `Sinks.Many`

### Challenge 3: Retry with Logging

Create a `Mono` that:
- Has a 50% chance of failing
- Retries up to 3 times
- Logs each attempt
- (Hint: Use `Mono.defer()` and `retry()`)

---

## Running the Examples

```bash
# Compile
mvn compile

# Run specific class
mvn exec:java -Dexec.mainClass="com.example.lab.MonoCreation"
mvn exec:java -Dexec.mainClass="com.example.lab.FluxCreation"
mvn exec:java -Dexec.mainClass="com.example.lab.NothingHappensUntilSubscribe"
mvn exec:java -Dexec.mainClass="com.example.lab.ColdVsHot"
mvn exec:java -Dexec.mainClass="com.example.lab.SubscriptionPatterns"
```

---

## What's Next?

In Chapter 5, we'll learn to **think in streams**—using operators like map, flatMap, filter, and zip to transform and combine data. The foundation you've built here (understanding Mono, Flux, laziness, cold/hot) will make operators feel natural.

---

## Summary

In this lab, you:

1. ✅ Set up a Reactor project
2. ✅ Created Mono using various factory methods
3. ✅ Created Flux using various factory methods
4. ✅ Proved that nothing happens until subscribe
5. ✅ Understood cold vs hot publisher behavior
6. ✅ Practiced different subscription patterns

**Congratulations!** You now have practical experience with Project Reactor's core types. This foundation will serve you well as you learn operators and build reactive applications.
