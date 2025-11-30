# Lab 10: Building APIs with Functional Endpoints

## Objectives

By the end of this lab, you will:

1. Convert an annotated controller API to functional endpoints
2. Understand the difference between the two programming models
3. Create custom request predicates for complex routing
4. Implement filters for authentication, logging, and error handling
5. Organize functional endpoints in a maintainable project structure
6. Test functional endpoints with WebTestClient

## Prerequisites

- Completed Chapters 8 and 9 (WebFlux fundamentals and annotated controllers)
- Understanding of Project Reactor (Mono/Flux)
- Java 17+ and Maven installed
- Your favorite IDE

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup | 10 min |
| 2 | Basic Handler and Router | 20 min |
| 3 | Request Extraction and Response Building | 20 min |
| 4 | Custom Predicates | 15 min |
| 5 | Filters for Cross-Cutting Concerns | 25 min |
| 6 | Organizing for Scale | 15 min |
| 7 | Testing Functional Endpoints | 20 min |
| 8 | Reflection | 5 min |
| **Total** | | **130 min** |

---

## Part 1: Project Setup (10 min)

### Step 1.1: Create the Project

Create a new directory and add the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>functional-endpoints-lab</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 1.2: Create the Project Structure

```
src/main/java/com/example/functional/
├── FunctionalEndpointsApplication.java
├── domain/
│   ├── Product.java
│   └── ProductRepository.java
├── handler/
│   └── ProductHandler.java
├── router/
│   └── ProductRouter.java
├── filter/
│   └── Filters.java
├── predicate/
│   └── CustomPredicates.java
└── dto/
    ├── ProductRequest.java
    └── ErrorResponse.java
```

### Step 1.3: Create the Main Application

```java
package com.example.functional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FunctionalEndpointsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FunctionalEndpointsApplication.class, args);
    }
}
```

### Step 1.4: Create the Domain Model

```java
package com.example.functional.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Product {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private int stockQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Product(String id, String name, String description,
                   BigDecimal price, String category, int stockQuantity) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

### Step 1.5: Create an In-Memory Repository

```java
package com.example.functional.domain;

import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductRepository {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        // Initialize with sample data
        save(new Product(null, "Laptop", "High-performance laptop",
            new BigDecimal("999.99"), "Electronics", 50)).block();
        save(new Product(null, "Headphones", "Wireless noise-canceling headphones",
            new BigDecimal("199.99"), "Electronics", 100)).block();
        save(new Product(null, "Coffee Maker", "Automatic drip coffee maker",
            new BigDecimal("79.99"), "Home", 30)).block();
        save(new Product(null, "Running Shoes", "Lightweight running shoes",
            new BigDecimal("129.99"), "Sports", 75)).block();
        save(new Product(null, "Backpack", "Water-resistant hiking backpack",
            new BigDecimal("89.99"), "Sports", 40)).block();
    }

    public Flux<Product> findAll() {
        return Flux.fromIterable(products.values());
    }

    public Mono<Product> findById(String id) {
        return Mono.justOrEmpty(products.get(id));
    }

    public Flux<Product> findByCategory(String category) {
        return Flux.fromIterable(products.values())
            .filter(p -> p.getCategory().equalsIgnoreCase(category));
    }

    public Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return Flux.fromIterable(products.values())
            .filter(p -> p.getPrice().compareTo(minPrice) >= 0
                      && p.getPrice().compareTo(maxPrice) <= 0);
    }

    public Flux<Product> search(String query) {
        String lowerQuery = query.toLowerCase();
        return Flux.fromIterable(products.values())
            .filter(p -> p.getName().toLowerCase().contains(lowerQuery)
                      || p.getDescription().toLowerCase().contains(lowerQuery));
    }

    public Mono<Product> save(Product product) {
        if (product.getId() == null) {
            product.setId(UUID.randomUUID().toString());
            product.setCreatedAt(LocalDateTime.now());
        }
        product.setUpdatedAt(LocalDateTime.now());
        products.put(product.getId(), product);
        return Mono.just(product);
    }

    public Mono<Void> deleteById(String id) {
        products.remove(id);
        return Mono.empty();
    }

    public Mono<Boolean> existsById(String id) {
        return Mono.just(products.containsKey(id));
    }
}
```

---

## Part 2: Basic Handler and Router (20 min)

### Step 2.1: Create DTOs

```java
package com.example.functional.dto;

import java.math.BigDecimal;

public record ProductRequest(
    String name,
    String description,
    BigDecimal price,
    String category,
    int stockQuantity
) {}
```

```java
package com.example.functional.dto;

import java.time.Instant;

public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    String path
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }

    public ErrorResponse withPath(String path) {
        return new ErrorResponse(code, message, timestamp, path);
    }
}
```

### Step 2.2: Create the Product Handler

```java
package com.example.functional.handler;

import com.example.functional.domain.Product;
import com.example.functional.domain.ProductRepository;
import com.example.functional.dto.ErrorResponse;
import com.example.functional.dto.ProductRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

@Component
public class ProductHandler {

    private final ProductRepository repository;

