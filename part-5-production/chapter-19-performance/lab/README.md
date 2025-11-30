# Lab 19: Performance Tuning Reactive Applications

## Objectives

By the end of this lab, you will:

1. Profile a reactive application to identify bottlenecks
2. Detect and fix blocking calls in reactive chains
3. Optimize event loop utilization
4. Configure connection pools for optimal performance
5. Implement memory-efficient streaming
6. Conduct load testing and analyze results
7. Create a performance monitoring dashboard

## Prerequisites

- Completed Chapters 17-18 (Testing and Observability)
- Docker and Docker Compose installed
- Apache Benchmark (ab) or wrk installed
- JDK 17+ with VisualVM or async-profiler available

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup & Baseline Measurement | 15 min |
| 2 | Blocking Call Detection | 20 min |
| 3 | Event Loop Optimization | 20 min |
| 4 | Connection Pool Tuning | 20 min |
| 5 | Memory Optimization | 20 min |
| 6 | Load Testing & Analysis | 20 min |
| 7 | Performance Dashboard | 15 min |
| **Total** | | **130 min** |

---

## Part 1: Project Setup & Baseline Measurement (15 min)

### Step 1.1: Create the Project

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>performance-lab</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Actuator for metrics -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Micrometer Prometheus -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- R2DBC H2 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.r2dbc</groupId>
            <artifactId>r2dbc-h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- BlockHound for blocking detection -->
        <dependency>
            <groupId>io.projectreactor.tools</groupId>
            <artifactId>blockhound</artifactId>
            <version>1.0.8.RELEASE</version>
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

### Step 1.2: Create Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: performance-lab

  r2dbc:
    url: r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1
    username: sa
    password:

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99

logging:
  level:
    root: INFO
    reactor.netty: INFO
```

### Step 1.3: Create Sample Application with Performance Issues

```java
package com.example.performance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PerformanceLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(PerformanceLabApplication.class, args);
    }
}
```

```java
package com.example.performance.model;

public record Product(
    Long id,
    String name,
    String description,
    double price,
    int stock
) {}
```

```java
package com.example.performance.service;

import com.example.performance.model.Product;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public ProductService() {
        // Initialize with sample data
        for (int i = 0; i < 1000; i++) {
            Product product = new Product(
                idGenerator.getAndIncrement(),
                "Product " + i,
                "Description for product " + i,
                Math.random() * 100,
                (int) (Math.random() * 100)
            );
            products.put(product.id(), product);
        }
    }

    public Mono<Product> findById(Long id) {
        return Mono.justOrEmpty(products.get(id));
    }

    public Flux<Product> findAll() {
        return Flux.fromIterable(products.values());
    }

    public Mono<Product> save(Product product) {
        Long id = product.id() != null ? product.id() : idGenerator.getAndIncrement();
        Product savedProduct = new Product(id, product.name(), product.description(),
            product.price(), product.stock());
        products.put(id, savedProduct);
        return Mono.just(savedProduct);
    }

    // Intentionally problematic method for demonstration
    public Flux<Product> searchProducts(String query) {
        return Flux.fromIterable(products.values())
            .filter(p -> p.name().toLowerCase().contains(query.toLowerCase()));
    }
}
```

```java
package com.example.performance.controller;

import com.example.performance.model.Product;
import com.example.performance.service.ProductService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public Mono<Product> getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping
    public Flux<Product> getAllProducts() {
        return productService.findAll();
    }

    @PostMapping
    public Mono<Product> createProduct(@RequestBody Product product) {
        return productService.save(product);
    }

    @GetMapping("/search")
    public Flux<Product> searchProducts(@RequestParam String q) {
        return productService.searchProducts(q);
    }
}
```

### Step 1.4: Establish Baseline Performance

```bash
# Start the application
./mvnw spring-boot:run &

# Wait for startup
sleep 10

# Run baseline load test with Apache Benchmark
ab -n 10000 -c 100 http://localhost:8080/api/products/1

# Or with wrk for more accurate results
wrk -t4 -c100 -d30s http://localhost:8080/api/products/1

