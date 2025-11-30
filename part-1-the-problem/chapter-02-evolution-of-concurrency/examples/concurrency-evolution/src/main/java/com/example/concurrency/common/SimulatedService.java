package com.example.concurrency.common;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Simulated service that provides various ways to fetch data.
 * Each method simulates network latency to demonstrate blocking behavior.
 */
public class SimulatedService {

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Simulates network latency.
     */
    public static void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during simulated latency", e);
        }
    }

    /**
     * Simulates network latency with logging.
     */
    public static void simulateLatency(long millis, String operation) {
        System.out.println("    [" + Thread.currentThread().getName() + "] " +
            operation + " - waiting " + millis + "ms...");
        simulateLatency(millis);
        System.out.println("    [" + Thread.currentThread().getName() + "] " +
            operation + " - complete!");
    }

    // ========== BLOCKING METHODS ==========

    /**
     * Blocking user fetch - simulates database call.
     */
    public static User fetchUserBlocking(Long userId) {
        simulateLatency(100, "Fetching user " + userId);
        return new User(userId, "User-" + userId, "PREMIUM");
    }

    /**
     * Blocking orders fetch - simulates database call.
     */
    public static List<Order> fetchOrdersBlocking(Long userId) {
        simulateLatency(150, "Fetching orders for user " + userId);
        return List.of(
            new Order(1L, userId),
            new Order(2L, userId),
            new Order(3L, userId)
        );
    }

    // ========== CALLBACK-BASED METHODS ==========

    /**
     * Callback interface for async operations.
     */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /**
     * Async user fetch with callback.
     */
    public static void fetchUserAsync(Long userId, Callback<User> callback) {
        executor.submit(() -> {
            try {
                User user = fetchUserBlocking(userId);
                callback.onSuccess(user);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Async orders fetch with callback.
     */
    public static void fetchOrdersAsync(Long userId, Callback<List<Order>> callback) {
        executor.submit(() -> {
            try {
                List<Order> orders = fetchOrdersBlocking(userId);
                callback.onSuccess(orders);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ========== COMPLETABLEFUTURE METHODS ==========

    /**
     * CompletableFuture-based user fetch.
     */
    public static CompletableFuture<User> fetchUserCF(Long userId) {
        return CompletableFuture.supplyAsync(() -> fetchUserBlocking(userId), executor);
    }

    /**
     * CompletableFuture-based orders fetch.
     */
    public static CompletableFuture<List<Order>> fetchOrdersCF(Long userId) {
        return CompletableFuture.supplyAsync(() -> fetchOrdersBlocking(userId), executor);
    }

    /**
     * CompletableFuture-based pricing fetch.
     */
    public static CompletableFuture<Double> fetchPricingCF(String tier) {
        return CompletableFuture.supplyAsync(() -> {
            simulateLatency(80, "Fetching pricing for tier " + tier);
            return tier.equals("PREMIUM") ? 0.10 : 0.0;
        }, executor);
    }

    // ========== REACTIVE METHODS ==========

    /**
     * Reactive user fetch using Mono.
     */
    public static Mono<User> fetchUserReactive(Long userId) {
        return Mono.fromCallable(() -> fetchUserBlocking(userId))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Reactive orders fetch using Flux.
     */
    public static Flux<Order> fetchOrdersReactive(Long userId) {
        return Flux.defer(() -> {
            simulateLatency(150, "Fetching orders for user " + userId);
            return Flux.just(
                new Order(1L, userId),
                new Order(2L, userId),
                new Order(3L, userId)
            );
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Reactive pricing fetch using Mono.
     */
    public static Mono<Double> fetchPricingReactive(String tier) {
        return Mono.fromCallable(() -> {
            simulateLatency(80, "Fetching pricing for tier " + tier);
            return tier.equals("PREMIUM") ? 0.10 : 0.0;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Shutdown the executor service.
     */
    public static void shutdown() {
        executor.shutdown();
    }
}
