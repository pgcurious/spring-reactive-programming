# Appendix D: Troubleshooting Guide

This appendix provides solutions to common problems encountered when developing reactive applications with Spring WebFlux and Project Reactor.

## Quick Diagnosis Flowchart

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE TROUBLESHOOTING FLOWCHART                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Problem Observed                                                       │
│         │                                                                │
│         ▼                                                                │
│   ┌─────────────────┐                                                   │
│   │ Nothing happens │──▶ Check: Is the stream subscribed?               │
│   │ (no output)     │    Fix: Add .subscribe() or return to framework   │
│   └─────────────────┘                                                   │
│         │                                                                │
│         ▼                                                                │
│   ┌─────────────────┐                                                   │
│   │ Errors silently │──▶ Check: Is error handling in place?             │
│   │ swallowed       │    Fix: Add .doOnError() or error handler         │
│   └─────────────────┘                                                   │
│         │                                                                │
│         ▼                                                                │
│   ┌─────────────────┐                                                   │
│   │ Wrong thread /  │──▶ Check: Is correct Scheduler used?              │
│   │ blocking        │    Fix: Use .subscribeOn() / .publishOn()         │
│   └─────────────────┘                                                   │
│         │                                                                │
│         ▼                                                                │
│   ┌─────────────────┐                                                   │
│   │ Memory issues / │──▶ Check: Unbounded buffers? Missing backpressure?│
│   │ OOM errors      │    Fix: Add .onBackpressure*() operators          │
│   └─────────────────┘                                                   │
│         │                                                                │
│         ▼                                                                │
│   ┌─────────────────┐                                                   │
│   │ Slow performance│──▶ Check: Blocking calls? Event loop blocked?     │
│   │                 │    Fix: Use BlockHound, move to boundedElastic    │
│   └─────────────────┘                                                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Problem 1: Nothing Happens (Stream Not Executing)

### Symptoms
- Code appears to run but produces no output
- Database queries don't execute
- HTTP calls aren't made
- Logs show no activity

### Cause
Reactive streams are lazy - they don't execute until subscribed.

### Diagnosis

```java
// This does NOTHING - no subscription
Mono.fromCallable(() -> {
    System.out.println("This never prints");
    return "result";
});

// This also does NOTHING
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .map(user -> {
            System.out.println("Never executed");
            return user;
        });
    // Caller must subscribe!
}
```

### Solutions

```java
// Solution 1: Subscribe explicitly
mono.subscribe(
    result -> log.info("Result: {}", result),
    error -> log.error("Error", error),
    () -> log.info("Complete")
);

// Solution 2: Return to framework (WebFlux subscribes for you)
@GetMapping("/user/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id);  // Framework subscribes
}

// Solution 3: Block (only for testing or main methods)
User user = userService.findById(id).block();

// Solution 4: Use StepVerifier for testing
StepVerifier.create(userService.findById(id))
    .expectNextMatches(user -> user.getName().equals("John"))
    .verifyComplete();
```

### Debug Technique

```java
// Add logging to trace execution
Mono.fromCallable(() -> expensiveOperation())
    .doOnSubscribe(s -> log.info("Subscribed!"))
    .doOnNext(v -> log.info("Got value: {}", v))
    .doOnError(e -> log.error("Error occurred", e))
    .doOnTerminate(() -> log.info("Terminated"))
    .doFinally(signal -> log.info("Finally: {}", signal));
```

## Problem 2: Errors Silently Disappear

### Symptoms
- Exceptions thrown but not visible
- Processing stops unexpectedly
- No error logs despite failures

### Cause
Unhandled errors in reactive streams terminate the stream silently if no error handler is attached.

### Diagnosis

```java
// Error disappears with fire-and-forget subscribe
flux.subscribe();  // No error handler!

// Error in nested publisher
Flux.just(1, 2, 3)
    .flatMap(i -> {
        if (i == 2) throw new RuntimeException("Boom!");
        return Mono.just(i);
    })
    .subscribe(System.out::println);  // Stream dies at 2
```

### Solutions

