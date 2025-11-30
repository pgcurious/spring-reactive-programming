package com.example.blocking.service;

import com.example.blocking.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Simulated Product Service demonstrating blocking database access.
 *
 * This service simulates fetching products from a database.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Value("${app.database-delay:50}")
    private int databaseDelay;

    /**
     * Fetches products by their IDs.
     *
     * In a real application, this might execute:
     * SELECT * FROM products WHERE id IN (?, ?, ?, ...)
     *
     * @param productIds List of product IDs to fetch
     * @return List of products
     */
    public List<Product> findByIds(List<Long> productIds) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.debug("[{}] Starting product lookup for {} products", threadName, productIds.size());

        // Simulate database query
        simulateDatabaseCall();

        List<Product> products = productIds.stream()
            .map(Product::of)
            .toList();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[{}] Found {} products in {}ms", threadName, products.size(), duration);

        return products;
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
