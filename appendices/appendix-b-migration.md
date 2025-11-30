# Appendix B: From Blocking to Reactive - Migration Guide

This appendix provides practical guidance for migrating existing Spring MVC applications to Spring WebFlux.

## Migration Strategies

### Strategy 1: Strangler Fig Pattern

Gradually replace blocking components with reactive ones:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    STRANGLER FIG PATTERN                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Phase 1: Coexistence                                                   │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │  Load Balancer                                                 │     │
│   │       │                                                        │     │
│   │       ├──▶ Spring MVC (existing)                              │     │
│   │       │    └── /api/legacy/*                                  │     │
│   │       │                                                        │     │
│   │       └──▶ Spring WebFlux (new)                               │     │
│   │            └── /api/new/*                                     │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                                                                          │
│   Phase 2: Gradual Migration                                            │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │  More endpoints moved to WebFlux                               │     │
│   │  MVC handles only critical legacy paths                       │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                                                                          │
│   Phase 3: Complete Migration                                           │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │  All endpoints on WebFlux                                      │     │
│   │  MVC decommissioned                                           │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Strategy 2: Inside-Out Migration

Start from the data layer and work outward:

```
1. Database Layer: JDBC → R2DBC
2. Repository Layer: JpaRepository → R2dbcRepository
3. Service Layer: Blocking → Reactive
4. Controller Layer: MVC → WebFlux
5. External Clients: RestTemplate → WebClient
```

### Strategy 3: Outside-In Migration

Start from the web layer and work inward:

```
1. Controllers: Return Mono/Flux (wrap blocking in boundedElastic)
2. External Clients: RestTemplate → WebClient
3. Services: Gradually convert to reactive
4. Repositories: JDBC → R2DBC (last, most impactful)
```

## Common Conversions

### Controller Conversions

**Before (Spring MVC):**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
}
```

**After (Spring WebFlux):**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody User user) {
        return userService.save(user);
    }
}
```

### Service Conversions

**Before (Blocking Service):**
```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public User createUser(CreateUserRequest request) {
        // Validate
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        // Save
        User user = new User(request.getName(), request.getEmail());
        User savedUser = userRepository.save(user);

        // Send welcome email
        emailService.sendWelcomeEmail(savedUser);

        return savedUser;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }
}
```

**After (Reactive Service):**
```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public Mono<User> createUser(CreateUserRequest request) {
        return userRepository.existsByEmail(request.getEmail())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new DuplicateEmailException());
                }
                User user = new User(request.getName(), request.getEmail());
                return userRepository.save(user);
            })
            .flatMap(savedUser ->
                emailService.sendWelcomeEmail(savedUser)
                    .thenReturn(savedUser)
            );
    }

    public Mono<User> findById(Long id) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }

    public Flux<User> findAll() {
        return userRepository.findAll();
    }
}
```

### Repository Conversions

**Before (JPA Repository):**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByActiveTrue();
}
```

**After (R2DBC Repository):**
```java
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByEmail(String email);
    Mono<Boolean> existsByEmail(String email);
    Flux<User> findByActiveTrue();
}
```

### HTTP Client Conversions

**Before (RestTemplate):**
```java
@Service
public class ExternalApiService {

    private final RestTemplate restTemplate;

    public UserDto fetchUser(Long id) {
        ResponseEntity<UserDto> response = restTemplate.getForEntity(
            "http://api.example.com/users/{id}",
            UserDto.class,
            id
        );
        return response.getBody();
    }

    public List<UserDto> fetchUsers() {
        ResponseEntity<List<UserDto>> response = restTemplate.exchange(
            "http://api.example.com/users",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<UserDto>>() {}
        );
        return response.getBody();
    }

    public UserDto createUser(CreateUserRequest request) {
        return restTemplate.postForObject(
            "http://api.example.com/users",
            request,
            UserDto.class
        );
    }
}
```

**After (WebClient):**
```java
@Service
public class ExternalApiService {

    private final WebClient webClient;

    public ExternalApiService(WebClient.Builder builder) {
        this.webClient = builder
            .baseUrl("http://api.example.com")
            .build();
    }

    public Mono<UserDto> fetchUser(Long id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(UserDto.class);
    }

    public Flux<UserDto> fetchUsers() {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlux(UserDto.class);
    }

    public Mono<UserDto> createUser(CreateUserRequest request) {
        return webClient.post()
            .uri("/users")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(UserDto.class);
    }
}
```

## Handling Blocking Code During Migration

### Wrapping Legacy Blocking Services

```java
@Service
public class HybridUserService {

    private final LegacyUserService legacyService;  // Blocking
    private final Scheduler blockingScheduler;

    public HybridUserService(LegacyUserService legacyService) {
        this.legacyService = legacyService;
        this.blockingScheduler = Schedulers.boundedElastic();
    }

    public Mono<User> findById(Long id) {
        return Mono.fromCallable(() -> legacyService.findById(id))
            .subscribeOn(blockingScheduler);
    }

    public Flux<User> findAll() {
        return Mono.fromCallable(() -> legacyService.findAll())
            .subscribeOn(blockingScheduler)
            .flatMapMany(Flux::fromIterable);
    }
}
```

### JDBC Bridge Pattern

```java
@Component
public class JdbcToReactiveAdapter {

    private final JdbcTemplate jdbcTemplate;
    private final Scheduler scheduler = Schedulers.boundedElastic();

    public <T> Mono<T> queryForObject(String sql, Class<T> type, Object... args) {
        return Mono.fromCallable(() ->
            jdbcTemplate.queryForObject(sql, type, args)
        ).subscribeOn(scheduler);
    }

    public <T> Flux<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return Mono.fromCallable(() ->
            jdbcTemplate.query(sql, mapper, args)
        )
        .subscribeOn(scheduler)
        .flatMapMany(Flux::fromIterable);
    }

    public Mono<Integer> update(String sql, Object... args) {
        return Mono.fromCallable(() ->
            jdbcTemplate.update(sql, args)
        ).subscribeOn(scheduler);
    }
}
```

## Control Flow Conversions

### If-Else Patterns

**Before:**
```java
public User processUser(Long id) {
    User user = userRepository.findById(id);
    if (user == null) {
        user = createDefaultUser();
    }
    if (user.isActive()) {
        sendNotification(user);
    }
    return user;
}
```

**After:**
```java
public Mono<User> processUser(Long id) {
    return userRepository.findById(id)
        .switchIfEmpty(createDefaultUser())
        .flatMap(user -> {
            if (user.isActive()) {
                return sendNotification(user).thenReturn(user);
            }
            return Mono.just(user);
        });
}

// Or using filter:
public Mono<User> processUser(Long id) {
    return userRepository.findById(id)
        .switchIfEmpty(createDefaultUser())
        .flatMap(user ->
            Mono.just(user)
                .filter(User::isActive)
                .flatMap(u -> sendNotification(u).thenReturn(u))
                .defaultIfEmpty(user)
        );
}
```

### Loop Patterns

**Before:**
```java
public List<Result> processAll(List<Long> ids) {
    List<Result> results = new ArrayList<>();
    for (Long id : ids) {
        User user = userRepository.findById(id);
        if (user != null) {
            Result result = processUser(user);
            results.add(result);
        }
    }
    return results;
}
```

**After:**
```java
public Flux<Result> processAll(List<Long> ids) {
    return Flux.fromIterable(ids)
        .flatMap(id -> userRepository.findById(id))
        .flatMap(this::processUser);
}

// With concurrency control:
public Flux<Result> processAll(List<Long> ids) {
    return Flux.fromIterable(ids)
        .flatMap(id -> userRepository.findById(id), 10)  // Max 10 concurrent
        .flatMap(this::processUser, 5);                   // Max 5 concurrent
}
```

### Try-Catch Patterns

**Before:**
```java
public User safeGetUser(Long id) {
    try {
        return userRepository.findById(id);
    } catch (DatabaseException e) {
        log.error("Database error", e);
        return getFromCache(id);
    } catch (Exception e) {
        log.error("Unexpected error", e);
        return createDefaultUser();
    }
}
```

**After:**
```java
public Mono<User> safeGetUser(Long id) {
    return userRepository.findById(id)
        .onErrorResume(DatabaseException.class, e -> {
            log.error("Database error", e);
            return getFromCache(id);
        })
        .onErrorResume(e -> {
            log.error("Unexpected error", e);
            return createDefaultUser();
        });
}
```

## Transaction Migration

### JPA Transactions

**Before:**
```java
@Service
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request);
        orderRepository.save(order);

        for (OrderItem item : request.getItems()) {
            orderItemRepository.save(item);
            inventoryService.decrementStock(item.getProductId(), item.getQuantity());
        }

        paymentService.processPayment(order);
        return order;
    }
}
```

### R2DBC Transactions

**After:**
```java
@Service
public class OrderService {

    private final TransactionalOperator transactionalOperator;

    @Transactional
    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.just(new Order(request))
            .flatMap(orderRepository::save)
            .flatMap(order ->
                Flux.fromIterable(request.getItems())
                    .flatMap(item ->
                        orderItemRepository.save(item)
                            .then(inventoryService.decrementStock(
                                item.getProductId(), item.getQuantity()))
                    )
                    .then(Mono.just(order))
            )
            .flatMap(order ->
                paymentService.processPayment(order)
                    .thenReturn(order)
            )
            .as(transactionalOperator::transactional);
    }
}
```

## Testing Migration

### JUnit Test Conversion

**Before:**
```java
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void findById_existingUser_returnsUser() {
        User user = userService.findById(1L);
        assertNotNull(user);
        assertEquals("John", user.getName());
    }

    @Test
    void findById_nonExisting_throwsException() {
        assertThrows(UserNotFoundException.class,
            () -> userService.findById(999L));
    }
}
```

**After:**
```java
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void findById_existingUser_returnsUser() {
        StepVerifier.create(userService.findById(1L))
            .assertNext(user -> {
                assertNotNull(user);
                assertEquals("John", user.getName());
            })
            .verifyComplete();
    }

    @Test
    void findById_nonExisting_returnsError() {
        StepVerifier.create(userService.findById(999L))
            .expectError(UserNotFoundException.class)
            .verify();
    }
}
```

## Migration Checklist

### Pre-Migration

- [ ] Inventory all blocking dependencies (JDBC, file I/O, legacy services)
- [ ] Identify critical paths that benefit most from reactive
- [ ] Set up monitoring for baseline metrics
- [ ] Create integration tests for existing behavior

### During Migration

- [ ] Add WebFlux dependency alongside MVC
- [ ] Start with leaf services (no dependencies on other services)
- [ ] Wrap blocking calls in `subscribeOn(Schedulers.boundedElastic())`
- [ ] Convert controllers incrementally
- [ ] Replace RestTemplate with WebClient
- [ ] Migrate to R2DBC for database access
- [ ] Update tests to use StepVerifier

### Post-Migration

- [ ] Remove MVC dependency if fully migrated
- [ ] Eliminate all `subscribeOn(boundedElastic)` wrappers
- [ ] Verify no blocking calls on event loop (use BlockHound)
- [ ] Performance test and compare with baseline
- [ ] Update monitoring for reactive-specific metrics

## Common Pitfalls

### 1. Blocking in Reactive Chain

```java
// WRONG: Blocks the event loop
public Mono<User> getUser(Long id) {
    return Mono.fromCallable(() -> {
        Thread.sleep(1000);  // BLOCKING!
        return legacyService.findById(id);
    });
}

// RIGHT: Offload to bounded elastic
public Mono<User> getUser(Long id) {
    return Mono.fromCallable(() -> legacyService.findById(id))
        .subscribeOn(Schedulers.boundedElastic());
}
```

### 2. Not Subscribing

```java
// WRONG: Nothing happens!
public void sendEmail(User user) {
    emailService.send(user);  // Returns Mono, never subscribed
}

// RIGHT: Subscribe or return
public Mono<Void> sendEmail(User user) {
    return emailService.send(user);  // Caller subscribes
}
```

### 3. Using block()

```java
// WRONG: Defeats the purpose
public Mono<User> getEnrichedUser(Long id) {
    User user = userRepository.findById(id).block();  // BLOCKING!
    List<Order> orders = orderRepository.findByUserId(id).collectList().block();
    user.setOrders(orders);
    return Mono.just(user);
}

// RIGHT: Chain operations
public Mono<User> getEnrichedUser(Long id) {
    return userRepository.findById(id)
        .flatMap(user ->
            orderRepository.findByUserId(id)
                .collectList()
                .map(orders -> {
                    user.setOrders(orders);
                    return user;
                })
        );
}
```

### 4. Losing Error Context

```java
// WRONG: Original exception lost
public Mono<User> getUser(Long id) {
    return userRepository.findById(id)
        .onErrorResume(e -> Mono.empty());  // Silently swallows errors
}

// RIGHT: Preserve or transform meaningfully
public Mono<User> getUser(Long id) {
    return userRepository.findById(id)
        .onErrorMap(e -> new ServiceException("Failed to get user " + id, e));
}
```
