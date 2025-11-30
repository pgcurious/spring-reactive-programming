package com.example.concurrency.common;

/**
 * Simple User model used throughout the examples.
 */
public record User(Long id, String name, String tier) {

    public User(Long id, String name) {
        this(id, name, "STANDARD");
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', tier='" + tier + "'}";
    }
}
