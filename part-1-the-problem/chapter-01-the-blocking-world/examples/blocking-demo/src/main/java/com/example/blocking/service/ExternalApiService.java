package com.example.blocking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Simulated External API Service demonstrating blocking HTTP calls.
 *
 * This represents calls to external services like:
 * - Payment gateways
 * - Third-party APIs
 * - Other microservices
 *
 * External calls typically have HIGHER latency than database calls,
 * making the blocking problem even more severe.
 */
@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    @Value("${app.external-service-delay:200}")
    private int externalServiceDelay;

    /**
     * Simulates calling an external pricing service.
     *
     * In reality, this would use RestTemplate or WebClient.retrieve().block()
     * Both approaches block the thread while waiting for the HTTP response.
     *
     * @param userTier The user's pricing tier
     * @return Pricing information
     */
    public Map<String, Object> getPricingInfo(String userTier) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.debug("[{}] Calling external pricing service for tier={}", threadName, userTier);

        // Simulate HTTP call to external service
        // This is typically the SLOWEST operation
        simulateExternalCall();

        Map<String, Object> pricing = Map.of(
            "tier", userTier,
            "discountPercent", "PREMIUM".equals(userTier) ? 15 : 0,
            "freeShipping", "PREMIUM".equals(userTier),
            "source", "external-pricing-service"
        );

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[{}] External pricing service responded in {}ms", threadName, duration);

        return pricing;
    }

    /**
     * Simulates calling a recommendation service.
     *
     * Recommendation services often involve:
     * - ML model inference
     * - Database lookups
     * - Caching layers
     *
     * All of which add latency.
     */
    public Map<String, Object> getRecommendations(Long userId) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.debug("[{}] Calling recommendation service for userId={}", threadName, userId);

        simulateExternalCall();

        Map<String, Object> recommendations = Map.of(
            "userId", userId,
            "recommendedProductIds", java.util.List.of(101L, 102L, 103L, 104L, 105L),
            "source", "recommendation-service"
        );

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[{}] Recommendation service responded in {}ms", threadName, duration);

        return recommendations;
    }

    private void simulateExternalCall() {
        try {
            // External services are typically SLOWER than databases
            // Network latency, service processing time, etc.
            Thread.sleep(externalServiceDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("External call interrupted", e);
        }
    }
}
