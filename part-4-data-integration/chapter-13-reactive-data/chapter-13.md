# Chapter 13: Reactive Data Access Patterns

> "Data is not information, information is not knowledge, knowledge is not understanding, understanding is not wisdom."
> — Clifford Stoll

## Introduction

Throughout this book, we've built reactive web applications that handle requests without blocking. But there's an elephant in the room: **the database**. Traditional JDBC is fundamentally blocking—every database call ties up a thread waiting for the response.

This creates a critical problem. If your web layer is reactive but your data layer is blocking, you've simply moved the bottleneck. One blocking database call in a reactive pipeline negates all the benefits we've worked so hard to achieve.

This chapter tackles the database challenge head-on. We'll explore truly reactive data access patterns that maintain the non-blocking promise from HTTP request to database and back.

## 13.1 The Database Dilemma

### Why JDBC is Blocking

JDBC was designed in 1997, when the thread-per-request model was the standard. Every operation blocks the calling thread:

```java
// Every line here blocks the thread
Connection conn = dataSource.getConnection();  // Blocks waiting for connection
PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
stmt.setLong(1, userId);
ResultSet rs = stmt.executeQuery();  // Blocks waiting for database
while (rs.next()) {  // Blocks for each row
    // Process row
}
```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    JDBC: THE BLOCKING REALITY                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread                                                                 │
│     │                                                                    │
│     │──── getConnection() ────────────────▶│                            │
│     │          BLOCKED                      │  Connection Pool           │
│     │◀─────────────────────────────────────│                            │
│     │                                                                    │
│     │──── executeQuery() ─────────────────▶│                            │
│     │          BLOCKED                      │  Database                  │
│     │          (10-100ms)                   │                            │
│     │◀─────────────────────────────────────│                            │
│     │                                                                    │
│   Thread does NOTHING while waiting                                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### The Reactive Mismatch

When you combine reactive WebFlux with blocking JDBC:

```java
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    // THIS IS WRONG - blocks the event loop thread!
    User user = userRepository.findById(id).orElse(null);
    return Mono.justOrEmpty(user);
}
```

This is worse than using Spring MVC! In MVC, you have dedicated threads for blocking. In WebFlux, you're blocking the precious few event loop threads, potentially bringing your entire application to a halt.

### Options for Reactive Data Access

| Option | Description | Best For |
|--------|-------------|----------|
| **R2DBC** | Reactive Relational Database Connectivity | SQL databases needing reactive |
| **Reactive MongoDB** | Native reactive driver | Document databases |
| **Reactive Redis** | Lettuce-based reactive | Caching, sessions |
| **Reactive Elasticsearch** | Native reactive client | Search operations |
| **Reactive Cassandra** | DataStax driver | Wide-column stores |

## 13.2 R2DBC: Reactive Relational Database Connectivity

### What is R2DBC?

R2DBC is a specification for reactive database access, similar to how JDBC is a specification for blocking access. It was designed from the ground up for non-blocking I/O.

Key differences from JDBC:

| JDBC | R2DBC |
|------|-------|
| `Connection` | `Connection` (reactive) |
| `Statement` | `Statement` (reactive) |
| `ResultSet` (blocking iterator) | `Result` (Publisher of rows) |
| Blocks on every operation | Returns Publishers |
| Thread-per-connection | Few threads, many connections |

### Driver Availability

R2DBC drivers exist for major databases:

- **PostgreSQL**: `r2dbc-postgresql` (most mature)
- **MySQL/MariaDB**: `r2dbc-mysql`, `r2dbc-mariadb`
- **Microsoft SQL Server**: `r2dbc-mssql`
- **H2**: `r2dbc-h2` (great for testing)
- **Oracle**: `oracle-r2dbc`

### Adding R2DBC to Your Project

```xml
<dependencies>
    <!-- Spring Data R2DBC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>

    <!-- PostgreSQL R2DBC Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>r2dbc-postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- H2 for testing -->
    <dependency>
        <groupId>io.r2dbc</groupId>
        <artifactId>r2dbc-h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Configuration

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: postgres
    password: secret
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
```

### Honest Assessment: R2DBC Limitations

Before diving in, understand the trade-offs:

**Advantages:**
- True non-blocking from connection to result
- Backpressure support
- Efficient resource utilization

