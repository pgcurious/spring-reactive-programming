# Chapter 4: Meet Project Reactor

> "Simplicity is prerequisite for reliability." — Edsger W. Dijkstra

In Part I, we built a deep understanding of why reactive programming exists and what problems it solves. We implemented Publisher and Subscriber from scratch and experienced the complexity firsthand. Now it's time to meet the tool that handles this complexity for us: **Project Reactor**.

By the end of this chapter, you'll understand why Spring chose Reactor, how Mono and Flux work, and—most importantly—the crucial concept that **nothing happens until you subscribe**.

---

## 4.1 Why Reactor? (Among All Reactive Libraries)

Before diving into Reactor's API, let's understand where it fits in the reactive landscape.

### The Reactive Library Landscape

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE LIBRARIES FOR JAVA                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  RxJava (ReactiveX)                                                        │
│  ─────────────────────                                                     │
│  • First major reactive library for Java (2013)                           │
│  • Port of Microsoft's Rx.NET                                             │
│  • Large community, extensive documentation                               │
│  • Popular in Android development                                         │
│  • Backpressure added later (RxJava 2, Flowable)                         │
│                                                                            │
│  Project Reactor                                                           │
│  ─────────────────────                                                     │
│  • Created by Pivotal (now VMware, Spring team)                          │
│  • Designed from scratch for Java 8+ and Reactive Streams                │
│  • Native integration with Spring Framework                               │
│  • Backpressure-first design                                              │
│  • Powers Spring WebFlux                                                  │
│                                                                            │
│  Akka Streams                                                              │
│  ─────────────────────                                                     │
│  • Part of the Akka toolkit (Lightbend)                                  │
│  • Graph-based stream processing                                          │
│  • Popular in Scala ecosystem                                             │
│  • More complex mental model                                              │
│                                                                            │
│  Mutiny (SmallRye)                                                         │
│  ─────────────────────                                                     │
│  • Newer library, event-driven API                                        │
│  • Used by Quarkus                                                        │
│  • Simpler API, less operators                                            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Spring Chose Reactor

Spring Framework 5 and Spring Boot 2 introduced reactive support, and the Spring team chose Reactor as their foundation. Here's why:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHY REACTOR FOR SPRING?                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. SAME TEAM                                                              │
│     ───────────                                                            │
│     Reactor is developed by the same team that builds Spring.             │
│     Tight integration, aligned roadmaps, shared testing.                  │
│                                                                            │
│  2. REACTIVE STREAMS NATIVE                                                │
│     ─────────────────────────                                              │
│     Designed from day one for Reactive Streams specification.             │
│     No adapters needed, no legacy baggage.                                │
│                                                                            │
│  3. JAVA 8+ OPTIMIZED                                                      │
│     ──────────────────                                                     │
│     Takes full advantage of lambdas, method references, and               │
│     functional interfaces. Modern, clean API.                             │
│                                                                            │
│  4. TWO-TYPE SIMPLICITY                                                    │
│     ─────────────────────                                                  │
│     Mono<T> for 0..1 elements, Flux<T> for 0..N elements.                │
│     RxJava has more types (Observable, Flowable, Single, Maybe, etc.)    │
│                                                                            │
│  5. FIRST-CLASS CONTEXT SUPPORT                                            │
│     ───────────────────────────                                            │
│     Context propagation built in (critical for tracing, security).       │
│     Spring Security, Sleuth/Micrometer integration.                       │
│                                                                            │
│  6. EXCELLENT TESTING SUPPORT                                              │
│     ─────────────────────────                                              │
│     StepVerifier for testing reactive pipelines.                          │
│     Virtual time for testing delays without waiting.                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Reactor vs. RxJava: A Quick Comparison

| Aspect | Project Reactor | RxJava 3 |
|--------|----------------|----------|
| Primary types | Mono, Flux | Single, Maybe, Completable, Flowable, Observable |
| Backpressure | Always (Mono/Flux) | Flowable (with), Observable (without) |
| Spring integration | Native | Requires adapters |
| Android support | Possible but not focus | Excellent |
| Operator count | ~400 | ~400 |
| Testing | StepVerifier | TestObserver/TestSubscriber |

**Bottom line**: If you're in the Spring ecosystem, Reactor is the natural choice. The integration is seamless, and you'll find reactive support throughout Spring's components.

---

