# Lab 8: WebFlux Fundamentals

## Objective

In this lab, you'll get hands-on experience with Spring WebFlux fundamentals. You'll:

1. Create a WebFlux project from scratch
2. Build endpoints using both annotated and functional styles
3. Compare behavior between WebFlux and Spring MVC
4. Use BlockHound to detect blocking calls
5. Experience the event loop model in action

By the end, you'll have a clear understanding of how WebFlux differs from MVC and when each is appropriate.

---

## Prerequisites

- Java 17+ installed
- Maven or Gradle installed
- IDE of your choice (IntelliJ IDEA recommended)
- Understanding of Chapter 8 concepts
- Completion of Reactor labs (Part II)

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | WebFlux Project Setup | 10 min |
| 2 | Annotated Controllers | 20 min |
| 3 | Functional Endpoints | 20 min |
| 4 | Comparing with Spring MVC | 25 min |
| 5 | BlockHound for Blocking Detection | 15 min |
| 6 | Event Loop Exploration | 15 min |
| 7 | Reflection and Key Takeaways | 5 min |

**Total: ~110 minutes**

---

## Part 1: WebFlux Project Setup (10 min)

### Exercise 1.1: Create a WebFlux Project

Create a new directory `webflux-lab` and add the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>webflux-lab</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- BlockHound for blocking detection -->
        <dependency>
            <groupId>io.projectreactor.tools</groupId>
            <artifactId>blockhound</artifactId>
            <version>1.0.8.RELEASE</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Exercise 1.2: Create the Main Application

Create `src/main/java/com/example/webflux/WebFluxLabApplication.java`:

```java
package com.example.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebFluxLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebFluxLabApplication.class, args);
    }
}
```

### Exercise 1.3: Verify Netty is Running

Create `src/main/resources/application.properties`:

```properties
server.port=8080
logging.level.reactor.netty=DEBUG
```

Run the application:

```bash
mvn spring-boot:run
```

Observe the logs. You should see:
- **Netty** being started (not Tomcat!)
- Event loop threads being created

```
... Netty started on port 8080
... Started WebFluxLabApplication in X.XXX seconds
```

---

## Part 2: Annotated Controllers (20 min)

### Exercise 2.1: Create a Domain Model

Create `src/main/java/com/example/webflux/model/User.java`:

```java
package com.example.webflux.model;

public class User {
    private String id;
    private String name;
    private String email;

    public User() {}

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return "User{id='" + id + "', name='" + name + "', email='" + email + "'}";
    }
}
```

### Exercise 2.2: Create a Reactive Service

Create `src/main/java/com/example/webflux/service/UserService.java`:

```java
package com.example.webflux.service;

import com.example.webflux.model.User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserService() {
        // Seed some initial data
        save(new User("1", "Alice", "alice@example.com")).block();
        save(new User("2", "Bob", "bob@example.com")).block();
        save(new User("3", "Charlie", "charlie@example.com")).block();
    }

    public Mono<User> findById(String id) {
        // Simulate database latency
        return Mono.justOrEmpty(users.get(id))
            .delayElement(Duration.ofMillis(100))
            .doOnSubscribe(s -> logThread("findById"));
    }

    public Flux<User> findAll() {
        // Simulate database latency
        return Flux.fromIterable(users.values())
            .delayElements(Duration.ofMillis(50))
            .doOnSubscribe(s -> logThread("findAll"));
    }

    public Mono<User> save(User user) {
        return Mono.fromCallable(() -> {
            if (user.getId() == null) {
                user.setId(UUID.randomUUID().toString());
            }
            users.put(user.getId(), user);
            return user;
        })
        .delayElement(Duration.ofMillis(100))
        .doOnSubscribe(s -> logThread("save"));
    }

    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> users.remove(id))
            .then()
            .delayElement(Duration.ofMillis(50))
            .doOnSubscribe(s -> logThread("deleteById"));
    }

    private void logThread(String operation) {
        System.out.printf("[%s] %s on thread: %s%n",
            java.time.LocalTime.now().toString().substring(0, 12),
            operation,
            Thread.currentThread().getName()
        );
    }
}
```

