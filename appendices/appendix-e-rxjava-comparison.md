# Appendix E: Reactor vs RxJava Comparison

This appendix provides a comprehensive comparison between Project Reactor and RxJava, helping developers who know one library understand the other, or choose between them.

## Overview Comparison

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTOR vs RxJAVA OVERVIEW                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Aspect              │ Project Reactor        │ RxJava 3               │
│   ────────────────────┼────────────────────────┼────────────────────────│
│   Maintainer          │ VMware/Pivotal         │ Netflix/ReactiveX      │
│   Spring Integration  │ Native                 │ Via adapters           │
│   Java Version        │ Java 8+                │ Java 8+                │
│   Reactive Streams    │ Native                 │ Native                 │
│   Main Types          │ Mono, Flux             │ Single, Maybe,         │
│                       │                        │ Completable,           │
│                       │                        │ Observable, Flowable   │
│   Backpressure        │ Built-in (Flux)        │ Flowable only          │
│   Context             │ Built-in Context       │ Not built-in           │
│   Null Values         │ Forbidden              │ Forbidden              │
│   Android Support     │ Limited                │ Excellent              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Type Mapping

| Reactor | RxJava 3 | Description |
|---------|----------|-------------|
| `Mono<T>` | `Single<T>` | Exactly one element |
| `Mono<T>` | `Maybe<T>` | Zero or one element |
| `Mono<Void>` | `Completable` | No elements, just completion |
| `Flux<T>` | `Flowable<T>` | 0-N elements with backpressure |
| `Flux<T>` | `Observable<T>` | 0-N elements without backpressure |

### Type Selection Guide

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CHOOSING THE RIGHT TYPE                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Reactor                                                                │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                  │   │
│   │   Need 0 or 1 value?  ────▶  Mono<T>                            │   │
│   │                                                                  │   │
│   │   Need 0 to N values? ────▶  Flux<T>                            │   │
│   │                                                                  │   │
│   │   (All types support backpressure)                              │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   RxJava                                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                  │   │
│   │   Exactly 1 value?    ────▶  Single<T>                          │   │
│   │   0 or 1 value?       ────▶  Maybe<T>                           │   │
│   │   No value (action)?  ────▶  Completable                        │   │
│   │                                                                  │   │
│   │   0-N values, need backpressure? ────▶  Flowable<T>             │   │
│   │   0-N values, UI/events?         ────▶  Observable<T>           │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Operator Comparison

### Creation Operators

| Operation | Reactor | RxJava |
|-----------|---------|--------|
| Single value | `Mono.just(value)` | `Single.just(value)` |
| Multiple values | `Flux.just(a, b, c)` | `Flowable.just(a, b, c)` |
| From iterable | `Flux.fromIterable(list)` | `Flowable.fromIterable(list)` |
| Empty | `Mono.empty()` | `Maybe.empty()` |
| Error | `Mono.error(ex)` | `Single.error(ex)` |
| Defer | `Mono.defer(() -> ...)` | `Single.defer(() -> ...)` |
| From callable | `Mono.fromCallable(...)` | `Single.fromCallable(...)` |
| Range | `Flux.range(1, 10)` | `Flowable.range(1, 10)` |
| Interval | `Flux.interval(duration)` | `Flowable.interval(duration)` |
| From future | `Mono.fromFuture(future)` | `Single.fromFuture(future)` |
| Create | `Flux.create(sink -> ...)` | `Flowable.create(emitter -> ...)` |
| Generate | `Flux.generate(...)` | `Flowable.generate(...)` |

### Code Examples - Creation

```java
// Reactor
Mono<String> mono = Mono.just("hello");
Flux<Integer> flux = Flux.range(1, 10);
Mono<String> deferred = Mono.defer(() -> Mono.just(getValue()));
Mono<User> fromCallable = Mono.fromCallable(() -> userService.findById(id))
    .subscribeOn(Schedulers.boundedElastic());

// RxJava
Single<String> single = Single.just("hello");
Flowable<Integer> flowable = Flowable.range(1, 10);
Single<String> deferred = Single.defer(() -> Single.just(getValue()));
Single<User> fromCallable = Single.fromCallable(() -> userService.findById(id))
    .subscribeOn(Schedulers.io());
```

### Transformation Operators