## 4.2 Mono and Flux: The Two Building Blocks

Reactor's API centers on two types: `Mono<T>` and `Flux<T>`. Understanding when to use each is fundamental.

### Mono<T>: Zero or One Element

A `Mono<T>` represents an asynchronous computation that will emit **at most one element** (or an error, or nothing at all).

```java
// A Mono that will emit one value
Mono<String> greeting = Mono.just("Hello, Reactor!");

// A Mono that will emit nothing (completes immediately)
Mono<String> empty = Mono.empty();

// A Mono that will emit an error
Mono<String> error = Mono.error(new RuntimeException("Oops!"));

// A Mono that defers computation (lazy)
Mono<Long> now = Mono.fromSupplier(System::currentTimeMillis);
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MONO: 0 OR 1 ELEMENT                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Possible outcomes:                                                        │
│                                                                            │
│  1. One element, then complete:                                            │
│     ──[value]──|                                                          │
│                                                                            │
│  2. Empty (no element), then complete:                                     │
│     ──|                                                                    │
│                                                                            │
│  3. Error:                                                                 │
│     ──X                                                                    │
│                                                                            │
│  Legend: ─ = time, | = complete, X = error                                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Not Just Use Optional?

You might wonder: "Java has `Optional<T>` for 0-or-1 values. Why do I need `Mono<T>`?"

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MONO vs OPTIONAL                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Optional<T>:                                                              │
│  ─────────────                                                             │
│  • Synchronous - value is already computed                                │
│  • Eager - computation happened before you got the Optional               │
│  • No operators - limited transformation capabilities                     │
│  • No error channel - exceptions propagate normally                       │
│                                                                            │
│  Mono<T>:                                                                  │
│  ─────────                                                                 │
│  • Asynchronous - value will be computed when subscribed                  │
│  • Lazy - computation only happens on subscribe                           │
│  • Rich operators - map, flatMap, filter, zip, etc.                      │
│  • Error channel - errors are first-class signals                        │
│  • Backpressure - subscriber controls the pace                           │
│                                                                            │
│  Example:                                                                  │
│                                                                            │
│  // Optional: The database call happens NOW, blocking                     │
│  Optional<User> user = userRepository.findById(id);                       │
│                                                                            │
│  // Mono: The database call happens LATER, non-blocking                   │
│  Mono<User> userMono = reactiveUserRepository.findById(id);              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### When to Use Mono

```java
// API call that returns one user or not found
Mono<User> findUserById(String id);

// Operation that succeeds or fails (no return value)
Mono<Void> deleteUser(String id);

// Single computation result
Mono<Integer> calculateTotal(Order order);

// External service call with single response
Mono<WeatherReport> getWeather(String city);
```

### Flux<T>: Zero to N Elements

A `Flux<T>` represents an asynchronous sequence of **zero to many elements**.

```java
// A Flux with known elements
Flux<String> names = Flux.just("Alice", "Bob", "Charlie");

// A Flux from a collection
Flux<Integer> numbers = Flux.fromIterable(List.of(1, 2, 3, 4, 5));

// A Flux that generates a range
Flux<Integer> range = Flux.range(1, 100);

// A Flux that emits every second
Flux<Long> ticks = Flux.interval(Duration.ofSeconds(1));

// A Flux from a database query (many results)
Flux<Order> orders = orderRepository.findByCustomerId(customerId);
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FLUX: 0 TO N ELEMENTS                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Possible outcomes:                                                        │
│                                                                            │
│  1. Multiple elements, then complete:                                      │
│     ──[1]──[2]──[3]──[4]──|                                               │
│                                                                            │
│  2. Empty (no elements), then complete:                                    │
│     ──|                                                                    │
│                                                                            │
│  3. Infinite stream (no completion):                                       │
│     ──[1]──[2]──[3]──[4]──[5]──►                                          │
│                                                                            │
│  4. Error at some point:                                                   │
│     ──[1]──[2]──X                                                         │
│                                                                            │
│  Legend: ─ = time, | = complete, X = error, ► = ongoing                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Not Just Use List?

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FLUX vs LIST                                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  List<T>:                                                                  │
│  ─────────                                                                 │
│  • Synchronous - all elements are already in memory                       │
│  • Bounded - finite size, known at creation                               │
│  • No streaming - must wait for all elements before processing           │
│  • Memory intensive - holds all elements at once                         │
│                                                                            │
│  Flux<T>:                                                                  │
│  ─────────                                                                 │
│  • Asynchronous - elements arrive over time                               │
│  • Can be unbounded - infinite streams possible                          │
│  • Streaming - process elements as they arrive                           │
│  • Memory efficient - only holds current element(s)                      │
│  • Backpressure - control flow rate                                      │
│                                                                            │
│  Example:                                                                  │
│                                                                            │
│  // List: Must load ALL 1 million users into memory, then return         │
│  List<User> users = userRepository.findAll();  // OOM risk!              │
│                                                                            │
│  // Flux: Stream users one at a time, process as they arrive             │
│  Flux<User> users = reactiveUserRepository.findAll();                    │
│  users.subscribe(user -> process(user));  // Memory efficient!           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### When to Use Flux