    public ProductHandler(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * GET /products - List all products with optional filtering
     * Query params: category, minPrice, maxPrice, search
     */
    public Mono<ServerResponse> listProducts(ServerRequest request) {
        // Extract optional query parameters
        Optional<String> category = request.queryParam("category");
        Optional<String> minPriceStr = request.queryParam("minPrice");
        Optional<String> maxPriceStr = request.queryParam("maxPrice");
        Optional<String> search = request.queryParam("search");

        // Build the query based on parameters
        var products = search.map(repository::search)
            .or(() -> category.map(repository::findByCategory))
            .or(() -> {
                if (minPriceStr.isPresent() && maxPriceStr.isPresent()) {
                    BigDecimal min = new BigDecimal(minPriceStr.get());
                    BigDecimal max = new BigDecimal(maxPriceStr.get());
                    return Optional.of(repository.findByPriceRange(min, max));
                }
                return Optional.empty();
            })
            .orElseGet(repository::findAll);

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(products, Product.class);
    }

    /**
     * GET /products/{id} - Get a single product by ID
     */
    public Mono<ServerResponse> getProduct(ServerRequest request) {
        String id = request.pathVariable("id");

        return repository.findById(id)
            .flatMap(product -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * POST /products - Create a new product
     */
    public Mono<ServerResponse> createProduct(ServerRequest request) {
        return request.bodyToMono(ProductRequest.class)
            .flatMap(this::validateAndCreateProduct)
            .flatMap(product -> ServerResponse
                .created(URI.create("/products/" + product.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product))
            .onErrorResume(ValidationException.class, e ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("VALIDATION_ERROR", e.getMessage())));
    }

    /**
     * PUT /products/{id} - Update an existing product
     */
    public Mono<ServerResponse> updateProduct(ServerRequest request) {
        String id = request.pathVariable("id");

        return request.bodyToMono(ProductRequest.class)
            .flatMap(updateRequest -> repository.findById(id)
                .flatMap(existing -> {
                    existing.setName(updateRequest.name());
                    existing.setDescription(updateRequest.description());
                    existing.setPrice(updateRequest.price());
                    existing.setCategory(updateRequest.category());
                    existing.setStockQuantity(updateRequest.stockQuantity());
                    return repository.save(existing);
                }))
            .flatMap(product -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * DELETE /products/{id} - Delete a product
     */
    public Mono<ServerResponse> deleteProduct(ServerRequest request) {
        String id = request.pathVariable("id");

        return repository.findById(id)
            .flatMap(product -> repository.deleteById(id)
                .then(ServerResponse.noContent().build()))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * GET /products/{id}/stock - Get stock information
     */
    public Mono<ServerResponse> getStock(ServerRequest request) {
        String id = request.pathVariable("id");

        return repository.findById(id)
            .flatMap(product -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new StockInfo(product.getId(), product.getStockQuantity())))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * PATCH /products/{id}/stock - Update stock quantity
     */
    public Mono<ServerResponse> updateStock(ServerRequest request) {
        String id = request.pathVariable("id");

        return request.bodyToMono(StockUpdate.class)
            .flatMap(update -> repository.findById(id)
                .flatMap(product -> {
                    int newQuantity = product.getStockQuantity() + update.adjustment();
                    if (newQuantity < 0) {
                        return Mono.error(new ValidationException(
                            "Stock cannot be negative. Current: " + product.getStockQuantity()));
                    }
                    product.setStockQuantity(newQuantity);
                    return repository.save(product);
                }))
            .flatMap(product -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new StockInfo(product.getId(), product.getStockQuantity())))
            .switchIfEmpty(ServerResponse.notFound().build())
            .onErrorResume(ValidationException.class, e ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("VALIDATION_ERROR", e.getMessage())));
    }

    // Helper methods and inner classes
    private Mono<Product> validateAndCreateProduct(ProductRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return Mono.error(new ValidationException("Product name is required"));
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new ValidationException("Price must be positive"));
        }
        if (request.stockQuantity() < 0) {
            return Mono.error(new ValidationException("Stock quantity cannot be negative"));
        }

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setStockQuantity(request.stockQuantity());

        return repository.save(product);
    }

    public record StockInfo(String productId, int quantity) {}
    public record StockUpdate(int adjustment) {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
```

### Step 2.3: Create the Product Router

```java
package com.example.functional.router;

import com.example.functional.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ProductRouter {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return route()
            // List and search products
            .GET("/products", accept(MediaType.APPLICATION_JSON), handler::listProducts)

            // Get single product
            .GET("/products/{id}", accept(MediaType.APPLICATION_JSON), handler::getProduct)

            // Create product
            .POST("/products", contentType(MediaType.APPLICATION_JSON), handler::createProduct)

            // Update product
            .PUT("/products/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateProduct)

            // Delete product
            .DELETE("/products/{id}", handler::deleteProduct)

            // Stock operations (nested under products)
            .path("/products/{id}/stock", stockBuilder -> stockBuilder
                .GET("", handler::getStock)
                .PATCH("", contentType(MediaType.APPLICATION_JSON), handler::updateStock)
            )

            .build();
    }
}
```

### Step 2.4: Test the Basic API

Start the application and test:

```bash
# List all products
curl http://localhost:8080/products

# Get a product (use an ID from the list response)
curl http://localhost:8080/products/{id}

# Search products
curl "http://localhost:8080/products?search=laptop"

# Filter by category
curl "http://localhost:8080/products?category=Electronics"

# Create a product
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Tablet","description":"10-inch tablet","price":499.99,"category":"Electronics","stockQuantity":25}'

# Update stock
curl -X PATCH http://localhost:8080/products/{id}/stock \
  -H "Content-Type: application/json" \
  -d '{"adjustment":-5}'
```

---

## Part 3: Request Extraction and Response Building (20 min)

### Step 3.1: Create a Handler for Complex Request Handling

Create a new handler that demonstrates various extraction patterns:

```java
package com.example.functional.handler;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DemoHandler {

    /**
     * Demonstrates extracting various parts of a request
     */
    public Mono<ServerResponse> inspectRequest(ServerRequest request) {
        Map<String, Object> inspection = new HashMap<>();

        // URI and path information
        inspection.put("uri", request.uri().toString());
        inspection.put("path", request.path());
        inspection.put("method", request.method().name());

        // Path variables
        try {
            inspection.put("pathVariables", request.pathVariables());
        } catch (Exception e) {
            inspection.put("pathVariables", Map.of());
        }

        // Query parameters
        inspection.put("queryParams", request.queryParams().toSingleValueMap());

        // Headers (selected)
        Map<String, String> headers = new HashMap<>();
        request.headers().header("Content-Type").stream().findFirst()
            .ifPresent(v -> headers.put("Content-Type", v));
        request.headers().header("Accept").stream().findFirst()
            .ifPresent(v -> headers.put("Accept", v));
        request.headers().header("User-Agent").stream().findFirst()
            .ifPresent(v -> headers.put("User-Agent", v));
        request.headers().header("Authorization").stream().findFirst()
            .ifPresent(v -> headers.put("Authorization", "[REDACTED]"));
        inspection.put("headers", headers);

        // Remote address
        request.remoteAddress()
            .ifPresent(addr -> inspection.put("remoteAddress", addr.toString()));

        // Cookies
        Map<String, String> cookies = new HashMap<>();
        request.cookies().forEach((name, cookieList) ->
            cookieList.stream().findFirst().ifPresent(cookie ->
                cookies.put(name, cookie.getValue())));
        inspection.put("cookies", cookies);

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(inspection);
    }

    /**
     * Demonstrates pagination with query parameters
     */
    public Mono<ServerResponse> paginatedList(ServerRequest request) {
        // Extract pagination parameters with defaults
        int page = request.queryParam("page")
            .map(Integer::parseInt)
            .orElse(0);
        int size = request.queryParam("size")
            .map(Integer::parseInt)
            .map(s -> Math.min(s, 100)) // Cap at 100
            .orElse(20);
        String sortBy = request.queryParam("sortBy").orElse("id");
        String sortDir = request.queryParam("sortDir").orElse("asc");

        // Create response with pagination info
        Map<String, Object> response = Map.of(
            "page", page,
            "size", size,
            "sortBy", sortBy,
            "sortDir", sortDir,
            "totalElements", 150,
            "totalPages", (int) Math.ceil(150.0 / size),
            "content", List.of("item1", "item2", "item3")
        );

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    /**
     * Demonstrates streaming responses
     */
    public Mono<ServerResponse> streamData(ServerRequest request) {
        int count = request.queryParam("count")
            .map(Integer::parseInt)
            .orElse(10);

        Flux<Map<String, Object>> dataStream = Flux.interval(Duration.ofMillis(500))
            .take(count)
            .map(i -> Map.of(
                "sequence", i,
                "timestamp", System.currentTimeMillis(),
                "data", "Event " + i
            ));

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_NDJSON)
            .body(dataStream, Map.class);
    }

    /**
     * Demonstrates Server-Sent Events
     */
    public Mono<ServerResponse> sseStream(ServerRequest request) {
        Flux<String> events = Flux.interval(Duration.ofSeconds(1))
            .take(10)
            .map(i -> "Event number " + i + " at " + System.currentTimeMillis());

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(events, String.class);
    }

    /**
     * Demonstrates conditional responses based on headers
     */
    public Mono<ServerResponse> conditionalResponse(ServerRequest request) {
        Optional<String> acceptHeader = request.headers()
            .header("Accept")
            .stream()
            .findFirst();

        Map<String, Object> data = Map.of(
            "message", "Hello, World!",
            "timestamp", System.currentTimeMillis()
        );

        if (acceptHeader.isPresent() && acceptHeader.get().contains("text/plain")) {
            return ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("Message: " + data.get("message"));
        }

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(data);
    }

    /**
     * Demonstrates file upload handling
     */
    public Mono<ServerResponse> uploadFile(ServerRequest request) {
        return request.multipartData()
            .flatMap(parts -> {
                List<String> uploadedFiles = parts.get("file").stream()
                    .filter(part -> part instanceof FilePart)
                    .map(part -> ((FilePart) part).filename())
                    .toList();

                Map<String, Object> response = Map.of(
                    "message", "Files received",
                    "fileCount", uploadedFiles.size(),
                    "fileNames", uploadedFiles
                );

                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response);
            });
    }
}
```

### Step 3.2: Add Demo Routes

Update or create a demo router:

```java
package com.example.functional.router;

import com.example.functional.handler.DemoHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class DemoRouter {

    @Bean
    public RouterFunction<ServerResponse> demoRoutes(DemoHandler handler) {
        return route()
            .path("/demo", builder -> builder
                // Request inspection
                .GET("/inspect", handler::inspectRequest)
                .GET("/inspect/{id}", handler::inspectRequest)
                .POST("/inspect", handler::inspectRequest)

                // Pagination demo
                .GET("/paginated", handler::paginatedList)

                // Streaming demos
                .GET("/stream", handler::streamData)
                .GET("/sse", handler::sseStream)

                // Content negotiation
                .GET("/conditional", handler::conditionalResponse)

                // File upload
                .POST("/upload", contentType(MediaType.MULTIPART_FORM_DATA), handler::uploadFile)
            )
            .build();
    }
}
```

### Step 3.3: Test the Demo Endpoints

```bash
# Inspect a GET request
curl "http://localhost:8080/demo/inspect?foo=bar&baz=qux"

# Inspect with path variable
curl http://localhost:8080/demo/inspect/123

# Pagination
curl "http://localhost:8080/demo/paginated?page=2&size=10&sortBy=name"

# Streaming (will receive 10 events, one every 500ms)
curl http://localhost:8080/demo/stream?count=5

# SSE stream
curl http://localhost:8080/demo/sse

# Content negotiation - get JSON
curl http://localhost:8080/demo/conditional

# Content negotiation - get plain text
curl -H "Accept: text/plain" http://localhost:8080/demo/conditional
```

---

## Part 4: Custom Predicates (15 min)

### Step 4.1: Create Custom Predicates

```java
package com.example.functional.predicate;

import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Set;
import java.util.function.Predicate;

public class CustomPredicates {

    /**
     * Matches requests with a specific API version header
     */
    public static RequestPredicate apiVersion(String version) {
        return request -> request.headers()
            .header("X-Api-Version")
            .stream()
            .anyMatch(v -> v.equals(version));
    }

    /**
     * Matches requests with any of the specified API versions
     */
    public static RequestPredicate apiVersionIn(String... versions) {
        Set<String> versionSet = Set.of(versions);
        return request -> request.headers()
            .header("X-Api-Version")
            .stream()
            .anyMatch(versionSet::contains);
    }

    /**
     * Matches requests from internal IP addresses
     */
    public static RequestPredicate internalNetwork() {
        return request -> request.remoteAddress()
            .map(addr -> {
                String ip = addr.getAddress().getHostAddress();
                return ip.startsWith("10.") ||
                       ip.startsWith("192.168.") ||
                       ip.startsWith("172.16.") ||
                       ip.equals("127.0.0.1") ||
                       ip.equals("0:0:0:0:0:0:0:1");
            })
            .orElse(false);
    }

    /**
     * Matches requests with a valid API key
     */
    public static RequestPredicate hasApiKey() {
        return request -> request.headers()
            .header("X-Api-Key")
            .stream()
            .findFirst()
            .filter(key -> !key.isBlank())
            .isPresent();
    }

    /**
     * Matches requests with a query parameter present
     */
    public static RequestPredicate hasQueryParam(String name) {
        return request -> request.queryParam(name).isPresent();
    }

    /**
     * Matches requests with a query parameter having a specific value
     */
    public static RequestPredicate queryParamEquals(String name, String value) {
        return request -> request.queryParam(name)
            .map(v -> v.equals(value))
            .orElse(false);
    }

    /**
     * Matches requests where query param matches a predicate
     */
    public static RequestPredicate queryParam(String name, Predicate<String> predicate) {
        return request -> request.queryParam(name)
            .map(predicate::test)
            .orElse(false);
    }

    /**
     * Matches premium tier requests (based on header)
     */
    public static RequestPredicate premiumTier() {
        return request -> request.headers()
            .header("X-Subscription-Tier")
            .stream()
            .anyMatch(tier -> tier.equalsIgnoreCase("premium") ||
                             tier.equalsIgnoreCase("enterprise"));
    }

    /**
     * Combines multiple predicates with AND logic
     */
    public static RequestPredicate allOf(RequestPredicate... predicates) {
        return request -> {
            for (RequestPredicate predicate : predicates) {
                if (!predicate.test(request)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Combines multiple predicates with OR logic
     */
    public static RequestPredicate anyOf(RequestPredicate... predicates) {
        return request -> {
            for (RequestPredicate predicate : predicates) {
                if (predicate.test(request)) {
                    return true;
                }
            }
            return false;
        };
    }
}
```

### Step 4.2: Create Routes Using Custom Predicates

```java
package com.example.functional.router;

import com.example.functional.dto.ErrorResponse;
import com.example.functional.predicate.CustomPredicates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class VersionedRouter {

    @Bean
    public RouterFunction<ServerResponse> versionedRoutes() {
        return route()
            .path("/api", builder -> builder

                // V2 API - matched first when version header is "2"
                .GET("/data",
                    CustomPredicates.apiVersion("2").and(accept(MediaType.APPLICATION_JSON)),
                    request -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "version", "2",
                            "format", "enhanced",
                            "data", Map.of("id", 1, "name", "Item", "metadata", Map.of("new", true))
                        )))

                // V1 API - default fallback
                .GET("/data",
                    accept(MediaType.APPLICATION_JSON),
                    request -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "version", "1",
                            "format", "legacy",
                            "data", Map.of("id", 1, "name", "Item")
                        )))

                // Premium-only endpoint
                .GET("/premium/features",
                    CustomPredicates.premiumTier(),
                    request -> ServerResponse.ok()
                        .bodyValue(Map.of(
                            "features", java.util.List.of("advanced-analytics", "priority-support", "custom-branding")
                        )))

                // Premium endpoint - access denied for non-premium
                .GET("/premium/features",
                    request -> ServerResponse.status(403)
                        .bodyValue(new ErrorResponse("FORBIDDEN", "Premium subscription required")))

                // Internal-only endpoint
                .GET("/internal/metrics",
                    CustomPredicates.internalNetwork(),
                    request -> ServerResponse.ok()
                        .bodyValue(Map.of(
                            "requests", 12345,
                            "errors", 42,
                            "avgLatencyMs", 23.5
                        )))

                // Internal endpoint - access denied for external
                .GET("/internal/metrics",
                    request -> ServerResponse.status(403)
                        .bodyValue(new ErrorResponse("FORBIDDEN", "Internal access only")))

                // Endpoint requiring API key
                .path("/protected", protectedBuilder -> protectedBuilder
                    .filter((req, next) -> {
                        if (CustomPredicates.hasApiKey().test(req)) {
                            return next.handle(req);
                        }
                        return ServerResponse.status(401)
                            .bodyValue(new ErrorResponse("UNAUTHORIZED", "API key required"));
                    })
                    .GET("/resource", request -> ServerResponse.ok()
                        .bodyValue(Map.of("secret", "data")))
                )
            )
            .build();
    }
}
```

### Step 4.3: Test Custom Predicates

```bash
# V1 API (no version header)
curl http://localhost:8080/api/data

# V2 API
curl -H "X-Api-Version: 2" http://localhost:8080/api/data

# Premium features - denied
curl http://localhost:8080/api/premium/features

# Premium features - allowed
curl -H "X-Subscription-Tier: premium" http://localhost:8080/api/premium/features

# Internal metrics (should work from localhost)
curl http://localhost:8080/api/internal/metrics

# Protected resource - denied
curl http://localhost:8080/api/protected/resource

# Protected resource - allowed
curl -H "X-Api-Key: my-secret-key" http://localhost:8080/api/protected/resource
```

---

## Part 5: Filters for Cross-Cutting Concerns (25 min)

### Step 5.1: Create Filter Utilities

```java
package com.example.functional.filter;

import com.example.functional.dto.ErrorResponse;
import com.example.functional.handler.ProductHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Filters {

    private static final Logger log = LoggerFactory.getLogger(Filters.class);

    /**
     * Adds a unique request ID to each request for tracing
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> requestIdFilter() {
        return (request, next) -> {
            String requestId = request.headers()
                .header("X-Request-Id")
                .stream()
                .findFirst()
                .orElseGet(() -> UUID.randomUUID().toString());

            ServerRequest mutatedRequest = ServerRequest.from(request)
                .attribute("requestId", requestId)
                .build();

            return next.handle(mutatedRequest)
                .map(response -> {
                    // Note: In production, use ServerResponse.from() properly
                    // This is simplified for the lab
                    return response;
                })
                .contextWrite(ctx -> ctx.put("requestId", requestId));
        };
    }

    /**
     * Logs incoming requests and outgoing responses with timing
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> loggingFilter() {
        return (request, next) -> {
            long startTime = System.currentTimeMillis();
            String method = request.method().name();
            String path = request.path();
            String requestId = request.attribute("requestId")
                .map(Object::toString)
                .orElse("unknown");

            log.info("[{}] --> {} {}", requestId, method, path);

            return next.handle(request)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[{}] <-- {} {} {} ({}ms)",
                        requestId, method, path, response.statusCode().value(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[{}] <-- {} {} ERROR ({}ms): {}",
                        requestId, method, path, duration, error.getMessage());
                });
        };
    }

    /**
     * Simple token-based authentication filter
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> authenticationFilter(
            Map<String, UserInfo> validTokens) {
        return (request, next) -> {
            String authHeader = request.headers()
                .header("Authorization")
                .stream()
                .findFirst()
                .orElse("");

            if (!authHeader.startsWith("Bearer ")) {
                return unauthorized("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            UserInfo user = validTokens.get(token);

            if (user == null) {
                return unauthorized("Invalid token");
            }

            ServerRequest authenticatedRequest = ServerRequest.from(request)
                .attribute("currentUser", user)
                .build();

            return next.handle(authenticatedRequest);
        };
    }

    /**
     * Role-based authorization filter
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> requireRole(String... roles) {
        return (request, next) -> {
            UserInfo user = request.attribute("currentUser")
                .filter(u -> u instanceof UserInfo)
                .map(u -> (UserInfo) u)
                .orElse(null);

            if (user == null) {
                return unauthorized("Authentication required");
            }

            for (String role : roles) {
                if (user.roles().contains(role)) {
                    return next.handle(request);
                }
            }

            return forbidden("Insufficient permissions. Required roles: " + String.join(", ", roles));
        };
    }

    /**
     * Simple rate limiting filter based on IP
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimitFilter(
            int maxRequestsPerMinute) {

        Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

        return (request, next) -> {
            String clientId = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");

            RateLimitBucket bucket = buckets.computeIfAbsent(clientId,
                k -> new RateLimitBucket(maxRequestsPerMinute));

            if (!bucket.tryAcquire()) {
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("RATE_LIMITED",
                        "Rate limit exceeded. Maximum " + maxRequestsPerMinute + " requests per minute."));
            }

            return next.handle(request);
        };
    }

    /**
     * Global error handling filter
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> errorHandlingFilter() {
        return (request, next) -> next.handle(request)
            .onErrorResume(ProductHandler.ValidationException.class, e ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("VALIDATION_ERROR", e.getMessage())))
            .onErrorResume(IllegalArgumentException.class, e ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("BAD_REQUEST", e.getMessage())))
            .onErrorResume(e -> {
                log.error("Unexpected error processing request", e);
                return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
            });
    }

    /**
     * Cache control header filter
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> cacheControl(
            String cacheControlValue) {
        return (request, next) -> next.handle(request)
            .map(response -> ServerResponse.from(response)
                .header("Cache-Control", cacheControlValue)
                .build());
    }

    /**
     * CORS filter for cross-origin requests
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> corsFilter(
            String allowedOrigin) {
        return (request, next) -> next.handle(request)
            .map(response -> ServerResponse.from(response)
                .header("Access-Control-Allow-Origin", allowedOrigin)
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Api-Key")
                .header("Access-Control-Max-Age", "3600")
                .build());
    }

    // Helper methods
    private static Mono<ServerResponse> unauthorized(String message) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ErrorResponse("UNAUTHORIZED", message));
    }

    private static Mono<ServerResponse> forbidden(String message) {
        return ServerResponse.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ErrorResponse("FORBIDDEN", message));
    }

    // Supporting classes
    public record UserInfo(String id, String username, java.util.Set<String> roles) {}

    private static class RateLimitBucket {
        private final int maxRequests;
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        public RateLimitBucket(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                windowStart = now;
                requestCount.set(0);
            }
            return requestCount.incrementAndGet() <= maxRequests;
        }
    }
}
```

### Step 5.2: Create a Filtered Router

```java
package com.example.functional.router;

import com.example.functional.filter.Filters;
import com.example.functional.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;
import java.util.Set;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class FilteredRouter {

    // Simulated token store
    private final Map<String, Filters.UserInfo> validTokens = Map.of(
        "user-token-123", new Filters.UserInfo("1", "alice", Set.of("USER")),
        "admin-token-456", new Filters.UserInfo("2", "bob", Set.of("USER", "ADMIN")),
        "superadmin-token-789", new Filters.UserInfo("3", "charlie", Set.of("USER", "ADMIN", "SUPERADMIN"))
    );

    @Bean
    public RouterFunction<ServerResponse> filteredProductRoutes(ProductHandler handler) {
        return route()
            // Public routes - no auth required, rate limited
            .path("/public", publicBuilder -> publicBuilder
                .filter(Filters.requestIdFilter())
                .filter(Filters.loggingFilter())
                .filter(Filters.errorHandlingFilter())
                .filter(Filters.rateLimitFilter(60)) // 60 requests per minute
                .filter(Filters.corsFilter("*"))
                .GET("/products", handler::listProducts)
                .GET("/products/{id}", handler::getProduct)
            )

            // Authenticated routes
            .path("/api", apiBuilder -> apiBuilder
                .filter(Filters.requestIdFilter())
                .filter(Filters.loggingFilter())
                .filter(Filters.errorHandlingFilter())
                .filter(Filters.authenticationFilter(validTokens))
                .filter(Filters.corsFilter("https://myapp.com"))

                // User-level access
                .GET("/products", handler::listProducts)
                .GET("/products/{id}", handler::getProduct)

                // Write operations require USER role
                .POST("/products",
                    contentType(MediaType.APPLICATION_JSON),
                    handler::createProduct)
                .PUT("/products/{id}",
                    contentType(MediaType.APPLICATION_JSON),
                    handler::updateProduct)

                // Admin-only operations
                .path("/admin", adminBuilder -> adminBuilder
                    .filter(Filters.requireRole("ADMIN"))
                    .DELETE("/products/{id}", handler::deleteProduct)
                    .GET("/stats", request -> ServerResponse.ok()
                        .bodyValue(Map.of(
                            "totalProducts", 100,
                            "totalOrders", 500,
                            "revenue", 50000.00
                        )))
                )
            )
            .build();
    }
}
```

### Step 5.3: Test the Filtered Routes

```bash
# Public endpoint - no auth needed
curl http://localhost:8080/public/products

# Try to exceed rate limit (run many times quickly)
for i in {1..70}; do curl -s http://localhost:8080/public/products > /dev/null; done
curl http://localhost:8080/public/products  # Should be rate limited

# Authenticated endpoint - no token
curl http://localhost:8080/api/products
# Response: 401 Unauthorized

# Authenticated endpoint - with token
curl -H "Authorization: Bearer user-token-123" http://localhost:8080/api/products

# Create product (requires auth)
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer user-token-123" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","price":99.99,"category":"Test","stockQuantity":10}'

# Admin endpoint with user token
curl -H "Authorization: Bearer user-token-123" http://localhost:8080/api/admin/stats
# Response: 403 Forbidden

# Admin endpoint with admin token
curl -H "Authorization: Bearer admin-token-456" http://localhost:8080/api/admin/stats
```

---

## Part 6: Organizing for Scale (15 min)

### Step 6.1: Create Domain-Specific Routers

Create a modular structure for a larger application:

```java
package com.example.functional.router;

import com.example.functional.handler.ProductHandler;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class ProductRouterV2 {

    private final ProductHandler handler;

    public ProductRouterV2(ProductHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns product routes to be composed with other routers
     */
    public RouterFunction<ServerResponse> routes() {
        return route()
            .path("/products", builder -> builder
                .GET("", accept(MediaType.APPLICATION_JSON), handler::listProducts)
                .GET("/{id}", accept(MediaType.APPLICATION_JSON), handler::getProduct)
                .POST("", contentType(MediaType.APPLICATION_JSON), handler::createProduct)
                .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateProduct)
                .DELETE("/{id}", handler::deleteProduct)
                .path("/{id}/stock", stockBuilder -> stockBuilder
                    .GET("", handler::getStock)
                    .PATCH("", contentType(MediaType.APPLICATION_JSON), handler::updateStock)
                )
            )
            .build();
    }
}
```

### Step 6.2: Create the Main Router Composer

```java
package com.example.functional.router;

import com.example.functional.dto.ErrorResponse;
import com.example.functional.filter.Filters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class MainRouter {

    @Bean
    @Primary
    public RouterFunction<ServerResponse> mainRoutes(
            ProductRouterV2 productRouter,
            DemoHandler demoHandler) {

        return route()
            // Health and info endpoints (always accessible)
            .GET("/health", request -> ServerResponse.ok()
                .bodyValue(Map.of("status", "UP")))
            .GET("/info", request -> ServerResponse.ok()
                .bodyValue(Map.of(
                    "app", "Functional Endpoints Lab",
                    "version", "1.0.0"
                )))

            // API v1 routes
            .path("/api/v1", v1Builder -> v1Builder
                .filter(Filters.requestIdFilter())
                .filter(Filters.loggingFilter())
                .filter(Filters.errorHandlingFilter())
                .add(productRouter.routes())
            )

            // Fallback for unmatched API routes
            .path("/api", apiBuilder -> apiBuilder
                .route(all(), request -> ServerResponse.notFound()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("NOT_FOUND",
                        "The requested resource was not found")))
            )

            // Global fallback
            .route(all(), request -> ServerResponse.notFound()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse("NOT_FOUND",
                    "Resource not found: " + request.path())))

            .build();
    }
}
```

### Step 6.3: Test the Organized Routes

```bash
# Health check
curl http://localhost:8080/health