```java
// Solution 1: Always provide error handler in subscribe
flux.subscribe(
    value -> process(value),
    error -> log.error("Stream error", error),  // Error handler
    () -> log.info("Complete")
);

// Solution 2: Use onErrorResume for recovery
flux.flatMap(this::riskyOperation)
    .onErrorResume(ex -> {
        log.error("Operation failed", ex);
        return Mono.empty();  // Or fallback value
    });

// Solution 3: Use onErrorContinue to skip bad elements
Flux.just(1, 2, 3, 4, 5)
    .map(i -> {
        if (i == 3) throw new RuntimeException("Bad element");
        return i * 2;
    })
    .onErrorContinue((error, value) -> {
        log.warn("Skipping {} due to {}", value, error.getMessage());
    })
    .subscribe(System.out::println);  // Prints 2, 4, 8, 10

// Solution 4: Global error handler
@Bean
public WebExceptionHandler globalErrorHandler() {
    return (exchange, ex) -> {
        log.error("Unhandled error", ex);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return exchange.getResponse().setComplete();
    };
}
```

### Debug Technique

```java
// Enable Reactor debug mode for better stack traces
Hooks.onOperatorDebug();  // Development only - performance impact

// Or use checkpoint for specific locations
flux
    .checkpoint("after-database-query")
    .map(this::transform)
    .checkpoint("after-transform")
    .subscribe();
```

## Problem 3: Blocking Calls Causing Issues

### Symptoms
- Entire application becomes unresponsive
- Requests timeout randomly
- Poor throughput despite low CPU usage
- BlockHound exceptions in logs

### Cause
Blocking operations on event loop threads exhaust the small thread pool.

### Diagnosis

```java
// WRONG: Blocking on event loop
@GetMapping("/data")
public Mono<Data> getData() {
    // This blocks the event loop thread!
    Data result = blockingService.fetchData();
    return Mono.just(result);
}

// WRONG: Blocking inside reactive chain
Flux.range(1, 100)
    .map(i -> {
        Thread.sleep(100);  // Blocking!
        return i;
    });
```

### Solutions

```java
// Solution 1: Use boundedElastic for blocking code
@GetMapping("/data")
public Mono<Data> getData() {
    return Mono.fromCallable(() -> blockingService.fetchData())
        .subscribeOn(Schedulers.boundedElastic());
}

// Solution 2: Wrap blocking code properly
public Mono<String> readFile(String path) {
    return Mono.fromCallable(() -> {
            return Files.readString(Path.of(path));
        })
        .subscribeOn(Schedulers.boundedElastic());
}

// Solution 3: Use reactive alternatives
// Instead of JDBC, use R2DBC
// Instead of RestTemplate, use WebClient
// Instead of blocking Redis, use reactive Redis

// Solution 4: Use BlockHound to detect blocking calls
public static void main(String[] args) {
    BlockHound.install();
    SpringApplication.run(Application.class, args);
}
```

### Debug Technique

```java
// Custom BlockHound configuration
BlockHound.builder()
    .allowBlockingCallsInside("com.example.legacy.LegacyService", "slowMethod")
    .blockingMethodCallback(method -> {
        log.error("Blocking call detected: {}.{}",
            method.getClassName(), method.getName());
        new Exception("Blocking call stack trace").printStackTrace();
    })
    .install();
```

## Problem 4: Memory Leaks and OOM

### Symptoms
- OutOfMemoryError after running for a while
- Heap keeps growing
- GC pauses increase over time
- Application becomes sluggish

### Cause
Unbounded buffers, missing cancellation, or retained references.

### Diagnosis

```java
// Problem: Unbounded buffer
Flux.interval(Duration.ofMillis(1))
    .map(this::processData)  // Slow consumer, fast producer
    .subscribe();  // Buffer grows unbounded!

// Problem: Missing cancellation
Flux<Data> infiniteStream = createInfiniteStream();
Disposable subscription = infiniteStream.subscribe();
// Never cancelled - runs forever
```

### Solutions

```java
// Solution 1: Apply backpressure
Flux.interval(Duration.ofMillis(1))
    .onBackpressureDrop(dropped -> log.warn("Dropped: {}", dropped))
    .subscribe();

// Solution 2: Use bounded buffers
Flux.interval(Duration.ofMillis(1))
    .onBackpressureBuffer(1000,
        dropped -> log.warn("Buffer full, dropped: {}", dropped),
        BufferOverflowStrategy.DROP_OLDEST)
    .subscribe();

// Solution 3: Use limitRate
Flux.interval(Duration.ofMillis(1))
    .limitRate(100)  // Request only 100 at a time
    .subscribe();

// Solution 4: Always cancel subscriptions
Disposable subscription = flux.subscribe();
// Later...
subscription.dispose();

// Solution 5: Use takeUntil/takeWhile
flux.takeUntil(signal -> shouldStop())
    .subscribe();
```