| Operation | Reactor | RxJava |
|-----------|---------|--------|
| Map | `map()` | `map()` |
| FlatMap | `flatMap()` | `flatMap()` |
| FlatMap sequential | `flatMapSequential()` | `concatMapEager()` |
| ConcatMap | `concatMap()` | `concatMap()` |
| SwitchMap | `switchMap()` | `switchMap()` |
| Filter | `filter()` | `filter()` |
| Take | `take(n)` | `take(n)` |
| Skip | `skip(n)` | `skip(n)` |
| Distinct | `distinct()` | `distinct()` |
| Buffer | `buffer()` | `buffer()` |
| Window | `window()` | `window()` |
| Reduce | `reduce()` | `reduce()` |
| Scan | `scan()` | `scan()` |
| CollectList | `collectList()` | `toList()` |
| CollectMap | `collectMap()` | `toMap()` |

### Code Examples - Transformation

```java
// Reactor
Flux<String> transformed = Flux.just(1, 2, 3)
    .map(i -> "Item " + i)
    .filter(s -> s.length() > 5)
    .flatMap(s -> processAsync(s))
    .take(10);

Mono<List<User>> collected = userFlux.collectList();
Mono<Map<String, User>> mapped = userFlux.collectMap(User::getId);

// RxJava
Flowable<String> transformed = Flowable.just(1, 2, 3)
    .map(i -> "Item " + i)
    .filter(s -> s.length() > 5)
    .flatMap(s -> processAsync(s))
    .take(10);

Single<List<User>> collected = userFlowable.toList();
Single<Map<String, User>> mapped = userFlowable.toMap(User::getId);
```

### Combining Operators

| Operation | Reactor | RxJava |
|-----------|---------|--------|
| Merge | `Flux.merge(f1, f2)` | `Flowable.merge(f1, f2)` |
| Concat | `Flux.concat(f1, f2)` | `Flowable.concat(f1, f2)` |
| Zip | `Mono.zip(m1, m2)` | `Single.zip(s1, s2)` |
| Zip with | `mono.zipWith(other)` | `single.zipWith(other)` |
| Combine latest | `Flux.combineLatest(...)` | `Flowable.combineLatest(...)` |
| First/race | `Flux.first(f1, f2)` | `Flowable.amb(f1, f2)` |
| Then | `mono.then(other)` | `single.flatMap(x -> other)` |
| ThenMany | `mono.thenMany(flux)` | `completable.andThen(flowable)` |

### Code Examples - Combining

```java
// Reactor
Mono<Tuple2<User, Profile>> zipped = Mono.zip(
    userService.findById(userId),
    profileService.findByUserId(userId)
);

Flux<Data> merged = Flux.merge(
    dataSource1.getData(),
    dataSource2.getData()
);

Mono<Result> sequential = step1()
    .then(step2())
    .then(step3());

// RxJava
Single<Pair<User, Profile>> zipped = Single.zip(
    userService.findById(userId),
    profileService.findByUserId(userId),
    Pair::of
);

Flowable<Data> merged = Flowable.merge(
    dataSource1.getData(),
    dataSource2.getData()
);

Single<Result> sequential = step1()
    .flatMap(r -> step2())
    .flatMap(r -> step3());
```

### Error Handling

| Operation | Reactor | RxJava |
|-----------|---------|--------|
| On error return | `onErrorReturn(fallback)` | `onErrorReturnItem(fallback)` |
| On error resume | `onErrorResume(ex -> ...)` | `onErrorResumeNext(ex -> ...)` |
| On error map | `onErrorMap(ex -> new Ex())` | `onErrorResumeNext(ex -> Single.error(new Ex()))` |
| On error continue | `onErrorContinue(...)` | N/A (use flatMap with error handling) |
| Retry | `retry(n)` | `retry(n)` |
| Retry with backoff | `retryWhen(Retry.backoff(...))` | `retryWhen(...)` |
| Do on error | `doOnError(...)` | `doOnError(...)` |

### Code Examples - Error Handling

```java
// Reactor
Mono<User> withFallback = userService.findById(id)
    .onErrorReturn(new User("default"))
    .onErrorResume(NotFoundException.class, ex -> Mono.empty())
    .retry(3)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));

// RxJava
Single<User> withFallback = userService.findById(id)
    .onErrorReturnItem(new User("default"))
    .onErrorResumeNext(ex -> {
        if (ex instanceof NotFoundException) {
            return Maybe.empty().toSingle();
        }
        return Single.error(ex);
    })
    .retry(3)
    .retryWhen(errors -> errors
        .zipWith(Flowable.range(1, 3), (err, i) -> i)
        .flatMap(i -> Flowable.timer(i, TimeUnit.SECONDS)));
```

