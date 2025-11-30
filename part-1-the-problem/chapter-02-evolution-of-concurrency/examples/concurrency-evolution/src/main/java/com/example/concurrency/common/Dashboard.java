package com.example.concurrency.common;

import java.util.List;

/**
 * Dashboard aggregating user data from multiple sources.
 * This is the target object for the lab exercises.
 */
public record Dashboard(
    User user,
    List<Order> orders,
    double discount,
    String message
) {

    public Dashboard(User user, List<Order> orders, double discount) {
        this(user, orders, discount, generateMessage(user, orders, discount));
    }

    private static String generateMessage(User user, List<Order> orders, double discount) {
        return String.format(
            "Dashboard for %s: %d orders, %.0f%% discount",
            user.name(),
            orders.size(),
            discount * 100
        );
    }

    @Override
    public String toString() {
        return message;
    }
}
