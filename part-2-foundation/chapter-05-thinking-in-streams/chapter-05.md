# Chapter 5: Thinking in Streams

> "It is not the language that makes programs appear simple. It is the programmer that make the language appear simple!" — Robert C. Martin

The biggest hurdle in reactive programming isn't learning the API—it's rewiring your brain to think in streams. This chapter is about that mental shift. Once you internalize stream thinking, operators like `map`, `flatMap`, and `filter` become as natural as for-loops.

By the end of this chapter, you'll read marble diagrams fluently, chain operators confidently, and see data as flowing rivers rather than static containers.

---

## 5.1 From Loops to Streams

Let's start with what you know: imperative loops.

### The Imperative Mind

```java
// Imperative: HOW to do it step by step
List<String> uppercaseNames = new ArrayList<>();
for (User user : users) {
    if (user.isActive()) {
        String name = user.getName();
        String upper = name.toUpperCase();
        uppercaseNames.add(upper);
    }
}
```

This code tells the computer **how** to do the work:
1. Create an empty list
2. Iterate through each user
3. Check if active
4. Get the name
5. Convert to uppercase
6. Add to list
7. Repeat

### The Declarative Mind

```java
// Declarative: WHAT we want
List<String> uppercaseNames = users.stream()
    .filter(User::isActive)
    .map(User::getName)
    .map(String::toUpperCase)
    .collect(toList());
```

This code describes **what** we want:
- From users, keep only active ones
- Extract their names
- Make uppercase
- Collect into a list

### The Reactive Mind

```java
// Reactive: WHAT we want + WHEN it happens
Flux<String> uppercaseNames = userFlux
    .filter(User::isActive)
    .map(User::getName)
    .map(String::toUpperCase);
```

Same declarative style, but:
- Users arrive over time (async)
- Processing happens as they arrive
- Results push to subscribers
- Backpressure controls the flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE THREE PARADIGMS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  IMPERATIVE (How):                                                         │
│  ─────────────────                                                         │
│  for (item : collection) {           Step-by-step instructions            │
│      if (condition) {                You manage state                      │
│          result = transform(item);   You control iteration                │
│          output.add(result);                                              │
│      }                                                                     │
│  }                                                                         │
│                                                                            │
│  DECLARATIVE (What):                                                       │
│  ───────────────────                                                       │
│  collection.stream()                 Describe transformation              │
│      .filter(condition)              Runtime handles iteration            │
│      .map(transform)                 No explicit state management         │
│      .collect(...)                   Pull-based (you call terminal)       │
│                                                                            │
│  REACTIVE (What + When):                                                   │
│  ───────────────────────                                                   │
│  flux                                Describe transformation              │
│      .filter(condition)              Data pushes to you                   │
│      .map(transform)                 Async-native                         │
│      .subscribe(...)                 Backpressure built-in                │
│                                                                            │
│  Key insight: Reactive is declarative + asynchronous + push-based         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### The Mental Shift

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MENTAL MODEL SHIFT                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Imperative thinking:                                                      │
│  ─────────────────────                                                     │
│  "I have a list. Let me go through each item and do X."                  │
│                                                                            │
│  Reactive thinking:                                                        │
│  ─────────────────────                                                     │
│  "Items will arrive. When each arrives, X should happen."                │
│                                                                            │
│  Example: Processing orders                                               │
│                                                                            │
│  Imperative:                                                               │
│  "Get all orders. Loop through. Process each."                           │
│                                                                            │
│  Reactive:                                                                 │
│  "As orders arrive, process them. I don't need to know when or how       │
│   many—just what to do with each one."                                   │
│                                                                            │
│  The shift is from ACTIVE ITERATION to PASSIVE REACTION.                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 5.2 The Operator Mental Model

Operators are the vocabulary of reactive programming. Understanding them conceptually makes the API intuitive.

