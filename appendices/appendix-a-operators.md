# Appendix A: Operator Quick Reference

This appendix provides a categorized reference of the most commonly used Reactor operators with examples and use cases.

## Creation Operators

### Creating Mono

| Operator | Description | Example |
|----------|-------------|---------|
| `Mono.just(T)` | Create from a value | `Mono.just("hello")` |
| `Mono.empty()` | Empty Mono | `Mono.empty()` |
| `Mono.error(Throwable)` | Error signal | `Mono.error(new Exception())` |
| `Mono.fromCallable(Callable)` | From a callable | `Mono.fromCallable(() -> compute())` |
| `Mono.fromFuture(CompletableFuture)` | From CompletableFuture | `Mono.fromFuture(future)` |
| `Mono.fromSupplier(Supplier)` | From a supplier | `Mono.fromSupplier(() -> value)` |
| `Mono.defer(Supplier<Mono>)` | Lazy creation | `Mono.defer(() -> Mono.just(getValue()))` |
| `Mono.delay(Duration)` | Delayed empty | `Mono.delay(Duration.ofSeconds(1))` |
| `Mono.justOrEmpty(T)` | From nullable | `Mono.justOrEmpty(nullableValue)` |
| `Mono.justOrEmpty(Optional)` | From Optional | `Mono.justOrEmpty(optional)` |

### Creating Flux

| Operator | Description | Example |
|----------|-------------|---------|
| `Flux.just(T...)` | From values | `Flux.just(1, 2, 3)` |
| `Flux.fromIterable(Iterable)` | From collection | `Flux.fromIterable(list)` |
| `Flux.fromArray(T[])` | From array | `Flux.fromArray(array)` |
| `Flux.fromStream(Stream)` | From stream | `Flux.fromStream(stream)` |
| `Flux.range(int, int)` | Integer range | `Flux.range(1, 10)` |
| `Flux.interval(Duration)` | Periodic values | `Flux.interval(Duration.ofSeconds(1))` |
| `Flux.empty()` | Empty Flux | `Flux.empty()` |
| `Flux.error(Throwable)` | Error signal | `Flux.error(new Exception())` |
| `Flux.defer(Supplier<Flux>)` | Lazy creation | `Flux.defer(() -> Flux.fromIterable(getList()))` |
| `Flux.generate(...)` | Synchronous generation | See example below |
| `Flux.create(...)` | Async bridge | See example below |

**Flux.generate Example:**
```java
Flux.generate(
    () -> 0,  // Initial state
    (state, sink) -> {
        sink.next(state);
        if (state == 10) sink.complete();
        return state + 1;
    }
);
```

**Flux.create Example:**
```java
Flux.create(sink -> {
    eventSource.onData(data -> sink.next(data));
    eventSource.onError(err -> sink.error(err));
    eventSource.onComplete(() -> sink.complete());
});
```

## Transformation Operators

### Element Transformation

| Operator | Description | Example |
|----------|-------------|---------|
| `map(Function)` | Transform each element | `flux.map(s -> s.toUpperCase())` |
| `flatMap(Function)` | Transform to Publisher, merge | `flux.flatMap(id -> fetchById(id))` |
| `flatMapSequential(Function)` | Transform, preserve order | `flux.flatMapSequential(id -> fetch(id))` |
| `concatMap(Function)` | Transform, sequential | `flux.concatMap(id -> fetch(id))` |
| `switchMap(Function)` | Switch to latest | `flux.switchMap(id -> fetch(id))` |
| `cast(Class)` | Cast elements | `flux.cast(String.class)` |
| `index()` | Add index | `flux.index()` → `Tuple2<Long, T>` |

**flatMap vs concatMap vs flatMapSequential:**
```java
// flatMap: Concurrent, unordered (fastest)
Flux.range(1, 3)
    .flatMap(i -> Mono.just(i).delayElement(Duration.ofMillis(100)))

// concatMap: Sequential (slowest, preserves order)
Flux.range(1, 3)
    .concatMap(i -> Mono.just(i).delayElement(Duration.ofMillis(100)))

// flatMapSequential: Concurrent subscription, ordered results
Flux.range(1, 3)
    .flatMapSequential(i -> Mono.just(i).delayElement(Duration.ofMillis(100)))
```