```java
// Query that returns multiple results
Flux<Order> findOrdersByStatus(OrderStatus status);

// Event stream (potentially infinite)
Flux<StockPrice> getPriceStream(String symbol);

// Reading lines from a file
Flux<String> readLines(Path file);

// Polling an external service
Flux<Message> pollMessages(Duration interval);
```

### The Key Insight

**Think of Mono and Flux as "future collections that can push to you."**

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE MENTAL MODEL                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Traditional (Pull):                                                       │
│                                                                            │
│  Optional<User> user = repository.findById(id);  // I ask, I wait        │
│  List<Order> orders = repository.findAll();       // I ask, I wait        │
│                                                                            │
│  Reactive (Push):                                                          │
│                                                                            │
│  Mono<User> user = repository.findById(id);       // Describe reaction   │
│  Flux<Order> orders = repository.findAll();       // Describe reaction   │
│                                                                            │
│  user.subscribe(u -> handle(u));   // When available, call me            │
│  orders.subscribe(o -> handle(o)); // As each arrives, call me           │
│                                                                            │
│  The shift: From "give me data NOW" to "call me WHEN you have data"      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Mono + Flux = Complete Coverage

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    CHOOSING BETWEEN MONO AND FLUX                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Question: "How many elements can this produce?"                          │
│                                                                            │
│  ┌───────────────┐                                                         │
│  │  0 or 1?      │──────► Mono<T>                                         │
│  └───────────────┘                                                         │
│         │                                                                  │
│         │ No                                                               │
│         ▼                                                                  │
│  ┌───────────────┐                                                         │
│  │  0 to N?      │──────► Flux<T>                                         │
│  └───────────────┘                                                         │
│                                                                            │
│  Examples:                                                                 │
│                                                                            │
│  findById(id)        → Mono (one user or none)                            │
│  findAll()           → Flux (multiple users)                              │
│  save(user)          → Mono (returns saved entity)                        │
│  delete(id)          → Mono<Void> (no return, just completion)            │
│  findByStatus(s)     → Flux (multiple matching)                           │
│  count()             → Mono<Long> (single aggregate)                      │
│  existsById(id)      → Mono<Boolean> (single boolean)                     │
│  streamEvents()      → Flux (continuous stream)                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 4.3 Cold vs. Hot Publishers

This is one of the most important concepts in reactive programming, and it's often misunderstood. The distinction between **cold** and **hot** publishers fundamentally changes how your reactive code behaves.

### Cold Publishers: Replay from Start

A **cold publisher** generates data fresh for each subscriber. Each subscription triggers the entire data generation process from the beginning.

