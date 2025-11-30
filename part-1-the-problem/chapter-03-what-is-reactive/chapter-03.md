# Chapter 3: What is Reactive Programming, Really?

> "Simplicity is the ultimate sophistication." — Leonardo da Vinci

We've seen the problem (blocking wastes resources) and traced the evolution of solutions (from threads to reactive streams). Now it's time to strip away the frameworks and libraries and understand what reactive programming **really** is at its core.

By the end of this chapter, you'll have a mental model of reactive programming that will serve you throughout this book and your career. You'll understand the concepts deeply enough that the specific APIs (Reactor, RxJava, etc.) will feel like natural expressions of these ideas.

---

## 3.1 Reactive: A Definition That Actually Makes Sense

Let's start by clearing up common misconceptions.

### What Reactive Programming is NOT

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    COMMON MISCONCEPTIONS                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ✗ "Reactive programming is about speed"                                   │
│     Reality: Sometimes reactive code is SLOWER for simple operations       │
│     It's about scalability and resource efficiency, not raw speed         │
│                                                                            │
│  ✗ "Reactive programming is about callbacks"                               │
│     Reality: Callbacks are an implementation detail, not the essence       │
│     You can have reactive without callbacks (and callbacks without reactive)│
│                                                                            │
│  ✗ "Reactive programming is about async"                                   │
│     Reality: Async is a means, not the goal                                │
│     Reactive is about data flow and propagation of change                 │
│                                                                            │
│  ✗ "Reactive programming is just fancy callbacks"                          │
│     Reality: The key innovations are backpressure and composability        │
│     Callbacks alone give you callback hell, not reactive programming      │
│                                                                            │
│  ✗ "Reactive programming is only for high-scale systems"                   │
│     Reality: The patterns are useful at any scale                          │
│     The mindset helps even in small applications                          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Core Definition

Here's a definition that actually captures the essence:

> **Reactive Programming** is a declarative programming paradigm concerned with **data streams** and the **propagation of change**.

Let's break this down:

**1. Declarative**: You describe *what* should happen, not *how* to do it step by step.

```java
// Imperative (how):
List<String> results = new ArrayList<>();
for (User user : users) {
    if (user.isActive()) {
        results.add(user.getName().toUpperCase());
    }
}

// Declarative (what):
users.stream()
    .filter(User::isActive)
    .map(user -> user.getName().toUpperCase())
    .collect(toList());

// Reactive declarative:
userFlux
    .filter(User::isActive)
    .map(user -> user.getName().toUpperCase())
    .collectList();
```

**2. Data Streams**: Everything can be modeled as a stream of data over time.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    EVERYTHING IS A STREAM                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Mouse clicks:        ──●────●──●───●────●────────●──────►                │
│                                                                            │
│  HTTP requests:       ──[req]───[req]──[req]────[req]────►                │
│                                                                            │
│  Stock prices:        ──$42──$43──$41──$45──$44──$46─────►                │
│                                                                            │
│  Database query:      ──[user1]──[user2]──[user3]──|─────►                │
│                       (even a single result is a stream of one)           │
│                                                                            │
│  Server events:       ──[event]────[event]───[event]────►                 │
│                       (potentially infinite)                               │
│                                                                            │
│  Timer:               ──●────●────●────●────●────●───────►                │
│                       (every second)                                       │
│                                                                            │
│  Legend: ● = event/item, | = completion, ──► = time                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**3. Propagation of Change**: When something changes upstream, downstream is notified automatically.

```java
// Imagine a spreadsheet:
// Cell A1 = 10
// Cell A2 = 20
// Cell A3 = A1 + A2  (formula, not static value)

// Change A1 to 15, and A3 automatically becomes 35
// That's propagation of change!

// In reactive programming:
Flux<Integer> a1 = getValueStream();  // emits: 10, then 15, then 12...
Flux<Integer> a2 = Flux.just(20);
Flux<Integer> a3 = Flux.combineLatest(a1, a2, Integer::sum);
// a3 automatically emits: 30, then 35, then 32...
```

### The Spreadsheet Analogy