### Exercise 2.3: Create an Annotated Controller

Create `src/main/java/com/example/webflux/controller/UserController.java`:

```java
package com.example.webflux.controller;

import com.example.webflux.model.User;
import com.example.webflux.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        System.out.println("Controller handling request on: " + Thread.currentThread().getName());
        return userService.findById(id);
    }

    @GetMapping
    public Flux<User> getAllUsers() {
        System.out.println("Controller handling request on: " + Thread.currentThread().getName());
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable String id) {
        return userService.deleteById(id);
    }

    // Server-Sent Events endpoint
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<User> streamUsers() {
        return userService.findAll()
            .delayElements(Duration.ofSeconds(1))
            .repeat();  // Infinite stream for demonstration
    }
}
```

### Exercise 2.4: Test the Endpoints

Start the application and test with curl:

```bash
# Get a single user
curl http://localhost:8080/api/users/1

# Get all users
curl http://localhost:8080/api/users

# Create a new user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Diana","email":"diana@example.com"}'

# Stream users (SSE)
curl http://localhost:8080/api/users/stream
```

**Observe the thread names in the console output.** Notice how few threads are used!

---

## Part 3: Functional Endpoints (20 min)

### Exercise 3.1: Create a Handler

Create `src/main/java/com/example/webflux/handler/ProductHandler.java`:

```java
package com.example.webflux.handler;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProductHandler {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public ProductHandler() {
        products.put("1", new Product("1", "Laptop", 999.99));
        products.put("2", new Product("2", "Phone", 699.99));
        products.put("3", new Product("3", "Tablet", 499.99));
    }

    public Mono<ServerResponse> getProduct(ServerRequest request) {
        String id = request.pathVariable("id");
        System.out.println("Handler on thread: " + Thread.currentThread().getName());

        return Mono.justOrEmpty(products.get(id))
            .delayElement(Duration.ofMillis(100))
            .flatMap(product -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getAllProducts(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Flux.fromIterable(products.values())
                .delayElements(Duration.ofMillis(50)), Product.class);
    }

    public Mono<ServerResponse> createProduct(ServerRequest request) {
        return request.bodyToMono(Product.class)
            .map(product -> {
                product.id = UUID.randomUUID().toString();
                products.put(product.id, product);
                return product;
            })
            .flatMap(product -> ServerResponse
                .created(URI.create("/api/products/" + product.id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product));
    }

    public Mono<ServerResponse> deleteProduct(ServerRequest request) {
        String id = request.pathVariable("id");
        products.remove(id);
        return ServerResponse.noContent().build();
    }

    // Stream prices with SSE
    public Mono<ServerResponse> streamPrices(ServerRequest request) {
        Flux<Map<String, Object>> prices = Flux.interval(Duration.ofSeconds(1))
            .map(i -> {
                Product p = products.values().iterator().next();
                double variation = (Math.random() - 0.5) * 20;  // +/- $10
                return Map.of(
                    "product", p.name,
                    "price", Math.round((p.price + variation) * 100.0) / 100.0,
                    "timestamp", System.currentTimeMillis()
                );
            });

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(prices, Map.class);
    }

    // Inner class for Product
    public static class Product {
        public String id;
        public String name;
        public double price;

        public Product() {}

        public Product(String id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }
}
```

### Exercise 3.2: Create a Router

Create `src/main/java/com/example/webflux/router/ProductRouter.java`:

```java
package com.example.webflux.router;

import com.example.webflux.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class ProductRouter {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions.route()
            .path("/api/products", builder -> builder
                .nest(accept(MediaType.APPLICATION_JSON), b -> b
                    .GET("/{id}", handler::getProduct)
                    .GET("", handler::getAllProducts)
                    .POST("", handler::createProduct)
                    .DELETE("/{id}", handler::deleteProduct)
                )
                .GET("/prices/stream", handler::streamPrices)
            )
            .build();
    }
}
```