### Debug Technique

```java
// Monitor subscription count
Metrics.gauge("reactor.subscriptions",
    subscriptionCount, AtomicInteger::get);

// Use Hooks to track subscriptions
Hooks.onEachOperator(operator -> {
    return Operators.lift((scannable, subscriber) -> {
        log.debug("New subscriber to: {}", scannable.name());
        return subscriber;
    });
});
```

## Problem 5: Thread Context Lost (MDC, Security)

### Symptoms
- MDC logging context disappears
- Security context not available
- Request-scoped data missing
- Tracing IDs lost

### Cause
Reactive streams switch threads, and ThreadLocal data doesn't propagate.

### Diagnosis

```java
// MDC set here...
MDC.put("requestId", "123");

return Mono.fromCallable(() -> {
    // MDC is null here - different thread!
    log.info("Processing");  // requestId missing from logs
    return result;
}).subscribeOn(Schedulers.boundedElastic());
```

### Solutions

```java
// Solution 1: Use Reactor Context
Mono.deferContextual(ctx -> {
    String requestId = ctx.get("requestId");
    return processWithRequestId(requestId);
}).contextWrite(ctx -> ctx.put("requestId", "123"));

// Solution 2: Use context propagation library
// Add dependency: io.micrometer:context-propagation
Hooks.enableAutomaticContextPropagation();

// Solution 3: Manual MDC propagation
public <T> Mono<T> withMdc(Mono<T> mono) {
    Map<String, String> mdc = MDC.getCopyOfContextMap();
    return mono
        .doOnSubscribe(s -> {
            if (mdc != null) MDC.setContextMap(mdc);
        })
        .doFinally(signal -> MDC.clear());
}

// Solution 4: WebFilter for request context
@Component
public class RequestContextFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        return chain.filter(exchange)
            .contextWrite(ctx -> ctx.put("requestId", requestId));
    }
}

// Solution 5: Reactor's built-in MDC support (Reactor 3.5+)
Mono.just("data")
    .doOnNext(d -> log.info("Processing"))  // MDC available
    .contextWrite(ctx -> ctx.put("requestId", "123"))
    .contextCapture();  // Captures current ThreadLocal state
```

### Debug Technique

```java
// Log context at each step
flux
    .doOnNext(v -> log.info("Context: {}",
        Mono.deferContextual(ctx -> Mono.just(ctx.toString())).block()))
    .contextWrite(ctx -> ctx.put("step", "initial"));
```

## Problem 6: flatMap vs concatMap Confusion

### Symptoms
- Elements arrive out of order
- Concurrent execution when sequential expected
- Rate limiting not working
- Database transaction issues

### Cause
Misunderstanding of `flatMap` (concurrent) vs `concatMap` (sequential).

### Diagnosis

```java
// flatMap executes concurrently - order not preserved
Flux.just(1, 2, 3)
    .flatMap(i -> Mono.delay(Duration.ofMillis(100 - i * 30))
        .map(d -> i))
    .subscribe(System.out::println);
// Might print: 3, 2, 1 (or any order)

// Problematic for ordered operations
Flux.just(user1, user2, user3)
    .flatMap(user -> saveUser(user))  // Order not guaranteed!
```

### Solutions

```java
// Solution 1: Use concatMap for sequential execution
Flux.just(1, 2, 3)
    .concatMap(i -> Mono.delay(Duration.ofMillis(100))
        .map(d -> i))
    .subscribe(System.out::println);
// Always prints: 1, 2, 3

// Solution 2: Use flatMapSequential for concurrent exec, ordered results
Flux.just(1, 2, 3)
    .flatMapSequential(i -> processAsync(i))
    .subscribe(System.out::println);
// Executes concurrently but results arrive in order

// Solution 3: Control concurrency with flatMap
Flux.range(1, 100)
    .flatMap(
        i -> processAsync(i),
        16  // Max 16 concurrent operations
    );

// Solution 4: Use concatMap with prefetch
Flux.range(1, 100)
    .concatMap(
        i -> processAsync(i),
        1  // Prefetch 1 (strict sequential)
    );
```

