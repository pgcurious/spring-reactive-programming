# Chapter 9: Annotated Controllers in WebFlux

> "The best interface is no interface at all." — Golden Krishna

If you're a Spring MVC developer, this chapter will feel like coming home—with a twist. The annotations you know and love work in WebFlux, but the return types change. By the end of this chapter, you'll be comfortable building reactive REST APIs using the familiar `@RestController` style.

The transition is gentler than you might expect. Most of what you know transfers directly.

---

## 9.1 What Stays the Same

Let's start with good news: much of your Spring MVC knowledge applies directly to WebFlux.

### Familiar Annotations

```java
// These work EXACTLY the same way

@RestController           // Marks a REST controller
@Controller               // Marks a controller (with view resolution)
@RequestMapping           // Maps URLs to controllers/methods
@GetMapping               // Shorthand for @RequestMapping(method = GET)
@PostMapping              // Shorthand for @RequestMapping(method = POST)
@PutMapping               // Shorthand for @RequestMapping(method = PUT)
@DeleteMapping            // Shorthand for @RequestMapping(method = DELETE)
@PatchMapping             // Shorthand for @RequestMapping(method = PATCH)

@PathVariable             // Extracts values from URL path
@RequestParam             // Extracts query parameters
@RequestHeader            // Extracts header values
@CookieValue              // Extracts cookie values
@RequestBody              // Deserializes request body

@ResponseStatus           // Sets response status code
@ResponseBody             // Return value is response body
@ExceptionHandler         // Handles exceptions
@ControllerAdvice         // Global exception handling
```

### Example: MVC vs. WebFlux Side by Side

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SPRING MVC (Traditional)                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  @RestController                                                           │
│  @RequestMapping("/api/users")                                             │
│  public class UserController {                                             │
│                                                                            │
│      @GetMapping("/{id}")                                                  │
│      public User getUser(@PathVariable String id) {                       │
│          return userService.findById(id);                                 │
│      }                                                                     │
│                                                                            │
│      @GetMapping                                                           │
│      public List<User> getAllUsers() {                                    │
│          return userService.findAll();                                    │
│      }                                                                     │
│                                                                            │
│      @PostMapping                                                          │
│      @ResponseStatus(HttpStatus.CREATED)                                  │
│      public User createUser(@RequestBody User user) {                     │
│          return userService.save(user);                                   │
│      }                                                                     │
│  }                                                                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                    SPRING WEBFLUX (Reactive)                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  @RestController                                                           │
│  @RequestMapping("/api/users")                                             │
│  public class UserController {                                             │
│                                                                            │
│      @GetMapping("/{id}")                                                  │
│      public Mono<User> getUser(@PathVariable String id) {                 │
│          return userService.findById(id);   // Returns Mono<User>         │
│      }                                                                     │
│                                                                            │
│      @GetMapping                                                           │
│      public Flux<User> getAllUsers() {                                    │
│          return userService.findAll();      // Returns Flux<User>         │
│      }                                                                     │
│                                                                            │
│      @PostMapping                                                          │
│      @ResponseStatus(HttpStatus.CREATED)                                  │
│      public Mono<User> createUser(@RequestBody User user) {               │
│          return userService.save(user);     // Returns Mono<User>         │
│      }                                                                     │
│  }                                                                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**The only difference**: Return types are wrapped in `Mono<T>` or `Flux<T>`.

### Request Mapping Attributes

All the familiar mapping attributes work:

```java
@GetMapping(
    value = "/users/{id}",              // URL pattern
    params = "active=true",              // Required query parameter
    headers = "X-API-Version=1",         // Required header
    consumes = MediaType.APPLICATION_JSON_VALUE,  // Request content type
    produces = MediaType.APPLICATION_JSON_VALUE   // Response content type
)
public Mono<User> getActiveUser(@PathVariable String id) {
    return userService.findActiveById(id);
}
```

---

## 9.2 What Changes: Return Types

The fundamental change is in return types. Let's understand this deeply.

### The Rule

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    RETURN TYPE MAPPING                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Spring MVC                    Spring WebFlux                              │
│  ─────────────────────────     ─────────────────────────────────          │
│                                                                            │
│  T                       ──►   Mono<T>      (single item)                 │
│  List<T>                 ──►   Flux<T>      (multiple items)              │
│  void                    ──►   Mono<Void>   (no response body)            │
│  ResponseEntity<T>       ──►   Mono<ResponseEntity<T>>                    │
│                                                                            │
│  Exceptions:                                                               │
│  • Primitives, String can still be returned directly (auto-wrapped)       │
│  • ResponseEntity without Mono still works (immediate response)           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Why Mono and Flux?