**Limitations:**
- No lazy loading (no entities with relationships auto-fetched)
- No JPA/Hibernate (no @OneToMany, @ManyToOne)
- Simpler mapping (more manual work for complex objects)
- Fewer features than JDBC ecosystem
- Some databases have less mature drivers

**When R2DBC Makes Sense:**
- High-concurrency read-heavy workloads
- Simple to moderate data models
- Microservices with focused data needs
- New greenfield projects

**When to Stick with JDBC:**
- Complex domain models with many relationships
- Heavy use of JPA features (lazy loading, caching)
- Existing applications with extensive JPA code
- Team expertise in JPA/Hibernate

## 13.3 Spring Data R2DBC

### The Repository Pattern

Spring Data R2DBC provides the familiar repository abstraction:

```java
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Flux<User> findByLastName(String lastName);

    Mono<User> findByEmail(String email);

    @Query("SELECT * FROM users WHERE created_at > :since")
    Flux<User> findRecentUsers(@Param("since") LocalDateTime since);
}
```

### Entity Definition

R2DBC entities are simpler than JPA entities:

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Table("users")
public class User {

    @Id
    private Long id;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    private String email;

    @Column("created_at")
    private LocalDateTime createdAt;

    // Constructors, getters, setters

    public User() {}

    public User(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    // ... getters and setters
}
```

**Note:** No `@Entity`, no `@OneToMany`, no `@ManyToOne`. R2DBC entities are flat.

### Basic CRUD Operations

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Create
    public Mono<User> createUser(String firstName, String lastName, String email) {
        User user = new User(firstName, lastName, email);
        return userRepository.save(user);
    }

    // Read
    public Mono<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    public Flux<User> findByLastName(String lastName) {
        return userRepository.findByLastName(lastName);
    }

    // Update
    public Mono<User> updateEmail(Long id, String newEmail) {
        return userRepository.findById(id)
            .flatMap(user -> {
                user.setEmail(newEmail);
                return userRepository.save(user);
            });
    }

    // Delete
    public Mono<Void> deleteUser(Long id) {
        return userRepository.deleteById(id);
    }
}
```

### Custom Queries

```java
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    // Derived queries
    Flux<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Mono<Long> countByStatus(OrderStatus status);

    // Custom queries
    @Query("SELECT * FROM orders WHERE total_amount > :minAmount AND status = :status")
    Flux<Order> findLargeOrdersByStatus(
        @Param("minAmount") BigDecimal minAmount,
        @Param("status") String status
    );

    // Modifying queries
    @Modifying
    @Query("UPDATE orders SET status = :status WHERE id = :id")
    Mono<Integer> updateStatus(@Param("id") Long id, @Param("status") String status);

    // Native queries with joins
    @Query("""
        SELECT o.*, u.email as user_email
        FROM orders o
        JOIN users u ON o.user_id = u.id
        WHERE o.created_at > :since
        """)
    Flux<OrderWithUser> findRecentOrdersWithUser(@Param("since") LocalDateTime since);
}
```

### Handling Relationships (Without JPA Magic)

Since R2DBC doesn't support lazy loading, handle relationships explicitly:

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;

    // Fetch order with related data
    public Mono<OrderDetails> getOrderDetails(Long orderId) {
        return orderRepository.findById(orderId)
            .flatMap(order -> Mono.zip(
                Mono.just(order),
                userRepository.findById(order.getUserId()),
                orderItemRepository.findByOrderId(orderId).collectList()
            ))
            .map(tuple -> new OrderDetails(
                tuple.getT1(),  // order
                tuple.getT2(),  // user
                tuple.getT3()   // items
            ));
    }

    // Batch fetch for multiple orders
    public Flux<OrderDetails> getOrdersWithDetails(List<Long> orderIds) {
        return orderRepository.findAllById(orderIds)
            .flatMap(order ->
                Mono.zip(
                    Mono.just(order),
                    userRepository.findById(order.getUserId()),
                    orderItemRepository.findByOrderId(order.getId()).collectList()
                )
                .map(tuple -> new OrderDetails(tuple.getT1(), tuple.getT2(), tuple.getT3()))
            );
    }
}

public record OrderDetails(Order order, User user, List<OrderItem> items) {}
```

### DatabaseClient for Low-Level Access

For complex queries, use `DatabaseClient`:

```java
@Service
public class ReportService {

    private final DatabaseClient databaseClient;

