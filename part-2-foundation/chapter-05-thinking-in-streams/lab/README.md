# Lab 5: Stream Transformation Practice

## Objective

In this lab, you'll practice stream thinking by:

1. Converting imperative loops to reactive streams
2. Using map, flatMap, and filter fluently
3. Combining streams with zip, merge, and concat
4. Building complex real-world pipelines
5. Reading and writing marble diagrams

By the end, you'll think naturally in streams and operators will feel like second nature.

---

## Prerequisites

- Completed Lab 4 (basic Reactor project setup)
- Understanding of Chapter 5 concepts
- Java 17+ and Maven

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | Imperative to Reactive Conversion | 20 min |
| 2 | map vs flatMap Mastery | 25 min |
| 3 | filter and take/skip | 15 min |
| 4 | Combining Streams | 25 min |
| 5 | Aggregation and Collection | 15 min |
| 6 | Real-World Pipeline Challenge | 20 min |
| 7 | Reflection | 5 min |

**Total: ~125 minutes**

---

## Setup

Use the same project from Lab 4, or create a new one with:

```xml
<dependencies>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-core</artifactId>
        <version>3.6.0</version>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <version>3.6.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Part 1: Imperative to Reactive Conversion (20 min)

### Exercise 1.1: Simple Loop to Stream

Convert this imperative code to reactive:

```java
// Imperative version
List<String> getActiveUserNames(List<User> users) {
    List<String> names = new ArrayList<>();
    for (User user : users) {
        if (user.isActive()) {
            names.add(user.getName().toUpperCase());
        }
    }
    return names;
}
```

Create `src/main/java/com/example/lab/part1/ImperativeToReactive.java`:

```java
package com.example.lab.part1;

import reactor.core.publisher.Flux;
import java.util.List;

public class ImperativeToReactive {

    public static void main(String[] args) {
        List<User> users = List.of(
            new User("Alice", true),
            new User("Bob", false),
            new User("Charlie", true),
            new User("Diana", true),
            new User("Eve", false)
        );

        System.out.println("=== Imperative Version ===");
        System.out.println(getActiveUserNamesImperative(users));

        System.out.println("\n=== Reactive Version ===");
        getActiveUserNamesReactive(Flux.fromIterable(users))
            .collectList()
            .subscribe(System.out::println);
    }

    // Imperative (given)
    static List<String> getActiveUserNamesImperative(List<User> users) {
        List<String> names = new ArrayList<>();
        for (User user : users) {
            if (user.isActive()) {
                names.add(user.getName().toUpperCase());
            }
        }
        return names;
    }

    // TODO: Implement reactive version
    static Flux<String> getActiveUserNamesReactive(Flux<User> users) {
        // Hint: Use filter, map, and method references
        return null; // Replace this
    }

    record User(String name, boolean active) {
        boolean isActive() { return active; }
        String getName() { return name; }
    }
}
```

### Exercise 1.2: Nested Loops

Convert this nested loop:

```java
// Imperative: Get all items from all orders
List<Item> getAllItems(List<Order> orders) {
    List<Item> allItems = new ArrayList<>();
    for (Order order : orders) {
        for (Item item : order.getItems()) {
            if (item.getPrice() > 10) {
                allItems.add(item);
            }
        }
    }
    return allItems;
}
```

```java
// TODO: Implement reactive version
static Flux<Item> getAllItemsReactive(Flux<Order> orders) {
    // Hint: flatMap + filter
    return null;
}
```

### Exercise 1.3: Loop with Accumulator

Convert this accumulating loop:

```java
// Imperative: Calculate total price
double calculateTotal(List<Item> items) {
    double total = 0;
    for (Item item : items) {
        total += item.getPrice() * item.getQuantity();
    }
    return total;
}
```

```java
// TODO: Implement reactive version
static Mono<Double> calculateTotalReactive(Flux<Item> items) {
    // Hint: map (calculate line total) + reduce (sum)
    return null;
}
```

---

## Part 2: map vs flatMap Mastery (25 min)

Understanding when to use each is critical. Let's practice.

### Exercise 2.1: When to Use map

Create `src/main/java/com/example/lab/part2/MapVsFlatMap.java`:

```java
package com.example.lab.part2;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

public class MapVsFlatMap {

    public static void main(String[] args) throws InterruptedException {
        demonstrateMap();
        demonstrateFlatMapNeed();
        demonstrateFlatMapAsync();
        demonstrateMapVsFlatMapDifference();
    }

