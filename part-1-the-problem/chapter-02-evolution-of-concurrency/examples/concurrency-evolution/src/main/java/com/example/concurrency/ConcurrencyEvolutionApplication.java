package com.example.concurrency;

import com.example.concurrency.completablefuture_era.CompletableFutureExample;
import com.example.concurrency.executor_era.ExecutorExample;
import com.example.concurrency.future_era.FutureExample;
import com.example.concurrency.reactive_era.ReactiveExample;
import com.example.concurrency.thread_era.ThreadExample;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Concurrency Evolution Application
 *
 * This application demonstrates the evolution of Java concurrency models:
 * - Thread Era (Java 1.0-1.4): Direct thread management
 * - Executor Era (Java 5): Thread pools and executors
 * - Future Era (Java 5-7): Futures and callbacks
 * - CompletableFuture Era (Java 8): Composable async operations
 * - Reactive Era (Java 9+): Reactive Streams with backpressure
 *
 * Run this application to see each approach in action.
 */
@SpringBootApplication
public class ConcurrencyEvolutionApplication implements CommandLineRunner {

    public static void main(String[] args) {
        System.out.println("""
            ╔════════════════════════════════════════════════════════════════════╗
            ║              CONCURRENCY EVOLUTION DEMONSTRATION                   ║
            ╠════════════════════════════════════════════════════════════════════╣
            ║  This application demonstrates the evolution of Java concurrency:  ║
            ║                                                                    ║
            ║  1. Thread Era (1995)      - Direct thread management              ║
            ║  2. Executor Era (2004)    - Thread pools                          ║
            ║  3. Future Era (2004)      - Futures and callbacks                 ║
            ║  4. CompletableFuture Era  - Composable async (2014)               ║
            ║  5. Reactive Era (2017+)   - Reactive Streams                      ║
            ║                                                                    ║
            ║  Watch the console to see each approach in action!                 ║
            ╚════════════════════════════════════════════════════════════════════╝
            """);

        SpringApplication.run(ConcurrencyEvolutionApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Starting Concurrency Evolution Demonstration");
        System.out.println("=".repeat(70) + "\n");

        // Run each era's demonstration
        runThreadEra();
        runExecutorEra();
        runFutureEra();
        runCompletableFutureEra();
        runReactiveEra();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Demonstration Complete!");
        System.out.println("=".repeat(70));
    }

    private void runThreadEra() throws InterruptedException {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("ERA 1: THE THREAD ERA (Java 1.0 - 1.4)");
        System.out.println("-".repeat(70));
        System.out.println("Direct thread management with wait/notify");
        System.out.println();

        ThreadExample example = new ThreadExample();
        example.demonstrateBasicThread();
        Thread.sleep(500);
        example.demonstrateProducerConsumer();
        Thread.sleep(1000);
    }

    private void runExecutorEra() throws InterruptedException {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("ERA 2: THE EXECUTOR ERA (Java 5)");
        System.out.println("-".repeat(70));
        System.out.println("Thread pools and the Executor framework");
        System.out.println();

        ExecutorExample example = new ExecutorExample();
        example.demonstrateThreadPool();
        Thread.sleep(500);
        example.demonstrateBlockingQueue();
        Thread.sleep(1000);
    }

    private void runFutureEra() throws Exception {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("ERA 3: THE FUTURE ERA (Java 5-7)");
        System.out.println("-".repeat(70));
        System.out.println("Futures and Callable - but Future.get() still blocks!");
        System.out.println();

        FutureExample example = new FutureExample();
        example.demonstrateFuture();
        Thread.sleep(500);
        example.demonstrateCallbackHell();
        Thread.sleep(1000);
    }

    private void runCompletableFutureEra() throws Exception {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("ERA 4: THE COMPLETABLEFUTURE ERA (Java 8)");
        System.out.println("-".repeat(70));
        System.out.println("Composable async operations - proto-reactive!");
        System.out.println();

        CompletableFutureExample example = new CompletableFutureExample();
        example.demonstrateChaining();
        Thread.sleep(500);
        example.demonstrateParallelComposition();
        Thread.sleep(500);
        example.demonstrateErrorHandling();
        Thread.sleep(1000);
    }

    private void runReactiveEra() throws InterruptedException {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("ERA 5: THE REACTIVE ERA (Java 9+)");
        System.out.println("-".repeat(70));
        System.out.println("Reactive Streams with backpressure using Project Reactor");
        System.out.println();

        ReactiveExample example = new ReactiveExample();
        example.demonstrateFluxBasics();
        Thread.sleep(500);
        example.demonstrateChaining();
        Thread.sleep(500);
        example.demonstrateBackpressure();
        Thread.sleep(500);
        example.demonstrateLaziness();
        Thread.sleep(1000);
    }
}
