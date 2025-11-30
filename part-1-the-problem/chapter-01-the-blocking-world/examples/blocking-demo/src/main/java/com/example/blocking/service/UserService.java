package com.example.blocking.service;

import com.example.blocking.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Simulated User Service that demonstrates blocking behavior.
 *
 * Key Observation:
 * When findById() is called, the current thread BLOCKS for the entire
 * duration of the simulated database call. During this time, the thread
 * cannot do any other work.
 *
 * In a real application, this would be a database call via JPA/JDBC,
 * which has the same blocking characteristic.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Value("${app.database-delay:50}")
    private int databaseDelay;

    /**
     * Simulates fetching a user from a database.
     *
     * Watch the logs - you'll see the thread is occupied for the entire duration.
     *
     * @param id User ID to fetch
     * @return User object
     */
    public User findById(Long id) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.debug("[{}] Starting user lookup for id={}", threadName, id);

        // THIS IS THE BLOCKING CALL
        // The thread sits here doing NOTHING, just waiting
        simulateDatabaseCall();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[{}] Completed user lookup for id={} in {}ms", threadName, id, duration);

        return User.of(id);
    }

    /**
     * Simulates a blocking database call.
     *
     * In reality, this would be:
     * - JDBC connection acquisition
     * - SQL execution
     * - Result set processing
     *
     * The thread is BLOCKED during all of this.
     */
    private void simulateDatabaseCall() {
        try {
            // Thread.sleep() is a blocking operation
            // The thread enters TIMED_WAITING state
            // It cannot be used for anything else
            Thread.sleep(databaseDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Database call interrupted", e);
        }
    }
}