```java
// MVC: Method completes when data is available (blocks until then)
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userService.findById(id);  // Thread waits here
}

// WebFlux: Method completes immediately with a "promise"
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id);  // Returns immediately
    // When data is available, framework sends it to client
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    EXECUTION COMPARISON                                     │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  MVC Method Execution:                                                     │
│  ─────────────────────                                                     │
│                                                                            │
│  Thread: [Enter Method] ──[Call Service]──[Wait...]──[Return]──[Exit]     │
│  Time:   0ms             1ms             1-100ms    101ms      102ms      │
│  State:  Working         Blocked         Blocked    Working    Done       │
│                                                                            │
│  Thread is BLOCKED for ~100ms doing nothing                               │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  WebFlux Method Execution:                                                 │
│  ─────────────────────────                                                 │
│                                                                            │
│  Thread: [Enter Method] ──[Call Service]──[Return Mono]──[Exit]           │
│  Time:   0ms             0.1ms           0.2ms          0.3ms             │
│  State:  Working         Working         Working        Free!             │
│                                                                            │
│  Later (when data ready):                                                  │
│  Thread: [onNext callback] ──[Send to client]──[Done]                     │
│  Time:   100ms              100.1ms           100.2ms                     │
│                                                                            │
│  Thread is NEVER blocked, always doing useful work                        │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Who Subscribes?

A common question: "If I return a Mono, who subscribes to it?"

**Answer: The framework subscribes automatically.**

```java
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id);
    // You DON'T subscribe
    // WebFlux subscribes when it needs to send the response
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SUBSCRIPTION FLOW                                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  HTTP Request arrives                                                      │
│         │                                                                  │
│         ▼                                                                  │
│  Framework calls your controller method                                   │
│         │                                                                  │
│         ▼                                                                  │
│  Your method returns Mono<User>                                           │
│         │                                                                  │
│         ▼                                                                  │
│  Framework subscribes to the Mono                                         │
│         │                                                                  │
│         ▼                                                                  │
│  When Mono emits User, framework serializes and sends response            │
│         │                                                                  │
│         ▼                                                                  │
│  When Mono completes, HTTP response is finished                           │
│                                                                            │
│  NEVER subscribe() in your controller code!                               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Returning Non-Reactive Types

You can still return non-reactive types, but they're automatically wrapped:

```java
// These all work, but lose the non-blocking benefit

@GetMapping("/simple")
public String simple() {
    return "Hello";  // Auto-wrapped in Mono.just("Hello")
}

@GetMapping("/status")
public ResponseEntity<String> status() {
    return ResponseEntity.ok("OK");  // Works, but synchronous
}

// For truly reactive behavior, return Mono/Flux
@GetMapping("/reactive")
public Mono<String> reactive() {
    return Mono.just("Hello").delayElement(Duration.ofMillis(100));
}
```

---

## 9.3 Request Body Handling

Request body handling in WebFlux offers more flexibility.

### Traditional Request Body

```java
// Request body is deserialized before method is called
@PostMapping
public Mono<User> createUser(@RequestBody User user) {
    // 'user' is already deserialized
    // This buffers the entire body in memory first
    return userService.save(user);
}
```

### Reactive Request Body

```java
// Request body is a stream that hasn't been read yet
@PostMapping
public Mono<User> createUser(@RequestBody Mono<User> userMono) {
    // 'userMono' will emit the user when body is deserialized
    // More efficient: doesn't buffer before your code runs
    return userMono.flatMap(userService::save);
}
```

### When to Use Each

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    @RequestBody VARIANTS                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  @RequestBody T:                                                           │
│  ───────────────                                                           │
│  • Body is fully read and deserialized before method executes             │
│  • Simpler to use (just access the object)                                │
│  • Fine for small payloads                                                │
│  • Good when you need the whole object immediately                        │
│                                                                            │
│  @RequestBody Mono<T>:                                                     │
│  ─────────────────────                                                     │
│  • Body reading is deferred                                               │
│  • Better for chaining operations                                         │
│  • Doesn't buffer unnecessarily                                           │
│  • Use with flatMap to process                                            │
│                                                                            │
│  @RequestBody Flux<T>:                                                     │
│  ─────────────────────                                                     │
│  • For streaming request bodies (e.g., JSON array as stream)              │
│  • Process items as they arrive                                           │
│  • Memory efficient for large collections                                 │
│  • Client must stream the data                                            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Example: Streaming Request Body