    public ReportService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<SalesReport> getMonthlySalesReport(int year) {
        return databaseClient.sql("""
            SELECT
                DATE_TRUNC('month', created_at) as month,
                COUNT(*) as order_count,
                SUM(total_amount) as total_sales,
                AVG(total_amount) as average_order
            FROM orders
            WHERE EXTRACT(YEAR FROM created_at) = :year
            GROUP BY DATE_TRUNC('month', created_at)
            ORDER BY month
            """)
            .bind("year", year)
            .map((row, metadata) -> new SalesReport(
                row.get("month", LocalDateTime.class),
                row.get("order_count", Long.class),
                row.get("total_sales", BigDecimal.class),
                row.get("average_order", BigDecimal.class)
            ))
            .all();
    }

    public Mono<Long> bulkUpdatePrices(BigDecimal percentage) {
        return databaseClient.sql("""
            UPDATE products
            SET price = price * (1 + :percentage / 100),
                updated_at = NOW()
            WHERE active = true
            """)
            .bind("percentage", percentage)
            .fetch()
            .rowsUpdated();
    }
}
```

## 13.4 Reactive MongoDB

### Natural Fit for Reactive

MongoDB's document model and wire protocol make it a natural fit for reactive access. The reactive streams driver was one of the first mature reactive database drivers.

### Setup

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
</dependency>
```

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb
```

### Document Definition

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed
    private String sku;

    private String name;

    private String description;

    private BigDecimal price;

    @Field("category_id")
    private String categoryId;

    private List<String> tags;

    private Map<String, String> attributes;

    @Field("created_at")
    private Instant createdAt;

    // Constructors, getters, setters
}
```

### Reactive Repository

```java
public interface ProductRepository extends ReactiveMongoRepository<Product, String> {

    Flux<Product> findByCategoryId(String categoryId);

    Flux<Product> findByTagsContaining(String tag);

    Flux<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Flux<Product> searchByName(String namePattern);

    @Query("{ 'attributes.?0': ?1 }")
    Flux<Product> findByAttribute(String key, String value);
}
```

### ReactiveMongoTemplate for Complex Operations

```java
@Service
public class ProductSearchService {

    private final ReactiveMongoTemplate mongoTemplate;

    public ProductSearchService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Flux<Product> search(ProductSearchCriteria criteria) {
        Query query = new Query();

        if (criteria.getCategoryId() != null) {
            query.addCriteria(Criteria.where("categoryId").is(criteria.getCategoryId()));
        }

        if (criteria.getMinPrice() != null) {
            query.addCriteria(Criteria.where("price").gte(criteria.getMinPrice()));
        }

        if (criteria.getMaxPrice() != null) {
            query.addCriteria(Criteria.where("price").lte(criteria.getMaxPrice()));
        }

        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").all(criteria.getTags()));
        }

        if (criteria.getSearchText() != null) {
            query.addCriteria(Criteria.where("name").regex(criteria.getSearchText(), "i"));
        }

        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.limit(criteria.getLimit());
        query.skip(criteria.getOffset());

        return mongoTemplate.find(query, Product.class);
    }

    public Flux<CategoryStats> getCategoryStats() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group("categoryId")
                .count().as("productCount")
                .avg("price").as("averagePrice")
                .min("price").as("minPrice")
                .max("price").as("maxPrice"),
            Aggregation.project()
                .and("_id").as("categoryId")
                .andInclude("productCount", "averagePrice", "minPrice", "maxPrice")
        );

        return mongoTemplate.aggregate(aggregation, "products", CategoryStats.class);
    }
}
```

### Change Streams for Real-Time Updates

MongoDB supports watching for changes reactively:

```java
@Service
public class ProductChangeWatcher {

    private final ReactiveMongoTemplate mongoTemplate;

    public Flux<Product> watchProductChanges() {
        return mongoTemplate.changeStream(Product.class)
            .watchCollection("products")
            .listen()
            .map(ChangeStreamEvent::getBody);
    }

    public Flux<Product> watchCategoryChanges(String categoryId) {
        return mongoTemplate.changeStream(Product.class)
            .watchCollection("products")
            .filter(Criteria.where("categoryId").is(categoryId))
            .listen()
            .map(ChangeStreamEvent::getBody);
    }
}
```

## 13.5 Reactive Redis

### Use Cases for Reactive Redis

- **Caching**: Fast lookups with reactive backpressure
- **Sessions**: Distributed session storage
- **Rate limiting**: Request counters with TTL
- **Pub/Sub**: Real-time messaging
- **Leaderboards**: Sorted sets for rankings