# Record these metrics:
# - Requests per second
# - Latency (mean, p50, p95, p99)
# - Errors
```

**Record your baseline metrics:**

| Metric | Value |
|--------|-------|
| Requests/sec | _____ |
| Mean latency | _____ ms |
| p99 latency | _____ ms |
| Errors | _____ |

---

## Part 2: Blocking Call Detection (20 min)

### Step 2.1: Add Intentional Blocking Code

```java
package com.example.performance.service;

import com.example.performance.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProblematicService {

    private static final Logger log = LoggerFactory.getLogger(ProblematicService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // PROBLEM 1: Blocking file I/O
    public Mono<String> readConfig() {
        return Mono.fromCallable(() -> {
            // This blocks the event loop!
            return Files.readString(Path.of("/tmp/config.json"));
        });
    }

    // PROBLEM 2: Thread.sleep
    public Mono<Product> processWithDelay(Product product) {
        return Mono.fromCallable(() -> {
            // This blocks the event loop!
            Thread.sleep(100);
            return product;
        });
    }

    // PROBLEM 3: Synchronous HTTP call (simulated)
    public Mono<String> callExternalService() {
        return Mono.fromCallable(() -> {
            // Simulating a blocking HTTP call
            Thread.sleep(50);
            return "response";
        });
    }

    // PROBLEM 4: Heavy JSON serialization
    public Mono<String> serializeLargeObject(Object obj) {
        return Mono.fromCallable(() -> {
            // Large object serialization can block
            return objectMapper.writeValueAsString(obj);
        });
    }
}
```

### Step 2.2: Enable BlockHound

```java
package com.example.performance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.blockhound.BlockHound;

import javax.annotation.PostConstruct;

@Configuration
@Profile("blockhound")
public class BlockHoundConfig {

    @PostConstruct
    public void enableBlockHound() {
        BlockHound.builder()
            // Allow specific blocking calls that are known to be safe
            .allowBlockingCallsInside("java.util.UUID", "randomUUID")
            .allowBlockingCallsInside("java.security.SecureRandom", "nextBytes")
            // Log instead of throwing
            .blockingMethodCallback(method -> {
                System.err.println("Blocking call detected: " + method);
                new Exception("Blocking call stack trace").printStackTrace();
            })
            .install();
    }
}
```

### Step 2.3: Create Test to Detect Blocking

```java
package com.example.performance;

import com.example.performance.service.ProblematicService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class BlockingDetectionTest {

    @BeforeAll
    static void setupBlockHound() {
        BlockHound.install();
    }

    @Test
    void detectBlockingInProcessWithDelay() {
        ProblematicService service = new ProblematicService();

        // This should fail because Thread.sleep blocks!
        Mono<String> mono = Mono.fromCallable(() -> {
            Thread.sleep(10);
            return "done";
        }).subscribeOn(Schedulers.parallel());  // parallel scheduler doesn't allow blocking

        StepVerifier.create(mono)
            .expectErrorMatches(e -> e.getMessage().contains("Blocking call"))
            .verify();
    }
}
```

### Step 2.4: Fix Blocking Calls

```java
package com.example.performance.service;

import com.example.performance.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class FixedService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public FixedService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // FIX 1: Use reactive file reading
    public Mono<String> readConfig() {
        return DataBufferUtils.read(
                Path.of("/tmp/config.json"),
                new DefaultDataBufferFactory(),
                8192)
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .reduce(String::concat)
            .onErrorReturn("{}");  // Default config if file not found
    }

    // FIX 2: Use non-blocking delay
    public Mono<Product> processWithDelay(Product product) {
        return Mono.just(product)
            .delayElement(Duration.ofMillis(100));  // Non-blocking delay!
    }

    // FIX 3: Use WebClient for HTTP calls
    public Mono<String> callExternalService() {
        return webClient.get()
            .uri("http://example.com/api")
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(5))
            .onErrorReturn("fallback");
    }

    // FIX 4: Offload heavy serialization to boundedElastic
    public Mono<String> serializeLargeObject(Object obj) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(obj))
            .subscribeOn(Schedulers.boundedElastic());  // Offload blocking work!
    }
}
```

### Step 2.5: Verify Fixes

Run the application with BlockHound enabled:

```bash
./mvnw spring-boot:run -Dspring.profiles.active=blockhound

# Run load test again
wrk -t4 -c100 -d30s http://localhost:8080/api/products/1