### Exercise 3.3: Test Functional Endpoints

```bash
# Get a product
curl http://localhost:8080/api/products/1

# Get all products
curl http://localhost:8080/api/products

# Create a product
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Watch","price":299.99}'

# Stream prices (SSE)
curl http://localhost:8080/api/products/prices/stream
```

### Exercise 3.4: Compare the Two Styles

Create a comparison file `src/main/java/com/example/webflux/StyleComparison.java`:

```java
package com.example.webflux;

/**
 * STYLE COMPARISON: Annotated vs Functional
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    ANNOTATED CONTROLLERS                         │
 * ├──────────────────────────────────────────────────────────────────┤
 * │                                                                  │
 * │  @RestController                                                 │
 * │  @RequestMapping("/api/users")                                   │
 * │  public class UserController {                                   │
 * │                                                                  │
 * │      @GetMapping("/{id}")                                        │
 * │      public Mono<User> getUser(@PathVariable String id) {       │
 * │          return userService.findById(id);                        │
 * │      }                                                           │
 * │  }                                                               │
 * │                                                                  │
 * │  Pros:                                                           │
 * │  • Familiar if you know Spring MVC                              │
 * │  • Less boilerplate                                              │
 * │  • Annotations are declarative                                   │
 * │                                                                  │
 * │  Cons:                                                           │
 * │  • Less flexible for complex routing                            │
 * │  • Routes are static                                             │
 * │                                                                  │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    FUNCTIONAL ENDPOINTS                          │
 * ├──────────────────────────────────────────────────────────────────┤
 * │                                                                  │
 * │  @Bean                                                           │
 * │  RouterFunction<ServerResponse> routes(Handler h) {             │
 * │      return route()                                              │
 * │          .GET("/api/users/{id}", h::getUser)                    │
 * │          .build();                                               │
 * │  }                                                               │
 * │                                                                  │
 * │  Mono<ServerResponse> getUser(ServerRequest request) {          │
 * │      String id = request.pathVariable("id");                    │
 * │      return userService.findById(id)                            │
 * │          .flatMap(user -> ok().bodyValue(user))                 │
 * │          .switchIfEmpty(notFound().build());                    │
 * │  }                                                               │
 * │                                                                  │
 * │  Pros:                                                           │
 * │  • Routes are data (composable, dynamic)                        │
 * │  • Explicit control over responses                              │
 * │  • Better for complex routing logic                             │
 * │                                                                  │
 * │  Cons:                                                           │
 * │  • More verbose                                                  │
 * │  • Less familiar to MVC developers                              │
 * │                                                                  │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * KEY INSIGHT: Both are EQUALLY REACTIVE under the hood!
 * The choice is purely stylistic.
 */
public class StyleComparison {
    // This class is for documentation purposes
}
```

---

## Part 4: Comparing with Spring MVC (25 min)

Now let's create an equivalent Spring MVC application to compare behavior.

### Exercise 4.1: Create an MVC Project

Create a new directory `mvc-comparison` alongside `webflux-lab`.

