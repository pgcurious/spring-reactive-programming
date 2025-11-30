package com.example.blocking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Blocking Demo Application
 *
 * This application demonstrates the limitations of the traditional
 * thread-per-request model in Spring MVC.
 *
 * Key points to observe:
 * 1. Each request occupies a thread for its entire duration
 * 2. Thread pool exhaustion occurs under load
 * 3. Threads spend most of their time waiting (blocking on I/O)
 *
 * Run this application and use the load testing scripts to observe
 * how the thread pool becomes saturated.
 *
 * @see com.example.blocking.controller.UserController
 * @see com.example.blocking.service.SlowExternalService
 */
@SpringBootApplication
public class BlockingDemoApplication {

    public static void main(String[] args) {
        System.out.println("""
            ╔════════════════════════════════════════════════════════════════╗
            ║                    BLOCKING DEMO APPLICATION                   ║
            ╠════════════════════════════════════════════════════════════════╣
            ║  This app demonstrates thread-per-request blocking behavior.   ║
            ║                                                                ║
            ║  Endpoints:                                                    ║
            ║  • GET /api/users/{id}        - Simulated user lookup          ║
            ║  • GET /api/users/{id}/orders - Multiple blocking calls        ║
            ║  • GET /api/slow              - Configurable delay             ║
            ║  • GET /api/threads           - Current thread pool status     ║
            ║                                                                ║
            ║  Metrics:                                                      ║
            ║  • GET /actuator/metrics      - All metrics                    ║
            ║  • GET /actuator/prometheus   - Prometheus format              ║
            ╚════════════════════════════════════════════════════════════════╝
            """);

        SpringApplication.run(BlockingDemoApplication.class, args);
    }
}