    static void demonstrateMap() {
        System.out.println("=== map: Synchronous 1:1 Transform ===");

        Flux<Integer> numbers = Flux.just(1, 2, 3, 4, 5);

        // Transform each number
        numbers.map(n -> n * 10)
               .subscribe(n -> System.out.println("Mapped: " + n));

        // Extract field
        Flux<String> users = Flux.just(
            new User("Alice", "alice@example.com"),
            new User("Bob", "bob@example.com")
        );

        users.map(User::email)
             .subscribe(email -> System.out.println("Email: " + email));

        System.out.println();
    }

    static void demonstrateFlatMapNeed() {
        System.out.println("=== Why flatMap? Unwrapping Publishers ===");

        Flux<String> userIds = Flux.just("u1", "u2", "u3");

        // WRONG: map with async operation
        System.out.println("With map (WRONG - nested Monos):");
        Flux<Mono<User>> wrongResult = userIds.map(id -> fetchUser(id));
        // Result is Flux<Mono<User>> - not useful!

        // CORRECT: flatMap unwraps
        System.out.println("\nWith flatMap (CORRECT - unwrapped):");
        Flux<User> correctResult = userIds.flatMap(id -> fetchUser(id));
        correctResult.subscribe(user -> System.out.println("Got: " + user));

        System.out.println();
    }

    static void demonstrateFlatMapAsync() throws InterruptedException {
        System.out.println("=== flatMap: Async and Concurrent ===");

        Flux<String> ids = Flux.just("A", "B", "C");

        System.out.println("Processing concurrently:");
        ids.flatMap(id -> simulateAsyncCall(id))
           .subscribe(result -> System.out.println("Result: " + result));

        Thread.sleep(2000);  // Wait for async completion
        System.out.println();
    }

    static void demonstrateMapVsFlatMapDifference() throws InterruptedException {
        System.out.println("=== The Key Difference ===");

        Flux<Integer> numbers = Flux.just(1, 2, 3);

        // map: Each number becomes ONE thing
        System.out.println("map (1:1):");
        numbers.map(n -> "Number-" + n)
               .subscribe(System.out::println);

        // flatMap: Each number becomes MANY things
        System.out.println("\nflatMap (1:N):");
        numbers.flatMap(n -> Flux.range(n, 3))  // 1 becomes [1,2,3], 2 becomes [2,3,4], etc.
               .subscribe(System.out::println);

        System.out.println();
    }

    // Simulated async operations
    static Mono<User> fetchUser(String id) {
        return Mono.just(new User("User-" + id, id + "@example.com"))
                   .delayElement(Duration.ofMillis(100));
    }

    static Mono<String> simulateAsyncCall(String id) {
        return Mono.just("Result for " + id)
                   .delayElement(Duration.ofMillis(500))
                   .doOnSubscribe(s -> System.out.println("  Starting " + id));
    }

    record User(String name, String email) {}
}
```

### Exercise 2.2: Fix the Bug

What's wrong with this code? Fix it.

```java
Flux<String> userIds = Flux.just("u1", "u2", "u3");

// Intention: Fetch user, then fetch their orders, return order IDs
Flux<String> orderIds = userIds
    .map(id -> userRepository.findById(id))           // Line 1
    .map(user -> orderRepository.findByUserId(user))  // Line 2
    .map(order -> order.getId());                     // Line 3

// TODO: This doesn't compile and doesn't work. Fix it.
// Hint: What type does findById return? What about findByUserId?
```

### Exercise 2.3: Decision Tree

For each scenario, decide: map or flatMap?

```java
// Scenario 1: Convert User to UserDTO
// users._____(user -> new UserDTO(user.getName(), user.getEmail()))
// Answer: ?

// Scenario 2: For each user, fetch their profile picture (returns Mono<Image>)
// users._____(user -> imageService.getProfileImage(user.getId()))
// Answer: ?

// Scenario 3: Convert price in cents to dollars
// prices._____(cents -> cents / 100.0)
// Answer: ?

// Scenario 4: For each order, get all its items (returns Flux<Item>)
// orders._____(order -> itemRepository.findByOrderId(order.getId()))
// Answer: ?

// Scenario 5: Check if user name is valid (returns boolean)
// users.filter(user -> user._____(user.getName()).startsWith("A"))
// Answer: Not _____, but what should be inside filter?
```

---

## Part 3: filter and take/skip (15 min)

### Exercise 3.1: Filtering Practice

Create `src/main/java/com/example/lab/part3/FilteringPractice.java`:

```java
package com.example.lab.part3;