### Collection Operators

| Operator | Description | Return Type |
|----------|-------------|-------------|
| `collectList()` | Collect to List | `Mono<List<T>>` |
| `collectMap(keyMapper)` | Collect to Map | `Mono<Map<K, T>>` |
| `collectMap(keyMapper, valueMapper)` | Custom Map | `Mono<Map<K, V>>` |
| `collectMultimap(keyMapper)` | Multimap | `Mono<Map<K, Collection<T>>>` |
| `collect(Collector)` | Custom collector | `Mono<R>` |
| `count()` | Count elements | `Mono<Long>` |
| `reduce(BiFunction)` | Reduce to one | `Mono<T>` |
| `reduce(initial, BiFunction)` | Reduce with seed | `Mono<T>` |
| `scan(BiFunction)` | Running reduce | `Flux<T>` |

## Filtering Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `filter(Predicate)` | Keep matching | `flux.filter(x -> x > 0)` |
| `filterWhen(Function)` | Async predicate | `flux.filterWhen(x -> isValid(x))` |
| `distinct()` | Remove duplicates | `flux.distinct()` |
| `distinctUntilChanged()` | Remove consecutive duplicates | `flux.distinctUntilChanged()` |
| `take(n)` | First n elements | `flux.take(5)` |
| `takeLast(n)` | Last n elements | `flux.takeLast(5)` |
| `takeWhile(Predicate)` | Take while true | `flux.takeWhile(x -> x < 10)` |
| `takeUntil(Predicate)` | Take until true | `flux.takeUntil(x -> x == 10)` |
| `skip(n)` | Skip first n | `flux.skip(5)` |
| `skipLast(n)` | Skip last n | `flux.skipLast(5)` |
| `skipWhile(Predicate)` | Skip while true | `flux.skipWhile(x -> x < 5)` |
| `skipUntil(Predicate)` | Skip until true | `flux.skipUntil(x -> x == 5)` |
| `first()` | First element | `flux.next()` (deprecated, use next()) |
| `last()` | Last element | `flux.last()` |
| `elementAt(index)` | Element at index | `flux.elementAt(5)` |
| `single()` | Exactly one element | `flux.single()` |

## Combining Operators

### Merge and Concat

| Operator | Description | Example |
|----------|-------------|---------|
| `Flux.merge(Publishers...)` | Interleave multiple | `Flux.merge(flux1, flux2)` |
| `Flux.concat(Publishers...)` | Sequential | `Flux.concat(flux1, flux2)` |
| `mergeWith(Publisher)` | Merge with another | `flux1.mergeWith(flux2)` |
| `concatWith(Publisher)` | Concat with another | `flux1.concatWith(flux2)` |
| `startWith(T...)` | Prepend values | `flux.startWith(0)` |

### Zip and Combine

| Operator | Description | Example |
|----------|-------------|---------|
| `Flux.zip(Publishers...)` | Combine by position | `Flux.zip(flux1, flux2)` |
| `Flux.zip(Publishers..., combinator)` | Custom combinator | `Flux.zip(f1, f2, (a, b) -> a + b)` |
| `zipWith(Publisher)` | Zip with another | `flux1.zipWith(flux2)` |
| `Flux.combineLatest(...)` | Latest from each | `Flux.combineLatest(f1, f2, combinator)` |
| `withLatestFrom(Publisher)` | Combine with latest | `flux1.withLatestFrom(flux2)` |

**Zip vs CombineLatest:**
```java
// zip: Waits for both to emit, pairs by position
Flux.zip(
    Flux.just("A", "B", "C"),
    Flux.just(1, 2, 3)
) // → (A,1), (B,2), (C,3)

// combineLatest: Emits when either emits, uses latest from other
Flux.combineLatest(
    Flux.interval(Duration.ofMillis(100)),
    Flux.interval(Duration.ofMillis(150)),
    (a, b) -> a + "-" + b
)
```