```java
// This is a COLD publisher
Flux<Integer> coldFlux = Flux.range(1, 3)
    .doOnSubscribe(s -> System.out.println("Subscribed!"))
    .doOnNext(i -> System.out.println("Generating: " + i));

System.out.println("--- First subscriber ---");
coldFlux.subscribe(i -> System.out.println("Subscriber 1 received: " + i));

System.out.println("--- Second subscriber ---");
coldFlux.subscribe(i -> System.out.println("Subscriber 2 received: " + i));

// Output:
// --- First subscriber ---
// Subscribed!
// Generating: 1
// Subscriber 1 received: 1
// Generating: 2
// Subscriber 1 received: 2
// Generating: 3
// Subscriber 1 received: 3
// --- Second subscriber ---
// Subscribed!
// Generating: 1
// Subscriber 2 received: 1
// Generating: 2
// Subscriber 2 received: 2
// Generating: 3
// Subscriber 2 received: 3
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    COLD PUBLISHER: VIDEO ON DEMAND                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Analogy: Watching a movie on Netflix                                     │
│                                                                            │
│  Person A starts watching at 8:00 PM                                      │
│     └──► Sees the movie from the beginning                                │
│                                                                            │
│  Person B starts watching at 9:00 PM                                      │
│     └──► Also sees the movie from the beginning                           │
│                                                                            │
│  Each viewer gets their own "copy" of the stream.                         │
│                                                                            │
│  ┌───────────────┐                                                         │
│  │   Publisher   │                                                         │
│  │   (Netflix)   │                                                         │
│  └───────┬───────┘                                                         │
│          │                                                                 │
│    subscribe()                                                             │
│          │                                                                 │
│          ▼                                                                 │
│  ┌───────────────┐     Fresh stream created for THIS subscriber           │
│  │ ──[1]──[2]──  │ ──────────────────────────────────────────────►        │
│  └───────────────┘                                                         │
│                                                                            │
│  Characteristics:                                                          │
│  • Data generation happens per subscriber                                 │
│  • Each subscriber sees all elements from the start                       │
│  • Common for: DB queries, HTTP calls, file reads                        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Hot Publishers: Live Stream

A **hot publisher** generates data regardless of subscribers. Subscribers only see elements emitted after they subscribe.

```java
// Create a hot publisher using a Processor/Sink
Sinks.Many<String> hotSource = Sinks.many().multicast().onBackpressureBuffer();
Flux<String> hotFlux = hotSource.asFlux();

// Emit before any subscribers
hotSource.tryEmitNext("Message 1");
hotSource.tryEmitNext("Message 2");

// First subscriber joins
System.out.println("--- First subscriber joins ---");
hotFlux.subscribe(msg -> System.out.println("Subscriber 1: " + msg));

// More emissions
hotSource.tryEmitNext("Message 3");
hotSource.tryEmitNext("Message 4");

// Second subscriber joins
System.out.println("--- Second subscriber joins ---");
hotFlux.subscribe(msg -> System.out.println("Subscriber 2: " + msg));

// More emissions
hotSource.tryEmitNext("Message 5");

// Output:
// --- First subscriber joins ---
// Subscriber 1: Message 3
// Subscriber 1: Message 4
// --- Second subscriber joins ---
// Subscriber 1: Message 5
// Subscriber 2: Message 5
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    HOT PUBLISHER: LIVE BROADCAST                           │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Analogy: Watching live TV                                                │
│                                                                            │
│  The broadcast is happening NOW, regardless of who's watching.            │
│                                                                            │
│  Person A tunes in at 8:00 PM                                             │
│     └──► Sees whatever is airing at 8:00 PM                               │
│                                                                            │
│  Person B tunes in at 8:30 PM                                             │
│     └──► Misses the first 30 minutes, sees from 8:30 onwards              │
│                                                                            │
│  Timeline:                                                                 │
│  ──[1]──[2]──[3]──[4]──[5]──[6]──[7]──►                                   │
│                    ↑                                                       │
│                    └── Subscriber A joins here, sees [4], [5], [6], [7]  │
│                              ↑                                             │
│                              └── Subscriber B joins here, sees [6], [7]  │
│                                                                            │
│  Characteristics:                                                          │
│  • Data generation is independent of subscribers                          │
│  • Late subscribers miss earlier elements                                 │
│  • Common for: Events, sensor data, stock prices, user actions           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Does This Matter?

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    COLD vs HOT: IMPLICATIONS                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  COLD (Default for most Reactor operations):                              │
│  ─────────────────────────────────────────────                             │
│                                                                            │
│  Mono<User> user = webClient.get()                                        │
│      .uri("/users/123")                                                    │
│      .retrieve()                                                           │
│      .bodyToMono(User.class);                                             │
│                                                                            │
│  // DANGER: This makes TWO HTTP calls!                                    │
│  user.subscribe(u -> log.info("First: {}", u));                           │
│  user.subscribe(u -> log.info("Second: {}", u));                          │
│                                                                            │
│  // FIX: Use cache() to make it hot (share result)                        │
│  Mono<User> cachedUser = user.cache();                                    │
│  cachedUser.subscribe(u -> log.info("First: {}", u));                     │
│  cachedUser.subscribe(u -> log.info("Second: {}", u));  // Reuses result │
│                                                                            │
│  HOT (Events, real-time data):                                            │
│  ─────────────────────────────                                             │
│                                                                            │
│  Flux<StockPrice> prices = stockService.getPriceStream("AAPL");           │
│                                                                            │
│  // Late subscriber misses earlier prices                                 │
│  // This is usually the desired behavior for live data                    │
│                                                                            │
│  // If you need history, use replay()                                     │
│  Flux<StockPrice> withHistory = prices.replay(10).autoConnect();          │
│  // New subscribers get last 10 prices, then live updates                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Converting Between Cold and Hot

