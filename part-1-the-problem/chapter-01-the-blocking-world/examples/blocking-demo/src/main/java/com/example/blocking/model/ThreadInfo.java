package com.example.blocking.model;

import java.time.Instant;
import java.util.List;

/**
 * Information about current thread pool status.
 */
public record ThreadInfo(
    String currentThread,
    int activeThreads,
    int totalThreads,
    List<String> threadNames,
    Instant timestamp,
    String message
) {}
