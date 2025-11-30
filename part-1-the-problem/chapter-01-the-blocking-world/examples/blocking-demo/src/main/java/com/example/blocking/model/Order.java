package com.example.blocking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Simple Order model for demonstration purposes.
 */
public record Order(
    Long id,
    Long userId,
    Long productId,
    int quantity,
    BigDecimal totalPrice,
    LocalDateTime createdAt
) {
    public static Order of(Long id, Long userId) {
        return new Order(
            id,
            userId,
            100L + id,  // productId
            1 + (int)(id % 5),
            BigDecimal.valueOf(19.99 * (1 + id % 10)),
            LocalDateTime.now().minusDays(id % 30)
        );
    }
}