```java
// Process a stream of items without loading all into memory
@PostMapping("/bulk-create")
public Flux<User> bulkCreate(@RequestBody Flux<User> users) {
    return users
        .flatMap(userService::save)
        .doOnNext(saved -> log.info("Saved user: {}", saved.getId()));
}

// Client can send:
// [{"name":"Alice"},{"name":"Bob"},{"name":"Charlie"}]
// Items are processed as they're parsed
```

### Accessing Request Metadata with Body

```java
@PostMapping("/with-headers")
public Mono<User> createWithHeaders(
    @RequestBody Mono<User> userMono,
    @RequestHeader("X-Request-Id") String requestId,
    @RequestHeader(value = "X-Client-Version", defaultValue = "1.0") String version
) {
    return userMono
        .doOnNext(user -> log.info("Request {} from client v{}", requestId, version))
        .flatMap(userService::save);
}
```

---

## 9.4 Server-Sent Events

WebFlux makes Server-Sent Events (SSE) trivially easy.

### What are Server-Sent Events?

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    SERVER-SENT EVENTS (SSE)                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  SSE is a simple protocol for server-to-client streaming:                 │
│                                                                            │
│  Client:                                                                   │
│  ────────                                                                  │
│  const eventSource = new EventSource('/api/events');                      │
│  eventSource.onmessage = (event) => {                                     │
│      console.log('Received:', event.data);                                │
│  };                                                                        │
│                                                                            │
│  Server sends:                                                             │
│  ─────────────                                                             │
│  data: {"type":"message","content":"Hello"}\n\n                           │
│  data: {"type":"update","content":"World"}\n\n                            │
│  data: {"type":"done"}\n\n                                                │
│                                                                            │
│  Characteristics:                                                          │
│  ────────────────                                                          │
│  • Unidirectional: Server → Client only                                   │
│  • Text-based: UTF-8 encoded                                              │
│  • Auto-reconnect: Browser handles reconnection                           │
│  • Simple: No WebSocket complexity                                        │
│                                                                            │
│  Use cases:                                                                │
│  ───────────                                                               │
│  • Live dashboards                                                         │
│  • Notification feeds                                                      │
│  • Stock price updates                                                     │
│  • Progress indicators                                                     │
│  • Log streaming                                                           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Basic SSE Endpoint

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamEvents() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(sequence -> "Event #" + sequence);
}
```

The key is `produces = MediaType.TEXT_EVENT_STREAM_VALUE` which sets `Content-Type: text/event-stream`.

### SSE with Domain Objects

```java
@GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<StockPrice> streamPrices() {
    return stockService.getPriceStream();  // Returns Flux<StockPrice>
}

// JSON automatically serialized:
// data: {"symbol":"AAPL","price":150.25,"timestamp":1234567890}
//
// data: {"symbol":"AAPL","price":150.30,"timestamp":1234567891}
```

### Custom SSE with ServerSentEvent Wrapper

For more control over SSE format:

```java
@GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Notification>> streamNotifications() {
    return notificationService.getNotifications()
        .map(notification -> ServerSentEvent.<Notification>builder()
            .id(notification.getId())           // SSE event ID
            .event(notification.getType())      // SSE event type
            .data(notification)                 // SSE data
            .retry(Duration.ofSeconds(5))       // Reconnect hint
            .build());
}

// Produces:
// id: 123
// event: alert
// retry: 5000
// data: {"message":"Server update available"}
//
// id: 124
// event: info
// data: {"message":"Processing complete"}
```

### Real-World SSE Example: Progress Tracking

```java
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    @PostMapping("/{id}/start")
    public Mono<Void> startJob(@PathVariable String id) {
        return jobService.startAsync(id);
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<JobProgress> streamProgress(@PathVariable String id) {
        return jobService.getProgressStream(id)
            .takeUntil(progress -> progress.isComplete())
            .doOnComplete(() -> log.info("Progress stream completed for job {}", id));
    }
}