### Schedulers

| Purpose | Reactor | RxJava |
|---------|---------|--------|
| Compute-bound | `Schedulers.parallel()` | `Schedulers.computation()` |
| I/O/Blocking | `Schedulers.boundedElastic()` | `Schedulers.io()` |
| Single thread | `Schedulers.single()` | `Schedulers.single()` |
| Immediate | `Schedulers.immediate()` | `Schedulers.trampoline()` |
| New thread | `Schedulers.newParallel(name)` | `Schedulers.newThread()` |
| Main/UI | N/A (use fromExecutor) | `AndroidSchedulers.mainThread()` |

### Code Examples - Schedulers

```java
// Reactor
Mono.fromCallable(() -> blockingOperation())
    .subscribeOn(Schedulers.boundedElastic())
    .publishOn(Schedulers.parallel())
    .map(this::cpuIntensiveWork);

// RxJava
Single.fromCallable(() -> blockingOperation())
    .subscribeOn(Schedulers.io())
    .observeOn(Schedulers.computation())
    .map(this::cpuIntensiveWork);
```

### Backpressure

| Strategy | Reactor | RxJava |
|----------|---------|--------|
| Buffer | `onBackpressureBuffer()` | `onBackpressureBuffer()` |
| Drop | `onBackpressureDrop()` | `onBackpressureDrop()` |
| Latest | `onBackpressureLatest()` | `onBackpressureLatest()` |
| Error | `onBackpressureError()` | N/A (default behavior) |
| Limit rate | `limitRate(n)` | `rebatchRequests(n)` |

### Code Examples - Backpressure

```java
// Reactor
Flux.interval(Duration.ofMillis(1))
    .onBackpressureBuffer(1000,
        BufferOverflowStrategy.DROP_OLDEST)
    .limitRate(100)
    .subscribe();

// RxJava
Flowable.interval(1, TimeUnit.MILLISECONDS)
    .onBackpressureBuffer(1000,
        () -> {}, // overflow action
        BackpressureOverflowStrategy.DROP_OLDEST)
    .rebatchRequests(100)
    .subscribe();
```

## Context Propagation

### Reactor Context

```java
// Reactor has built-in Context
Mono.deferContextual(ctx -> {
    String traceId = ctx.get("traceId");
    return processWithTracing(traceId);
})
.contextWrite(ctx -> ctx.put("traceId", generateTraceId()))
.contextWrite(Context.of("userId", currentUserId()));

// Reading context downstream
flux.transformDeferredContextual((original, ctx) -> {
    String traceId = ctx.getOrDefault("traceId", "unknown");
    return original.doOnNext(item ->
        log.info("[{}] Processing: {}", traceId, item));
});
```

### RxJava - No Built-in Context

```java
// RxJava requires manual context passing
public class RequestContext {
    public final String traceId;
    public final String userId;

    public RequestContext(String traceId, String userId) {
        this.traceId = traceId;
        this.userId = userId;
    }
}

// Option 1: Pass context explicitly
Single<Result> process(String data, RequestContext ctx) {
    return Single.fromCallable(() -> {
        log.info("[{}] Processing", ctx.traceId);
        return doProcess(data);
    });
}

// Option 2: Use Tuple
Single<Tuple2<RequestContext, Data>> withContext =
    Single.just(data)
        .map(d -> Tuple2.of(context, d));

// Option 3: ThreadLocal with observeOn precautions
```

## Hot vs Cold Publishers

### Reactor

```java
// Cold (default) - each subscriber gets all elements
Flux<Integer> cold = Flux.range(1, 5);
cold.subscribe(i -> System.out.println("Sub1: " + i));
cold.subscribe(i -> System.out.println("Sub2: " + i));
// Both get 1, 2, 3, 4, 5

// Hot - share source
Flux<Long> hot = Flux.interval(Duration.ofSeconds(1))
    .share();

// Hot with replay
Flux<Long> replay = Flux.interval(Duration.ofSeconds(1))
    .replay(3)
    .autoConnect();

// Using Sinks (modern API)
Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
Flux<String> hotFlux = sink.asFlux();
sink.tryEmitNext("event1");
```