```java
// Cold → Hot with share()
Flux<Long> cold = Flux.interval(Duration.ofSeconds(1));
Flux<Long> hot = cold.share();  // Multiple subscribers share the stream

// Cold → Hot with cache()
Mono<User> coldMono = fetchUser();
Mono<User> hotMono = coldMono.cache();  // First subscriber triggers, others reuse

// Cold → Hot with replay()
Flux<Integer> coldFlux = Flux.range(1, 10);
Flux<Integer> hotFlux = coldFlux.replay(3).autoConnect();  // Replay last 3
```

### Summary Table

| Aspect | Cold Publisher | Hot Publisher |
|--------|---------------|---------------|
| Data generation | On subscribe | Independent of subscribers |
| Multiple subscribers | Each gets full stream | Share the stream |
| Late subscribers | See all data | Miss earlier data |
| Use cases | DB queries, HTTP calls | Events, sensors, live feeds |
| Default in Reactor | Yes | No (must convert explicitly) |
| Analogy | Netflix (on-demand) | Live TV broadcast |

---

## 4.4 Nothing Happens Until You Subscribe

This is the most critical concept in reactive programming, and the source of many beginner mistakes.

### The Lazy Nature of Reactive Streams

```java
Flux<Integer> flux = Flux.range(1, 5)
    .map(i -> {
        System.out.println("Mapping: " + i);
        return i * 2;
    })
    .filter(i -> {
        System.out.println("Filtering: " + i);
        return i > 4;
    });

System.out.println("Pipeline built. Nothing happened yet!");

// At this point, ZERO output has been printed
// The map and filter functions have NOT been called

flux.subscribe(i -> System.out.println("Received: " + i));

// NOW the output appears:
// Pipeline built. Nothing happened yet!
// Mapping: 1
// Filtering: 2
// Mapping: 2
// Filtering: 4
// Mapping: 3
// Filtering: 6
// Received: 6
// ... and so on
```

### The Blueprint Analogy

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BLUEPRINTS vs BUILDINGS                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  IMPERATIVE CODE (Building):                                               │
│  ───────────────────────────                                               │
│                                                                            │
│  List<Integer> results = new ArrayList<>();                               │
│  for (int i = 1; i <= 5; i++) {         // Immediately executes          │
│      int doubled = i * 2;                // Immediately computes          │
│      if (doubled > 4) {                  // Immediately checks            │
│          results.add(doubled);           // Immediately adds              │
│      }                                                                     │
│  }                                                                         │
│  // By this line, all work is DONE                                        │
│                                                                            │
│  REACTIVE CODE (Blueprint):                                                │
│  ──────────────────────────                                                │
│                                                                            │
│  Flux<Integer> flux = Flux.range(1, 5)   // Defines "source: 1-5"        │
│      .map(i -> i * 2)                    // Defines "multiply by 2"      │
│      .filter(i -> i > 4);                // Defines "keep if > 4"        │
│  // By this line, NO WORK has been done!                                  │
│  // We just have a DESCRIPTION of what to do                              │
│                                                                            │
│  flux.subscribe(System.out::println);    // NOW work happens!            │
│                                                                            │
│  Think of it this way:                                                    │
│  ┌──────────────┐                        ┌──────────────┐                 │
│  │   BLUEPRINT  │   subscribe()          │   BUILDING   │                 │
│  │              │ ─────────────────────► │              │                 │
│  │  (Pipeline)  │   triggers execution   │  (Results)   │                 │
│  └──────────────┘                        └──────────────┘                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Common Mistake #1: Building but Never Subscribing