// Client code:
// const eventSource = new EventSource('/api/jobs/123/progress');
// eventSource.onmessage = (e) => {
//     const progress = JSON.parse(e.data);
//     updateProgressBar(progress.percentage);
//     if (progress.complete) eventSource.close();
// };
```

### SSE Error Handling

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<Event> streamWithErrorHandling() {
    return eventService.getEvents()
        .onErrorResume(ex -> {
            log.error("Stream error", ex);
            // Send error event, then complete
            return Flux.just(Event.error(ex.getMessage()));
        })
        .doOnCancel(() -> log.info("Client disconnected"));
}
```

---

## 9.5 Exception Handling

Exception handling in WebFlux uses familiar patterns with reactive adaptations.

### @ExceptionHandler

Works exactly like in MVC, but can return reactive types:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return userService.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return Mono.just(new ErrorResponse(
            "USER_NOT_FOUND",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return Mono.just(new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred"
        ));
    }
}
```

### @ControllerAdvice for Global Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            "USER_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(ValidationException ex) {
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.badRequest().body(error));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleBindException(
            WebExchangeBindException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("code", "VALIDATION_FAILED");
        errors.put("timestamp", LocalDateTime.now());

        List<Map<String, String>> fieldErrors = ex.getFieldErrors().stream()
            .map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid"
            ))
            .collect(Collectors.toList());

        errors.put("errors", fieldErrors);

        return Mono.just(ResponseEntity.badRequest().body(errors));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
}
```

### Error Handling in the Reactive Chain

You can also handle errors within the reactive chain itself:

```java
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
        .onErrorResume(DatabaseException.class, ex -> {
            log.error("Database error for user {}", id, ex);
            return Mono.error(new ServiceException("Unable to fetch user"));
        });
}
```

### ResponseStatusException

WebFlux supports the convenient `ResponseStatusException`:

```java
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User not found: " + id
        )));
}
```

---

## 9.6 Validation

Bean validation works in WebFlux with some reactive considerations.

### Basic Validation

```java
@PostMapping
public Mono<User> createUser(@Valid @RequestBody User user) {
    // @Valid triggers validation before method executes
    // ValidationException thrown if invalid
    return userService.save(user);
}

// User class with validation annotations
public class User {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 50, message = "Name must be 2-50 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Min(value = 0, message = "Age must be non-negative")
    @Max(value = 150, message = "Age must be realistic")
    private Integer age;

    // getters, setters
}
```

### Validation with Mono Request Body

```java
@PostMapping
public Mono<User> createUser(@RequestBody Mono<User> userMono) {
    return userMono
        .doOnNext(this::validate)  // Manual validation
        .flatMap(userService::save);
}

private void validate(User user) {
    Set<ConstraintViolation<User>> violations = validator.validate(user);
    if (!violations.isEmpty()) {
        String message = violations.stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
        throw new ValidationException(message);
    }
}
```

### Programmatic Validation with Validator

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final Validator validator;

    @PostMapping
    public Mono<User> createUser(@RequestBody Mono<User> userMono) {
        return userMono
            .flatMap(user -> {
                Errors errors = new BeanPropertyBindingResult(user, "user");
                validator.validate(user, errors);

                if (errors.hasErrors()) {
                    return Mono.error(new ValidationException(
                        formatErrors(errors)
                    ));
                }
                return userService.save(user);
            });
    }

    private String formatErrors(Errors errors) {
        return errors.getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
    }
}
```

### Custom Validators

```java
// Custom constraint annotation
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator implementation (note: this is blocking!)
@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // WARNING: This is blocking! See next section for reactive approach
        return !userRepository.existsByEmail(email).block();
    }
}
```

### Reactive Validation Pattern

For truly reactive validation, handle it in the service layer:

```java
@Service
public class UserService {

    private final UserRepository repository;

    public Mono<User> save(User user) {
        return validateUnique(user)
            .then(repository.save(user));
    }

