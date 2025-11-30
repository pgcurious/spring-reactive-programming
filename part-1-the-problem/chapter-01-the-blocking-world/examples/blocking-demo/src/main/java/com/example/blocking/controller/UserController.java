package com.example.blocking.controller;

import com.example.blocking.model.Order;
import com.example.blocking.model.OrderSummary;
import com.example.blocking.model.Product;
import com.example.blocking.model.User;
import com.example.blocking.service.ExternalApiService;
import com.example.blocking.service.OrderService;
import com.example.blocking.service.ProductService;
import com.example.blocking.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User Controller demonstrating blocking request handling.
 *
 * IMPORTANT OBSERVATIONS:
 * 1. Each method holds a thread for its ENTIRE duration
 * 2. Sequential blocking calls compound the total wait time
 * 3. The thread is IDLE during each blocking call
 * 4. Under load, threads become the bottleneck
 *
 * Watch the logs to see:
 * - Which thread handles each request
 * - How long each blocking operation takes
 * - Thread reuse (or lack thereof) under load
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final OrderService orderService;
    private final ProductService productService;
    private final ExternalApiService externalApiService;

    public UserController(UserService userService,
                          OrderService orderService,
                          ProductService productService,
                          ExternalApiService externalApiService) {
        this.userService = userService;
        this.orderService = orderService;
        this.productService = productService;
        this.externalApiService = externalApiService;
    }

    /**
     * Simple endpoint with ONE blocking call.
     *
     * Timeline:
     * [Request arrives] → [Thread assigned] → [BLOCK: DB call ~50ms] → [Response]
     *
     * Thread is blocked for ~50ms doing nothing.
     */
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.info("════════════════════════════════════════════════════════════");
        log.info("[{}] REQUEST START: GET /api/users/{}", threadName, id);

        // BLOCKING CALL #1: Database lookup
        User user = userService.findById(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[{}] REQUEST END: Completed in {}ms", threadName, duration);
        log.info("════════════════════════════════════════════════════════════");

        return user;
    }

    /**
     * Complex endpoint with MULTIPLE SEQUENTIAL blocking calls.
     *
     * Timeline:
     * [Request] → [BLOCK: User ~50ms] → [BLOCK: Orders ~50ms] →
     * [BLOCK: Products ~50ms] → [BLOCK: Pricing ~200ms] → [Response]
     *
     * Total blocking time: ~350ms
     * Total CPU work: ~1ms
     *
     * The thread is idle for 99.7% of the request duration!
     */
    @GetMapping("/{id}/orders")
    public Map<String, Object> getUserOrders(@PathVariable Long id) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.info("════════════════════════════════════════════════════════════");
        log.info("[{}] REQUEST START: GET /api/users/{}/orders", threadName, id);
        log.info("[{}] This request will make 4 SEQUENTIAL blocking calls", threadName);

        // BLOCKING CALL #1: Get user (~50ms)
        log.info("[{}] Step 1/4: Fetching user...", threadName);
        User user = userService.findById(id);
        log.info("[{}] Step 1/4: Complete - got user {}", threadName, user.name());

        // BLOCKING CALL #2: Get orders (~50ms)
        log.info("[{}] Step 2/4: Fetching orders...", threadName);
        List<Order> orders = orderService.findByUserId(user.id());
        log.info("[{}] Step 2/4: Complete - got {} orders", threadName, orders.size());

        // BLOCKING CALL #3: Get products (~50ms)
        log.info("[{}] Step 3/4: Fetching products...", threadName);
        List<Long> productIds = orders.stream()
            .map(Order::productId)
            .distinct()
            .toList();
        List<Product> products = productService.findByIds(productIds);
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::id, Function.identity()));
        log.info("[{}] Step 3/4: Complete - got {} products", threadName, products.size());

        // BLOCKING CALL #4: Get pricing from external service (~200ms)
        log.info("[{}] Step 4/4: Calling external pricing service...", threadName);
        Map<String, Object> pricing = externalApiService.getPricingInfo(user.tier());
        log.info("[{}] Step 4/4: Complete - got pricing info", threadName);

        // Build response (actual CPU work - microseconds)
        List<OrderSummary> summaries = orders.stream()
            .map(order -> OrderSummary.from(order, productMap.get(order.productId())))
            .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("orders", summaries);
        response.put("pricing", pricing);
        response.put("threadUsed", threadName);

        long duration = System.currentTimeMillis() - startTime;
        response.put("totalTimeMs", duration);

        log.info("[{}] REQUEST END: Completed in {}ms", threadName, duration);
        log.info("[{}] Thread was BLOCKED (idle) for ~{}ms of that time", threadName, duration - 1);
        log.info("════════════════════════════════════════════════════════════");

        return response;
    }

    /**
     * Endpoint demonstrating the data dependency problem.
     *
     * Even though pricing and recommendations could theoretically be fetched
     * in parallel (they don't depend on each other), we fetch them sequentially
     * because that's the natural way to write blocking code.
     *
     * In reactive code, parallelizing independent calls is trivial.
     */
    @GetMapping("/{id}/dashboard")
    public Map<String, Object> getUserDashboard(@PathVariable Long id) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        log.info("════════════════════════════════════════════════════════════");
        log.info("[{}] REQUEST START: GET /api/users/{}/dashboard", threadName, id);
        log.info("[{}] Note: Some of these calls COULD be parallel but aren't", threadName);

        // Step 1: Get user (required first - others depend on it)
        User user = userService.findById(id);

        // Steps 2 & 3: These could run in PARALLEL but we run them sequentially
        // because that's easier with blocking code
        var pricing = externalApiService.getPricingInfo(user.tier());      // ~200ms
        var recommendations = externalApiService.getRecommendations(id);   // ~200ms

        // Sequential total: ~400ms for these two calls
        // Parallel would be: ~200ms (the max of the two)

        // Step 4: Get orders
        var orders = orderService.findByUserId(id);

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("pricing", pricing);
        response.put("recommendations", recommendations);
        response.put("recentOrders", orders.subList(0, Math.min(3, orders.size())));
        response.put("threadUsed", threadName);

        long duration = System.currentTimeMillis() - startTime;
        response.put("totalTimeMs", duration);
        response.put("note", "Pricing and recommendations were fetched SEQUENTIALLY. " +
            "In reactive code, they would run in PARALLEL, saving ~200ms.");

        log.info("[{}] REQUEST END: Completed in {}ms", threadName, duration);
        log.info("════════════════════════════════════════════════════════════");

        return response;
    }
}
