package com.example.concurrency.common;

import java.math.BigDecimal;

/**
 * Simple Order model used throughout the examples.
 */
public record Order(Long id, Long userId, String product, BigDecimal amount) {

    public Order(Long id, Long userId) {
        this(id, userId, "Product-" + id, BigDecimal.valueOf(99.99));
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", userId=" + userId + ", product='" + product + "'}";
    }
}