## Error Handling Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `onErrorReturn(T)` | Return default | `mono.onErrorReturn("default")` |
| `onErrorReturn(Class, T)` | Return for exception type | `mono.onErrorReturn(IOException.class, "io-error")` |
| `onErrorResume(Function)` | Switch to fallback | `mono.onErrorResume(e -> getFallback())` |
| `onErrorResume(Class, Function)` | Switch for type | `mono.onErrorResume(IOException.class, e -> fallback)` |
| `onErrorMap(Function)` | Transform error | `mono.onErrorMap(e -> new CustomException(e))` |
| `onErrorComplete()` | Complete on error | `flux.onErrorComplete()` |
| `doOnError(Consumer)` | Side effect on error | `mono.doOnError(e -> log.error("Error", e))` |
| `retry()` | Retry indefinitely | `mono.retry()` |
| `retry(n)` | Retry n times | `mono.retry(3)` |
| `retryWhen(Retry)` | Custom retry | See example below |

**RetryWhen Example:**
```java
mono.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
    .maxBackoff(Duration.ofSeconds(10))
    .filter(e -> e instanceof RetryableException)
    .onRetryExhaustedThrow((spec, signal) ->
        new MaxRetriesExceededException(signal.failure()))
);
```

## Time-Related Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `delayElements(Duration)` | Delay each element | `flux.delayElements(Duration.ofMillis(100))` |
| `delaySubscription(Duration)` | Delay subscription | `mono.delaySubscription(Duration.ofSeconds(1))` |
| `timeout(Duration)` | Timeout signal | `mono.timeout(Duration.ofSeconds(5))` |
| `timeout(Duration, Mono)` | Timeout with fallback | `mono.timeout(Duration.ofSeconds(5), fallback)` |
| `elapsed()` | Time since subscription | `flux.elapsed()` → `Tuple2<Long, T>` |
| `timestamp()` | Add timestamp | `flux.timestamp()` → `Tuple2<Long, T>` |
| `sample(Duration)` | Sample periodically | `flux.sample(Duration.ofSeconds(1))` |
| `sampleFirst(Duration)` | First in each window | `flux.sampleFirst(Duration.ofSeconds(1))` |
| `throttleFirst(Duration)` | Rate limit | `flux.throttleFirst(Duration.ofSeconds(1))` |

## Buffering and Windowing

| Operator | Description | Return Type |
|----------|-------------|-------------|
| `buffer()` | Buffer all | `Mono<List<T>>` |
| `buffer(n)` | Buffer by count | `Flux<List<T>>` |
| `buffer(Duration)` | Buffer by time | `Flux<List<T>>` |
| `bufferTimeout(n, Duration)` | Buffer by count or time | `Flux<List<T>>` |
| `window(n)` | Window by count | `Flux<Flux<T>>` |
| `window(Duration)` | Window by time | `Flux<Flux<T>>` |
| `windowTimeout(n, Duration)` | Window by count or time | `Flux<Flux<T>>` |
| `groupBy(keyMapper)` | Group by key | `Flux<GroupedFlux<K, T>>` |

**Buffer vs Window:**
```java
// buffer: Collects into List, emits when full
flux.buffer(3) // Flux<List<T>> - each list has 3 elements

// window: Creates sub-Flux, for streaming processing
flux.window(3) // Flux<Flux<T>> - each Flux has 3 elements
    .flatMap(window -> window.reduce(...))
```

## Side Effect Operators

| Operator | Description | When Triggered |
|----------|-------------|----------------|
| `doOnNext(Consumer)` | On each element | Each onNext signal |
| `doOnError(Consumer)` | On error | onError signal |
| `doOnComplete(Runnable)` | On complete | onComplete signal |
| `doOnSubscribe(Consumer)` | On subscription | When subscribed |
| `doOnCancel(Runnable)` | On cancel | When cancelled |
| `doOnTerminate(Runnable)` | On terminate | Error or complete |
| `doAfterTerminate(Runnable)` | After terminate | After error/complete |
| `doOnEach(Consumer<Signal>)` | On any signal | Any signal |
| `doFirst(Runnable)` | Before subscription | Before subscribe |
| `doFinally(Consumer<SignalType>)` | On any termination | Cancel/error/complete |
| `log()` | Log all signals | All signals |
| `log(String)` | Log with category | All signals |