    private Mono<Void> validateUnique(User user) {
        return repository.existsByEmail(user.getEmail())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new ValidationException(
                        "Email already exists: " + user.getEmail()
                    ));
                }
                return Mono.empty();
            });
    }
}
```

---

## 9.7 Full CRUD Example

Let's put it all together with a complete CRUD API:

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // CREATE
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Product> create(@Valid @RequestBody Product product) {
        return productService.save(product);
    }

    // READ ONE
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getById(@PathVariable String id) {
        return productService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // READ ALL
    @GetMapping
    public Flux<Product> getAll(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (category != null) {
            return productService.findByCategory(category)
                .skip((long) page * size)
                .take(size);
        }
        return productService.findAll()
            .skip((long) page * size)
            .take(size);
    }

    // UPDATE
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Product>> update(
            @PathVariable String id,
            @Valid @RequestBody Product product) {

        return productService.findById(id)
            .flatMap(existing -> {
                product.setId(id);
                return productService.save(product);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // PATCH (partial update)
    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Product>> patch(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        return productService.findById(id)
            .flatMap(existing -> {
                applyUpdates(existing, updates);
                return productService.save(existing);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // DELETE
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return productService.findById(id)
            .flatMap(existing -> productService.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build())))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // SEARCH
    @GetMapping("/search")
    public Flux<Product> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        return productService.search(query)
            .take(limit);
    }

    // STREAM (SSE)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Product> stream() {
        return productService.getProductUpdates();
    }

    private void applyUpdates(Product product, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> product.setName((String) value);
                case "price" -> product.setPrice(((Number) value).doubleValue());
                case "category" -> product.setCategory((String) value);
                case "description" -> product.setDescription((String) value);
            }
        });
    }
}
```

---

## 9.8 Summary

In this chapter, we learned how to build WebFlux controllers using the familiar annotated style:

**What Stays the Same:**
- All the annotations you know (`@RestController`, `@GetMapping`, etc.)
- Request parameter handling (`@PathVariable`, `@RequestParam`, etc.)
- Exception handling patterns (`@ExceptionHandler`, `@ControllerAdvice`)
- Validation annotations (`@Valid`, `@NotNull`, etc.)

**What Changes:**
- Return `Mono<T>` instead of `T`
- Return `Flux<T>` instead of `List<T>`
- Framework handles subscription automatically
- Can stream responses with SSE

**Request Body Options:**
- `@RequestBody T` - buffered, synchronous
- `@RequestBody Mono<T>` - deferred, reactive
- `@RequestBody Flux<T>` - streaming, reactive

**Server-Sent Events:**
- Add `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
- Return `Flux<T>`
- Use `ServerSentEvent` wrapper for advanced control

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          KEY TAKEAWAYS                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Most Spring MVC knowledge transfers directly                          │
│     Same annotations, same patterns, different return types.              │
│                                                                            │
│  2. Return Mono<T> or Flux<T>                                             │
│     Let the framework handle subscription.                                │
│                                                                            │
│  3. Never call block() in a controller                                    │
│     That defeats the entire purpose of WebFlux.                           │
│                                                                            │
│  4. SSE is trivially easy                                                  │
│     Just return Flux<T> with the right media type.                        │
│                                                                            │
│  5. Exception handling works the same way                                  │
│     @ExceptionHandler and @ControllerAdvice just return Mono.             │
│                                                                            │
│  6. Validation needs care with reactive types                             │
│     Use reactive patterns for async validation (like uniqueness checks).  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### What's Next?

In Chapter 10, we'll explore **functional endpoints**—an alternative programming model that offers more flexibility for complex routing scenarios. Both models are equally reactive; the choice is about coding style.

---

## Hands-On Lab 9: Building a Complete API

Now it's time to build a complete reactive API. In this lab, you'll:

1. Create a full CRUD API with annotated controllers
2. Add SSE for real-time updates
3. Implement comprehensive validation
4. Build global error handling with `@ControllerAdvice`

**Proceed to the `lab/` directory for detailed instructions.**

---

## Further Reading

- [Spring WebFlux Reference - Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
- [Server-Sent Events Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [Bean Validation with Spring](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)

---

## Discussion Questions

1. Your team is migrating a Spring MVC API to WebFlux. What's your strategy for minimal disruption?

2. A colleague suggests using `@RequestBody Mono<User>` everywhere "to be more reactive." Is this always better?

3. You need to validate that a username is unique (requires database check). How do you handle this reactively?

4. Your SSE endpoint sends thousands of events per second. What concerns do you have?

5. An exception handler in your `@ControllerAdvice` accidentally returns `null` instead of `Mono<ErrorResponse>`. What happens?