# API v1 products
curl http://localhost:8080/api/v1/products

# Get specific product
curl http://localhost:8080/api/v1/products/{id}

# Non-existent API route
curl http://localhost:8080/api/v2/products
# Response: 404 Not Found

# Non-existent route
curl http://localhost:8080/unknown
# Response: 404 Not Found
```

---

## Part 7: Testing Functional Endpoints (20 min)

### Step 7.1: Unit Testing Handlers

Create `src/test/java/com/example/functional/handler/ProductHandlerTest.java`:

```java
package com.example.functional.handler;

import com.example.functional.domain.Product;
import com.example.functional.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductHandlerTest {

    @Mock
    private ProductRepository repository;

    private ProductHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductHandler(repository);
    }

    @Test
    void listProducts_returnsAllProducts() {
        // Given
        Product product1 = new Product("1", "Laptop", "Description",
            new BigDecimal("999.99"), "Electronics", 10);
        Product product2 = new Product("2", "Phone", "Description",
            new BigDecimal("599.99"), "Electronics", 20);
        when(repository.findAll()).thenReturn(Flux.just(product1, product2));

        MockServerRequest request = MockServerRequest.builder().build();

        // When
        Mono<ServerResponse> response = handler.listProducts(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.OK, serverResponse.statusCode());
            })
            .verifyComplete();
    }

    @Test
    void getProduct_whenExists_returnsProduct() {
        // Given
        Product product = new Product("1", "Laptop", "Description",
            new BigDecimal("999.99"), "Electronics", 10);
        when(repository.findById("1")).thenReturn(Mono.just(product));

        MockServerRequest request = MockServerRequest.builder()
            .pathVariable("id", "1")
            .build();

        // When
        Mono<ServerResponse> response = handler.getProduct(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.OK, serverResponse.statusCode());
            })
            .verifyComplete();
    }

    @Test
    void getProduct_whenNotExists_returnsNotFound() {
        // Given
        when(repository.findById("999")).thenReturn(Mono.empty());

        MockServerRequest request = MockServerRequest.builder()
            .pathVariable("id", "999")
            .build();

        // When
        Mono<ServerResponse> response = handler.getProduct(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.NOT_FOUND, serverResponse.statusCode());
            })
            .verifyComplete();
    }

    @Test
    void listProducts_withCategoryFilter_returnsFilteredProducts() {
        // Given
        Product product = new Product("1", "Laptop", "Description",
            new BigDecimal("999.99"), "Electronics", 10);
        when(repository.findByCategory("Electronics")).thenReturn(Flux.just(product));

        MockServerRequest request = MockServerRequest.builder()
            .queryParam("category", "Electronics")
            .build();

        // When
        Mono<ServerResponse> response = handler.listProducts(request);

        // Then
        StepVerifier.create(response)
            .assertNext(serverResponse -> {
                assertEquals(HttpStatus.OK, serverResponse.statusCode());
            })
            .verifyComplete();
    }
}
```

### Step 7.2: Integration Testing with WebTestClient

Create `src/test/java/com/example/functional/router/ProductRoutesIntegrationTest.java`:

```java
package com.example.functional.router;