## Backpressure Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `onBackpressureBuffer()` | Buffer unbounded | `flux.onBackpressureBuffer()` |
| `onBackpressureBuffer(n)` | Buffer bounded | `flux.onBackpressureBuffer(100)` |
| `onBackpressureBuffer(n, strategy)` | Buffer with overflow | See example below |
| `onBackpressureDrop()` | Drop if full | `flux.onBackpressureDrop()` |
| `onBackpressureDrop(Consumer)` | Drop with callback | `flux.onBackpressureDrop(v -> log(v))` |
| `onBackpressureLatest()` | Keep only latest | `flux.onBackpressureLatest()` |
| `onBackpressureError()` | Error if full | `flux.onBackpressureError()` |
| `limitRate(n)` | Prefetch limit | `flux.limitRate(100)` |
| `limitRequest(n)` | Request limit | `flux.limitRequest(1000)` |

**Buffer Overflow Strategies:**
```java
flux.onBackpressureBuffer(100,
    dropped -> log.warn("Dropped: {}", dropped),
    BufferOverflowStrategy.DROP_OLDEST)

// Strategies:
// DROP_OLDEST - drop oldest buffered
// DROP_LATEST - drop newest incoming
// ERROR - throw exception
```

## Scheduler Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `subscribeOn(Scheduler)` | Where to subscribe | `mono.subscribeOn(Schedulers.boundedElastic())` |
| `publishOn(Scheduler)` | Where to publish | `flux.publishOn(Schedulers.parallel())` |

**Available Schedulers:**
```java
Schedulers.immediate()      // Current thread
Schedulers.single()         // Single reusable thread
Schedulers.parallel()       // Fixed pool (CPU cores)
Schedulers.boundedElastic() // Bounded elastic pool (for blocking)
Schedulers.fromExecutor(e)  // Custom executor
```

## Context Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `contextWrite(Function)` | Write to context | `mono.contextWrite(ctx -> ctx.put("key", value))` |
| `contextWrite(Context)` | Write context | `mono.contextWrite(Context.of("key", value))` |
| `deferContextual(Function)` | Read context | See example below |
| `transformDeferredContextual(...)` | Transform with context | Advanced use |

**Context Usage:**
```java
// Writing context
mono.contextWrite(ctx -> ctx.put("userId", 123))

// Reading context
Mono.deferContextual(ctx -> {
    Long userId = ctx.get("userId");
    return processForUser(userId);
})
```

## Testing Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `checkpoint()` | Add checkpoint | `mono.checkpoint("after-transform")` |
| `checkpoint(description)` | Named checkpoint | `mono.checkpoint("step-1")` |
| `checkpoint(description, forceStackTrace)` | With stack trace | `mono.checkpoint("step-1", true)` |
| `hide()` | Hide identity | `flux.hide()` |
| `metrics()` | Enable metrics | `flux.name("myFlux").metrics()` |
| `tag(key, value)` | Add tag | `flux.tag("type", "orders")` |
| `name(String)` | Name for metrics | `flux.name("orders")` |

## Quick Decision Guide

**Which operator to use?**

```
Need to transform elements?
├── One-to-one → map()
├── One-to-many (async) → flatMap()
└── One-to-many (sync) → flatMapIterable()

Need to filter?
├── By predicate → filter()
├── First n → take(n)
├── Skip first n → skip(n)
└── Remove duplicates → distinct()

Need to combine?
├── Wait for all → zip()
├── First to emit → merge()
├── Sequential → concat()
└── Latest from each → combineLatest()

Need error handling?
├── Return default → onErrorReturn()
├── Switch to fallback → onErrorResume()
├── Retry → retry() or retryWhen()
└── Transform error → onErrorMap()

Need to control timing?
├── Delay elements → delayElements()
├── Add timeout → timeout()
├── Rate limit → sample() or throttle()
└── Periodic emission → Flux.interval()

Need to batch?
├── Fixed size → buffer(n)
├── Time window → buffer(Duration)
└── Group by key → groupBy()
```

## Performance Tips

1. **Use `flatMap` with concurrency**: `flatMap(fn, 10)` limits concurrent subscriptions
2. **Prefer `concatMap` when order matters**: It's sequential but preserves order
3. **Use `publishOn` judiciously**: Each switch has overhead
4. **Avoid `block()` in reactive chains**: It defeats the purpose
5. **Use `cache()` with TTL**: `mono.cache(Duration.ofMinutes(5))`
6. **Bound your buffers**: Always use sized variants
7. **Use `limitRate()` for backpressure**: Prevents overwhelming downstream