```java
@Service
public class UserService {

    private final ReactiveUserRepository repository;

    public void updateUserEmail(String userId, String newEmail) {
        // BUG! This does NOTHING!
        repository.findById(userId)
            .map(user -> {
                user.setEmail(newEmail);
                return user;
            })
            .flatMap(repository::save);
        // No subscribe() - nothing happens!
    }

    // CORRECT version:
    public Mono<User> updateUserEmail(String userId, String newEmail) {
        return repository.findById(userId)    // Return the Mono
            .map(user -> {
                user.setEmail(newEmail);
                return user;
            })
            .flatMap(repository::save);
        // Caller will subscribe, or WebFlux will subscribe automatically
    }
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    COMMON MISTAKE: NO SUBSCRIPTION                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ❌ WRONG (void method, no subscription):                                  │
│                                                                            │
│  public void sendEmail(String to, String message) {                       │
│      emailClient.send(to, message);  // Returns Mono<Void>               │
│      // Mono is ignored! Email is NEVER sent!                            │
│  }                                                                         │
│                                                                            │
│  ✅ CORRECT (return the Mono, let caller handle):                         │
│                                                                            │
│  public Mono<Void> sendEmail(String to, String message) {                 │
│      return emailClient.send(to, message);                                │
│  }                                                                         │
│                                                                            │
│  ✅ CORRECT (subscribe explicitly if fire-and-forget):                    │
│                                                                            │
│  public void sendEmailFireAndForget(String to, String message) {          │
│      emailClient.send(to, message)                                        │
│          .doOnError(e -> log.error("Failed to send", e))                  │
│          .subscribe();  // Explicit subscription                          │
│  }                                                                         │
│                                                                            │
│  Rule: If your method returns void but uses reactive types,               │
│        you probably have a bug!                                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Is Laziness a Feature?

Laziness might seem like a source of bugs, but it's actually a powerful feature:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BENEFITS OF LAZINESS                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. COMPOSITION                                                            │
│     ─────────────                                                          │
│     You can build complex pipelines from simple pieces:                   │
│                                                                            │
│     Flux<User> base = repository.findAll();                               │
│     Flux<User> active = base.filter(User::isActive);                      │
│     Flux<User> recent = active.filter(u -> u.lastLogin().isRecent());    │
│     Flux<String> names = recent.map(User::getName);                       │
│                                                                            │
│     // Nothing executed yet - just composition!                           │
│     names.subscribe(...);  // NOW it runs                                 │
│                                                                            │
│  2. EFFICIENCY                                                             │
│     ──────────────                                                         │
│     Don't compute what won't be used:                                     │
│                                                                            │
│     Flux<Data> data = expensiveComputation()                              │
│         .take(10);  // Only need first 10                                 │
│                                                                            │
│     // Computation stops after 10 elements!                               │
│                                                                            │
│  3. REUSABILITY                                                            │
│     ─────────────                                                          │
│     Same pipeline, different subscribers:                                 │
│                                                                            │
│     Flux<Order> orders = orderRepository.findRecent();                    │
│                                                                            │
│     orders.subscribe(o -> sendToWarehouse(o));                            │
│     orders.subscribe(o -> updateDashboard(o));                            │
│     orders.subscribe(o -> notifyCustomer(o));                             │
│                                                                            │
│  4. CANCELABILITY                                                          │
│     ──────────────                                                         │
│     Can cancel mid-stream:                                                │
│                                                                            │
│     Disposable subscription = flux.subscribe(...);                        │
│     // Later:                                                              │
│     subscription.dispose();  // Cancels the stream                        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Who Subscribes in Spring WebFlux?

In a Spring WebFlux application, you often don't call `subscribe()` explicitly:

```java
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        // No subscribe() needed!
        // WebFlux subscribes automatically when handling the request
        return userRepository.findById(id);
    }

    @GetMapping("/users")
    public Flux<User> getAllUsers() {
        // WebFlux subscribes and streams the response
        return userRepository.findAll();
    }
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHO SUBSCRIBES?                                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  In Spring WebFlux:                                                        │
│  ─────────────────                                                         │
│  The framework subscribes when the HTTP response needs to be written.     │
│  You just build and return the reactive pipeline.                         │
│                                                                            │
│  Request ──► Controller ──► Returns Mono/Flux ──► WebFlux subscribes      │
│                                                      │                     │
│                                                      ▼                     │
│                                               Response written             │
│                                                                            │
│  Manual subscription needed when:                                         │
│  ─────────────────────────────────                                         │
│  • Fire-and-forget operations (logging, metrics)                          │
│  • Testing (to verify results)                                            │
│  • Application initialization (loading config)                            │
│  • Background tasks (not tied to requests)                                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 4.5 Creating Mono and Flux