### Quick Reference

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    flatMap vs concatMap vs flatMapSequential            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   flatMap:                                                               │
│   - Concurrent execution                                                 │
│   - Results arrive in completion order                                   │
│   - Best throughput                                                      │
│   - Use when order doesn't matter                                        │
│                                                                          │
│   concatMap:                                                             │
│   - Sequential execution                                                 │
│   - Results arrive in source order                                       │
│   - One at a time                                                        │
│   - Use when order matters and operations must be sequential             │
│                                                                          │
│   flatMapSequential:                                                     │
│   - Concurrent execution                                                 │
│   - Results arrive in source order (queued if needed)                    │
│   - Good throughput with ordering                                        │
│   - Use when order matters but concurrent execution is OK                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Problem 7: Hot vs Cold Publisher Confusion

### Symptoms
- Multiple subscribers get different data
- Data replayed unexpectedly
- Events missed by late subscribers
- Unexpected repeated side effects

### Cause
Misunderstanding cold (replay on subscribe) vs hot (shared, live) publishers.

### Diagnosis

```java
// Cold: Each subscriber gets all elements
Flux<Long> cold = Flux.interval(Duration.ofSeconds(1)).take(5);
cold.subscribe(i -> System.out.println("Sub1: " + i));
Thread.sleep(2000);
cold.subscribe(i -> System.out.println("Sub2: " + i));
// Both get 0, 1, 2, 3, 4

// Hot: Late subscribers miss elements
Sinks.Many<Integer> sink = Sinks.many().multicast().onBackpressureBuffer();
Flux<Integer> hot = sink.asFlux();
hot.subscribe(i -> System.out.println("Sub1: " + i));
sink.tryEmitNext(1);
sink.tryEmitNext(2);
hot.subscribe(i -> System.out.println("Sub2: " + i));  // Misses 1, 2
sink.tryEmitNext(3);
// Sub1 gets: 1, 2, 3
// Sub2 gets: 3
```

### Solutions

```java
// Solution 1: Share a cold publisher (make it hot)
Flux<Long> shared = Flux.interval(Duration.ofSeconds(1))
    .share();  // Converts cold to hot

// Solution 2: Cache/replay for late subscribers
Flux<Long> cached = Flux.interval(Duration.ofSeconds(1))
    .take(5)
    .cache();  // Replays all elements to new subscribers

// Solution 3: Replay limited history
Flux<Long> replayed = Flux.interval(Duration.ofSeconds(1))
    .replay(3)  // Replay last 3 elements
    .autoConnect();

// Solution 4: Use appropriate Sink type
// For multiple subscribers:
Sinks.Many<String> multicast = Sinks.many().multicast().onBackpressureBuffer();

// For single subscriber:
Sinks.Many<String> unicast = Sinks.many().unicast().onBackpressureBuffer();

// For replay to all:
Sinks.Many<String> replay = Sinks.many().replay().all();
```

## Problem 8: Timeout and Retry Issues

### Symptoms
- Timeouts not working as expected
- Infinite retry loops
- Retry happening too fast
- Wrong exception triggering retry

### Cause
Incorrect timeout placement or retry configuration.

### Diagnosis

```java
// Problem: Timeout on wrong level
Mono.fromCallable(() -> slowOperation())
    .timeout(Duration.ofSeconds(5))  // Only times out the Mono creation, not the operation
    .subscribeOn(Schedulers.boundedElastic());

// Problem: Retrying non-retryable errors
webClient.get()
    .retrieve()
    .bodyToMono(String.class)
    .retry(3);  // Retries everything including 4xx errors!
```

### Solutions

```java
// Solution 1: Correct timeout placement
Mono.fromCallable(() -> slowOperation())
    .subscribeOn(Schedulers.boundedElastic())
    .timeout(Duration.ofSeconds(5));  // Times out the entire operation

// Solution 2: Selective retry with filter
webClient.get()
    .retrieve()
    .bodyToMono(String.class)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .filter(ex -> ex instanceof WebClientResponseException.ServiceUnavailable)
        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));

// Solution 3: Exponential backoff
mono.retryWhen(Retry.backoff(5, Duration.ofMillis(100))
    .maxBackoff(Duration.ofSeconds(10))
    .jitter(0.5));

// Solution 4: Timeout with fallback
mono.timeout(Duration.ofSeconds(5))
    .onErrorResume(TimeoutException.class, ex -> {
        log.warn("Operation timed out, using fallback");
        return fallbackMono;
    });

// Solution 5: Combined timeout and retry
mono
    .timeout(Duration.ofSeconds(2))
    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
        .filter(ex -> ex instanceof TimeoutException));
```

## Problem 9: Testing Difficulties

### Symptoms
- Tests pass inconsistently
- Tests timeout
- Virtual time not working
- Cannot test error scenarios