# Check console for any blocking call warnings
```

---

## Part 3: Event Loop Optimization (20 min)

### Step 3.1: Create CPU-Intensive Endpoint

```java
package com.example.performance.controller;

import com.example.performance.model.Product;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/compute")
public class ComputeController {

    // BAD: CPU-intensive work on event loop
    @GetMapping("/bad/hash")
    public Mono<String> badHash(@RequestParam String input) {
        return Mono.fromCallable(() -> {
            // This blocks the event loop!
            return computeExpensiveHash(input);
        });
    }

    // GOOD: CPU-intensive work offloaded
    @GetMapping("/good/hash")
    public Mono<String> goodHash(@RequestParam String input) {
        return Mono.fromCallable(() -> computeExpensiveHash(input))
            .subscribeOn(Schedulers.parallel());  // CPU-bound scheduler
    }

    // BAD: Unbounded parallel processing
    @PostMapping("/bad/batch")
    public Flux<String> badBatch(@RequestBody List<String> inputs) {
        return Flux.fromIterable(inputs)
            .flatMap(input -> Mono.fromCallable(() -> computeExpensiveHash(input)));
            // Unbounded concurrency!
    }

    // GOOD: Bounded parallel processing
    @PostMapping("/good/batch")
    public Flux<String> goodBatch(@RequestBody List<String> inputs) {
        return Flux.fromIterable(inputs)
            .flatMap(input ->
                    Mono.fromCallable(() -> computeExpensiveHash(input))
                        .subscribeOn(Schedulers.parallel()),
                Runtime.getRuntime().availableProcessors()  // Bounded!
            );
    }