The spreadsheet is the perfect mental model for reactive programming:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SPREADSHEET: REACTIVE IN ACTION                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Traditional programming (imperative):                                     │
│  ────────────────────────────────────                                      │
│  int a = 10;                                                               │
│  int b = 20;                                                               │
│  int c = a + b;  // c is 30                                               │
│  a = 15;         // c is still 30! We'd have to recalculate manually.    │
│                                                                            │
│  Spreadsheet (reactive):                                                   │
│  ────────────────────────                                                  │
│  ┌─────┬─────┬─────────┐                                                  │
│  │  A  │  B  │    C    │                                                  │
│  ├─────┼─────┼─────────┤                                                  │
│  │  10 │  20 │ =A1+B1  │  → C shows 30                                    │
│  └─────┴─────┴─────────┘                                                  │
│                                                                            │
│  Change A1 to 15:                                                          │
│  ┌─────┬─────┬─────────┐                                                  │
│  │  15 │  20 │ =A1+B1  │  → C automatically shows 35!                     │
│  └─────┴─────┴─────────┘                                                  │
│                                                                            │
│  This is reactive programming:                                             │
│  • Declare relationships (formulas)                                        │
│  • Changes propagate automatically                                         │
│  • No manual recalculation needed                                          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Key Insight

**Reactive programming inverts the traditional control flow.**

```
Traditional (Pull):
─────────────────────
Consumer: "Give me data"
Producer: "Here's data"
Consumer: "Give me more data"
Producer: "Here's more data"
(Consumer controls timing, must wait)

Reactive (Push):
─────────────────────
Consumer: "I want to react to data. Here's how."
Producer: (later) "Here's data!"
Consumer: (reacts)
Producer: (later) "Here's more data!"
Consumer: (reacts)
(Producer controls timing, consumer reacts)
```

This inversion is the fundamental shift. Instead of actively pulling data, we **describe how to react** when data arrives.

---

## 3.2 The Four Pillars of Reactive Systems

Before we dive into reactive programming details, let's understand the broader context: **Reactive Systems**.