### Setup

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

### ReactiveRedisTemplate Configuration

```java
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(Object.class);

        RedisSerializationContext<String, Object> context =
            RedisSerializationContext.<String, Object>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(serializer)
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisTemplate<>(connectionFactory,
            RedisSerializationContext.string());
    }
}
```

### Reactive Caching Service

```java
@Service
public class ReactiveCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Duration defaultTtl = Duration.ofMinutes(30);

    public ReactiveCacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public <T> Mono<T> getOrFetch(String key, Class<T> type, Mono<T> fetchOperation) {
        return redisTemplate.opsForValue()
            .get(key)
            .cast(type)
            .switchIfEmpty(
                fetchOperation.flatMap(value ->
                    redisTemplate.opsForValue()
                        .set(key, value, defaultTtl)
                        .thenReturn(value)
                )
            );
    }

    public <T> Mono<T> get(String key, Class<T> type) {
        return redisTemplate.opsForValue()
            .get(key)
            .cast(type);
    }

    public Mono<Boolean> set(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Mono<Boolean> delete(String key) {
        return redisTemplate.delete(key).map(count -> count > 0);
    }

    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key);
    }
}
```

### Rate Limiting with Redis

```java
@Service
public class RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAllowed(String clientId, int maxRequests, Duration window) {
        String key = "rate_limit:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        return redisTemplate.opsForZSet()
            // Remove old entries
            .removeRangeByScore(key, Range.closed(0.0, (double) windowStart))
            // Count recent requests
            .then(redisTemplate.opsForZSet().count(key,
                Range.closed((double) windowStart, (double) now)))
            .flatMap(count -> {
                if (count < maxRequests) {
                    // Add current request
                    return redisTemplate.opsForZSet()
                        .add(key, String.valueOf(now), now)
                        .then(redisTemplate.expire(key, window))
                        .thenReturn(true);
                }
                return Mono.just(false);
            });
    }
}
```

### Redis Pub/Sub for Real-Time Events

```java
@Service
public class RedisEventPublisher {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Mono<Long> publishEvent(String channel, Object event) {
        return redisTemplate.convertAndSend(channel, event);
    }
}

@Service
public class RedisEventSubscriber {

    private final ReactiveRedisConnectionFactory connectionFactory;

    public Flux<Message<String, String>> subscribe(String... channels) {
        return connectionFactory.getReactiveConnection()
            .pubSubCommands()
            .subscribe(Arrays.stream(channels)
                .map(c -> ByteBuffer.wrap(c.getBytes()))
                .toArray(ByteBuffer[]::new))
            .thenMany(connectionFactory.getReactiveConnection()
                .pubSubCommands()
                .receive());
    }
}
```

## 13.6 Connection Pool Management

### R2DBC Connection Pool

R2DBC uses connection pools just like JDBC, but non-blocking:

```yaml
spring:
  r2dbc:
    url: r2dbc:pool:postgresql://localhost:5432/mydb
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      max-life-time: 60m
      max-create-connection-time: 30s
      validation-query: SELECT 1
```

### Understanding Pool Sizing

Unlike JDBC where you might need hundreds of connections, reactive access needs fewer:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONNECTION POOL: JDBC vs R2DBC                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   JDBC (Blocking)                                                        │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │  Thread 1 ─── Connection 1 ─── WAITING for query              │     │
│   │  Thread 2 ─── Connection 2 ─── WAITING for query              │     │
│   │  Thread 3 ─── Connection 3 ─── WAITING for query              │     │
│   │  ...                                                           │     │
│   │  Thread N ─── Connection N ─── WAITING for query              │     │
│   │                                                                │     │
│   │  100 concurrent users = 100 connections needed                 │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                                                                          │
│   R2DBC (Non-Blocking)                                                   │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │  Event Loop ─── Connection 1 ─── Query 1, Query 5, Query 9... │     │
│   │             ─── Connection 2 ─── Query 2, Query 6, Query 10...│     │
│   │             ─── Connection 3 ─── Query 3, Query 7, Query 11...│     │
│   │             ─── Connection 4 ─── Query 4, Query 8, Query 12...│     │
│   │                                                                │     │
│   │  100 concurrent users = 10-20 connections often sufficient    │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Pool Metrics

Monitor your connection pool:

```java
@Configuration
public class R2dbcMetricsConfig {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer metricsCustomizer(
            MeterRegistry meterRegistry) {
        return builder -> builder.option(PoolingConnectionFactoryProvider.POOL_METRICS_RECORDER,
            new MicrometerPoolMetricsRecorder(meterRegistry, "r2dbc.pool"));
    }
}
```

## 13.7 Best Practices

### 1. Always Stream Large Results

Don't collect everything into memory:

```java
// BAD - loads all into memory
public Mono<List<User>> getAllUsers() {
    return userRepository.findAll().collectList();  // Could be millions!
}

// GOOD - streams with backpressure
public Flux<User> getAllUsers() {
    return userRepository.findAll();
}

// GOOD - paginate for UI
public Mono<Page<User>> getUsers(int page, int size) {
    return userRepository.findBy(PageRequest.of(page, size))
        .collectList()
        .zipWith(userRepository.count())
        .map(tuple -> new PageImpl<>(tuple.getT1(), PageRequest.of(page, size), tuple.getT2()));
}
```

### 2. Batch Operations

```java
// BAD - N+1 queries
public Flux<UserWithOrders> getUsersWithOrders(List<Long> userIds) {
    return Flux.fromIterable(userIds)
        .flatMap(id -> userRepository.findById(id)
            .flatMap(user -> orderRepository.findByUserId(id)
                .collectList()
                .map(orders -> new UserWithOrders(user, orders))));
}

// GOOD - batch fetch
public Flux<UserWithOrders> getUsersWithOrders(List<Long> userIds) {
    Mono<Map<Long, User>> usersMap = userRepository.findAllById(userIds)
        .collectMap(User::getId);

    Mono<Map<Long, List<Order>>> ordersMap = orderRepository.findByUserIdIn(userIds)
        .collectList()
        .map(orders -> orders.stream()
            .collect(Collectors.groupingBy(Order::getUserId)));

    return Mono.zip(usersMap, ordersMap)
        .flatMapMany(tuple -> Flux.fromIterable(userIds)
            .map(id -> new UserWithOrders(
                tuple.getT1().get(id),
                tuple.getT2().getOrDefault(id, List.of())
            )));
}
```

### 3. Handle Empty Results Gracefully

```java
public Mono<User> getUser(Long id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
}

public Mono<ResponseEntity<User>> getUserResponse(Long id) {
    return userRepository.findById(id)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

### 4. Resource Cleanup

```java
@Service
public class DataExportService {

    private final DatabaseClient databaseClient;

    public Flux<DataBuffer> exportLargeDataset(String query) {
        return databaseClient.sql(query)
            .fetch()
            .all()
            .map(this::toJson)
            .map(this::toDataBuffer)
            .doOnCancel(() -> log.info("Export cancelled"))
            .doOnComplete(() -> log.info("Export completed"))
            .doOnError(e -> log.error("Export failed", e));
    }
}
```

### 5. Testing with Testcontainers

```java
@Testcontainers
@SpringBootTest
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_returnsUser() {
        User user = new User("John", "Doe", "john@example.com");

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.findByEmail("john@example.com"))
        )
        .assertNext(found -> {
            assertThat(found.getFirstName()).isEqualTo("John");
            assertThat(found.getEmail()).isEqualTo("john@example.com");
        })
        .verifyComplete();
    }
}
```

## Summary

Reactive data access is the final piece of the fully-reactive puzzle. Key takeaways:

| Technology | Use Case | Maturity |
|------------|----------|----------|
| **R2DBC** | Relational databases | Production-ready |
| **Reactive MongoDB** | Document stores | Very mature |
| **Reactive Redis** | Caching, sessions | Very mature |
| **Reactive Elasticsearch** | Search | Mature |
| **Reactive Cassandra** | Wide-column | Mature |

**Remember:**

1. **One blocking call breaks everything**. A single JDBC query in a reactive chain defeats the purpose of going reactive.

2. **R2DBC is not JPA**. Don't expect lazy loading, complex mappings, or the full ORM feature set. It's intentionally simpler.

3. **Fetch relationships explicitly**. Without lazy loading, use `Mono.zip()` and `flatMap()` to assemble complex objects.

4. **Size pools appropriately**. Reactive access needs fewer connections than blocking access.

5. **Stream, don't collect**. Return `Flux<T>` instead of `Mono<List<T>>` for large results.

The database is often the bottleneck in any application. By making data access reactive, you've removed the last blocking obstacle between your users and truly scalable, responsive applications.