    private String computeExpensiveHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Simulate expensive computation
            for (int i = 0; i < 1000; i++) {
                md.update(input.getBytes());
            }
            byte[] digest = md.digest();
            return new BigInteger(1, digest).toString(16);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Step 3.2: Configure Custom Schedulers

```java
package com.example.performance.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class SchedulerConfig {

    @Bean
    public Scheduler cpuIntensiveScheduler() {
        return Schedulers.newParallel(
            "cpu-intensive",
            Runtime.getRuntime().availableProcessors(),
            true  // daemon threads
        );
    }

    @Bean
    public Scheduler blockingScheduler() {
        return Schedulers.newBoundedElastic(
            20,                    // thread cap
            10000,                 // queued task cap
            "blocking-io",
            60,                    // TTL in seconds
            true                   // daemon threads
        );
    }
}
```

### Step 3.3: Monitor Scheduler Usage

```java
package com.example.performance.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;

@Component
public class SchedulerMetrics {

    private final MeterRegistry registry;

    public SchedulerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void enableSchedulerMetrics() {
        Schedulers.enableMetrics();
    }
}
```

### Step 3.4: Compare Performance

```bash
# Test bad endpoint (blocks event loop)
wrk -t4 -c100 -d30s "http://localhost:8080/api/compute/bad/hash?input=test"

# Test good endpoint (offloaded)
wrk -t4 -c100 -d30s "http://localhost:8080/api/compute/good/hash?input=test"

# Compare:
# - Throughput difference
# - Latency consistency
# - Event loop utilization
```

---

## Part 4: Connection Pool Tuning (20 min)

### Step 4.1: Create External Service Client

```java
package com.example.performance.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    // BAD: Default connection pool (not optimized)
    @Bean("defaultWebClient")
    public WebClient defaultWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl("http://localhost:8081")
            .build();
    }

    // GOOD: Optimized connection pool
    @Bean("optimizedWebClient")
    public WebClient optimizedWebClient(WebClient.Builder builder) {
        ConnectionProvider provider = ConnectionProvider.builder("optimized")
            .maxConnections(500)                           // Max total connections
            .maxIdleTime(Duration.ofSeconds(30))           // Close idle connections
            .maxLifeTime(Duration.ofMinutes(5))            // Max connection lifetime
            .pendingAcquireTimeout(Duration.ofSeconds(60)) // Wait time for connection
            .evictInBackground(Duration.ofSeconds(120))    // Background cleanup
            .metrics(true)                                 // Enable metrics
            .build();

        HttpClient httpClient = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .responseTimeout(Duration.ofSeconds(30))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS))
            );

        return builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl("http://localhost:8081")
            .build();
    }
}
```

### Step 4.2: Create Mock External Service

```java
package com.example.performance.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/mock")
public class MockExternalServiceController {

    @GetMapping("/fast")
    public Mono<String> fastEndpoint() {
        return Mono.just("fast response");
    }

    @GetMapping("/slow")
    public Mono<String> slowEndpoint() {
        return Mono.just("slow response")
            .delayElement(Duration.ofMillis(100));
    }

    @GetMapping("/variable")
    public Mono<String> variableEndpoint() {
        int delay = (int) (Math.random() * 200);
        return Mono.just("response after " + delay + "ms")
            .delayElement(Duration.ofMillis(delay));
    }
}
```

### Step 4.3: Create Test Endpoint

```java
package com.example.performance.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/aggregate")
public class AggregationController {

    private final WebClient defaultClient;
    private final WebClient optimizedClient;

    public AggregationController(
            @Qualifier("defaultWebClient") WebClient defaultClient,
            @Qualifier("optimizedWebClient") WebClient optimizedClient) {
        this.defaultClient = defaultClient;
        this.optimizedClient = optimizedClient;
    }

    @GetMapping("/default")
    public Mono<String> aggregateWithDefault(@RequestParam(defaultValue = "10") int calls) {
        return Flux.range(1, calls)
            .flatMap(i -> defaultClient.get()
                .uri("/mock/variable")
                .retrieve()
                .bodyToMono(String.class), 50)
            .collectList()
            .map(list -> "Completed " + list.size() + " calls");
    }

    @GetMapping("/optimized")
    public Mono<String> aggregateWithOptimized(@RequestParam(defaultValue = "10") int calls) {
        return Flux.range(1, calls)
            .flatMap(i -> optimizedClient.get()
                .uri("/mock/variable")
                .retrieve()
                .bodyToMono(String.class), 50)
            .collectList()
            .map(list -> "Completed " + list.size() + " calls");
    }
}
```

### Step 4.4: Compare Pool Performance

```bash
# Start a second instance to act as external service
SERVER_PORT=8081 ./mvnw spring-boot:run &

# Test default client
wrk -t4 -c100 -d30s "http://localhost:8080/api/aggregate/default?calls=5"

# Test optimized client
wrk -t4 -c100 -d30s "http://localhost:8080/api/aggregate/optimized?calls=5"

# Check connection pool metrics
curl http://localhost:8080/actuator/prometheus | grep netty
```

---

## Part 5: Memory Optimization (20 min)

### Step 5.1: Create Memory-Intensive Endpoints

```java
package com.example.performance.controller;

import com.example.performance.model.Product;
import com.example.performance.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final ProductService productService;

    public MemoryController(ProductService productService) {
        this.productService = productService;
    }

    // BAD: Collects entire result into memory
    @GetMapping("/bad/all")
    public Mono<List<Product>> badGetAll() {
        return productService.findAll()
            .collectList();  // All 1000 products in memory!
    }

    // GOOD: Streams results
    @GetMapping(value = "/good/all", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Product> goodGetAll() {
        return productService.findAll();  // Streamed!
    }

    // BAD: Unbounded buffer
    @GetMapping("/bad/buffer")
    public Flux<List<Product>> badBuffer() {
        return productService.findAll()
            .buffer();  // Unbounded!
    }

    // GOOD: Bounded buffer
    @GetMapping("/good/buffer")
    public Flux<List<Product>> goodBuffer() {
        return productService.findAll()
            .buffer(100);  // Fixed size batches
    }

    // BAD: Generating large data in memory
    @GetMapping("/bad/generate")
    public Mono<List<String>> badGenerate(@RequestParam(defaultValue = "100000") int count) {
        return Flux.range(1, count)
            .map(i -> "Item " + i + " with some extra data to make it larger")
            .collectList();  // All in memory!
    }

    // GOOD: Streaming generated data
    @GetMapping(value = "/good/generate", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> goodGenerate(@RequestParam(defaultValue = "100000") int count) {
        return Flux.range(1, count)
            .map(i -> "Item " + i + " with some extra data to make it larger");
    }
}
```

### Step 5.2: Create Memory Monitoring

```java
package com.example.performance.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MemoryMetrics {

    private final MeterRegistry registry;

    public MemoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void registerMetrics() {
        Runtime runtime = Runtime.getRuntime();

        Gauge.builder("jvm.memory.used.custom", runtime, r ->
                r.totalMemory() - r.freeMemory())
            .description("Used heap memory")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder("jvm.memory.free.custom", runtime, Runtime::freeMemory)
            .description("Free heap memory")
            .baseUnit("bytes")
            .register(registry);
    }
}
```

### Step 5.3: Test Memory Usage

```bash
# Monitor memory while running tests
watch -n 1 'curl -s http://localhost:8080/actuator/prometheus | grep jvm_memory'

# In another terminal:

# Test bad endpoint (memory spikes)
curl "http://localhost:8080/api/memory/bad/generate?count=1000000"

# Test good endpoint (memory stable)
curl "http://localhost:8080/api/memory/good/generate?count=1000000" > /dev/null

# Compare memory usage during each request
```

### Step 5.4: Add Backpressure Handling

```java
package com.example.performance.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class BackpressureService {

    // Simulates a fast producer
    public Flux<Long> fastProducer() {
        return Flux.interval(Duration.ofMillis(1))
            .take(10000);
    }

    // BAD: No backpressure handling
    public Flux<Long> processWithoutBackpressure() {
        return fastProducer()
            .flatMap(this::slowProcess);  // Will overwhelm!
    }

    // GOOD: With backpressure
    public Flux<Long> processWithBackpressure() {
        return fastProducer()
            .onBackpressureBuffer(100,
                dropped -> System.out.println("Dropped: " + dropped))
            .flatMap(this::slowProcess, 10);  // Bounded concurrency
    }

    // GOOD: With backpressure and dropping
    public Flux<Long> processWithDrop() {
        return fastProducer()
            .onBackpressureDrop(dropped ->
                System.out.println("Dropped: " + dropped))
            .flatMap(this::slowProcess, 10);
    }

    private Mono<Long> slowProcess(Long value) {
        return Mono.just(value)
            .delayElement(Duration.ofMillis(10));
    }
}
```

---

## Part 6: Load Testing & Analysis (20 min)

### Step 6.1: Create Load Test Script

Create `load-test.sh`:

```bash
#!/bin/bash

echo "=== Performance Lab Load Tests ==="
echo ""

BASE_URL="http://localhost:8080"
DURATION="30s"
THREADS=4
CONNECTIONS=100

# Function to run test and record results
run_test() {
    local name=$1
    local endpoint=$2

    echo "Testing: $name"
    echo "Endpoint: $endpoint"
    echo "---"

    wrk -t$THREADS -c$CONNECTIONS -d$DURATION "$endpoint" 2>&1

    echo ""
    echo "========================"
    echo ""
}

# Test 1: Simple endpoint
run_test "Simple GET" "$BASE_URL/api/products/1"

# Test 2: List endpoint
run_test "List Products" "$BASE_URL/api/products"

# Test 3: Search endpoint
run_test "Search Products" "$BASE_URL/api/products/search?q=product"

# Test 4: CPU-intensive (bad)
run_test "CPU Intensive (Bad)" "$BASE_URL/api/compute/bad/hash?input=test"

# Test 5: CPU-intensive (good)
run_test "CPU Intensive (Good)" "$BASE_URL/api/compute/good/hash?input=test"

# Test 6: Memory (bad)
run_test "Memory (Bad)" "$BASE_URL/api/memory/bad/all"

# Test 7: Memory (good - streaming)
run_test "Memory (Good)" "$BASE_URL/api/memory/good/all"

echo "=== Load Tests Complete ==="
```

### Step 6.2: Create Gatling Simulation (Optional)

Create `src/test/scala/PerformanceSimulation.scala`:

```scala
package performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PerformanceSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .shareConnections

  val getProduct = scenario("Get Product")
    .exec(http("Get Single Product")
      .get("/api/products/1")
      .check(status.is(200)))

  val searchProducts = scenario("Search Products")
    .exec(http("Search")
      .get("/api/products/search")
      .queryParam("q", "product")
      .check(status.is(200)))

  val computeHash = scenario("Compute Hash")
    .exec(http("Good Hash")
      .get("/api/compute/good/hash")
      .queryParam("input", "test")
      .check(status.is(200)))

  setUp(
    getProduct.inject(
      constantConcurrentUsers(50).during(60.seconds)
    ),
    searchProducts.inject(
      constantConcurrentUsers(30).during(60.seconds)
    ),
    computeHash.inject(
      constantConcurrentUsers(20).during(60.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(99).lt(500),
     global.successfulRequests.percent.gt(99)
   )
}
```

### Step 6.3: Analyze Results

```bash
# Run load test
chmod +x load-test.sh
./load-test.sh | tee results.txt

# Check Prometheus metrics during test
curl http://localhost:8080/actuator/prometheus > metrics_snapshot.txt

# Key metrics to analyze:
# - http_server_requests_seconds_count
# - http_server_requests_seconds_sum
# - jvm_memory_used_bytes
# - reactor_scheduler_tasks_pending
```

---

## Part 7: Performance Dashboard (15 min)

### Step 7.1: Docker Compose for Monitoring Stack

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v2.48.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.2.2
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

### Step 7.2: Grafana Dashboard

Import this dashboard JSON into Grafana:

```json
{
  "dashboard": {
    "title": "Reactive Performance Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "timeseries",
        "targets": [{"expr": "rate(http_server_requests_seconds_count[1m])"}],
        "gridPos": {"x": 0, "y": 0, "w": 12, "h": 8}
      },
      {
        "title": "Response Time Percentiles",
        "type": "timeseries",
        "targets": [
          {"expr": "histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[1m]))", "legendFormat": "p50"},
          {"expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))", "legendFormat": "p95"},
          {"expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))", "legendFormat": "p99"}
        ],
        "gridPos": {"x": 12, "y": 0, "w": 12, "h": 8}
      },
      {
        "title": "JVM Memory",
        "type": "timeseries",
        "targets": [
          {"expr": "jvm_memory_used_bytes{area=\"heap\"}", "legendFormat": "Heap Used"},
          {"expr": "jvm_memory_max_bytes{area=\"heap\"}", "legendFormat": "Heap Max"}
        ],
        "gridPos": {"x": 0, "y": 8, "w": 12, "h": 8}
      },
      {
        "title": "Scheduler Tasks",
        "type": "timeseries",
        "targets": [
          {"expr": "reactor_scheduler_tasks_pending", "legendFormat": "Pending"},
          {"expr": "reactor_scheduler_tasks_active", "legendFormat": "Active"}
        ],
        "gridPos": {"x": 12, "y": 8, "w": 12, "h": 8}
      }
    ]
  }
}
```

### Step 7.3: Start Monitoring

```bash
# Start monitoring stack
docker-compose up -d

# Wait for services
sleep 10

# Access:
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000 (admin/admin)

# Run load test while watching dashboard
./load-test.sh
```

---

## Performance Checklist

After completing this lab, verify:

- [ ] BlockHound is detecting blocking calls
- [ ] All blocking operations are offloaded to appropriate schedulers
- [ ] Connection pools are properly sized
- [ ] Memory usage is stable under load
- [ ] Backpressure is handled appropriately
- [ ] Performance metrics are being collected
- [ ] Dashboard shows all key metrics

## Key Takeaways

1. **Blocking calls are catastrophic** in reactive systems - always detect and fix them
2. **Scheduler selection matters** - use parallel for CPU, boundedElastic for blocking I/O
3. **Connection pools need tuning** based on your workload
4. **Memory must be bounded** - use streaming and fixed-size buffers
5. **Measure everything** - you can't optimize what you can't measure
6. **Load test early and often** - performance issues show up under load

## Troubleshooting

### Common Issues

1. **High latency under load**
   - Check for blocking calls with BlockHound
   - Verify connection pool sizing
   - Look for unbounded operations

2. **Memory growing**
   - Look for collectList() on large streams
   - Check for unbounded buffers
   - Verify DataBuffer cleanup

3. **Low throughput**
   - Check event loop thread utilization
   - Look for CPU-intensive operations on event loop
   - Verify scheduler configuration

---

## Summary

In this lab, you:

1. Established baseline performance metrics
2. Detected and fixed blocking calls using BlockHound
3. Optimized event loop utilization with proper schedulers
4. Tuned connection pools for high concurrency
5. Implemented memory-efficient streaming
6. Conducted load testing and analyzed results
7. Created a performance monitoring dashboard

Your reactive application is now optimized for production performance!