Now that you understand the concepts, let's look at the various ways to create Mono and Flux instances.

### Creating Mono

```java
// From a known value
Mono<String> just = Mono.just("Hello");

// Empty Mono (completes without emitting)
Mono<String> empty = Mono.empty();

// From a Supplier (lazy evaluation)
Mono<Instant> now = Mono.fromSupplier(Instant::now);

// From a Callable (can throw checked exceptions)
Mono<String> fromCallable = Mono.fromCallable(() -> {
    return readFromFile();  // May throw IOException
});

// From a Future
CompletableFuture<User> future = asyncService.getUser();
Mono<User> fromFuture = Mono.fromFuture(future);

// Error Mono
Mono<String> error = Mono.error(new RuntimeException("Failed"));

// Defer creation until subscription
Mono<Long> deferred = Mono.defer(() -> Mono.just(System.currentTimeMillis()));

// Never emits (for testing/special cases)
Mono<String> never = Mono.never();
```

### Creating Flux

```java
// From known values
Flux<String> just = Flux.just("A", "B", "C");

// From an Iterable
Flux<Integer> fromList = Flux.fromIterable(List.of(1, 2, 3, 4, 5));

// From an array
Flux<String> fromArray = Flux.fromArray(new String[]{"X", "Y", "Z"});

// From a Stream (be careful - streams can only be consumed once!)
Flux<Integer> fromStream = Flux.fromStream(IntStream.range(1, 10).boxed());

// Generate a range
Flux<Integer> range = Flux.range(1, 100);  // 1, 2, 3, ... 100

// Interval (emits Long sequence at fixed intervals)
Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));  // 0, 1, 2, ... every second

// Empty Flux
Flux<String> empty = Flux.empty();

// Error Flux
Flux<String> error = Flux.error(new RuntimeException("Failed"));

// Generate with state (programmatic creation)
Flux<String> generated = Flux.generate(
    () -> 0,  // Initial state
    (state, sink) -> {
        sink.next("Value " + state);
        if (state == 10) sink.complete();
        return state + 1;
    }
);

// Create with full control
Flux<String> created = Flux.create(sink -> {
    sink.next("First");
    sink.next("Second");
    sink.complete();
});

// Concatenate multiple publishers
Flux<Integer> concat = Flux.concat(
    Flux.range(1, 3),
    Flux.range(10, 3)
);  // 1, 2, 3, 10, 11, 12

// Merge multiple publishers (interleaved)
Flux<Integer> merged = Flux.merge(
    Flux.interval(Duration.ofMillis(100)).map(i -> i.intValue()),
    Flux.interval(Duration.ofMillis(150)).map(i -> i.intValue() + 100)
);
```

### Converting Between Mono and Flux

```java
// Flux to Mono (single)
Mono<Integer> single = Flux.just(1, 2, 3).single();  // Error if not exactly 1

// Flux to Mono (first)
Mono<Integer> first = Flux.just(1, 2, 3).next();  // Just the first

// Flux to Mono (last)
Mono<Integer> last = Flux.just(1, 2, 3).last();  // Just the last

// Flux to Mono (collect to list)
Mono<List<Integer>> list = Flux.just(1, 2, 3).collectList();

// Flux to Mono (reduce)
Mono<Integer> sum = Flux.just(1, 2, 3).reduce(0, Integer::sum);

// Flux to Mono (count)
Mono<Long> count = Flux.just(1, 2, 3).count();

// Mono to Flux
Flux<Integer> flux = Mono.just(42).flux();

// Mono to Flux (repeat)
Flux<Integer> repeated = Mono.just(42).repeat(3);  // 42, 42, 42, 42
```

---

## 4.6 Subscribing: Making Things Happen

The `subscribe()` method is what triggers execution. There are several overloads:

### Subscribe Variants