Create `mvc-comparison/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>mvc-comparison</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring MVC (NOT WebFlux) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Exercise 4.2: Create MVC Application

Create `src/main/java/com/example/mvc/MvcComparisonApplication.java`:

```java
package com.example.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class MvcComparisonApplication {

    public static void main(String[] args) {
        SpringApplication.run(MvcComparisonApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/users")
class MvcUserController {

    private final Map<String, User> users = new ConcurrentHashMap<>();

    public MvcUserController() {
        users.put("1", new User("1", "Alice", "alice@example.com"));
        users.put("2", new User("2", "Bob", "bob@example.com"));
        users.put("3", new User("3", "Charlie", "charlie@example.com"));
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable String id) throws InterruptedException {
        System.out.println("MVC handling request on: " + Thread.currentThread().getName());
        Thread.sleep(100);  // Simulate database latency
        return users.get(id);
    }

    @GetMapping
    public Collection<User> getAllUsers() throws InterruptedException {
        System.out.println("MVC handling request on: " + Thread.currentThread().getName());
        Thread.sleep(100);  // Simulate database latency
        return users.values();
    }

    @PostMapping
    public User createUser(@RequestBody User user) throws InterruptedException {
        Thread.sleep(100);
        user.id = UUID.randomUUID().toString();
        users.put(user.id, user);
        return user;
    }
}

class User {
    public String id;
    public String name;
    public String email;

    public User() {}

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
}
```

Create `src/main/resources/application.properties`:

```properties
server.port=8081
```

### Exercise 4.3: Load Test Comparison

Create a simple load test script `load-test.sh`:

```bash
#!/bin/bash

echo "=== Load Testing WebFlux (port 8080) ==="
echo "Sending 100 concurrent requests..."

time (
    for i in {1..100}; do
        curl -s http://localhost:8080/api/users/1 > /dev/null &
    done
    wait
)

echo ""
echo "=== Load Testing MVC (port 8081) ==="
echo "Sending 100 concurrent requests..."

time (
    for i in {1..100}; do
        curl -s http://localhost:8081/api/users/1 > /dev/null &
    done
    wait
)
```

Run both applications and execute the load test:

```bash
# Terminal 1: Start WebFlux app
cd webflux-lab && mvn spring-boot:run

# Terminal 2: Start MVC app
cd mvc-comparison && mvn spring-boot:run

# Terminal 3: Run load test
chmod +x load-test.sh
./load-test.sh
```

**Observe:**
- Thread names in both applications
- Response times under load
- Thread count (use `jcmd <pid> Thread.print | grep -c "http-nio"` for MVC)

### Exercise 4.4: Thread Analysis

Create a thread monitoring endpoint in the WebFlux app.

Add to `UserController.java`:

```java
@GetMapping("/threads")
public Mono<Map<String, Object>> getThreadInfo() {
    return Mono.fromCallable(() -> {
        Map<String, Object> info = new HashMap<>();
        info.put("activeThreads", Thread.activeCount());
        info.put("currentThread", Thread.currentThread().getName());

        // Count event loop threads
        long eventLoopThreads = Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().contains("reactor-http-nio") ||
                        t.getName().contains("nioEventLoopGroup"))
            .count();
        info.put("eventLoopThreads", eventLoopThreads);

        return info;
    });
}
```

Test it:
```bash
curl http://localhost:8080/api/users/threads
```

---

## Part 5: BlockHound for Blocking Detection (15 min)

### Exercise 5.1: Create a Test with BlockHound

Create `src/test/java/com/example/webflux/BlockingDetectionTest.java`:

```java
package com.example.webflux;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;

class BlockingDetectionTest {

    @BeforeAll
    static void setUp() {
        BlockHound.install();
    }

    @Test
    void nonBlockingCodeShouldPass() {
        Mono<String> mono = Mono.just("Hello")
            .delayElement(Duration.ofMillis(10))
            .map(String::toUpperCase);

        StepVerifier.create(mono)
            .expectNext("HELLO")
            .verifyComplete();
    }

    @Test
    void blockingCodeShouldBeDetected() {
        // This test will FAIL because of Thread.sleep()
        Mono<String> blockingMono = Mono.fromCallable(() -> {
            Thread.sleep(10);  // BlockHound will catch this!
            return "Hello";
        }).subscribeOn(Schedulers.parallel());  // Run on event loop thread

        StepVerifier.create(blockingMono)
            .expectError()  // Expecting BlockHound error
            .verify();
    }

    @Test
    void blockingOnBoundedElasticIsOk() {
        // Blocking on boundedElastic is allowed (designed for blocking I/O)
        Mono<String> boundedElasticMono = Mono.fromCallable(() -> {
            Thread.sleep(10);  // This is OK on boundedElastic
            return "Hello";
        }).subscribeOn(Schedulers.boundedElastic());  // Safe for blocking

        StepVerifier.create(boundedElasticMono)
            .expectNext("Hello")
            .verifyComplete();
    }
}
```

### Exercise 5.2: Run the Tests

```bash
mvn test -Dtest=BlockingDetectionTest
```

Observe:
- `nonBlockingCodeShouldPass` passes
- `blockingCodeShouldBeDetected` fails with BlockHound error
- `blockingOnBoundedElasticIsOk` passes (boundedElastic allows blocking)

### Exercise 5.3: Create a Service with Accidental Blocking

Create `src/main/java/com/example/webflux/service/BadService.java`:

```java
package com.example.webflux.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BadService {

    // DON'T DO THIS IN REAL CODE!
    public Mono<String> getDataWithAccidentalBlocking() {
        return Mono.just("data")
            .map(data -> {
                // Simulating accidental blocking call
                try {
                    Thread.sleep(100);  // This is BAD!
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return data.toUpperCase();
            });
    }

    // CORRECT way to handle necessary blocking
    public Mono<String> getDataCorrectly() {
        return Mono.fromCallable(() -> {
            // If you MUST block, isolate it
            Thread.sleep(100);
            return "DATA";
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
```

Create a test to verify:

```java
@Test
void detectAccidentalBlocking() {
    BadService service = new BadService();

    // This will be caught by BlockHound
    StepVerifier.create(service.getDataWithAccidentalBlocking()
            .subscribeOn(Schedulers.parallel()))
        .expectError()
        .verify();
}

@Test
void correctBlockingHandling() {
    BadService service = new BadService();

    // This will pass because blocking is on boundedElastic
    StepVerifier.create(service.getDataCorrectly())
        .expectNext("DATA")
        .verifyComplete();
}
```

---

## Part 6: Event Loop Exploration (15 min)

### Exercise 6.1: Observe Thread Behavior Under Load

Create `src/main/java/com/example/webflux/controller/ThreadExplorationController.java`:

```java
package com.example.webflux.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/explore")
public class ThreadExplorationController {

    private final Set<String> observedThreads = ConcurrentHashMap.newKeySet();
    private final AtomicLong requestCount = new AtomicLong(0);

    @GetMapping("/slow")
    public Mono<Map<String, Object>> slowEndpoint() {
        long requestId = requestCount.incrementAndGet();
        String startThread = Thread.currentThread().getName();
        observedThreads.add(startThread);

        return Mono.delay(Duration.ofMillis(500))  // Simulate slow I/O
            .map(tick -> {
                String endThread = Thread.currentThread().getName();
                observedThreads.add(endThread);

                Map<String, Object> result = new HashMap<>();
                result.put("requestId", requestId);
                result.put("startThread", startThread);
                result.put("endThread", endThread);
                result.put("sameThead", startThread.equals(endThread));
                result.put("totalUniqueThreadsObserved", observedThreads.size());
                result.put("allObservedThreads", new ArrayList<>(observedThreads));
                return result;
            });
    }

    @GetMapping("/parallel-calls")
    public Mono<Map<String, Object>> parallelCalls() {
        long start = System.currentTimeMillis();

        // Simulate calling 5 external services in parallel
        return Flux.range(1, 5)
            .flatMap(i -> Mono.delay(Duration.ofMillis(200))
                .map(tick -> {
                    String thread = Thread.currentThread().getName();
                    return Map.of("service" + i, thread);
                }))
            .collectList()
            .map(results -> {
                Map<String, Object> response = new HashMap<>();
                response.put("totalTime", System.currentTimeMillis() - start);
                response.put("expectedIfSequential", "1000ms");
                response.put("results", results);
                return response;
            });
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> getStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRequestsHandled", requestCount.get());
            stats.put("uniqueThreadsUsed", observedThreads.size());
            stats.put("threadNames", new ArrayList<>(observedThreads));
            stats.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            return stats;
        });
    }

    @GetMapping("/reset")
    public Mono<String> reset() {
        observedThreads.clear();
        requestCount.set(0);
        return Mono.just("Stats reset");
    }
}
```

### Exercise 6.2: Load Test the Event Loop

Run multiple concurrent requests:

```bash
# Reset stats first
curl http://localhost:8080/api/explore/reset

# Send 50 concurrent slow requests
for i in {1..50}; do
    curl -s http://localhost:8080/api/explore/slow &
done
wait

# Check how many threads were used
curl http://localhost:8080/api/explore/stats | jq
```

**Questions to answer:**
1. How many unique threads handled 50 requests?
2. Did the start and end threads differ for requests?
3. What would happen with Spring MVC?

### Exercise 6.3: Demonstrate Parallel Execution

```bash
curl http://localhost:8080/api/explore/parallel-calls | jq
```

**Observe:**
- Total time should be ~200ms, not 1000ms
- Multiple threads may be used for the parallel calls

---

## Part 7: Reflection and Key Takeaways (5 min)

### Discussion Questions

1. **What surprised you about thread usage in WebFlux?**
   - How many threads handled your requests?
   - Did the thread change during a single request?

2. **When would the annotated style be preferable? The functional style?**

3. **How did BlockHound help identify issues?**

4. **What would happen if you used Thread.sleep() in a WebFlux handler?**

5. **Based on what you observed, when would you choose WebFlux over MVC?**

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. WebFlux uses very few threads                                         │
│     Event loop threads handle all non-blocking work.                      │
│     Observed: ~4-8 threads handling many requests.                        │
│                                                                            │
│  2. Threads can change during a request                                   │
│     The thread handling request start may differ from                     │
│     the thread handling request completion.                               │
│                                                                            │
│  3. Both programming models are reactive                                   │
│     Annotated controllers and functional endpoints                        │
│     have identical performance characteristics.                           │
│                                                                            │
│  4. BlockHound catches blocking calls                                     │
│     Essential for maintaining non-blocking guarantees.                    │
│     Use in tests, not production.                                         │
│                                                                            │
│  5. Parallel operations are natural                                        │
│     No thread pool exhaustion concerns.                                   │
│     I/O-bound parallelism scales easily.                                  │
│                                                                            │
│  6. MVC and WebFlux serve different needs                                 │
│     Choose based on workload characteristics,                             │
│     not "which is newer."                                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

### Challenge 1: WebSocket Implementation

Add a WebSocket endpoint to the WebFlux application:
- Accept connections at `/ws/chat`
- Echo messages back to the sender
- Log all messages to console

### Challenge 2: Aggregation Pattern

Create an endpoint that:
- Calls the user service and product service in parallel
- Combines results into a single response
- Handles partial failures gracefully

### Challenge 3: Custom BlockHound Allowance

Configure BlockHound to allow specific blocking calls (e.g., a legacy library that you can't change).

---

## Running the Examples

```bash
# Compile and run WebFlux app
cd webflux-lab
mvn spring-boot:run

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=BlockingDetectionTest

# Run MVC comparison app
cd mvc-comparison
mvn spring-boot:run
```

---

## What's Next?

In Chapter 9, we'll dive deep into **annotated controllers in WebFlux**. You'll learn:
- How return types change (`Mono<T>` instead of `T`)
- Handling request bodies reactively
- Server-Sent Events in detail
- Exception handling patterns
- Validation with reactive types

---

## Summary

In this lab, you:

1. ✅ Created a WebFlux project with Netty
2. ✅ Built endpoints using annotated controllers
3. ✅ Built endpoints using functional style
4. ✅ Compared behavior with Spring MVC
5. ✅ Used BlockHound to detect blocking calls
6. ✅ Explored event loop thread behavior

**Congratulations!** You now have practical experience with Spring WebFlux fundamentals. The event loop model should feel more concrete, and you understand the trade-offs between WebFlux and MVC.