import reactor.core.publisher.Flux;

public class FilteringPractice {

    public static void main(String[] args) {
        Flux<Integer> numbers = Flux.range(1, 20);
        Flux<Product> products = createProductFlux();

        System.out.println("=== Basic filter ===");
        // TODO: Keep only even numbers
        numbers.filter(/* TODO */)
               .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        System.out.println("\n=== Multiple filters ===");
        // TODO: Keep products that are in stock AND price > 50
        products.filter(/* TODO */)
                .filter(/* TODO */)
                .subscribe(p -> System.out.println(p));

        System.out.println("\n=== take ===");
        // TODO: Get first 5 numbers
        numbers.take(/* TODO */)
               .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        System.out.println("\n=== skip ===");
        // TODO: Skip first 15, take the rest
        numbers.skip(/* TODO */)
               .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        System.out.println("\n=== takeWhile ===");
        // TODO: Take while number < 10
        numbers.takeWhile(/* TODO */)
               .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        System.out.println("\n=== skipWhile ===");
        // TODO: Skip while number < 15, then take all remaining
        numbers.skipWhile(/* TODO */)
               .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        System.out.println("\n=== distinct ===");
        Flux<String> withDuplicates = Flux.just("A", "B", "A", "C", "B", "D");
        // TODO: Remove duplicates
        withDuplicates.distinct()
                      .subscribe(s -> System.out.print(s + " "));
        System.out.println();

        System.out.println("\n=== distinctUntilChanged ===");
        Flux<String> consecutive = Flux.just("A", "A", "B", "B", "B", "A", "C");
        // TODO: Remove consecutive duplicates only
        consecutive.distinctUntilChanged()
                   .subscribe(s -> System.out.print(s + " "));
        System.out.println();
    }

    static Flux<Product> createProductFlux() {
        return Flux.just(
            new Product("Laptop", 999.99, true),
            new Product("Mouse", 29.99, true),
            new Product("Keyboard", 79.99, false),
            new Product("Monitor", 299.99, true),
            new Product("USB Cable", 9.99, true),
            new Product("Webcam", 89.99, false)
        );
    }

    record Product(String name, double price, boolean inStock) {}
}
```

---

## Part 4: Combining Streams (25 min)

### Exercise 4.1: zip Practice

Create `src/main/java/com/example/lab/part4/CombiningStreams.java`:

```java
package com.example.lab.part4;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

public class CombiningStreams {

    public static void main(String[] args) throws InterruptedException {
        demonstrateZip();
        demonstrateMerge();
        demonstrateConcat();
        challengeParallelFetch();
    }

    static void demonstrateZip() {
        System.out.println("=== zip: Pair by Position ===\n");

        Flux<String> names = Flux.just("Alice", "Bob", "Charlie");
        Flux<Integer> ages = Flux.just(25, 30, 35);
        Flux<String> cities = Flux.just("NYC", "LA", "Chicago");

        // Zip two streams
        System.out.println("Two streams:");
        Flux.zip(names, ages, (name, age) -> name + " is " + age)
            .subscribe(System.out::println);

        // Zip three streams
        System.out.println("\nThree streams:");
        Flux.zip(names, ages, cities)
            .map(tuple -> tuple.getT1() + ", " + tuple.getT2() + ", from " + tuple.getT3())
            .subscribe(System.out::println);

        // What happens with different lengths?
        System.out.println("\nDifferent lengths (zip stops at shortest):");
        Flux<String> moreNames = Flux.just("Alice", "Bob", "Charlie", "Diana", "Eve");
        Flux<Integer> fewerAges = Flux.just(25, 30);
        Flux.zip(moreNames, fewerAges, (name, age) -> name + " is " + age)
            .subscribe(System.out::println);

        System.out.println();
    }

    static void demonstrateMerge() throws InterruptedException {
        System.out.println("=== merge: Interleave by Time ===\n");

        Flux<String> fast = Flux.interval(Duration.ofMillis(100))
            .map(i -> "Fast-" + i)
            .take(5);

        Flux<String> slow = Flux.interval(Duration.ofMillis(250))
            .map(i -> "Slow-" + i)
            .take(3);

        System.out.println("Merged (order depends on timing):");
        Flux.merge(fast, slow)
            .subscribe(System.out::println);

        Thread.sleep(1500);
        System.out.println();
    }