The [Reactive Manifesto](https://www.reactivemanifesto.org/) defines four characteristics of reactive systems:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE FOUR PILLARS OF REACTIVE SYSTEMS                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│                           ┌───────────────┐                                │
│                           │   RESPONSIVE  │                                │
│                           │               │                                │
│                           │  The goal:    │                                │
│                           │  Always reply │                                │
│                           │  in time      │                                │
│                           └───────┬───────┘                                │
│                                   │                                        │
│                    ┌──────────────┼──────────────┐                         │
│                    │              │              │                         │
│                    ▼              │              ▼                         │
│           ┌───────────────┐      │      ┌───────────────┐                 │
│           │   RESILIENT   │      │      │    ELASTIC    │                 │
│           │               │      │      │               │                 │
│           │  Stay         │      │      │  Stay         │                 │
│           │  responsive   │      │      │  responsive   │                 │
│           │  despite      │      │      │  despite      │                 │
│           │  failure      │      │      │  varying load │                 │
│           └───────┬───────┘      │      └───────┬───────┘                 │
│                   │              │              │                         │
│                   └──────────────┼──────────────┘                         │
│                                  │                                        │
│                                  ▼                                        │
│                        ┌───────────────┐                                  │
│                        │MESSAGE-DRIVEN │                                  │
│                        │               │                                  │
│                        │ The means:    │                                  │
│                        │ Asynchronous  │                                  │
│                        │ message       │                                  │
│                        │ passing       │                                  │
│                        └───────────────┘                                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Pillar 1: Responsive

> The system responds in a timely manner if at all possible.

- Users get responses quickly
- Problems are detected early
- Response time is consistent

**Why it matters**: If your system doesn't respond, it might as well be down. A 30-second response time during peak load is effectively a failure.

### Pillar 2: Resilient

> The system stays responsive in the face of failure.

- Failures are contained
- Recovery is delegated
- The client is not burdened with handling failures

**Why it matters**: In distributed systems, failure is inevitable. The question isn't "will it fail?" but "how will it behave when it fails?"

```
Traditional system under failure:
─────────────────────────────────
Service A calls Service B
Service B is down
Service A waits... waits... timeout
Service A's thread is blocked for 30 seconds
Meanwhile, more requests pile up
Service A runs out of threads
Service A is now effectively down too
Cascade failure!

Resilient reactive system:
─────────────────────────────────
Service A calls Service B (non-blocking)
Service B is down
Service A immediately gets error signal
Service A returns fallback response
Service A's resources are free
System continues serving other requests
No cascade failure!
```

### Pillar 3: Elastic

> The system stays responsive under varying workload.

- Scales up when demand increases
- Scales down when demand decreases
- No bottlenecks, no contention points

**Why it matters**: Real-world load is variable. Black Friday isn't like a normal Tuesday. The system must adapt.

### Pillar 4: Message-Driven

> The system relies on asynchronous message passing.

- Loose coupling between components
- Location transparency
- Explicit boundaries
- **Backpressure** to control flow

**Why it matters**: This is the foundation that enables the other three. Without asynchronous message passing, you get blocking, which kills responsiveness, resilience, and elasticity.

### Reactive Programming Enables Reactive Systems

```
┌────────────────────────────────────────────────────────────────────────────┐
│             REACTIVE PROGRAMMING vs REACTIVE SYSTEMS                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Reactive Systems = Architecture/Design (the "what")                       │
│  ───────────────────────────────────────────────────                       │
│  • How components interact                                                 │
│  • How failures are handled                                                │
│  • How the system scales                                                   │
│  • Design principles for resilient systems                                 │
│                                                                            │
│  Reactive Programming = Implementation Tool (the "how")                    │
│  ─────────────────────────────────────────────────────                     │
│  • Flux, Mono, Observables                                                 │
│  • Operators and composition                                               │
│  • Non-blocking execution                                                  │
│  • Backpressure mechanisms                                                 │
│                                                                            │
│  Relationship:                                                              │
│  ─────────────                                                             │
│  Reactive Programming is ONE WAY to build Reactive Systems.                │
│  You could build reactive systems with other tools (actors, message queues)│
│  But reactive programming makes it natural and expressive.                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 3.3 The Publisher-Subscriber Model

At the heart of reactive programming is a simple pattern: **Publisher-Subscriber** (or Pub-Sub).

### The Basic Idea

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    PUBLISHER-SUBSCRIBER PATTERN                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌─────────────┐                              ┌─────────────┐             │
│  │  PUBLISHER  │                              │ SUBSCRIBER  │             │
│  │             │                              │             │             │
│  │  Source of  │         subscribe()          │  Consumer   │             │
│  │  data       │ ◄────────────────────────────│  of data    │             │
│  │             │                              │             │             │
│  │             │ ────────────────────────────►│             │             │
│  │             │         onNext(data)         │  (reacts)   │             │
│  │             │ ────────────────────────────►│             │             │
│  │             │         onNext(data)         │  (reacts)   │             │
│  │             │ ────────────────────────────►│             │             │
│  │             │         onComplete()         │  (done)     │             │
│  │             │            or                │             │             │
│  │             │         onError(err)         │  (handle)   │             │
│  └─────────────┘                              └─────────────┘             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Three Signals

A Publisher can send three types of signals to its Subscribers:

```java
public interface Subscriber<T> {
    void onSubscribe(Subscription s);  // Connection established
    void onNext(T item);               // Here's data
    void onError(Throwable t);         // Something went wrong (terminal)
    void onComplete();                 // No more data (terminal)
}
```

**Signal semantics:**
- `onNext(item)`: 0 to N times
- `onError(throwable)`: At most once, terminates the stream
- `onComplete()`: At most once, terminates the stream
- After `onError` or `onComplete`, no more signals are sent

```
Valid streams:
─────────────────────
──[1]──[2]──[3]──|            (3 items, then complete)
──[1]──[2]──X                 (2 items, then error)
──|                           (empty stream, complete)
──X                           (immediate error)
──[1]──[2]──[3]──[4]──[5]─►   (ongoing, no end yet)

Invalid streams:
─────────────────────
──[1]──|──[2]                 (item after complete - ILLEGAL)
──[1]──X──|                   (complete after error - ILLEGAL)
```

### The YouTube Analogy

Think about how YouTube subscriptions work:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    YOUTUBE: A PUBLISHER-SUBSCRIBER SYSTEM                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Without subscription (Pull/Polling):                                      │
│  ────────────────────────────────────                                      │
│  You: "Let me check if there's a new video"                               │
│  You: (visits channel page)                                                │
│  You: "Nope, nothing new"                                                  │
│  ... 10 minutes later ...                                                  │
│  You: "Let me check again"                                                 │
│  You: (visits channel page)                                                │
│  You: "Still nothing"                                                      │
│  ... continuous polling, wasted effort ...                                 │
│                                                                            │
│  With subscription (Push/Reactive):                                        │
│  ─────────────────────────────────                                         │
│  You: "I'll subscribe to this channel"                                     │
│  (You define HOW you want to be notified: email, app notification, etc.)  │
│  ... you go about your life ...                                            │
│  Channel: "New video uploaded!"                                            │
│  You: (notification arrives) "Oh, new video! Let me watch."               │
│  (You REACT to the event)                                                  │
│                                                                            │
│  Benefits:                                                                  │
│  • No wasted checking                                                      │
│  • Instant notification when content is ready                             │
│  • You control how/if you consume the content                             │
│  • Channel doesn't need to know details about subscribers                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Publisher: The Source

A Publisher is anything that can produce data:

```java
// A database query that returns multiple users
Publisher<User> userPublisher = userRepository.findAll();

// A stream of price updates
Publisher<Price> pricePublisher = stockService.getPriceStream("AAPL");

// An HTTP request response
Publisher<Response> responsePublisher = webClient.get().retrieve().bodyToMono(Response.class);

// A timer that emits every second
Publisher<Long> timerPublisher = Flux.interval(Duration.ofSeconds(1));
```

### Subscriber: The Consumer

A Subscriber defines what happens with the data:

```java
Publisher<String> publisher = getPublisher();

publisher.subscribe(new Subscriber<String>() {
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(1);  // Request first item
    }

    @Override
    public void onNext(String item) {
        System.out.println("Received: " + item);
        subscription.request(1);  // Request next item
    }

    @Override
    public void onError(Throwable t) {
        System.err.println("Error: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        System.out.println("Stream completed");
    }
});
```

### The Subscription: The Contract

The `Subscription` is the link between Publisher and Subscriber. It enables two critical things:

1. **Cancellation**: Subscriber can stop receiving data
2. **Backpressure**: Subscriber can control the flow rate

```java
public interface Subscription {
    void request(long n);  // "I'm ready for n more items"
    void cancel();         // "I don't want any more items"
}
```

---

## 3.4 Backpressure: The Superpower

This is the killer feature of reactive streams. Without backpressure, reactive programming is just fancy callbacks. With backpressure, it's a paradigm shift.

### The Problem: Producer-Consumer Speed Mismatch

Imagine a database that can query 10,000 records per second, but your consumer can only process 100 records per second:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WITHOUT BACKPRESSURE                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Database (Producer)                          Application (Consumer)       │
│  10,000 records/sec                           100 records/sec             │
│                                                                            │
│  Second 1:  ████████████ 10,000 ──────────► [100 processed]               │
│             └── 9,900 buffered ──┐                                        │
│                                  │                                        │
│  Second 2:  ████████████ 10,000 ──────────► [100 processed]               │
│             └── 19,800 buffered ─┤                                        │
│                                  │                                        │
│  Second 3:  ████████████ 10,000 ──────────► [100 processed]               │
│             └── 29,700 buffered ─┤                                        │
│                                  │                                        │
│  ...                             ▼                                        │
│                                                                            │
│  Second 100: Buffer = 990,000 records                                     │
│             = ~100MB+ of memory                                           │
│             = OutOfMemoryError                                            │
│             = CRASH                                                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Traditional "Solutions" (All Flawed)

**Option 1: Unbounded Buffer**
```java
// Just keep buffering...
Queue<Record> buffer = new LinkedList<>();  // Grows without limit
// Eventually: OutOfMemoryError
```

**Option 2: Drop Data**
```java
// Drop when buffer is full
if (buffer.size() < MAX) {
    buffer.add(record);
} else {
    // Silently dropped! Data loss!
}
```

**Option 3: Block the Producer**
```java
// Make producer wait
BlockingQueue<Record> buffer = new ArrayBlockingQueue<>(MAX);
buffer.put(record);  // Blocks if full
// Defeats the purpose of async!
```

None of these are good. We need a better way.

### The Reactive Solution: Backpressure

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WITH BACKPRESSURE                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Database (Publisher)                         Application (Subscriber)     │
│                                                                            │
│                   ◄─── request(100) ─────────                             │
│  "OK, sending 100"                                                         │
│  ──[100 records]─────────────────────────────► [processes all 100]        │
│                                                                            │
│                   ◄─── request(100) ─────────                             │
│  "OK, sending 100"                                                         │
│  ──[100 records]─────────────────────────────► [processes all 100]        │
│                                                                            │
│  ...continues at consumer's pace...                                        │
│                                                                            │
│  • No unbounded buffering                                                  │
│  • No data loss                                                            │
│  • No blocking                                                             │
│  • Consumer controls the rate                                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Fire Hose Analogy

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE FIRE HOSE ANALOGY                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Without backpressure (drinking from a fire hose):                        │
│  ────────────────────────────────────────────────                          │
│                                                                            │
│      ████████████                                                          │
│      ██████████████═══════════════════════════►  😵                       │
│      ████████████                                                          │
│                                                                            │
│  The hose blasts at full pressure, you can't control it,                  │
│  you choke, water goes everywhere. Disaster.                              │
│                                                                            │
│  With backpressure (water fountain with button):                          │
│  ─────────────────────────────────────────────                             │
│                                                                            │
│      ┌─────┐                                                               │
│      │  ●  │ ←── Press button for water                                   │
│      │  │  │                                                               │
│      │~~~~~│ ═══► 😊                                                      │
│      └─────┘                                                               │
│                                                                            │
│  You control the flow. Press when ready, release when not.                │
│  You get exactly what you can handle.                                     │
│                                                                            │
│  request(n) = pressing the button n times                                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### How Backpressure Works in Code

```java
publisher.subscribe(new Subscriber<Data>() {
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        // I can handle 10 items to start
        s.request(10);
    }

    @Override
    public void onNext(Data item) {
        // Process the item
        process(item);

        // I'm ready for one more
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {
        // Handle error
    }

    @Override
    public void onComplete() {
        // Done
    }
});
```

### Backpressure Strategies

When demand exceeds capacity, you have options:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BACKPRESSURE STRATEGIES                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. BUFFER (with limit)                                                    │
│     ─────────────────────                                                  │
│     Store items in a bounded buffer                                        │
│     .onBackpressureBuffer(100)                                            │
│     Risk: May still overflow if too slow                                  │
│                                                                            │
│  2. DROP                                                                   │
│     ─────────────────────                                                  │
│     Drop newest items when overwhelmed                                     │
│     .onBackpressureDrop()                                                 │
│     Use when: Data loss is acceptable (sensor readings, metrics)          │
│                                                                            │
│  3. LATEST                                                                 │
│     ─────────────────────                                                  │
│     Keep only the latest item, drop older ones                            │
│     .onBackpressureLatest()                                               │
│     Use when: Only current state matters (UI updates, prices)             │
│                                                                            │
│  4. ERROR                                                                  │
│     ─────────────────────                                                  │
│     Signal error when overwhelmed                                          │
│     .onBackpressureError()                                                │
│     Use when: Overflow indicates a bug that needs fixing                  │
│                                                                            │
│  5. THROTTLE                                                               │
│     ─────────────────────                                                  │
│     Slow down emission rate                                                │
│     .sample(Duration.ofMillis(100))                                       │
│     Use when: You can sample instead of processing all                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Backpressure is the Superpower

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BACKPRESSURE: WHY IT MATTERS                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Without backpressure:                                                     │
│  ─────────────────────                                                     │
│  • Fast producers overwhelm slow consumers                                │
│  • Unbounded memory growth                                                │
│  • System crashes under load                                              │
│  • No way to signal "slow down"                                           │
│                                                                            │
│  With backpressure:                                                        │
│  ─────────────────────                                                     │
│  • Consumers control the pace                                              │
│  • Memory usage is bounded and predictable                                │
│  • System gracefully handles overload                                     │
│  • End-to-end flow control                                                │
│                                                                            │
│  This is what separates reactive streams from callbacks:                  │
│  • Callbacks push without asking                                          │
│  • Reactive streams push only when subscriber requests                    │
│                                                                            │
│  BACKPRESSURE = SAFE, CONTROLLED DATA FLOW                                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 3.5 Push vs. Pull vs. Push-Pull

Understanding the different data flow models helps clarify where reactive streams fit.

### Pull Model: Iterator Pattern

```java
// Traditional Java iteration
Iterator<User> iterator = users.iterator();

while (iterator.hasNext()) {
    User user = iterator.next();  // Consumer PULLS data
    process(user);
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    PULL MODEL (Iterator)                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Consumer                                           Producer               │
│                                                                            │
│  "hasNext()?" ─────────────────────────────────────► "Yes"                │
│  "next()"     ─────────────────────────────────────► [item 1]             │
│  (process)                                                                 │
│  "hasNext()?" ─────────────────────────────────────► "Yes"                │
│  "next()"     ─────────────────────────────────────► [item 2]             │
│  (process)                                                                 │
│  "hasNext()?" ─────────────────────────────────────► "No"                 │
│  (done)                                                                    │
│                                                                            │
│  Characteristics:                                                          │
│  • Consumer initiates every interaction                                   │
│  • Synchronous (blocks on next())                                         │
│  • Simple to understand                                                    │
│  • No backpressure needed (consumer controls pace by pulling)             │
│  • But: Can't handle async data sources                                   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Push Model: Observer Pattern

```java
// Traditional Observer pattern
button.addClickListener(event -> {
    // Producer PUSHES events to us
    handleClick(event);
});
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    PUSH MODEL (Observer)                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Producer                                           Consumer               │
│                                                                            │
│  (event occurs)                                                            │
│  ────────────────────────────────────────►[item 1]  (process)             │
│  (event occurs)                                                            │
│  ────────────────────────────────────────►[item 2]  (process)             │
│  (event occurs)                                                            │
│  ────────────────────────────────────────►[item 3]  (process)             │
│  (event occurs)                                                            │
│  ────────────────────────────────────────►[item 4]  (process)             │
│                                                                            │
│  Characteristics:                                                          │
│  • Producer initiates every interaction                                   │
│  • Asynchronous by nature                                                  │
│  • Handles real-time events well                                          │
│  • NO backpressure! Producer fires at will                                │
│  • But: Can overwhelm slow consumers                                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Push-Pull Model: Reactive Streams

Reactive Streams combines the best of both:

```java
// Reactive Streams
publisher.subscribe(new Subscriber<Item>() {
    @Override
    public void onSubscribe(Subscription s) {
        s.request(10);  // PULL: "Give me 10"
    }

    @Override
    public void onNext(Item item) {  // PUSH: Producer sends items
        process(item);
        subscription.request(1);  // PULL: "Ready for 1 more"
    }
    // ...
});
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    PUSH-PULL MODEL (Reactive Streams)                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Consumer                                           Producer               │
│                                                                            │
│  request(3) ───────────────────────────────────────►                      │
│                      "I want 3 items"                                      │
│                                                                            │
│             ◄─────────────────────────────────────── [item 1] PUSH        │
│  (process)                                                                 │
│             ◄─────────────────────────────────────── [item 2] PUSH        │
│  (process)                                                                 │
│             ◄─────────────────────────────────────── [item 3] PUSH        │
│  (process)                                                                 │
│                                                                            │
│  request(2) ───────────────────────────────────────►  PULL                │
│                      "I want 2 more"                                       │
│                                                                            │
│             ◄─────────────────────────────────────── [item 4] PUSH        │
│  (process)                                                                 │
│             ◄─────────────────────────────────────── [item 5] PUSH        │
│  (process)                                                                 │
│                                                                            │
│  Characteristics:                                                          │
│  • Consumer PULLS demand (request)                                        │
│  • Producer PUSHES supply (onNext)                                        │
│  • Asynchronous and non-blocking                                          │
│  • Backpressure built-in!                                                 │
│  • Best of both worlds                                                    │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Comparison Summary

| Aspect | Pull (Iterator) | Push (Observer) | Push-Pull (Reactive) |
|--------|-----------------|-----------------|----------------------|
| Who controls pace | Consumer | Producer | Both |
| Async support | No (blocking) | Yes | Yes |
| Backpressure | Implicit (pull) | None | Explicit |
| Memory safety | Yes | No (can overflow) | Yes |
| Real-time events | No | Yes | Yes |
| Composition | Limited | Limited | Excellent |

---

## 3.6 The Reactive Streams Specification

Now let's look at the actual specification that standardizes reactive programming in Java.

### The Four Interfaces

The Reactive Streams spec defines exactly four interfaces:

```java
package org.reactivestreams;  // or java.util.concurrent.Flow in Java 9+

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
    // A component that is both subscriber and publisher
    // Transforms T to R
}
```

That's it. Four simple interfaces. The power comes from the **rules** around them.

### The Rules (Simplified)

The specification includes detailed rules. Here are the most important:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE STREAMS RULES (KEY ONES)                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  PUBLISHER RULES:                                                          │
│  ─────────────────                                                         │
│  1. Total onNext calls <= total request calls                             │
│     (Never send more than requested)                                       │
│                                                                            │
│  2. Signal onComplete or onError at most once                             │
│     (A stream terminates at most once)                                    │
│                                                                            │
│  3. Must respect subscription cancellation                                 │
│     (Stop sending after cancel)                                           │
│                                                                            │
│  SUBSCRIBER RULES:                                                         │
│  ─────────────────                                                         │
│  4. Must call request(n) to receive onNext signals                        │
│     (No request = no data)                                                │
│                                                                            │
│  5. Must be prepared to receive onError/onComplete at any time            │
│     (Terminal signals can arrive unexpectedly)                            │
│                                                                            │
│  SUBSCRIPTION RULES:                                                       │
│  ──────────────────                                                        │
│  6. request and cancel must be called serially (not concurrently)         │
│     (Thread safety within subscription)                                   │
│                                                                            │
│  7. request(Long.MAX_VALUE) = unbounded demand (effectively disables      │
│     backpressure)                                                         │
│                                                                            │
│  SIGNAL ORDERING:                                                          │
│  ────────────────                                                          │
│  8. onSubscribe → onNext* → (onError | onComplete)?                       │
│     (Signals follow this order)                                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why a Specification?

Before Reactive Streams, different libraries had different APIs:
- RxJava 1.x: `Observable`, `Observer`, `Subscription`
- Reactor 1.x: Different names and semantics
- Akka Streams: Actor-based approach

This caused interoperability problems. The specification solved this:

```java
// RxJava's Flowable
Flowable<String> rxFlowable = Flowable.just("a", "b", "c");

// Convert to Reactor's Flux
Flux<String> reactorFlux = Flux.from(rxFlowable);

// Convert back to RxJava
Flowable<String> backToRx = Flowable.fromPublisher(reactorFlux);

// They all implement Publisher!
// Interoperability achieved.
```

### The Specification vs. Implementation

```
┌────────────────────────────────────────────────────────────────────────────┐
│                SPECIFICATION vs IMPLEMENTATION                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Reactive Streams Specification                                            │
│  ─────────────────────────────────                                         │
│  • Defines the 4 interfaces                                                │
│  • Defines the rules/contract                                              │
│  • Minimal API (by design)                                                 │
│  • No operators, no convenience methods                                    │
│  • This IS in the JDK (java.util.concurrent.Flow)                         │
│                                                                            │
│  Implementations (Libraries)                                               │
│  ─────────────────────────────                                             │
│  Project Reactor:                                                          │
│    • Mono<T>: 0 or 1 element (implements Publisher)                       │
│    • Flux<T>: 0 to N elements (implements Publisher)                      │
│    • 400+ operators                                                        │
│    • Spring's choice                                                       │
│                                                                            │
│  RxJava 3:                                                                 │
│    • Flowable<T>: 0 to N with backpressure (implements Publisher)         │
│    • Observable<T>: 0 to N without backpressure                           │
│    • Also 400+ operators                                                   │
│    • Android's choice                                                      │
│                                                                            │
│  Others:                                                                   │
│    • Akka Streams                                                          │
│    • Vert.x                                                                │
│    • SmallRye Mutiny                                                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 3.7 Putting It All Together

Let's see how all these concepts combine:

### A Simple Reactive Pipeline

```java
// Using Project Reactor (which implements Reactive Streams)

Flux<Order> recentOrders = orderRepository.findAll()     // Publisher<Order>
    .filter(order -> order.isRecent())                   // Filter operator
    .map(order -> enrichWithCustomer(order))             // Transform operator
    .flatMap(order -> calculatePricing(order))           // Async transform
    .take(10);                                           // Limit to 10

// Nothing has executed yet! It's just a blueprint.

recentOrders.subscribe(
    order -> display(order),           // onNext
    error -> logError(error),          // onError
    () -> System.out.println("Done")   // onComplete
);

// NOW it executes, with backpressure automatically handled
```

### The Mental Model

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE PIPELINE MENTAL MODEL                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Think of it as an assembly line with these properties:                   │
│                                                                            │
│  1. BLUEPRINT FIRST                                                        │
│     ─────────────────                                                      │
│     Building the pipeline doesn't run it.                                 │
│     Like designing an assembly line before turning it on.                 │
│                                                                            │
│  2. SUBSCRIBE TO START                                                     │
│     ─────────────────                                                      │
│     The assembly line only runs when there's demand (subscribe).          │
│     No subscribers = nothing happens.                                     │
│                                                                            │
│  3. DATA FLOWS DOWNSTREAM                                                  │
│     ─────────────────                                                      │
│     ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐                 │
│     │ Source │───►│ Filter │───►│  Map   │───►│  Sink  │                 │
│     └────────┘    └────────┘    └────────┘    └────────┘                 │
│     Publisher      Operator      Operator     Subscriber                  │
│                                                                            │
│  4. DEMAND FLOWS UPSTREAM                                                  │
│     ─────────────────                                                      │
│     ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐                 │
│     │ Source │◄───│ Filter │◄───│  Map   │◄───│  Sink  │                 │
│     └────────┘    └────────┘    └────────┘    └────────┘                 │
│                              request(n)                                    │
│                                                                            │
│  5. ERRORS PROPAGATE                                                       │
│     ─────────────────                                                      │
│     An error anywhere in the pipeline propagates to subscriber's onError. │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Libraries Instead of Raw Interfaces?

Implementing Publisher/Subscriber correctly is hard:

```java
// DON'T do this - implementing Publisher correctly is extremely complex!
public class NaivePublisher implements Publisher<Integer> {
    @Override
    public void subscribe(Subscriber<? super Integer> s) {
        // Must handle:
        // - Concurrent request() calls
        // - Cancellation at any point
        // - Not sending more than requested
        // - Thread safety
        // - Error handling
        // - Resource cleanup
        // - Memory management
        // This is hundreds of lines of careful code!
    }
}

// DO this - use a library that handles complexity
Flux<Integer> numbers = Flux.range(1, 100);  // Done correctly!
```

This is why we use libraries like Project Reactor. They implement the specification correctly, so we can focus on business logic.

---

## 3.8 Summary

In this chapter, we've built a complete mental model of reactive programming:

**What Reactive Programming Is:**
- Declarative programming with data streams
- Automatic propagation of change
- Push-based with consumer-controlled flow (backpressure)

**The Four Pillars of Reactive Systems:**
- Responsive, Resilient, Elastic, Message-Driven
- Reactive programming is a tool to build reactive systems

**Publisher-Subscriber Pattern:**
- Publishers produce data (0 to N items)
- Subscribers consume data (react to onNext, onError, onComplete)
- Subscriptions connect them (enable request and cancel)

**Backpressure:**
- Consumer controls the flow rate
- Prevents overwhelming slow consumers
- The key differentiator from callbacks

**Push vs. Pull vs. Push-Pull:**
- Iterator: Consumer pulls (synchronous)
- Observer: Producer pushes (no backpressure)
- Reactive: Both push and pull (async with backpressure)

**The Reactive Streams Specification:**
- Four simple interfaces: Publisher, Subscriber, Subscription, Processor
- Standardizes reactive programming across libraries
- Libraries (Reactor, RxJava) provide the operators

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Reactive = Streams + Propagation of Change                            │
│     Everything is a stream. When sources change, consumers react.         │
│                                                                            │
│  2. Backpressure is the Superpower                                        │
│     Without it, you have callbacks. With it, you have safe data flow.    │
│                                                                            │
│  3. Nothing Happens Until Subscribe                                        │
│     Reactive pipelines are lazy. They're blueprints, not executions.     │
│                                                                            │
│  4. The Spec is Simple, Implementations are Powerful                      │
│     4 interfaces define the contract. Libraries provide 400+ operators.  │
│                                                                            │
│  5. Reactive Programming ≠ Reactive Systems                               │
│     Programming is a tool. Systems are an architecture.                   │
│     The tool helps build the architecture.                                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Part II, we'll dive into Project Reactor, the reactive library that powers Spring WebFlux. You'll learn to use Mono and Flux, the rich operator library, and how to think in streams. With the foundation from these first three chapters, you'll find it natural and intuitive.

---

## Hands-On Lab 3: Building Publisher and Subscriber from Scratch

Now it's time to get your hands dirty. In this lab, you'll:

1. Implement a simple Publisher from scratch (experiencing the complexity)
2. Implement a Subscriber from scratch
3. See how backpressure works at the low level
4. Understand why we use libraries like Reactor

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Reactive Manifesto](https://www.reactivemanifesto.org/) - The principles behind reactive systems
- [Reactive Streams Specification](https://www.reactive-streams.org/) - The official spec and TCK
- [Introduction to Reactive Programming](https://gist.github.com/staltz/868e7e9bc2a7b8c1f754) - André Staltz's excellent intro
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/) - Deep dive into Reactor
- [The Reactive Streams Protocol](https://github.com/reactive-streams/reactive-streams-jvm) - GitHub repository with spec

---

## Discussion Questions

1. How would you explain reactive programming to a developer who only knows imperative programming?

2. In what scenarios would backpressure be critical? When might you want to disable it (request Long.MAX_VALUE)?

3. How does the spreadsheet analogy help understand reactive programming? Where does it break down?

4. Why do you think the Reactive Streams specification is so minimal (just 4 interfaces)?

5. What's the relationship between reactive programming and functional programming?
