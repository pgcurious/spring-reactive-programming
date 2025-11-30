package com.example.blocking.controller;

import com.example.blocking.model.ThreadInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic Controller for observing thread behavior.
 *
 * Use these endpoints to understand:
 * - Current thread pool status
 * - How threads are being used
 * - Thread exhaustion under load
 */
@RestController
@RequestMapping("/api")
public class DiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticController.class);

    /**
     * Shows current thread pool status.
     *
     * Call this endpoint while load testing to see:
     * - How many threads are active
     * - Which threads are handling requests
     */
    @GetMapping("/threads")
    public ThreadInfo getThreadInfo() {
        Thread currentThread = Thread.currentThread();

        // Get all threads in the current thread group
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }

        Thread[] allThreads = new Thread[rootGroup.activeCount() + 10];
        int count = rootGroup.enumerate(allThreads, true);

        // Filter for HTTP threads (Tomcat worker threads)
        List<String> httpThreads = java.util.Arrays.stream(allThreads, 0, count)
            .filter(t -> t != null && t.getName().startsWith("http-nio"))
            .map(t -> t.getName() + " [" + t.getState() + "]")
            .toList();

        int activeHttpThreads = (int) java.util.Arrays.stream(allThreads, 0, count)
            .filter(t -> t != null && t.getName().startsWith("http-nio"))
            .filter(t -> t.getState() == Thread.State.RUNNABLE ||
                        t.getState() == Thread.State.TIMED_WAITING)
            .count();

        return new ThreadInfo(
            currentThread.getName(),
            activeHttpThreads,
            httpThreads.size(),
            httpThreads,
            Instant.now(),
            "HTTP threads shown. Under load, watch for BLOCKED or WAITING states."
        );
    }

    /**
     * Configurable slow endpoint for testing thread exhaustion.
     *
     * Usage:
     * - GET /api/slow?delayMs=5000  (blocks for 5 seconds)
     *
     * With max-threads=10, calling this endpoint 10 times simultaneously
     * will exhaust the thread pool. The 11th request will wait.
     */
    @GetMapping("/slow")
    public Map<String, Object> slowEndpoint(
            @RequestParam(defaultValue = "1000") int delayMs) {

        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.warn("╔══════════════════════════════════════════════════════════╗");
        log.warn("║ [{}] SLOW REQUEST: Blocking for {}ms", threadName, delayMs);
        log.warn("║ This thread is now UNAVAILABLE for other requests        ║");
        log.warn("╚══════════════════════════════════════════════════════════╝");

        try {
            // This simulates a slow external service or database
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - startTime;
        log.warn("[{}] SLOW REQUEST COMPLETE after {}ms", threadName, duration);

        Map<String, Object> response = new HashMap<>();
        response.put("threadUsed", threadName);
        response.put("requestedDelayMs", delayMs);
        response.put("actualDurationMs", duration);
        response.put("timestamp", Instant.now());
        response.put("message", "Thread " + threadName + " was blocked for " + duration + "ms");

        return response;
    }

    /**
     * Fast endpoint to contrast with slow endpoint.
     *
     * Use this to show that even fast requests queue behind slow ones
     * when the thread pool is exhausted.
     */
    @GetMapping("/fast")
    public Map<String, Object> fastEndpoint() {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        // Simulate minimal work
        Map<String, Object> response = new HashMap<>();
        response.put("threadUsed", threadName);
        response.put("message", "Fast response from " + threadName);
        response.put("timestamp", Instant.now());
        response.put("durationMs", System.currentTimeMillis() - startTime);

        return response;
    }

    /**
     * Health check endpoint for load testing tools.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "thread", Thread.currentThread().getName()
        );
    }
}