### RxJava

```java
// Cold (default)
Flowable<Integer> cold = Flowable.range(1, 5);

// Hot - share source
Flowable<Long> hot = Flowable.interval(1, TimeUnit.SECONDS)
    .share();

// Hot with replay
Flowable<Long> replay = Flowable.interval(1, TimeUnit.SECONDS)
    .replay(3)
    .autoConnect();

// Using Subjects (similar to Sinks)
PublishSubject<String> subject = PublishSubject.create();
subject.onNext("event1");

// With backpressure
PublishProcessor<String> processor = PublishProcessor.create();
Flowable<String> hotFlowable = processor;
processor.onNext("event1");
```

## Testing

### Reactor - StepVerifier

```java
@Test
void testFlux() {
    Flux<String> flux = service.getData();

    StepVerifier.create(flux)
        .expectNext("first")
        .expectNextCount(2)
        .expectComplete()
        .verify();
}

@Test
void testError() {
    Mono<String> mono = service.failingOperation();

    StepVerifier.create(mono)
        .expectErrorMatches(ex ->
            ex instanceof IllegalStateException)
        .verify();
}

@Test
void testVirtualTime() {
    StepVerifier.withVirtualTime(() ->
            Mono.delay(Duration.ofHours(1)))
        .expectSubscription()
        .thenAwait(Duration.ofHours(1))
        .expectNextCount(1)
        .verifyComplete();
}

@Test
void testBackpressure() {
    Flux<Integer> flux = Flux.range(1, 100);

    StepVerifier.create(flux, 10)
        .expectNextCount(10)
        .thenRequest(10)
        .expectNextCount(10)
        .thenCancel()
        .verify();
}
```

### RxJava - TestObserver/TestSubscriber

```java
@Test
void testFlowable() {
    Flowable<String> flowable = service.getData();

    TestSubscriber<String> testSubscriber = flowable.test();

    testSubscriber
        .assertValueAt(0, "first")
        .assertValueCount(3)
        .assertComplete();
}

@Test
void testError() {
    Single<String> single = service.failingOperation();

    TestObserver<String> testObserver = single.test();

    testObserver.assertError(IllegalStateException.class);
}

@Test
void testVirtualTime() {
    TestScheduler scheduler = new TestScheduler();

    Single<Long> delayed = Single.timer(1, TimeUnit.HOURS, scheduler);

    TestObserver<Long> observer = delayed.test();

    observer.assertNoValues();
    scheduler.advanceTimeBy(1, TimeUnit.HOURS);
    observer.assertValueCount(1).assertComplete();
}

@Test
void testBackpressure() {
    Flowable<Integer> flowable = Flowable.range(1, 100);

    TestSubscriber<Integer> subscriber = flowable.test(10);

    subscriber.assertValueCount(10);
    subscriber.request(10);
    subscriber.assertValueCount(20);
    subscriber.cancel();
}
```

## Interoperability

### Converting Between Reactor and RxJava

```java
// Dependencies needed:
// io.projectreactor.addons:reactor-adapter

// Reactor to RxJava
Flux<String> reactorFlux = Flux.just("a", "b", "c");
Flowable<String> rxFlowable = Flowable.fromPublisher(reactorFlux);
Observable<String> rxObservable = Observable.fromPublisher(reactorFlux);

Mono<String> reactorMono = Mono.just("hello");
Single<String> rxSingle = Single.fromPublisher(reactorMono);
Maybe<String> rxMaybe = Maybe.fromPublisher(reactorMono);

// RxJava to Reactor
Flowable<String> rxFlowable = Flowable.just("a", "b", "c");
Flux<String> reactorFlux = Flux.from(rxFlowable);

Single<String> rxSingle = Single.just("hello");
Mono<String> reactorMono = Mono.from(rxSingle.toFlowable());

// Using adapter library
import reactor.adapter.rxjava.RxJava3Adapter;

Flux<String> flux = RxJava3Adapter.flowableToFlux(flowable);
Mono<String> mono = RxJava3Adapter.singleToMono(single);
Flowable<String> flowable = RxJava3Adapter.fluxToFlowable(flux);
Single<String> single = RxJava3Adapter.monoToSingle(mono);
```

