package com.example.blocking.model;

import java.math.BigDecimal;

/**
 * Simple Product model for demonstration purposes.
 */
public record Product(
    Long id,
    String name,
    String description,
    BigDecimal price,
    int stockQuantity
) {
    public static Product of(Long id) {
        return new Product(
            id,
            "Product " + id,
            "Description for product " + id,
            BigDecimal.valueOf(9.99 + (id % 100)),
            100 + (int)(id % 500)
        );
    }
}