import com.example.functional.domain.Product;
import com.example.functional.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductRoutesIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpoint_returnsUp() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void listProducts_returnsProducts() {
        webTestClient.get()
            .uri("/api/v1/products")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Product.class)
            .hasSize(5); // From sample data
    }

    @Test
    void listProducts_withCategoryFilter_returnsFilteredProducts() {
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/products")
                .queryParam("category", "Electronics")
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Product.class)
            .value(products -> {
                products.forEach(p ->
                    org.junit.jupiter.api.Assertions.assertEquals("Electronics", p.getCategory()));
            });
    }

    @Test
    void createProduct_withValidData_returnsCreated() {
        ProductRequest request = new ProductRequest(
            "Test Product",
            "Test Description",
            new BigDecimal("49.99"),
            "Test",
            100
        );

        webTestClient.post()
            .uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Product")
            .jsonPath("$.price").isEqualTo(49.99);
    }

    @Test
    void createProduct_withInvalidData_returnsBadRequest() {
        ProductRequest request = new ProductRequest(
            "",  // Invalid: blank name
            "Description",
            new BigDecimal("-10"),  // Invalid: negative price
            "Test",
            100
        );

        webTestClient.post()
            .uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void getProduct_whenNotExists_returnsNotFound() {
        webTestClient.get()
            .uri("/api/v1/products/non-existent-id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void unknownRoute_returnsNotFound() {
        webTestClient.get()
            .uri("/api/v1/unknown")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }
}
```

### Step 7.3: Testing Predicates

Create `src/test/java/com/example/functional/predicate/CustomPredicatesTest.java`:

```java
package com.example.functional.predicate;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.RequestPredicate;

import static org.junit.jupiter.api.Assertions.*;

class CustomPredicatesTest {

    @Test
    void apiVersion_matchesCorrectVersion() {
        RequestPredicate predicate = CustomPredicates.apiVersion("2");

        MockServerRequest matchingRequest = MockServerRequest.builder()
            .header("X-Api-Version", "2")
            .build();

        MockServerRequest nonMatchingRequest = MockServerRequest.builder()
            .header("X-Api-Version", "1")
            .build();

        MockServerRequest noHeaderRequest = MockServerRequest.builder().build();

        assertTrue(predicate.test(matchingRequest));
        assertFalse(predicate.test(nonMatchingRequest));
        assertFalse(predicate.test(noHeaderRequest));
    }

    @Test
    void hasApiKey_matchesWhenPresent() {
        RequestPredicate predicate = CustomPredicates.hasApiKey();

        MockServerRequest withKey = MockServerRequest.builder()
            .header("X-Api-Key", "some-key")
            .build();

        MockServerRequest withEmptyKey = MockServerRequest.builder()
            .header("X-Api-Key", "")
            .build();

        MockServerRequest withoutKey = MockServerRequest.builder().build();

        assertTrue(predicate.test(withKey));
        assertFalse(predicate.test(withEmptyKey));
        assertFalse(predicate.test(withoutKey));
    }

    @Test
    void hasQueryParam_matchesWhenPresent() {
        RequestPredicate predicate = CustomPredicates.hasQueryParam("filter");

        MockServerRequest withParam = MockServerRequest.builder()
            .queryParam("filter", "active")
            .build();

        MockServerRequest withoutParam = MockServerRequest.builder().build();

        assertTrue(predicate.test(withParam));
        assertFalse(predicate.test(withoutParam));
    }

    @Test
    void queryParamEquals_matchesExactValue() {
        RequestPredicate predicate = CustomPredicates.queryParamEquals("status", "active");

        MockServerRequest matching = MockServerRequest.builder()
            .queryParam("status", "active")
            .build();

        MockServerRequest notMatching = MockServerRequest.builder()
            .queryParam("status", "inactive")
            .build();

        assertTrue(predicate.test(matching));
        assertFalse(predicate.test(notMatching));
    }

    @Test
    void premiumTier_matchesPremiumAndEnterprise() {
        RequestPredicate predicate = CustomPredicates.premiumTier();

        MockServerRequest premium = MockServerRequest.builder()
            .header("X-Subscription-Tier", "premium")
            .build();

        MockServerRequest enterprise = MockServerRequest.builder()
            .header("X-Subscription-Tier", "enterprise")
            .build();

        MockServerRequest basic = MockServerRequest.builder()
            .header("X-Subscription-Tier", "basic")
            .build();

        assertTrue(predicate.test(premium));
        assertTrue(predicate.test(enterprise));
        assertFalse(predicate.test(basic));
    }
}
```

### Step 7.4: Run the Tests

```bash
mvn test
```

---

## Part 8: Reflection (5 min)

### Questions to Consider

1. **When would you choose functional endpoints over annotated controllers?**
   - Consider: Complex routing logic, dynamic routes, testing isolation, functional programming preference

2. **How do filters in functional endpoints compare to Spring's WebFilter?**
   - Filters are scoped to specific routes
   - Easier to compose and test
   - More explicit about what's filtered

3. **What are the trade-offs of custom predicates?**
   - Pros: Flexible, testable, reusable
   - Cons: More code, less IDE support

4. **How would you organize a large application with 50+ endpoints?**
   - Domain-based packages
   - Separate router beans per domain
   - Central composition in main router

5. **What did you find easier or harder compared to annotated controllers?**
   - Consider: Boilerplate, testing, understanding flow, IDE support

### Key Takeaways

1. **Functional endpoints are explicit** - No annotation magic, everything is visible code

2. **Handlers are just functions** - Easy to test in isolation without Spring context

3. **Predicates enable complex routing** - Build sophisticated matching logic from simple building blocks

4. **Filters replace @ControllerAdvice** - Apply cross-cutting concerns to specific route groups

5. **Both styles can coexist** - Use annotated controllers for simple CRUD, functional for complex routing

### Next Steps

- Try converting one of your existing annotated controllers to functional endpoints
- Experiment with combining both styles in the same application
- Build a more complex predicate that matches requests based on multiple criteria
- Implement a caching filter that stores responses temporarily

---

## Summary

In this lab, you:

1. Created a complete CRUD API using functional endpoints
2. Explored `ServerRequest` for extracting request data
3. Built responses with `ServerResponse`
4. Created custom predicates for API versioning and access control
5. Implemented filters for logging, authentication, rate limiting, and error handling
6. Organized routes in a scalable structure
7. Tested handlers and routes with unit and integration tests

Functional endpoints provide a powerful alternative to annotated controllers, especially when you need explicit control over routing and want to embrace functional programming patterns.