### Operators as Assembly Line Stations

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    THE ASSEMBLY LINE                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Imagine a factory assembly line:                                         │
│                                                                            │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐               │
│  │  Source  │──►│ Station 1│──►│ Station 2│──►│   Sink   │               │
│  │ (items)  │   │  (check) │   │(assemble)│   │ (output) │               │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘               │
│                                                                            │
│  In reactive terms:                                                       │
│                                                                            │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐               │
│  │   Flux   │──►│  filter  │──►│   map    │──►│subscriber│               │
│  │(Publisher)│  │(Operator)│   │(Operator)│   │(Consumer)│               │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘               │
│                                                                            │
│  Each station (operator):                                                 │
│  • Receives items from upstream                                          │
│  • Does its specific job (filter, transform, combine, etc.)              │
│  • Passes results downstream                                             │
│                                                                            │
│  The assembly line only runs when there's a subscriber (customer order). │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Marble Diagrams Explained

Marble diagrams are the universal language for explaining reactive operators. Learn to read them, and you can understand any operator.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ANATOMY OF A MARBLE DIAGRAM                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Input stream (timeline going left to right):                             │
│                                                                            │
│  ──●──●──●──●──|                                                          │
│    1  2  3  4  complete                                                   │
│                                                                            │
│  Elements are circles/marbles on the timeline.                            │
│  | means complete (successful end)                                        │
│  X means error                                                            │
│                                                                            │
│  Example: map(x -> x * 2)                                                 │
│  ─────────────────────────                                                 │
│                                                                            │
│  Input:   ──(1)──(2)──(3)──|                                              │
│              │     │     │                                                │
│              │ map(x -> x * 2)                                            │
│              ▼     ▼     ▼                                                │
│  Output:  ──(2)──(4)──(6)──|                                              │
│                                                                            │
│  Example: filter(x -> x > 2)                                              │
│  ───────────────────────────                                               │
│                                                                            │
│  Input:   ──(1)──(2)──(3)──(4)──|                                         │
│              │     │     │     │                                          │
│              │ filter(x -> x > 2)                                         │
│              ▼     ▼     ▼     ▼                                          │
│  Output:  ─────────────(3)──(4)──|                                        │
│              ↑     ↑                                                      │
│              dropped (didn't match predicate)                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Categories of Operators

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    OPERATOR CATEGORIES                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  TRANSFORMING (change elements)                                           │
│  ─────────────────────────────────                                         │
│  map        : Transform each element                                      │
│  flatMap    : Transform to stream, then flatten                          │
│  cast       : Cast elements to a type                                    │
│  index      : Add index to each element                                  │
│                                                                            │
│  FILTERING (reduce elements)                                              │
│  ────────────────────────────                                              │
│  filter     : Keep matching elements                                      │
│  take       : Keep first N elements                                       │
│  skip       : Skip first N elements                                       │
│  distinct   : Remove duplicates                                           │
│  first/last : Keep only first/last                                       │
│                                                                            │
│  COMBINING (merge streams)                                                │
│  ──────────────────────────                                                │
│  merge      : Interleave multiple streams                                │
│  concat     : Append streams sequentially                                │
│  zip        : Pair elements from streams                                 │
│  combineLatest : Combine latest from each                                │
│                                                                            │
│  AGGREGATING (reduce to single value)                                    │
│  ─────────────────────────────────────                                     │
│  reduce     : Accumulate to single value                                 │
│  collect    : Collect to container                                       │
│  count      : Count elements                                             │
│  all/any    : Boolean aggregates                                         │
│                                                                            │
│  ERROR HANDLING                                                           │
│  ──────────────────                                                        │
│  onErrorReturn   : Replace error with value                              │
│  onErrorResume   : Replace error with stream                             │
│  retry           : Retry on error                                        │
│                                                                            │
│  TIMING                                                                   │
│  ────────                                                                  │
│  delay      : Delay emissions                                            │
│  timeout    : Error if too slow                                          │
│  sample     : Sample at intervals                                        │
│                                                                            │
│  UTILITY                                                                  │
│  ──────────                                                                │
│  doOnNext   : Side effect per element                                    │
│  doOnError  : Side effect on error                                       │
│  log        : Log all signals                                            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 5.3 Immutability and the Pipeline

Every operator returns a **new** Publisher. The original is unchanged.

### Operators Don't Mutate

```java
Flux<Integer> original = Flux.range(1, 5);
Flux<Integer> doubled = original.map(i -> i * 2);
Flux<Integer> filtered = original.filter(i -> i > 2);

// original is still Flux.range(1, 5)
// doubled is a NEW Flux that applies map
// filtered is a NEW Flux that applies filter

// These are THREE independent pipelines:
original.subscribe(i -> System.out.println("Original: " + i));
doubled.subscribe(i -> System.out.println("Doubled: " + i));
filtered.subscribe(i -> System.out.println("Filtered: " + i));
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    IMMUTABILITY IN ACTION                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Flux<Integer> a = Flux.range(1, 5);                                      │
│  Flux<Integer> b = a.map(x -> x * 2);                                     │
│  Flux<Integer> c = b.filter(x -> x > 4);                                  │
│                                                                            │
│  This creates a chain:                                                    │
│                                                                            │
│  a (source)                                                               │
│    └──► b (map wrapper around a)                                          │
│           └──► c (filter wrapper around b)                                │
│                                                                            │
│  When you subscribe to c:                                                 │
│  1. c subscribes to b                                                     │
│  2. b subscribes to a                                                     │
│  3. a emits elements                                                      │
│  4. Elements flow: a → b (map) → c (filter) → subscriber                 │
│                                                                            │
│  But a is unchanged! You can still:                                       │
│  a.subscribe(...)   // Uses original, no map or filter                   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Immutability Matters

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    BENEFITS OF IMMUTABILITY                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. COMPOSITION                                                            │
│     ─────────────                                                          │
│     Build complex pipelines from simple parts:                            │
│                                                                            │
│     Flux<Order> base = orderRepository.findAll();                         │
│     Flux<Order> recent = base.filter(Order::isRecent);                    │
│     Flux<Order> highValue = recent.filter(o -> o.getTotal() > 1000);      │
│     Flux<String> customerEmails = highValue                               │
│         .map(Order::getCustomer)                                          │
│         .map(Customer::getEmail);                                         │
│                                                                            │
│     Each step creates a new building block.                               │
│                                                                            │
│  2. REUSABILITY                                                            │
│     ─────────────                                                          │
│     Same source, multiple pipelines:                                      │
│                                                                            │
│     Flux<User> users = userRepository.findAll();                          │
│     users.filter(User::isAdmin).subscribe(this::notifyAdmin);            │
│     users.filter(User::isNew).subscribe(this::sendWelcome);              │
│     users.count().subscribe(this::updateDashboard);                       │
│                                                                            │
│  3. THREAD SAFETY                                                          │
│     ──────────────                                                         │
│     No shared mutable state between subscribers.                          │
│                                                                            │
│  4. REASONING                                                              │
│     ──────────                                                             │
│     Each operator's behavior is predictable and isolated.                │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 5.4 Transforming Data: map, flatMap, filter

These are the three most important operators. Master them and you can handle most scenarios.

### map: One-to-One Transformation

`map` transforms each element using a function. One input → one output.

```java
Flux<Integer> numbers = Flux.range(1, 5);
Flux<Integer> doubled = numbers.map(n -> n * 2);
// 1 → 2, 2 → 4, 3 → 6, 4 → 8, 5 → 10

Flux<User> users = userRepository.findAll();
Flux<String> names = users.map(User::getName);
// User → getName() → String

Flux<Order> orders = orderService.getOrders();
Flux<OrderDTO> dtos = orders.map(order -> new OrderDTO(
    order.getId(),
    order.getTotal(),
    order.getStatus()
));
// Order → OrderDTO
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MAP: ONE-TO-ONE                                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Input:   ──(1)────(2)────(3)────|                                        │
│              │       │       │                                            │
│              │  map(x -> x * 10)                                          │
│              ▼       ▼       ▼                                            │
│  Output:  ──(10)───(20)───(30)───|                                        │
│                                                                            │
│  Key points:                                                              │
│  • Synchronous transformation                                             │
│  • Same number of elements in and out                                    │
│  • Function: T → R (not T → Publisher<R>)                                │
│                                                                            │
│  When to use:                                                             │
│  • Extracting a field: user.map(User::getName)                           │
│  • Simple calculations: price.map(p -> p * 1.1)                          │
│  • Type conversions: entity.map(Entity::toDTO)                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### flatMap: One-to-Many with Async

`flatMap` transforms each element into a Publisher, then flattens the results. This is crucial for async operations.

```java
// Each user ID → fetch user (async operation)
Flux<String> userIds = Flux.just("u1", "u2", "u3");
Flux<User> users = userIds.flatMap(id -> userRepository.findById(id));
// "u1" → Mono<User> → User
// "u2" → Mono<User> → User
// "u3" → Mono<User> → User
// Results merged into single Flux<User>

// Each order → fetch its items (one-to-many)
Flux<Order> orders = orderService.getOrders();
Flux<OrderItem> allItems = orders.flatMap(order ->
    itemRepository.findByOrderId(order.getId())
);
// Order1 → [Item1, Item2]
// Order2 → [Item3]
// Order3 → [Item4, Item5, Item6]
// Result: [Item1, Item2, Item3, Item4, Item5, Item6] (flattened)
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FLATMAP: ONE-TO-MANY + FLATTEN                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Input:     ──(1)────────(2)────────(3)────|                              │
│                │           │           │                                   │
│                │  flatMap(x -> Flux.range(x, 2))                          │
│                ▼           ▼           ▼                                   │
│  Inners:    ──(1)(2)    ──(2)(3)    ──(3)(4)                              │
│                │  │        │  │        │  │                               │
│                └──┼────────┼──┼────────┼──┘                               │
│                   └────────┴──┴────────┘                                   │
│                        (merged/flattened)                                  │
│  Output:    ──(1)──(2)──(2)──(3)──(3)──(4)──|                             │
│                                                                            │
│  Note: Order may vary! flatMap processes concurrently.                   │
│                                                                            │
│  Key points:                                                              │
│  • Asynchronous transformation                                            │
│  • Each element → Publisher (0 to N elements)                            │
│  • Results are merged (interleaved)                                      │
│  • Function: T → Publisher<R>                                            │
│                                                                            │
│  When to use:                                                             │
│  • Async operations: id.flatMap(repository::findById)                    │
│  • One-to-many: order.flatMap(o -> findItems(o.getId()))                 │
│  • Nested Publishers: flux.flatMap(mono -> mono)                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Common Mistake #2: Using map When You Need flatMap

```java
// WRONG: map with async operation
Flux<String> userIds = Flux.just("u1", "u2", "u3");
Flux<Mono<User>> wrong = userIds.map(id -> userRepository.findById(id));
// Result: Flux<Mono<User>> - NOT what we want!
// We have a stream of Monos, not a stream of Users.

// CORRECT: flatMap unwraps the inner Publishers
Flux<User> correct = userIds.flatMap(id -> userRepository.findById(id));
// Result: Flux<User> - each User emitted as the Mono completes
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MAP vs FLATMAP: THE RULE                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Ask yourself: "What does my function return?"                           │
│                                                                            │
│  Function returns a plain value (T → R):                                  │
│  ─────────────────────────────────────────                                 │
│  user -> user.getName()          → Use map                               │
│  price -> price * 1.1            → Use map                               │
│  order -> new OrderDTO(order)    → Use map                               │
│                                                                            │
│  Function returns a Publisher (T → Mono<R> or T → Flux<R>):              │
│  ──────────────────────────────────────────────────────────               │
│  id -> repository.findById(id)           → Use flatMap                   │
│  order -> itemService.getItems(orderId)  → Use flatMap                   │
│  user -> webClient.get(user.getUrl())    → Use flatMap                   │
│                                                                            │
│  Mnemonic: "flat" flattens nested Publishers                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### filter: Conditional Pass-Through

`filter` keeps only elements matching a predicate.

```java
Flux<Integer> numbers = Flux.range(1, 10);
Flux<Integer> evens = numbers.filter(n -> n % 2 == 0);
// 2, 4, 6, 8, 10

Flux<User> users = userRepository.findAll();
Flux<User> activeAdmins = users
    .filter(User::isActive)
    .filter(User::isAdmin);
// Only active admin users

Flux<Order> orders = orderService.getOrders();
Flux<Order> highValue = orders.filter(o -> o.getTotal() > 1000);
// Only orders over $1000
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FILTER: CONDITIONAL KEEP                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Input:   ──(1)──(2)──(3)──(4)──(5)──|                                    │
│              │     │     │     │     │                                    │
│              │  filter(x -> x % 2 == 0)                                   │
│              ▼     ▼     ▼     ▼     ▼                                    │
│  Output:  ───────(2)────────(4)──────|                                    │
│              ↑           ↑           ↑                                    │
│              dropped     dropped     dropped                              │
│                                                                            │
│  Key points:                                                              │
│  • Output count <= input count                                           │
│  • Elements unchanged (just selected)                                    │
│  • Predicate: T → boolean                                                │
│                                                                            │
│  When to use:                                                             │
│  • Selecting: users.filter(User::isActive)                               │
│  • Validation: events.filter(this::isValid)                              │
│  • Range: numbers.filter(n -> n >= 10 && n <= 20)                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Combining map, flatMap, and filter

```java
// Real-world example: Get email addresses of active premium users
Flux<String> premiumEmails = userRepository.findAll()          // Flux<User>
    .filter(User::isActive)                                     // Keep active
    .filter(user -> "PREMIUM".equals(user.getSubscription()))   // Keep premium
    .flatMap(user -> addressService.getEmail(user.getId()))     // Fetch email (async)
    .filter(email -> email != null && !email.isBlank());        // Keep valid emails
```

---

## 5.5 Combining Streams: zip, merge, concat

Often you need to work with multiple streams. Here's how.

### zip: Pair Elements Together

`zip` combines elements from multiple streams into tuples (or custom combinations). It waits for one element from each stream before emitting.

```java
Flux<String> names = Flux.just("Alice", "Bob", "Charlie");
Flux<Integer> ages = Flux.just(25, 30, 35);

Flux<String> combined = Flux.zip(names, ages,
    (name, age) -> name + " is " + age);
// "Alice is 25", "Bob is 30", "Charlie is 35"

// Zip stops when the shortest stream completes
Flux<String> moreNames = Flux.just("Alice", "Bob", "Charlie", "Dave");
Flux<Integer> lessAges = Flux.just(25, 30);
Flux<String> partial = Flux.zip(moreNames, lessAges,
    (name, age) -> name + " is " + age);
// "Alice is 25", "Bob is 30" (stops at 2)
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ZIP: PAIR BY POSITION                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Stream1: ──(A)────(B)────(C)────|                                        │
│  Stream2: ──(1)──(2)────(3)──|                                            │
│               │     │     │                                                │
│               └──┬──┘     │                                                │
│                  │  ┌─────┘                                                │
│                  ▼  ▼                                                      │
│  Output:  ──(A,1)──(B,2)──(C,3)──|                                        │
│                                                                            │
│  Key points:                                                              │
│  • Waits for elements from ALL sources                                   │
│  • Pairs by emission order (1st with 1st, 2nd with 2nd)                 │
│  • Completes when ANY source completes                                   │
│                                                                            │
│  Use cases:                                                               │
│  • Combining related data: zip(names, addresses)                         │
│  • Parallel fetch + combine: zip(getUserMono, getOrdersMono)             │
│  • Coordinate multiple sources                                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### merge: Interleave As They Arrive

`merge` combines streams by interleaving elements as they arrive. No waiting, no coordination.

```java
Flux<String> fast = Flux.interval(Duration.ofMillis(100))
    .map(i -> "Fast-" + i)
    .take(5);

Flux<String> slow = Flux.interval(Duration.ofMillis(250))
    .map(i -> "Slow-" + i)
    .take(3);

Flux<String> merged = Flux.merge(fast, slow);
// Output order depends on timing: Fast-0, Fast-1, Slow-0, Fast-2, Fast-3, Slow-1, Fast-4, Slow-2
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    MERGE: INTERLEAVE BY TIME                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Stream1: ──(A)────────(B)────────(C)──|                                  │
│  Stream2: ────────(1)────────(2)────────(3)──|                            │
│              │      │    │      │    │      │                             │
│              └──────┼────┼──────┼────┼──────┘                             │
│                     │    │      │    │                                     │
│                     ▼    ▼      ▼    ▼                                     │
│  Output:  ──(A)──(1)──(B)──(2)──(C)──(3)──|                               │
│                                                                            │
│  Key points:                                                              │
│  • First-come, first-served                                              │
│  • No waiting for other streams                                          │
│  • Completes when ALL sources complete                                   │
│  • Errors propagate immediately                                          │
│                                                                            │
│  Use cases:                                                               │
│  • Multiple event sources: merge(clickEvents, keyEvents)                 │
│  • Parallel processing: merge results from multiple workers              │
│  • Real-time feeds: merge(stockPrices, news)                             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### concat: Sequential Appending

`concat` appends streams sequentially. The second starts only after the first completes.

```java
Flux<String> first = Flux.just("A", "B", "C");
Flux<String> second = Flux.just("X", "Y", "Z");

Flux<String> concatenated = Flux.concat(first, second);
// A, B, C, X, Y, Z (always this order)

// Useful for "try this, then that" patterns
Mono<User> fromCache = cache.get(userId);
Mono<User> fromDb = database.findById(userId);
Mono<User> user = Flux.concat(fromCache, fromDb).next();  // Cache first, then DB
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    CONCAT: SEQUENTIAL APPEND                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Stream1: ──(A)──(B)──(C)──|                                              │
│  Stream2:                   ──(1)──(2)──(3)──|                            │
│                                                                            │
│  Output:  ──(A)──(B)──(C)────(1)──(2)──(3)──|                             │
│                          │                                                 │
│                          └── Stream2 starts after Stream1 completes       │
│                                                                            │
│  Key points:                                                              │
│  • Strict ordering                                                        │
│  • Second source subscribes after first completes                        │
│  • Preserves order guarantee                                             │
│                                                                            │
│  Use cases:                                                               │
│  • Fallback: concat(primarySource, fallbackSource)                       │
│  • Pagination: concat(page1, page2, page3)                               │
│  • Ordered processing: concat(priority1, priority2)                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### When to Use Which

| Need | Operator | Behavior |
|------|----------|----------|
| Pair elements by position | zip | Wait for all, combine |
| Interleave as available | merge | First-come, first-served |
| Strict sequence | concat | One after another |
| Latest from each | combineLatest | Combine on each emission |

---

## 5.6 Reducing and Collecting

Sometimes you need to aggregate stream elements into a single result.

### reduce: Accumulate to Single Value

```java
// Sum all numbers
Mono<Integer> sum = Flux.range(1, 10)
    .reduce(0, Integer::sum);
// 55

// Find maximum
Mono<Integer> max = Flux.just(3, 1, 4, 1, 5, 9, 2, 6)
    .reduce(Integer::max);
// 9

// Build a string
Mono<String> joined = Flux.just("Hello", "Reactive", "World")
    .reduce("", (acc, word) -> acc.isEmpty() ? word : acc + " " + word);
// "Hello Reactive World"
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REDUCE: MANY TO ONE                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Marble diagram:                                                          │
│                                                                            │
│  Input:   ──(1)──(2)──(3)──(4)──(5)──|                                    │
│              │     │     │     │     │                                    │
│              └──┬──┘     │     │     │                                    │
│             (0+1=1)      │     │     │                                    │
│                 └────┬───┘     │     │                                    │
│                  (1+2=3)       │     │                                    │
│                      └────┬────┘     │                                    │
│                       (3+3=6)        │                                    │
│                           └─────┬────┘                                    │
│                             (6+4=10)                                       │
│                                 └──────┐                                   │
│                                 (10+5=15)                                  │
│                                        │                                   │
│  Output:  ──────────────────────────(15)──|                               │
│                                                                            │
│  Key points:                                                              │
│  • Flux<T> → Mono<T>                                                     │
│  • Waits for completion                                                  │
│  • Identity value optional                                               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### collect: Gather into Container

```java
// Collect to List
Mono<List<String>> list = Flux.just("A", "B", "C")
    .collectList();
// [A, B, C]

// Collect to Map
Mono<Map<String, User>> userMap = userFlux
    .collectMap(User::getId);
// {id1: User1, id2: User2, ...}

// Collect to grouped Map
Mono<Map<String, List<Order>>> ordersByStatus = orderFlux
    .collectMultimap(Order::getStatus);
// {PENDING: [o1, o3], SHIPPED: [o2], ...}

// Collect with custom collector
Mono<String> joined = Flux.just("A", "B", "C")
    .collect(Collectors.joining(", "));
// "A, B, C"
```

### count: Simple Counting

```java
Mono<Long> count = Flux.range(1, 100).count();
// 100

Mono<Long> activeUsers = userRepository.findAll()
    .filter(User::isActive)
    .count();
// Number of active users
```

### all/any: Boolean Aggregates

```java
// Are ALL users verified?
Mono<Boolean> allVerified = userFlux
    .all(User::isVerified);

// Is ANY order high-value?
Mono<Boolean> hasHighValue = orderFlux
    .any(order -> order.getTotal() > 10000);

// Are there NO errors?
Mono<Boolean> noErrors = eventFlux
    .filter(Event::isError)
    .hasElements()
    .map(hasErrors -> !hasErrors);
```

### Terminal vs Intermediate Operations

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    TERMINAL vs INTERMEDIATE                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  INTERMEDIATE OPERATORS:                                                   │
│  ─────────────────────────                                                 │
│  • Return Flux/Mono                                                       │
│  • Can be chained                                                         │
│  • Examples: map, filter, flatMap, take, skip                            │
│                                                                            │
│  flux.map(...).filter(...).flatMap(...)   // All intermediate            │
│                                                                            │
│  TERMINAL/AGGREGATING OPERATORS:                                          │
│  ─────────────────────────────────                                         │
│  • Often change Flux<T> → Mono<R>                                        │
│  • Consume the stream                                                     │
│  • Examples: reduce, collect, count, all, any                            │
│                                                                            │
│  Flux<T> ──► reduce() ──► Mono<T>                                        │
│  Flux<T> ──► collectList() ──► Mono<List<T>>                             │
│  Flux<T> ──► count() ──► Mono<Long>                                      │
│                                                                            │
│  NOTE: In reactive, these aren't truly "terminal" like in Stream API.    │
│  You still need to subscribe! The result is a Mono you can continue      │
│  chaining with Mono operators.                                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 5.7 Putting It All Together

Let's build a realistic pipeline that combines everything we've learned.

### Example: E-commerce Order Processing

```java
/**
 * Process recent orders:
 * 1. Get orders from the last 24 hours
 * 2. Filter to high-value orders (> $500)
 * 3. Fetch customer details for each
 * 4. Send notification emails
 * 5. Count how many were processed
 */
public Mono<Long> processRecentHighValueOrders() {
    return orderRepository.findAll()                           // Flux<Order>
        .filter(order -> order.getCreatedAt()
            .isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) // Recent only
        .filter(order -> order.getTotal() > 500)                // High-value only
        .flatMap(order -> customerService                       // Fetch customer
            .findById(order.getCustomerId())
            .map(customer -> new OrderWithCustomer(order, customer)))
        .flatMap(owc -> emailService                            // Send email
            .sendOrderNotification(owc.customer().getEmail(), owc.order())
            .thenReturn(owc))
        .doOnNext(owc -> log.info("Processed order {} for {}",
            owc.order().getId(), owc.customer().getName()))
        .count();                                               // Count processed
}

record OrderWithCustomer(Order order, Customer customer) {}
```

### Example: Parallel API Aggregation

```java
/**
 * Build a dashboard by fetching data from multiple sources in parallel.
 */
public Mono<Dashboard> buildDashboard(String userId) {
    Mono<UserProfile> profileMono = userService.getProfile(userId);
    Mono<List<Order>> ordersMono = orderService.getRecentOrders(userId).collectList();
    Mono<WalletBalance> balanceMono = walletService.getBalance(userId);
    Mono<List<Notification>> notificationsMono = notificationService
        .getUnread(userId).collectList();

    return Mono.zip(profileMono, ordersMono, balanceMono, notificationsMono)
        .map(tuple -> new Dashboard(
            tuple.getT1(),  // profile
            tuple.getT2(),  // orders
            tuple.getT3(),  // balance
            tuple.getT4()   // notifications
        ));
}
```

### Example: Streaming with Transformation

```java
/**
 * Stream products to a client, enriching with inventory data.
 */
public Flux<ProductDTO> streamProducts(String category) {
    return productRepository.findByCategory(category)          // Flux<Product>
        .flatMap(product -> inventoryService                   // Enrich with inventory
            .getStock(product.getId())
            .map(stock -> new ProductDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                stock.getQuantity(),
                stock.getQuantity() > 0
            ))
            .onErrorResume(e -> {                              // Handle inventory failure
                log.warn("Inventory unavailable for {}", product.getId());
                return Mono.just(new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    -1,                                        // Unknown stock
                    false
                ));
            }))
        .filter(dto -> dto.price() > 0);                       // Skip invalid
}
```

---

## 5.8 Summary

In this chapter, we've transformed from imperative thinkers to stream thinkers:

**From Loops to Streams:**
- Imperative: How (step by step)
- Declarative: What (describe transformations)
- Reactive: What + When (async, push-based)

**The Operator Mental Model:**
- Operators are assembly line stations
- Data flows through, transformations apply
- Marble diagrams visualize the flow

**Immutability:**
- Every operator returns a new Publisher
- Original unchanged, enabling composition and reuse

**Core Operators:**
- `map`: Transform each element (T → R)
- `flatMap`: Transform to Publisher, flatten (T → Publisher<R>)
- `filter`: Keep matching elements

**Combining Streams:**
- `zip`: Pair by position, wait for all
- `merge`: Interleave as available
- `concat`: Sequential append

**Aggregating:**
- `reduce`: Accumulate to single value
- `collect`: Gather into container
- `count`, `all`, `any`: Boolean and count aggregates

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Think Declaratively                                                   │
│     Describe WHAT should happen, not HOW to iterate.                     │
│                                                                            │
│  2. map vs flatMap                                                        │
│     map: T → R (sync, 1:1)                                               │
│     flatMap: T → Publisher<R> (async, 1:N)                               │
│                                                                            │
│  3. Read Marble Diagrams                                                  │
│     They're the universal language of reactive operators.                │
│                                                                            │
│  4. Operators Return New Publishers                                       │
│     Chain freely, compose naturally, reuse confidently.                  │
│                                                                            │
│  5. zip/merge/concat for Combining                                        │
│     zip: pair by position                                                │
│     merge: interleave by time                                            │
│     concat: sequential order                                             │
│                                                                            │
│  6. Practice Makes Fluent                                                 │
│     Stream thinking becomes natural with practice.                       │
│     Start simple, build complexity gradually.                            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 6, we'll tackle **error handling and resilience**—how to handle failures in reactive streams, implement retry logic, and build fault-tolerant pipelines. Errors in async code behave differently than traditional try-catch, and mastering them is essential for production code.

---

## Hands-On Lab 5: Stream Transformation Practice

Now it's time to practice stream thinking. In this lab, you'll:

1. Convert imperative loops to reactive streams
2. Use map, flatMap, and filter fluently
3. Combine multiple streams
4. Build complex real-world pipelines

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Reactor Reference: Which Operator Do I Need?](https://projectreactor.io/docs/core/release/reference/#which-operator) - Decision tree for operators
- [Marble Diagrams in RxMarbles](https://rxmarbles.com/) - Interactive marble diagrams
- [Learn RxJS](https://www.learnrxjs.io/) - Operator examples (concepts apply to Reactor)

---

## Discussion Questions

1. How would you explain the difference between map and flatMap to a colleague?

2. You have three API calls that can run in parallel. Which combining operator would you use?

3. In what scenario would concat be better than merge?

4. How does reactive's declarative style compare to Java Streams? What's the key difference?

5. Design a pipeline that: fetches users, filters active ones, fetches their orders, and calculates total revenue.
