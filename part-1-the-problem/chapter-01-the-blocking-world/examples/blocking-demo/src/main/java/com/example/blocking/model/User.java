package com.example.blocking.model;

/**
 * Simple User model for demonstration purposes.
 */
public record User(
    Long id,
    String name,
    String email,
    String tier
) {
    public static User of(Long id) {
        return new User(
            id,
            "User " + id,
            "user" + id + "@example.com",
            id % 2 == 0 ? "PREMIUM" : "STANDARD"
        );
    }
}
