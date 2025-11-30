package com.example.blocking.model;

import java.math.BigDecimal;

/**
 * Combined order summary with product details.
 */
public record OrderSummary(
    Long orderId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {
    public static OrderSummary from(Order order, Product product) {
        return new OrderSummary(
            order.id(),
            product.name(),
            order.quantity(),
            product.price(),
            order.totalPrice()
        );
    }
}