    static void demonstrateConcat() throws InterruptedException {
        System.out.println("=== concat: Sequential ===\n");

        Flux<String> first = Flux.just("A", "B", "C")
            .delayElements(Duration.ofMillis(100));

        Flux<String> second = Flux.just("X", "Y", "Z")
            .delayElements(Duration.ofMillis(100));

        System.out.println("Concatenated (first completes, then second starts):");
        Flux.concat(first, second)
            .subscribe(System.out::println);

        Thread.sleep(1000);
        System.out.println();
    }

    static void challengeParallelFetch() throws InterruptedException {
        System.out.println("=== Challenge: Parallel Fetch with zip ===\n");

        // Simulate fetching from multiple services
        Mono<String> userMono = fetchUser("u123");
        Mono<String> ordersMono = fetchOrders("u123");
        Mono<String> preferencesMono = fetchPreferences("u123");

        System.out.println("Fetching user data in parallel...");
        long start = System.currentTimeMillis();

        // TODO: Use Mono.zip to fetch all three in parallel
        // and combine into a single result
        Mono.zip(userMono, ordersMono, preferencesMono)
            .map(tuple -> String.format(
                "User: %s\nOrders: %s\nPreferences: %s",
                tuple.getT1(), tuple.getT2(), tuple.getT3()))
            .subscribe(result -> {
                long duration = System.currentTimeMillis() - start;
                System.out.println(result);
                System.out.println("\nCompleted in " + duration + "ms");
                System.out.println("(Should be ~500ms, not ~1500ms if parallel)");
            });

        Thread.sleep(1000);
    }

    // Simulated async fetches (each takes 500ms)
    static Mono<String> fetchUser(String id) {
        return Mono.just("User{id=" + id + ", name=Alice}")
                   .delayElement(Duration.ofMillis(500))
                   .doOnSubscribe(s -> System.out.println("  Fetching user..."));
    }

    static Mono<String> fetchOrders(String userId) {
        return Mono.just("Orders[order1, order2, order3]")
                   .delayElement(Duration.ofMillis(500))
                   .doOnSubscribe(s -> System.out.println("  Fetching orders..."));
    }

    static Mono<String> fetchPreferences(String userId) {
        return Mono.just("Preferences{theme=dark, language=en}")
                   .delayElement(Duration.ofMillis(500))
                   .doOnSubscribe(s -> System.out.println("  Fetching preferences..."));
    }
}
```

### Exercise 4.2: Choose the Right Operator

For each scenario, which operator would you use?

```
1. Combine user profile (Mono) with user orders (Mono) into a dashboard
   Answer: ________

2. Listen to both click events and keyboard events
   Answer: ________

3. Try cache first, if empty then try database
   Answer: ________

4. Pair products with their prices from a separate stream
   Answer: ________

5. Combine responses from 5 microservices into one aggregated response
   Answer: ________
```

---

## Part 5: Aggregation and Collection (15 min)

### Exercise 5.1: Aggregation Practice

Create `src/main/java/com/example/lab/part5/AggregationPractice.java`:

```java
package com.example.lab.part5;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.stream.Collectors;

public class AggregationPractice {

    public static void main(String[] args) {
        Flux<Integer> numbers = Flux.just(5, 3, 8, 1, 9, 2, 7, 4, 6);
        Flux<Order> orders = createOrderFlux();

        System.out.println("=== reduce: Sum ===");
        // TODO: Calculate sum of all numbers
        numbers.reduce(0, Integer::sum)
               .subscribe(sum -> System.out.println("Sum: " + sum));

        System.out.println("\n=== reduce: Max ===");
        // TODO: Find maximum
        numbers.reduce(Integer::max)
               .subscribe(max -> System.out.println("Max: " + max));

        System.out.println("\n=== reduce: Custom ===");
        // TODO: Build a comma-separated string
        Flux.just("A", "B", "C", "D")
            .reduce((a, b) -> a + ", " + b)
            .subscribe(str -> System.out.println("Joined: " + str));

        System.out.println("\n=== collectList ===");
        // TODO: Collect all numbers into a List
        numbers.collectList()
               .subscribe(list -> System.out.println("List: " + list));

        System.out.println("\n=== collectMap ===");
        // TODO: Collect orders into a Map by ID
        orders.collectMap(Order::id)
              .subscribe(map -> System.out.println("Map: " + map));

        System.out.println("\n=== collectMultimap ===");
        // TODO: Group orders by status
        orders.collectMultimap(Order::status)
              .subscribe(map -> {
                  System.out.println("Grouped by status:");
                  map.forEach((status, orderList) ->
                      System.out.println("  " + status + ": " + orderList.size() + " orders"));
              });

        System.out.println("\n=== count ===");
        // TODO: Count orders with total > 100
        orders.filter(o -> o.total() > 100)
              .count()
              .subscribe(count -> System.out.println("High-value orders: " + count));

        System.out.println("\n=== all ===");
        // TODO: Check if ALL orders are completed
        orders.all(o -> "COMPLETED".equals(o.status()))
              .subscribe(allCompleted -> System.out.println("All completed: " + allCompleted));

        System.out.println("\n=== any ===");
        // TODO: Check if ANY order is pending
        orders.any(o -> "PENDING".equals(o.status()))
              .subscribe(hasPending -> System.out.println("Has pending: " + hasPending));

        System.out.println("\n=== hasElements ===");
        Flux<String> empty = Flux.empty();
        empty.hasElements()
             .subscribe(hasAny -> System.out.println("Empty flux has elements: " + hasAny));
    }