```java
Flux<Integer> flux = Flux.range(1, 5);

// 1. No arguments - just trigger, ignore results
flux.subscribe();

// 2. Consumer for each element
flux.subscribe(
    element -> System.out.println("Received: " + element)
);

// 3. Consumer for elements + error handler
flux.subscribe(
    element -> System.out.println("Received: " + element),
    error -> System.err.println("Error: " + error.getMessage())
);

// 4. Consumer for elements + error handler + completion handler
flux.subscribe(
    element -> System.out.println("Received: " + element),
    error -> System.err.println("Error: " + error.getMessage()),
    () -> System.out.println("Completed!")
);

// 5. Full control with subscription consumer
flux.subscribe(
    element -> System.out.println("Received: " + element),
    error -> System.err.println("Error: " + error.getMessage()),
    () -> System.out.println("Completed!"),
    subscription -> subscription.request(10)  // Control backpressure
);

// 6. Using a custom Subscriber (full control)
flux.subscribe(new BaseSubscriber<Integer>() {
    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        System.out.println("Subscribed!");
        request(1);  // Request first element
    }

    @Override
    protected void hookOnNext(Integer value) {
        System.out.println("Received: " + value);
        request(1);  // Request next element
    }

    @Override
    protected void hookOnComplete() {
        System.out.println("Completed!");
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        System.err.println("Error: " + throwable.getMessage());
    }
});
```

### Disposable: Canceling Subscriptions

```java
// subscribe() returns a Disposable
Disposable subscription = Flux.interval(Duration.ofSeconds(1))
    .subscribe(i -> System.out.println("Tick: " + i));

// Later, cancel the subscription
Thread.sleep(5000);
subscription.dispose();  // Stop receiving ticks

// Check if disposed
boolean isCanceled = subscription.isDisposed();
```

---

## 4.7 Summary

In this chapter, we've laid the groundwork for working with Project Reactor:

**Why Reactor:**
- Native to Spring, optimized for Java 8+
- Two-type simplicity (Mono and Flux)
- First-class backpressure and context support
- Excellent testing utilities

**Mono and Flux:**
- Mono: 0 or 1 element (async Optional)
- Flux: 0 to N elements (async Stream)
- Both are lazy, asynchronous, with backpressure

**Cold vs Hot:**
- Cold: Data generated per subscriber (default)
- Hot: Data shared among subscribers
- Convert with share(), cache(), replay()

**Nothing Happens Until Subscribe:**
- Reactive pipelines are blueprints
- subscribe() triggers execution
- In WebFlux, the framework subscribes for you

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Mono for 0-1, Flux for 0-N                                            │
│     Choose based on how many elements your operation produces.            │
│                                                                            │
│  2. Cold by Default                                                        │
│     Each subscription triggers the data source. Be aware of this         │
│     when multiple subscribers exist.                                      │
│                                                                            │
│  3. Nothing Happens Until Subscribe                                        │
│     Building a pipeline doesn't execute it. This is a feature,           │
│     enabling composition and efficiency.                                  │
│                                                                            │
│  4. Return, Don't Subscribe                                                │
│     In WebFlux, return Mono/Flux from your methods. Let the              │
│     framework subscribe.                                                  │
│                                                                            │
│  5. Reactor is Your Foundation                                            │
│     Understanding Reactor deeply will make WebFlux, R2DBC, and           │
│     all reactive Spring components feel natural.                          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 5, we'll learn to **think in streams**—transforming data with operators, combining multiple streams, and building complex reactive pipelines. The mental shift from imperative to reactive is the biggest hurdle; once you master it, the API becomes intuitive.

---

## Hands-On Lab 4: Your First Reactor Project

Now it's time to get hands-on with Reactor. In this lab, you'll:

1. Set up a Reactor project
2. Create your first Mono and Flux
3. Experiment with cold vs hot behavior
4. Prove that nothing happens without subscribe

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Project Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/) - The official documentation
- [Reactor by Example](https://www.baeldung.com/reactor-core) - Practical tutorials
- [Reactor GitHub Repository](https://github.com/reactor/reactor-core) - Source code and issues
- [Lite Rx API Hands-on](https://github.com/reactor/lite-rx-api-hands-on) - Interactive exercises

---

## Discussion Questions

1. When would you choose Mono over Flux, and vice versa?

2. You have a method that calls an external API. Should the returned Mono be cold or hot? Why?

3. Your colleague wrote a void method that builds a reactive pipeline but doesn't subscribe. What's wrong?

4. How does the "nothing happens until subscribe" principle affect how you design your service layer?

5. In what scenarios would you want to convert a cold publisher to a hot publisher?
