package com.example.blocking.service;

import com.example.blocking.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.LongStream;

/**
 * Simulated Order Service demonstrating blocking database access.
 *
 * This service simulates fetching orders from a database.
 * Each call blocks the thread for the duration of the "database query".
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Value("${app.database-delay:50}")
    private int databaseDelay;

    /**
     * Fetches orders for a user.
     *
     * In a real application, this might execute:
     * SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC
     *
     * The thread blocks during query execution.
     */
    public List<Order> findByUserId(Long userId) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.debug("[{}] Starting order lookup for userId={}", threadName, userId);

        // Simulate database query time
        simulateDatabaseCall();

        // Return mock data
        List<Order> orders = LongStream.range(1, 6)
            .mapToObj(i -> Order.of(userId * 100 + i, userId))
            .toList();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[{}] Found {} orders for userId={} in {}ms",
            threadName, orders.size(), userId, duration);

        return orders;
    }

    private void simulateDatabaseCall() {
        try {
            Thread.sleep(databaseDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Database call interrupted", e);
        }
    }
}