    static Flux<Order> createOrderFlux() {
        return Flux.just(
            new Order("o1", "COMPLETED", 150.00),
            new Order("o2", "PENDING", 75.50),
            new Order("o3", "COMPLETED", 220.00),
            new Order("o4", "SHIPPED", 180.00),
            new Order("o5", "PENDING", 45.00),
            new Order("o6", "COMPLETED", 310.00)
        );
    }

    record Order(String id, String status, double total) {}
}
```

### Exercise 5.2: Complex Aggregation

Calculate these statistics from a stream of sales:

```java
record Sale(String product, String category, double amount, String region);

Flux<Sale> sales = getSalesFlux();

// TODO: Calculate:
// 1. Total revenue (sum of all amounts)
// 2. Average sale amount
// 3. Revenue by category (Map<String, Double>)
// 4. Top 3 products by total revenue
// 5. Number of sales per region
```

---

## Part 6: Real-World Pipeline Challenge (20 min)

### Exercise 6.1: E-Commerce Order Processing

Build a complete reactive pipeline:

```java
package com.example.lab.part6;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

public class OrderProcessingChallenge {

    public static void main(String[] args) throws InterruptedException {
        OrderProcessor processor = new OrderProcessor();

        // Process orders placed in the last hour
        processor.processRecentOrders()
                 .subscribe(
                     result -> System.out.println("Processed: " + result),
                     error -> System.err.println("Error: " + error),
                     () -> System.out.println("\nProcessing complete!")
                 );

        Thread.sleep(3000);
    }
}

class OrderProcessor {

    /**
     * TODO: Implement this pipeline
     *
     * Requirements:
     * 1. Get all orders from orderRepository.findAll()
     * 2. Filter to orders placed within the last hour
     * 3. Filter to orders with status "PENDING"
     * 4. For each order, fetch customer details (customerService.findById)
     * 5. For each order, calculate shipping cost (shippingService.calculateCost)
     * 6. Combine order + customer + shipping into OrderSummary
     * 7. Log each processed order
     * 8. Return Flux<OrderSummary>
     */
    public Flux<OrderSummary> processRecentOrders() {
        // TODO: Implement
        return Flux.empty();  // Replace this
    }

    // Simulated services
    OrderRepository orderRepository = new OrderRepository();
    CustomerService customerService = new CustomerService();
    ShippingService shippingService = new ShippingService();

    // Domain classes
    record Order(String id, String customerId, String status,
                 double total, Instant createdAt, List<String> items) {}

    record Customer(String id, String name, String email, String address) {}

    record ShippingCost(double cost, String carrier, int estimatedDays) {}

    record OrderSummary(Order order, Customer customer, ShippingCost shipping) {}

    // Simulated repositories/services
    class OrderRepository {
        Flux<Order> findAll() {
            return Flux.just(
                new Order("o1", "c1", "PENDING", 150.0, Instant.now().minus(Duration.ofMinutes(30)), List.of("item1")),
                new Order("o2", "c2", "COMPLETED", 200.0, Instant.now().minus(Duration.ofMinutes(45)), List.of("item2")),
                new Order("o3", "c1", "PENDING", 75.0, Instant.now().minus(Duration.ofHours(2)), List.of("item3")),
                new Order("o4", "c3", "PENDING", 300.0, Instant.now().minus(Duration.ofMinutes(15)), List.of("item4"))
            ).delayElements(Duration.ofMillis(100));
        }
    }