### Cause
Not using proper reactive testing utilities.

### Diagnosis

```java
// Problem: Using block() makes tests slow and unreliable
@Test
void testSlowOperation() {
    String result = slowMono.block();  // Blocks test thread
    assertEquals("expected", result);
}

// Problem: Time-based tests are slow
@Test
void testDelay() {
    Mono.delay(Duration.ofHours(1))
        .block();  // Test takes 1 hour!
}
```

### Solutions

```java
// Solution 1: Use StepVerifier
@Test
void testMono() {
    Mono<String> mono = service.getData();

    StepVerifier.create(mono)
        .expectNext("expected")
        .verifyComplete();
}

// Solution 2: Test errors
@Test
void testError() {
    Mono<String> mono = service.failingOperation();

    StepVerifier.create(mono)
        .expectErrorMatches(ex ->
            ex instanceof IllegalStateException &&
            ex.getMessage().contains("failed"))
        .verify();
}

// Solution 3: Virtual time for time-based operations
@Test
void testDelay() {
    StepVerifier.withVirtualTime(() ->
            Mono.delay(Duration.ofHours(1)).map(l -> "done"))
        .expectSubscription()
        .expectNoEvent(Duration.ofMinutes(30))
        .thenAwait(Duration.ofMinutes(30))
        .expectNext("done")
        .verifyComplete();
}

// Solution 4: Test backpressure
@Test
void testBackpressure() {
    Flux<Integer> flux = Flux.range(1, 100);

    StepVerifier.create(flux, 10)  // Request only 10
        .expectNextCount(10)
        .thenRequest(5)
        .expectNextCount(5)
        .thenCancel()
        .verify();
}

// Solution 5: Use TestPublisher for controlled emission
@Test
void testWithTestPublisher() {
    TestPublisher<String> publisher = TestPublisher.create();

    Flux<String> flux = publisher.flux()
        .map(String::toUpperCase);

    StepVerifier.create(flux)
        .then(() -> publisher.emit("a", "b", "c"))
        .expectNext("A", "B", "C")
        .verifyComplete();
}
```

## Problem 10: WebClient Issues

### Symptoms
- Connection refused errors
- SSL/TLS errors
- Response not being read
- Connection pool exhaustion

### Cause
Incorrect WebClient configuration or usage.

### Diagnosis

```java
// Problem: Response body not consumed
webClient.get()
    .uri("/api/data")
    .retrieve()
    .toBodilessEntity();  // Body leaked if not consumed!

// Problem: Not handling errors properly
webClient.get()
    .uri("/api/data")
    .retrieve()  // Throws on 4xx/5xx
    .bodyToMono(Data.class);
```

### Solutions

```java
// Solution 1: Properly configure WebClient
@Bean
public WebClient webClient() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .responseTimeout(Duration.ofSeconds(5))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(5))
            .addHandlerLast(new WriteTimeoutHandler(5)));

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
}

// Solution 2: Handle HTTP errors
webClient.get()
    .uri("/api/data")
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, response ->
        response.bodyToMono(ErrorResponse.class)
            .flatMap(body -> Mono.error(new ClientException(body))))
    .onStatus(HttpStatusCode::is5xxServerError, response ->
        Mono.error(new ServerException("Server error")))
    .bodyToMono(Data.class);

// Solution 3: Exchange for full control
webClient.get()
    .uri("/api/data")
    .exchangeToMono(response -> {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(Data.class);
        } else {
            return response.createError();
        }
    });

// Solution 4: Connection pool configuration
ConnectionProvider provider = ConnectionProvider.builder("custom")
    .maxConnections(100)
    .maxIdleTime(Duration.ofSeconds(20))
    .maxLifeTime(Duration.ofSeconds(60))
    .pendingAcquireTimeout(Duration.ofSeconds(60))
    .evictInBackground(Duration.ofSeconds(120))
    .build();

HttpClient httpClient = HttpClient.create(provider);
```

## Problem 11: R2DBC/Database Issues

### Symptoms
- Connection pool exhausted
- Transactions not working
- Queries returning empty
- Deadlocks

### Cause
Incorrect connection management or transaction handling.

### Diagnosis

```java
// Problem: Connections not released
repository.findAll()
    .map(entity -> {
        // Long processing - connection held
        return slowTransform(entity);
    });

// Problem: Transaction not applied
public Mono<Void> transfer(String from, String to, BigDecimal amount) {
    return accountRepository.debit(from, amount)
        .then(accountRepository.credit(to, amount));
    // Not transactional!
}
```

