# Concurrency Evolution Demo

This project demonstrates the evolution of Java concurrency models from raw threads to reactive streams.

## Purpose

This demo helps you **experience** the evolution firsthand:

1. **Thread Era (1995)** - Manual thread management with wait/notify
2. **Executor Era (2004)** - Thread pools and ExecutorService
3. **Future Era (2004-2014)** - Futures and callbacks (callback hell!)
4. **CompletableFuture Era (2014)** - Composable async operations
5. **Reactive Era (2017+)** - Reactive Streams with backpressure

## Running the Application

```bash
# From this directory
./mvnw spring-boot:run

# Or with Maven
mvn spring-boot:run
```

The application will demonstrate each era in sequence, printing output to the console.

## What You'll See

### Era 1: Thread Era
- Basic thread creation and joining
- Producer-consumer with wait/notify (the old way)
- The complexity of manual synchronization

### Era 2: Executor Era
- Thread pool usage with ExecutorService
- Producer-consumer with BlockingQueue (much simpler!)
- How threads are reused

### Era 3: Future Era
- Future and Callable for async results
- The problem: Future.get() still blocks!
- Callback hell as a workaround

### Era 4: CompletableFuture Era
- Chaining with thenApply/thenCompose
- Parallel composition with thenCombine/allOf
- Integrated error handling
- The limitations (no backpressure, single value, not lazy)

### Era 5: Reactive Era
- Mono and Flux basics
- Backpressure in action
- Laziness demonstrated
- Rich operator library
- Scheduler control

## Key Observations

After running this demo, you should understand:

1. **Each era solved real problems** from the previous era
2. **The trend is from imperative to declarative**
3. **CompletableFuture is "proto-reactive"** - the bridge to reactive
4. **Reactive adds what CF lacks**: backpressure, streaming, laziness, operators

## Project Structure

```
src/main/java/com/example/concurrency/
├── ConcurrencyEvolutionApplication.java  # Main application
├── common/
│   ├── User.java                         # Domain model
│   ├── Order.java                        # Domain model
│   └── SimulatedService.java             # Service with all styles
├── thread_era/
│   └── ThreadExample.java                # Thread + wait/notify
├── executor_era/
│   └── ExecutorExample.java              # ExecutorService + BlockingQueue
├── future_era/
│   └── FutureExample.java                # Future + Callbacks
├── completablefuture_era/
│   └── CompletableFutureExample.java     # CompletableFuture
└── reactive_era/
    └── ReactiveExample.java              # Project Reactor
```

## Experimenting

Feel free to modify the examples:

1. **Add more threads** to thread era examples - see the complexity grow
2. **Increase work duration** - observe how blocking affects throughput
3. **Remove backpressure** in reactive example - see what happens
4. **Add more nesting** to callback example - experience callback hell

## Next Steps

After understanding the evolution, proceed to:
- **Chapter 3**: What is Reactive Programming, Really?
- **Hands-On Lab 2**: Implement the same scenario across all eras