    class CustomerService {
        Mono<Customer> findById(String id) {
            Map<String, Customer> customers = Map.of(
                "c1", new Customer("c1", "Alice", "alice@example.com", "123 Main St"),
                "c2", new Customer("c2", "Bob", "bob@example.com", "456 Oak Ave"),
                "c3", new Customer("c3", "Charlie", "charlie@example.com", "789 Pine Rd")
            );
            return Mono.justOrEmpty(customers.get(id))
                       .delayElement(Duration.ofMillis(150));
        }
    }

    class ShippingService {
        Mono<ShippingCost> calculateCost(Order order) {
            double cost = order.total() > 100 ? 0.0 : 9.99;
            return Mono.just(new ShippingCost(cost, "FastShip", 3))
                       .delayElement(Duration.ofMillis(100));
        }
    }
}
```

### Exercise 6.2: Dashboard Aggregation

Build a dashboard that aggregates data from multiple sources:

```java
/**
 * Build a user dashboard by:
 * 1. Fetch user profile
 * 2. Fetch recent orders (last 5)
 * 3. Fetch unread notifications
 * 4. Fetch account balance
 * 5. All fetches should happen in PARALLEL
 * 6. Combine into Dashboard object
 */
public Mono<Dashboard> buildDashboard(String userId) {
    // TODO: Implement using Mono.zip
    return Mono.empty();
}

record Dashboard(
    UserProfile profile,
    List<Order> recentOrders,
    List<Notification> notifications,
    AccountBalance balance
) {}
```

---

## Part 7: Reflection (5 min)

### Discussion Questions

1. **What was the hardest part of thinking in streams?**
   - Understanding when to use flatMap?
   - Visualizing the data flow?
   - Debugging the pipelines?

2. **How do you decide between map and flatMap now?**

3. **What patterns do you see emerging?**
   - filter + map + flatMap combinations
   - zip for parallel aggregation
   - concat for fallbacks

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. THINK DECLARATIVELY                                                   │
│     Describe what you want, not how to iterate.                          │
│                                                                            │
│  2. THE MAP/FLATMAP RULE                                                  │
│     Function returns T → R? Use map.                                     │
│     Function returns T → Publisher<R>? Use flatMap.                      │
│                                                                            │
│  3. FILTER EARLY                                                          │
│     Reduce elements before expensive operations.                          │
│                                                                            │
│  4. ZIP FOR PARALLEL                                                      │
│     Multiple independent fetches? Use zip.                               │
│                                                                            │
│  5. CONCAT FOR FALLBACK                                                   │
│     Try primary source, then fallback? Use concat.                       │
│                                                                            │
│  6. REDUCE FOR AGGREGATION                                                │
│     Many elements → one result? Use reduce or collect.                   │
│                                                                            │
│  7. PRACTICE MAKES FLUENT                                                 │
│     Stream thinking becomes natural with practice.                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus: Marble Diagram Practice

Draw marble diagrams for these operations:

```
1. Flux.just(1, 2, 3).map(x -> x * 10)

2. Flux.just(1, 2, 3, 4, 5).filter(x -> x % 2 == 0)

3. Flux.just("a", "b").flatMap(s -> Flux.just(s + "1", s + "2"))

4. Flux.zip(
       Flux.just("A", "B", "C"),
       Flux.just(1, 2, 3)
   )

5. Flux.merge(
       Flux.just("A", "B").delayElements(100ms),
       Flux.just("1", "2").delayElements(150ms)
   )
```

---

## Running the Examples

```bash
# Compile
mvn compile

# Run specific exercise
mvn exec:java -Dexec.mainClass="com.example.lab.part1.ImperativeToReactive"
mvn exec:java -Dexec.mainClass="com.example.lab.part2.MapVsFlatMap"
mvn exec:java -Dexec.mainClass="com.example.lab.part4.CombiningStreams"
mvn exec:java -Dexec.mainClass="com.example.lab.part6.OrderProcessingChallenge"
```

---

## What's Next?

In Chapter 6, we'll tackle **error handling and resilience**—how to handle failures, implement retries, timeouts, and fallbacks in reactive streams.

---

## Summary

In this lab, you practiced:

1. ✅ Converting imperative loops to reactive streams
2. ✅ Using map vs flatMap correctly
3. ✅ Filtering and selecting elements
4. ✅ Combining streams with zip, merge, concat
5. ✅ Aggregating with reduce and collect
6. ✅ Building real-world pipelines

**Congratulations!** You now think in streams. This fundamental skill will serve you throughout your reactive programming journey.