## When to Use Which

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CHOOSING BETWEEN REACTOR AND RXJAVA                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Choose Project Reactor when:                                           │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                  │   │
│   │   ✓ Building Spring WebFlux applications                        │   │
│   │   ✓ Need built-in Context propagation                           │   │
│   │   ✓ Prefer simpler type system (Mono/Flux only)                 │   │
│   │   ✓ Using other Spring ecosystem projects                       │   │
│   │   ✓ Want first-class Kotlin coroutines support                  │   │
│   │   ✓ Need debugging features (checkpoints, debug mode)           │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Choose RxJava when:                                                    │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                  │   │
│   │   ✓ Building Android applications                               │   │
│   │   ✓ Need fine-grained type distinctions                         │   │
│   │     (Single vs Maybe vs Completable)                            │   │
│   │   ✓ Using RxJava ecosystem libraries (RxBinding, RxAndroid)     │   │
│   │   ✓ Team already experienced with RxJava                        │   │
│   │   ✓ Cross-platform (RxJS, RxSwift compatibility concepts)       │   │
│   │   ✓ Need Observable (non-backpressured) for UI events           │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Either works well when:                                                │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                  │   │
│   │   • Building microservices                                       │   │
│   │   • Need reactive database access                                │   │
│   │   • Processing streaming data                                    │   │
│   │   • Building event-driven systems                                │   │
│   │                                                                  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Migration Cheat Sheet

### RxJava to Reactor

```java
// Single → Mono
Single<User> rxSingle = ...;
Mono<User> reactorMono = Mono.from(rxSingle.toFlowable());

// Maybe → Mono
Maybe<User> rxMaybe = ...;
Mono<User> reactorMono = Mono.from(rxMaybe.toFlowable());

// Completable → Mono<Void>
Completable rxCompletable = ...;
Mono<Void> reactorMono = Mono.from(rxCompletable.toFlowable());

// Flowable → Flux
Flowable<User> rxFlowable = ...;
Flux<User> reactorFlux = Flux.from(rxFlowable);

// Observable → Flux (be careful with backpressure!)
Observable<User> rxObservable = ...;
Flux<User> reactorFlux = Flux.from(rxObservable.toFlowable(BackpressureStrategy.BUFFER));
```

### Reactor to RxJava

```java
// Mono → Single (when exactly one element expected)
Mono<User> reactorMono = ...;
Single<User> rxSingle = Single.fromPublisher(reactorMono);

// Mono → Maybe (when zero or one element)
Mono<User> reactorMono = ...;
Maybe<User> rxMaybe = Maybe.fromPublisher(reactorMono);

// Mono<Void> → Completable
Mono<Void> reactorMono = ...;
Completable rxCompletable = Completable.fromPublisher(reactorMono);

// Flux → Flowable
Flux<User> reactorFlux = ...;
Flowable<User> rxFlowable = Flowable.fromPublisher(reactorFlux);

// Flux → Observable (loses backpressure)
Flux<User> reactorFlux = ...;
Observable<User> rxObservable = Observable.fromPublisher(reactorFlux);
```

### Common Pattern Migrations

```java
// RxJava pattern
Single.just(data)
    .subscribeOn(Schedulers.io())
    .observeOn(Schedulers.computation())
    .flatMap(d -> processAsync(d))
    .subscribe(
        result -> handleSuccess(result),
        error -> handleError(error)
    );

// Equivalent Reactor pattern
Mono.just(data)
    .subscribeOn(Schedulers.boundedElastic())
    .publishOn(Schedulers.parallel())
    .flatMap(d -> processAsync(d))
    .subscribe(
        result -> handleSuccess(result),
        error -> handleError(error)
    );
```

## Key Differences Summary

| Feature | Reactor | RxJava |
|---------|---------|--------|
| Types | 2 (Mono, Flux) | 5 (Single, Maybe, Completable, Observable, Flowable) |
| Context | Built-in | Manual |
| Spring Integration | Native | Adapter required |
| Android | Limited | Excellent |
| Debug tools | Hooks.onOperatorDebug, checkpoint | None built-in |
| Kotlin support | First-class (coroutines) | Good (rxkotlin) |
| Processor types | Sinks API | Subject/Processor |
| Backpressure | All types | Flowable only |
| Assembly tracking | reactor-tools | None |
| Test utilities | StepVerifier | TestObserver/TestSubscriber |

Both libraries are mature, well-documented, and actively maintained. The choice often comes down to ecosystem fit: Reactor for Spring applications, RxJava for Android or when using the broader ReactiveX ecosystem.