### Solutions

```java
// Solution 1: Configure connection pool properly
@Bean
public ConnectionFactory connectionFactory() {
    return ConnectionFactories.get(ConnectionFactoryOptions.builder()
        .option(DRIVER, "postgresql")
        .option(HOST, "localhost")
        .option(DATABASE, "mydb")
        .option(USER, "user")
        .option(PASSWORD, "pass")
        .option(Option.valueOf("initialSize"), 10)
        .option(Option.valueOf("maxSize"), 50)
        .option(Option.valueOf("maxIdleTime"), Duration.ofMinutes(30))
        .build());
}

// Solution 2: Use @Transactional properly
@Service
public class TransferService {

    @Transactional
    public Mono<Void> transfer(String from, String to, BigDecimal amount) {
        return accountRepository.debit(from, amount)
            .then(accountRepository.credit(to, amount));
    }
}

// Solution 3: Programmatic transactions
@Service
public class TransferService {
    private final TransactionalOperator transactionalOperator;

    public Mono<Void> transfer(String from, String to, BigDecimal amount) {
        return accountRepository.debit(from, amount)
            .then(accountRepository.credit(to, amount))
            .as(transactionalOperator::transactional);
    }
}

// Solution 4: Release connections quickly
repository.findAll()
    .collectList()  // Collect all, release connection
    .flatMapIterable(list -> list)
    .map(entity -> slowTransform(entity));
```

## Problem 12: Debugging Asynchronous Stack Traces

### Symptoms
- Stack traces don't show original call site
- Cannot trace where an error originated
- Debugging is difficult

### Cause
Async execution loses stack trace context.

### Solutions

```java
// Solution 1: Enable debug mode (development only)
Hooks.onOperatorDebug();

// Solution 2: Use checkpoints
flux
    .checkpoint("before-transform")
    .map(this::transform)
    .checkpoint("after-transform")
    .flatMap(this::process)
    .checkpoint("after-process");

// Solution 3: Use descriptive checkpoint
flux.checkpoint("Loading user data from database", true);

// Solution 4: Wrap with meaningful errors
flux.map(this::transform)
    .onErrorMap(ex -> new ProcessingException(
        "Failed to transform user data", ex));

// Solution 5: Production-safe alternative to debug mode
// Use reactor-tools for assembly tracing
ReactorDebugAgent.init();  // Bytecode instrumentation, lower overhead
```

## Common Error Messages Reference

| Error Message | Likely Cause | Solution |
|---------------|--------------|----------|
| `Timeout on blocking read` | Blocking call on event loop | Move to boundedElastic |
| `Only one subscriber allowed` | UnicastProcessor reused | Use replay() or multicast() |
| `Context does not contain key` | Context not propagated | Use contextWrite() upstream |
| `Pool exhausted` | Connection/thread pool full | Increase pool size or fix leaks |
| `Operator has been terminated` | Using terminated Sink | Create new Sink instance |
| `Scheduler was disposed` | Using stopped Scheduler | Check Scheduler lifecycle |
| `Request overflow` | Requested Long.MAX_VALUE twice | Check request logic |
| `Spec violation: null` | Null value in stream | Filter nulls or use Optional |

## Best Practices Summary

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE BEST PRACTICES                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. Always handle errors                                                │
│      .onErrorResume() / .onErrorReturn() / .doOnError()                 │
│                                                                          │
│   2. Never block on event loop threads                                   │
│      Use boundedElastic for blocking code                                │
│                                                                          │
│   3. Use appropriate operators                                           │
│      flatMap for concurrent, concatMap for sequential                   │
│                                                                          │
│   4. Apply backpressure                                                  │
│      limitRate(), onBackpressure*()                                     │
│                                                                          │
│   5. Use Context instead of ThreadLocal                                  │
│      contextWrite(), deferContextual()                                  │
│                                                                          │
│   6. Test with StepVerifier                                              │
│      Virtual time for delays                                             │
│                                                                          │
│   7. Use checkpoints for debugging                                       │
│      .checkpoint("meaningful-name")                                      │
│                                                                          │
│   8. Configure timeouts and retries                                      │
│      .timeout(), .retryWhen(Retry.backoff())                            │
│                                                                          │
│   9. Monitor and observe                                                 │
│      Metrics, logging, tracing                                           │
│                                                                          │
│   10. Clean up resources                                                 │
│       dispose(), using(), doFinally()                                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

