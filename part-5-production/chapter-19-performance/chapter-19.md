# Chapter 19: Performance Tuning

> "Premature optimization is the root of all evil. But mature optimization is the root of all good performance."
> — A practical interpretation of Donald Knuth

## Introduction

Your reactive application works correctly. Tests pass. Users are happy—until they're not. One day, latency spikes. Throughput drops. Memory usage creeps up. The event loop stutters. You need to optimize, but where do you start?

Reactive programming promises better resource utilization, but it doesn't guarantee performance. A poorly optimized reactive application can be slower than a well-tuned blocking one. Understanding what to measure, how to profile, and where to optimize is crucial for production-ready reactive systems.

This chapter covers the art and science of performance tuning reactive applications—from understanding what "performance" means in a reactive context to systematic optimization techniques.

## 19.1 Understanding Reactive Performance

### What Does "Performance" Mean?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PERFORMANCE DIMENSIONS                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│   │   THROUGHPUT    │  │    LATENCY      │  │  SCALABILITY    │        │
│   ├─────────────────┤  ├─────────────────┤  ├─────────────────┤        │
│   │                 │  │                 │  │                 │        │
│   │  How many       │  │  How long each  │  │  How does       │        │
│   │  requests/sec?  │  │  request takes  │  │  performance    │        │
│   │                 │  │                 │  │  change under   │        │
│   │  (capacity)     │  │  (responsiveness│  │  load?          │        │
│   │                 │  │                 │  │                 │        │
│   └─────────────────┘  └─────────────────┘  └─────────────────┘        │
│                                                                          │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│   │   EFFICIENCY    │  │   CONSISTENCY   │  │   RESILIENCE    │        │
│   ├─────────────────┤  ├─────────────────┤  ├─────────────────┤        │
│   │                 │  │                 │  │                 │        │
│   │  Resources used │  │  Variance in    │  │  Performance    │        │
│   │  per request    │  │  response times │  │  under failure  │        │
│   │                 │  │                 │  │  conditions     │        │
│   │  (CPU, memory,  │  │  (p99 vs p50)   │  │                 │        │
│   │   connections)  │  │                 │  │                 │        │
│   └─────────────────┘  └─────────────────┘  └─────────────────┘        │
│                                                                          │
│   Key Insight: Optimizing one dimension often affects others            │
│   Example: Higher throughput may increase tail latencies                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Why Reactive Performance Is Different

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING VS REACTIVE PERFORMANCE                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Blocking (Thread-Per-Request):                                        │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Performance ∝ Thread Count × (1 / Avg Request Time)        │      │
│   │                                                               │      │
│   │   Optimization strategies:                                    │      │
│   │   • More threads                                              │      │
│   │   • Faster database queries                                   │      │
│   │   • Connection pooling                                        │      │
│   │   • Caching                                                   │      │
│   │                                                               │      │
│   │   Bottleneck: Usually thread count (memory limits)           │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive (Event Loop):                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Performance ∝ Event Loop Capacity × Pipeline Efficiency    │      │
│   │                                                               │      │
│   │   Optimization strategies:                                    │      │
│   │   • Never block the event loop                               │      │
│   │   • Efficient operator usage                                  │      │
│   │   • Appropriate scheduler selection                          │      │
│   │   • Backpressure handling                                    │      │
│   │   • Memory-efficient data handling                           │      │
│   │                                                               │      │
│   │   Bottleneck: Usually CPU (event loop utilization)           │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Critical Difference: One blocking call in reactive affects           │
│   ALL requests, not just one.                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### The Performance Model

```java
// Understanding where time goes in reactive applications
public class PerformanceBreakdown {

    /*
     * Time spent in a reactive request:
     *
     * 1. Request parsing and routing (microseconds)
     * 2. Reactive pipeline setup (microseconds)
     * 3. Actual async I/O (milliseconds)
     *    - Network latency
     *    - Database queries
     *    - External API calls
     * 4. CPU work in operators (microseconds to milliseconds)
     *    - Transformations (map, flatMap)
     *    - Serialization/deserialization
     *    - Business logic
     * 5. Response serialization (microseconds)
     *
     * In a healthy reactive app:
     * - Most time is in async I/O waiting (not consuming resources)
     * - CPU work is minimal per request
     * - Pipeline overhead is negligible
     */
}
```

## 19.2 Profiling Reactive Applications

### The Challenge of Async Profiling

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PROFILING CHALLENGES                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional Profiling:                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Thread-1: methodA() → methodB() → methodC() (waiting)       │      │
│   │                                                               │      │
│   │  Profiler sees: Clear call stack, time spent in each method │      │
│   │  Easy to identify: "methodC takes 500ms"                     │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive Profiling:                                                    │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  EventLoop-1:                                                 │      │
│   │    t=0: Setup pipeline for Request A                         │      │
│   │    t=1: Setup pipeline for Request B                         │      │
│   │    t=2: Callback from Request A (network done)               │      │
│   │    t=3: Setup pipeline for Request C                         │      │
│   │    t=4: Callback from Request B (network done)               │      │
│   │    ...                                                        │      │
│   │                                                               │      │
│   │  Profiler sees: Interleaved callbacks, no clear call stacks │      │
│   │  Hard to trace: Which operation belongs to which request?   │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Solution: Use reactive-aware profiling tools and techniques          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Setting Up Reactor Metrics

```java
@Configuration
public class ReactorProfileConfig {

    @PostConstruct
    public void setupReactorMetrics() {
        // Enable Reactor built-in metrics
        Schedulers.enableMetrics();

        // Enable operator fusion metrics
        Hooks.onOperatorDebug(); // DEV ONLY - significant overhead!
    }
}
```

### Application Configuration for Profiling

```yaml
# application.yml for performance profiling
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        reactor: true
      percentiles:
        http.server.requests: 0.5, 0.75, 0.95, 0.99
    tags:
      application: ${spring.application.name}

# Netty-specific metrics
reactor:
  netty:
    pool:
      metrics: true

logging:
  level:
    reactor.netty: DEBUG  # Enable for connection pool debugging
```

### Custom Performance Metrics

```java
@Component
public class PerformanceMetrics {

    private final Timer pipelineSetupTime;
    private final Timer operatorExecutionTime;
    private final Counter blockedCallsDetected;
    private final DistributionSummary memoryPerRequest;

    public PerformanceMetrics(MeterRegistry registry) {
        this.pipelineSetupTime = Timer.builder("reactor.pipeline.setup")
            .description("Time to set up reactive pipeline")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.operatorExecutionTime = Timer.builder("reactor.operator.execution")
            .description("Time spent in operators")
            .tag("type", "transform")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.blockedCallsDetected = Counter.builder("reactor.blocking.detected")
            .description("Number of blocking calls detected")
            .register(registry);

        this.memoryPerRequest = DistributionSummary.builder("reactor.memory.per.request")
            .description("Memory allocated per request")
            .baseUnit("bytes")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public <T> Mono<T> instrumentPipeline(Mono<T> mono, String name) {
        long startTime = System.nanoTime();

        return mono
            .doOnSubscribe(s -> {
                pipelineSetupTime.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            })
            .name(name)
            .metrics();
    }
}
```

### Detecting Blocking Calls

```java
@Configuration
@Profile("dev")
public class BlockingDetectionConfig {

    private static final Logger log = LoggerFactory.getLogger(BlockingDetectionConfig.class);

    @PostConstruct
    public void enableBlockingDetection() {
        // BlockHound integration for detecting blocking calls
        /*
         * Add to pom.xml:
         * <dependency>
         *     <groupId>io.projectreactor.tools</groupId>
         *     <artifactId>blockhound</artifactId>
         *     <version>1.0.8.RELEASE</version>
         * </dependency>
         */

        // Custom detection for specific patterns
        Hooks.onOperatorDebug();

        Schedulers.onScheduleHook("blocking-detector", runnable -> {
            return () -> {
                long start = System.nanoTime();
                runnable.run();
                long duration = System.nanoTime() - start;

                // Flag if operation took too long (potential blocking)
                if (duration > TimeUnit.MILLISECONDS.toNanos(10)) {
                    log.warn("Potentially blocking operation detected: {}ms",
                        TimeUnit.NANOSECONDS.toMillis(duration));
                    // Log stack trace in debug mode
                    if (log.isDebugEnabled()) {
                        log.debug("Stack trace:", new Exception("Blocking detection"));
                    }
                }
            };
        });
    }
}
```

### Using BlockHound

```java
// In test or development
public class BlockingDetectionTest {

    @BeforeAll
    static void setup() {
        BlockHound.builder()
            .allowBlockingCallsInside("com.zaxxer.hikari.pool.HikariPool", "getConnection")
            .allowBlockingCallsInside("java.util.UUID", "randomUUID")
            .blockingMethodCallback(blockingMethod -> {
                throw new BlockingOperationError(blockingMethod);
            })
            .install();
    }

    @Test
    void detectBlockingInReactiveChain() {
        Mono<String> problematicMono = Mono.fromCallable(() -> {
            Thread.sleep(100);  // This will be detected!
            return "result";
        }).subscribeOn(Schedulers.parallel());

        StepVerifier.create(problematicMono)
            .expectError(BlockingOperationError.class)
            .verify();
    }
}
```

## 19.3 Event Loop Optimization

### Understanding the Event Loop

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    EVENT LOOP ARCHITECTURE                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Netty Event Loop (Default in WebFlux):                                │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Boss Group (1 thread)                                          │   │
│   │  • Accepts new connections                                      │   │
│   │  • Hands off to worker threads                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                          │                                              │
│                          ▼                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Worker Group (N threads, default = CPU cores)                  │   │
│   │                                                                  │   │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │   │
│   │  │ Worker-1 │ │ Worker-2 │ │ Worker-3 │ │ Worker-4 │           │   │
│   │  │          │ │          │ │          │ │          │           │   │
│   │  │ Conn A   │ │ Conn E   │ │ Conn I   │ │ Conn M   │           │   │
│   │  │ Conn B   │ │ Conn F   │ │ Conn J   │ │ Conn N   │           │   │
│   │  │ Conn C   │ │ Conn G   │ │ Conn K   │ │ Conn O   │           │   │
│   │  │ Conn D   │ │ Conn H   │ │ Conn L   │ │ Conn P   │           │   │
│   │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │   │
│   │                                                                  │   │
│   │  Each worker handles multiple connections via non-blocking I/O  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Rule: NEVER block a worker thread!                                   │
│   Blocking Worker-1 affects all connections it handles (A, B, C, D)    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Configuring Event Loop Threads

```java
@Configuration
public class NettyConfig {

    @Bean
    public NettyReactiveWebServerFactory nettyFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();

        factory.addServerCustomizers(httpServer -> httpServer
            .runOn(LoopResources.create(
                "http",
                1,                          // Selector threads (acceptors)
                Runtime.getRuntime().availableProcessors(),  // Worker threads
                true                        // Daemon threads
            ))
        );

        return factory;
    }
}
```

```yaml
# Alternative: application.yml configuration
server:
  netty:
    # Number of threads for the event loop
    # Default: number of available processors
    connection-timeout: 5000
```

### Offloading CPU-Intensive Work

```java
@Service
public class OptimizedProcessingService {

    // Bounded elastic scheduler for blocking/CPU-intensive operations
    private final Scheduler cpuScheduler = Schedulers.newBoundedElastic(
        10,                      // Thread cap
        10000,                   // Queued task cap
        "cpu-intensive",
        60,                      // TTL in seconds
        true                     // Daemon threads
    );

    public Mono<Result> processWithHeavyComputation(Data data) {
        return Mono.just(data)
            // Light operations stay on event loop
            .map(this::validateInput)
            .flatMap(this::fetchFromDatabase)
            // Heavy computation offloaded to CPU scheduler
            .publishOn(cpuScheduler)
            .map(this::performHeavyComputation)
            // Switch back for I/O operations
            .publishOn(Schedulers.boundedElastic())
            .flatMap(this::saveResult);
    }

    private Result performHeavyComputation(Data data) {
        // CPU-intensive work: encryption, compression, complex calculations
        // This would block the event loop if not offloaded
        return heavyWork(data);
    }
}
```

### Scheduler Selection Guide

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SCHEDULER SELECTION                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Scheduler               │ Use For                  │ Thread Count     │
│   ────────────────────────┼──────────────────────────┼─────────────────  │
│   Schedulers.immediate()  │ Testing, inline exec     │ Current thread   │
│   Schedulers.single()     │ Sequential operations    │ 1 thread         │
│   Schedulers.parallel()   │ CPU-bound work           │ N = CPU cores    │
│   Schedulers.boundedElastic()│ Blocking/legacy calls│ Dynamic (capped) │
│   Schedulers.newParallel()│ Custom CPU pools         │ Configurable     │
│   Schedulers.newBoundedElastic()│ Custom blocking   │ Configurable     │
│                                                                          │
│   ──────────────────────────────────────────────────────────────────── │
│                                                                          │
│   Anti-Patterns:                                                         │
│   ✗ Using boundedElastic for CPU work (wastes threads)                 │
│   ✗ Using parallel for blocking calls (starves event loop)             │
│   ✗ Creating unbounded thread pools (resource exhaustion)              │
│                                                                          │
│   Best Practices:                                                        │
│   ✓ Keep event loop threads for I/O only                               │
│   ✓ Use parallel() for pure CPU work                                   │
│   ✓ Use boundedElastic() for legacy blocking code                      │
│   ✓ Cap all custom schedulers                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Avoiding Event Loop Stalls

```java
@Service
public class EventLoopSafeService {

    private static final Logger log = LoggerFactory.getLogger(EventLoopSafeService.class);

    // BAD: Blocking the event loop
    public Mono<String> badExample() {
        return Mono.fromCallable(() -> {
            // This blocks the event loop!
            Thread.sleep(1000);
            return blockingHttpClient.get("http://api.example.com");
        });
    }

    // GOOD: Using non-blocking client
    public Mono<String> goodExample(WebClient webClient) {
        return webClient.get()
            .uri("http://api.example.com")
            .retrieve()
            .bodyToMono(String.class);
    }

    // ACCEPTABLE: Offloading blocking call
    public Mono<String> acceptableExample() {
        return Mono.fromCallable(() -> {
            return legacyBlockingService.call();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // BAD: Synchronous logging with I/O
    public Mono<String> badLogging(String data) {
        return Mono.just(data)
            .doOnNext(d -> {
                // File I/O on event loop!
                writeToFile(d);
            });
    }

    // GOOD: Async logging
    public Mono<String> goodLogging(String data) {
        return Mono.just(data)
            .doOnNext(d -> {
                // Use async logger (Logback AsyncAppender)
                log.info("Processing: {}", d);
            });
    }
}
```

## 19.4 Memory Optimization

### Understanding Memory in Reactive Applications

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    MEMORY PROFILE OF REACTIVE APPS                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional (Thread-Per-Request):                                     │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Memory = (Stack per thread × Thread count) + Heap objects   │      │
│   │                                                               │      │
│   │  100 threads × 1MB stack = 100MB just for stacks             │      │
│   │  Each request: Stack frame + request objects                 │      │
│   │                                                               │      │
│   │  Predictable: Memory scales with thread count               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive:                                                              │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Memory = (Small stack × Few threads) + Operator objects     │      │
│   │                                                               │      │
│   │  10 threads × 1MB stack = 10MB for stacks                   │      │
│   │  Each request: Subscriber chain + buffer objects             │      │
│   │                                                               │      │
│   │  Concern: Unbounded buffers can cause memory issues         │      │
│   │  Concern: Long pipelines create many intermediate objects   │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Key Memory Consumers in Reactive:                                     │
│   1. Buffers (especially unbounded)                                     │
│   2. Operator fusion overhead                                           │
│   3. Context objects (propagated through chain)                        │
│   4. DataBuffer allocations for I/O                                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Buffer Management

```java
@Service
public class MemoryEfficientService {

    // BAD: Unbounded buffer can cause OOM
    public Flux<Data> badBuffering() {
        return fastProducer()
            .buffer();  // Unbounded!
    }

    // GOOD: Bounded buffer with backpressure
    public Flux<Data> goodBuffering() {
        return fastProducer()
            .onBackpressureBuffer(1000,
                dropped -> log.warn("Dropped: {}", dropped),
                BufferOverflowStrategy.DROP_OLDEST)
            .buffer(100);  // Fixed size batches
    }

    // BETTER: Use windowing for memory efficiency
    public Flux<List<Data>> windowedProcessing() {
        return fastProducer()
            .windowTimeout(100, Duration.ofSeconds(1))
            .flatMap(window -> window.collectList());
    }

    // BAD: Collecting entire flux into memory
    public Mono<List<Data>> badCollecting() {
        return hugeFlux()
            .collectList();  // All in memory!
    }

    // GOOD: Stream processing
    public Flux<Result> goodStreaming() {
        return hugeFlux()
            .flatMap(this::processItem, 10);  // Process in batches
    }
}
```

### DataBuffer Management

```java
@Service
public class DataBufferService {

    private final DataBufferFactory bufferFactory;

    public DataBufferService(DataBufferFactory bufferFactory) {
        this.bufferFactory = bufferFactory;
    }

    // BAD: Not releasing buffers
    public Mono<byte[]> badBufferHandling(Flux<DataBuffer> buffers) {
        return buffers
            .map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                // Buffer not released! Memory leak!
                return bytes;
            })
            .reduce(this::concatenate);
    }

    // GOOD: Proper buffer release
    public Mono<byte[]> goodBufferHandling(Flux<DataBuffer> buffers) {
        return DataBufferUtils.join(buffers)
            .map(buffer -> {
                try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            });
    }

    // BEST: Using utility methods
    public Flux<String> streamLargeFile(Resource resource) {
        return DataBufferUtils.read(resource, bufferFactory, 8192)
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            });
    }
}
```

### Object Pooling

```java
@Configuration
public class ObjectPoolConfig {

    @Bean
    public ObjectPool<ExpensiveObject> expensiveObjectPool() {
        GenericObjectPoolConfig<ExpensiveObject> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(100);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        config.setTestOnBorrow(true);

        return new GenericObjectPool<>(new BasePooledObjectFactory<>() {
            @Override
            public ExpensiveObject create() {
                return new ExpensiveObject();
            }

            @Override
            public PooledObject<ExpensiveObject> wrap(ExpensiveObject obj) {
                return new DefaultPooledObject<>(obj);
            }

            @Override
            public void passivateObject(PooledObject<ExpensiveObject> p) {
                p.getObject().reset();
            }
        }, config);
    }
}

@Service
public class PooledService {

    private final ObjectPool<ExpensiveObject> pool;

    public Mono<Result> processWithPooledObject(Data data) {
        return Mono.usingWhen(
            Mono.fromCallable(() -> pool.borrowObject()),
            obj -> processAsync(obj, data),
            obj -> Mono.fromRunnable(() -> pool.returnObject(obj)),
            (obj, err) -> Mono.fromRunnable(() -> pool.invalidateObject(obj)),
            obj -> Mono.fromRunnable(() -> pool.returnObject(obj))
        );
    }
}
```

### GC Optimization

```java
// JVM flags for reactive applications
/*
 * Recommended JVM settings:
 *
 * # Use G1GC for balanced latency/throughput
 * -XX:+UseG1GC
 * -XX:MaxGCPauseMillis=100
 *
 * # String deduplication (saves memory for many similar strings)
 * -XX:+UseStringDeduplication
 *
 * # Heap sizing (example for 4GB available)
 * -Xms2g -Xmx2g
 * -XX:+AlwaysPreTouch  # Commit all memory at startup
 *
 * # Netty-specific: Use unpooled allocator to avoid native memory leaks
 * -Dio.netty.allocator.type=unpooled
 * # Or configure pooled allocator properly
 * -Dio.netty.allocator.maxOrder=11
 *
 * # Reactor-specific: Disable operator fusion debugging in production
 * -Dreactor.netty.ioWorkerCount=16
 */
```

## 19.5 Network Optimization

### Connection Pool Configuration

```java
@Configuration
public class WebClientPoolConfig {

    @Bean
    public WebClient webClient() {
        // Create a connection provider with specific pool settings
        ConnectionProvider provider = ConnectionProvider.builder("custom")
            .maxConnections(500)                    // Max connections per destination
            .maxIdleTime(Duration.ofSeconds(30))    // Close idle connections
            .maxLifeTime(Duration.ofMinutes(5))     // Max connection lifetime
            .pendingAcquireTimeout(Duration.ofSeconds(60))  // Wait time for connection
            .evictInBackground(Duration.ofSeconds(120))     // Background cleanup
            .metrics(true)                          // Enable metrics
            .build();

        // Create HTTP client with the connection provider
        HttpClient httpClient = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .responseTimeout(Duration.ofSeconds(30))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS))
            );

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
```

### HTTP/2 Configuration

```java
@Configuration
public class Http2Config {

    @Bean
    public WebClient http2WebClient() {
        HttpClient httpClient = HttpClient.create()
            .protocol(HttpProtocol.H2)  // Enable HTTP/2
            .secure(spec -> spec
                .sslContext(SslContextBuilder.forClient().build()));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}

// Server-side HTTP/2
@Configuration
public class ServerHttp2Config {

    @Bean
    public NettyReactiveWebServerFactory nettyFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers(httpServer ->
            httpServer.protocol(HttpProtocol.H2C)  // HTTP/2 cleartext
        );
        return factory;
    }
}
```

### Response Streaming

```java
@RestController
public class StreamingController {

    // BAD: Loading all data before sending
    @GetMapping("/bad/large-data")
    public Mono<List<LargeObject>> badLargeData() {
        return repository.findAll().collectList();  // All in memory!
    }

    // GOOD: Streaming response
    @GetMapping(value = "/good/large-data", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<LargeObject> goodLargeData() {
        return repository.findAll();  // Streamed!
    }

    // BETTER: With backpressure hints
    @GetMapping(value = "/better/large-data", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<LargeObject> betterLargeData() {
        return repository.findAll()
            .onBackpressureBuffer(100)
            .delayElements(Duration.ofMillis(10));  // Rate limiting
    }
}
```

### Request Compression

```yaml
# application.yml
server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/plain
    min-response-size: 1024  # Only compress responses > 1KB
```

```java
@Configuration
public class CompressionConfig {

    @Bean
    public WebClient compressedWebClient() {
        HttpClient httpClient = HttpClient.create()
            .compress(true);  // Enable request/response compression

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .build();
    }
}
```

## 19.6 Database Optimization

### R2DBC Connection Pool Tuning

```java
@Configuration
public class R2dbcPoolConfig {

    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "pool")
            .option(PROTOCOL, "postgresql")
            .option(HOST, "localhost")
            .option(PORT, 5432)
            .option(USER, "user")
            .option(PASSWORD, "password")
            .option(DATABASE, "mydb")
            // Pool configuration
            .option(Option.valueOf("initialSize"), 10)
            .option(Option.valueOf("maxSize"), 50)
            .option(Option.valueOf("maxIdleTime"), Duration.ofMinutes(30))
            .option(Option.valueOf("acquireRetry"), 3)
            .option(Option.valueOf("backgroundEvictionInterval"), Duration.ofMinutes(5))
            .option(Option.valueOf("maxLifeTime"), Duration.ofMinutes(60))
            .option(Option.valueOf("validationQuery"), "SELECT 1")
            .build());
    }

    // Pool metrics
    @Bean
    public ConnectionPoolMetrics poolMetrics(ConnectionPool pool, MeterRegistry registry) {
        pool.getMetrics().ifPresent(metrics -> {
            Gauge.builder("r2dbc.pool.acquired", metrics, PoolMetrics::acquiredSize)
                .register(registry);
            Gauge.builder("r2dbc.pool.allocated", metrics, PoolMetrics::allocatedSize)
                .register(registry);
            Gauge.builder("r2dbc.pool.pending", metrics, PoolMetrics::pendingAcquireSize)
                .register(registry);
            Gauge.builder("r2dbc.pool.max", metrics, PoolMetrics::getMaxAllocatedSize)
                .register(registry);
        });
        return new ConnectionPoolMetrics(pool);
    }
}
```

### Reactive Query Optimization

```java
@Repository
public class OptimizedUserRepository {

    private final DatabaseClient databaseClient;

    // BAD: N+1 query problem
    public Flux<UserWithOrders> badLoadUsersWithOrders() {
        return databaseClient.sql("SELECT * FROM users")
            .map(row -> new User(row.get("id", Long.class), row.get("name", String.class)))
            .all()
            .flatMap(user -> loadOrders(user.id())
                .collectList()
                .map(orders -> new UserWithOrders(user, orders)));
    }

    // GOOD: Single query with join
    public Flux<UserWithOrders> goodLoadUsersWithOrders() {
        return databaseClient.sql("""
                SELECT u.id, u.name, o.id as order_id, o.amount
                FROM users u
                LEFT JOIN orders o ON u.id = o.user_id
                """)
            .fetch()
            .all()
            .bufferUntilChanged(row -> row.get("id"))
            .map(this::mapToUserWithOrders);
    }

    // GOOD: Batch fetching
    public Flux<User> findUsersByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }

        return databaseClient.sql("SELECT * FROM users WHERE id = ANY($1)")
            .bind("$1", ids.toArray(new Long[0]))
            .map(row -> new User(row.get("id", Long.class), row.get("name", String.class)))
            .all();
    }

    // Pagination with keyset (more efficient than OFFSET)
    public Flux<User> findUsersAfter(Long lastId, int limit) {
        return databaseClient.sql("""
                SELECT * FROM users
                WHERE id > $1
                ORDER BY id
                LIMIT $2
                """)
            .bind("$1", lastId)
            .bind("$2", limit)
            .map(row -> new User(row.get("id", Long.class), row.get("name", String.class)))
            .all();
    }
}
```

### Caching with Reactive Redis

```java
@Service
public class CachedUserService {

    private final ReactiveRedisTemplate<String, User> redisTemplate;
    private final UserRepository repository;
    private final Duration cacheTtl = Duration.ofMinutes(30);

    public Mono<User> findById(Long id) {
        String key = "user:" + id;

        return redisTemplate.opsForValue().get(key)
            .switchIfEmpty(
                repository.findById(id)
                    .flatMap(user ->
                        redisTemplate.opsForValue()
                            .set(key, user, cacheTtl)
                            .thenReturn(user)
                    )
            );
    }

    // Cache-aside with invalidation
    public Mono<User> updateUser(User user) {
        String key = "user:" + user.id();

        return repository.save(user)
            .flatMap(saved ->
                redisTemplate.delete(key)
                    .thenReturn(saved)
            );
    }

    // Batch cache loading
    public Flux<User> findByIds(List<Long> ids) {
        List<String> keys = ids.stream()
            .map(id -> "user:" + id)
            .toList();

        return redisTemplate.opsForValue().multiGet(keys)
            .flatMapMany(cachedUsers -> {
                List<Long> missingIds = new ArrayList<>();
                List<User> foundUsers = new ArrayList<>();

                for (int i = 0; i < ids.size(); i++) {
                    if (cachedUsers.get(i) != null) {
                        foundUsers.add(cachedUsers.get(i));
                    } else {
                        missingIds.add(ids.get(i));
                    }
                }

                if (missingIds.isEmpty()) {
                    return Flux.fromIterable(foundUsers);
                }

                return repository.findByIdIn(missingIds)
                    .doOnNext(user ->
                        redisTemplate.opsForValue()
                            .set("user:" + user.id(), user, cacheTtl)
                            .subscribe()
                    )
                    .concatWith(Flux.fromIterable(foundUsers));
            });
    }
}
```

## 19.7 Common Performance Pitfalls

### Pitfall 1: Hidden Blocking Calls

```java
@Service
public class BlockingPitfalls {

    // PITFALL: ObjectMapper can be blocking
    private final ObjectMapper objectMapper = new ObjectMapper();

    // BAD: JSON serialization blocks
    public Mono<String> badSerialization(Object data) {
        return Mono.fromCallable(() ->
            objectMapper.writeValueAsString(data)  // Blocks for large objects!
        );
    }

    // GOOD: Offload serialization
    public Mono<String> goodSerialization(Object data) {
        return Mono.fromCallable(() ->
                objectMapper.writeValueAsString(data))
            .subscribeOn(Schedulers.boundedElastic());
    }

    // PITFALL: JDBC in reactive chain
    public Mono<User> badDatabaseCall(JdbcTemplate jdbc, Long id) {
        return Mono.fromCallable(() ->
            jdbc.queryForObject("SELECT * FROM users WHERE id = ?",
                new BeanPropertyRowMapper<>(User.class), id)
        );  // Blocks event loop!
    }

    // GOOD: Use R2DBC or offload
    public Mono<User> goodDatabaseCall(JdbcTemplate jdbc, Long id) {
        return Mono.fromCallable(() ->
                jdbc.queryForObject("SELECT * FROM users WHERE id = ?",
                    new BeanPropertyRowMapper<>(User.class), id))
            .subscribeOn(Schedulers.boundedElastic());
    }

    // PITFALL: Synchronous file I/O
    public Mono<String> badFileRead(Path path) {
        return Mono.fromCallable(() ->
            Files.readString(path)  // Blocks!
        );
    }

    // GOOD: Use async file reading
    public Mono<String> goodFileRead(Path path, DataBufferFactory factory) {
        return DataBufferUtils.read(path, factory, 8192)
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .reduce(String::concat);
    }
}
```

### Pitfall 2: Unbounded Operations

```java
@Service
public class UnboundedPitfalls {

    // PITFALL: Unbounded flatMap concurrency
    public Flux<Result> badConcurrency(Flux<Data> data) {
        return data.flatMap(this::processAsync);  // Unbounded!
    }

    // GOOD: Bounded concurrency
    public Flux<Result> goodConcurrency(Flux<Data> data) {
        return data.flatMap(this::processAsync, 10);  // Max 10 concurrent
    }

    // PITFALL: Unbounded buffer
    public Flux<Data> badBuffer(Flux<Data> fast) {
        return fast.buffer();  // Grows without limit!
    }

    // GOOD: Bounded buffer with overflow strategy
    public Flux<Data> goodBuffer(Flux<Data> fast) {
        return fast.onBackpressureBuffer(1000,
            BufferOverflowStrategy.DROP_OLDEST);
    }

    // PITFALL: Collecting entire stream
    public Mono<List<Data>> badCollect(Flux<Data> huge) {
        return huge.collectList();  // Entire stream in memory!
    }

    // GOOD: Streaming with windowing
    public Flux<List<Data>> goodWindow(Flux<Data> huge) {
        return huge
            .window(1000)
            .flatMap(Flux::collectList);
    }
}
```

### Pitfall 3: Hot Publisher Memory Leaks

```java
@Service
public class HotPublisherPitfalls {

    // PITFALL: Hot publisher without cleanup
    private final Sinks.Many<Event> eventSink = Sinks.many().multicast().directBestEffort();

    // Subscribers accumulate without limit!
    public Flux<Event> badSubscription() {
        return eventSink.asFlux();  // No cleanup on disconnect
    }

    // GOOD: With replay and auto-cleanup
    private final Sinks.Many<Event> boundedSink = Sinks.many()
        .replay()
        .limit(100);  // Only keep last 100 events

    public Flux<Event> goodSubscription() {
        return boundedSink.asFlux()
            .doOnCancel(() -> log.debug("Subscriber disconnected"));
    }

    // PITFALL: cache() without limit
    public Mono<ExpensiveData> badCache() {
        return computeExpensiveData()
            .cache();  // Cached forever!
    }

    // GOOD: cache() with TTL
    public Mono<ExpensiveData> goodCache() {
        return computeExpensiveData()
            .cache(Duration.ofMinutes(5));  // Auto-expires
    }
}
```

### Pitfall 4: Context Propagation Overhead

```java
@Service
public class ContextPitfalls {

    // PITFALL: Large context objects
    public Mono<Result> badContext(LargeObject largeContext) {
        return process()
            .contextWrite(ctx -> ctx.put("large", largeContext));  // Copied through chain!
    }

    // GOOD: Store references or IDs
    public Mono<Result> goodContext(String contextId) {
        return process()
            .contextWrite(ctx -> ctx.put("contextId", contextId));
    }

    // PITFALL: Excessive context writes
    public Flux<Result> badMultipleContext(Flux<Data> data) {
        return data.flatMap(d ->
            process(d)
                .contextWrite(ctx -> ctx.put("data1", d.field1()))
                .contextWrite(ctx -> ctx.put("data2", d.field2()))
                .contextWrite(ctx -> ctx.put("data3", d.field3()))
        );
    }

    // GOOD: Single context write with all data
    public Flux<Result> goodSingleContext(Flux<Data> data) {
        return data.flatMap(d ->
            process(d)
                .contextWrite(ctx -> ctx.putAll(
                    Context.of("data1", d.field1(),
                        "data2", d.field2(),
                        "data3", d.field3())))
        );
    }
}
```

## 19.8 Performance Testing

### Load Testing with Gatling

```scala
// GatlingSimulation.scala
class ReactiveLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .shareConnections  // Important for reactive servers

  val scn = scenario("Reactive Load Test")
    .exec(http("Get Users")
      .get("/api/users")
      .check(status.is(200)))
    .pause(100.milliseconds)
    .exec(http("Create Order")
      .post("/api/orders")
      .body(StringBody("""{"userId": "123", "amount": 99.99}"""))
      .check(status.is(201)))

  setUp(
    scn.inject(
      constantConcurrentUsers(100).during(60.seconds),
      rampConcurrentUsers(100).to(500).during(120.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(99).lt(1000),
     global.successfulRequests.percent.gt(99)
   )
}
```

### Benchmark Tests

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ReactivePerformanceBenchmark {

    private WebTestClient webTestClient;

    @Setup
    public void setup() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:8080")
            .build();
    }

    @Benchmark
    public void benchmarkSimpleGet() {
        webTestClient.get()
            .uri("/api/health")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockLast();
    }

    @Benchmark
    public void benchmarkComplexOperation() {
        webTestClient.post()
            .uri("/api/orders")
            .bodyValue(new CreateOrderRequest("user-1", BigDecimal.valueOf(99.99)))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Order.class)
            .getResponseBody()
            .blockLast();
    }
}
```

## 19.9 Performance Monitoring Dashboard

### Key Metrics to Track

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE PERFORMANCE DASHBOARD                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Event Loop Health:                                                     │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  • reactor_scheduler_tasks_pending < 1000                        │  │
│   │  • reactor_scheduler_tasks_active ≈ CPU cores                   │  │
│   │  • Blocking calls detected = 0                                  │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│   HTTP Metrics:                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  • http_server_requests_seconds{quantile="0.99"} < 1.0          │  │
│   │  • http_server_requests_total (rate)                            │  │
│   │  • http_server_active_connections                               │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│   Connection Pools:                                                      │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  • r2dbc_pool_acquired / r2dbc_pool_max < 0.8                   │  │
│   │  • reactor_netty_connection_provider_pending_count < 100        │  │
│   │  • reactor_netty_connection_provider_active_connections         │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│   Memory:                                                                │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  • jvm_memory_used_bytes{area="heap"}                           │  │
│   │  • jvm_gc_pause_seconds{quantile="0.99"}                        │  │
│   │  • jvm_buffer_memory_used_bytes{id="direct"}                    │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Grafana Dashboard JSON

```json
{
  "dashboard": {
    "title": "Reactive Application Performance",
    "panels": [
      {
        "title": "Request Latency Percentiles",
        "type": "timeseries",
        "targets": [
          {"expr": "histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))"},
          {"expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))"},
          {"expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))"}
        ]
      },
      {
        "title": "Throughput",
        "type": "timeseries",
        "targets": [
          {"expr": "rate(http_server_requests_seconds_count[1m])"}
        ]
      },
      {
        "title": "Connection Pool Utilization",
        "type": "gauge",
        "targets": [
          {"expr": "r2dbc_pool_acquired / r2dbc_pool_max * 100"}
        ]
      },
      {
        "title": "Event Loop Tasks",
        "type": "timeseries",
        "targets": [
          {"expr": "reactor_scheduler_tasks_pending"},
          {"expr": "reactor_scheduler_tasks_active"}
        ]
      }
    ]
  }
}
```

## Summary

Performance tuning reactive applications requires understanding the unique characteristics of the reactive model:

| Aspect | Traditional | Reactive |
|--------|-------------|----------|
| **Bottleneck** | Thread count | Event loop capacity |
| **Memory concern** | Stack per thread | Buffers and objects |
| **Profiling** | Stack traces | Async profilers, metrics |
| **Blocking impact** | One request | All requests on that thread |

**Key Takeaways:**

1. **Measure first**: Use metrics and profiling before optimizing
2. **Never block the event loop**: Offload blocking operations
3. **Bound everything**: Buffers, concurrency, connection pools
4. **Use appropriate schedulers**: Parallel for CPU, boundedElastic for blocking
5. **Stream, don't collect**: Process data incrementally
6. **Monitor continuously**: Set up dashboards and alerts

**Performance Optimization Checklist:**

- [ ] BlockHound enabled in development
- [ ] Connection pools properly sized
- [ ] Schedulers configured for workload type
- [ ] Buffers bounded with overflow strategies
- [ ] Metrics exposed and monitored
- [ ] Load testing performed
- [ ] GC tuned for reactive workload
- [ ] No blocking calls on event loop threads

With proper performance tuning, reactive applications can achieve remarkable throughput with minimal resource usage—handling thousands of concurrent connections with just a handful of threads.
