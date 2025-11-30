# Chapter 17: Testing Reactive Applications

> "The more your tests resemble the way your software is used, the more confidence they can give you."
> — Kent C. Dodds

## Introduction

Reactive applications present unique testing challenges. The asynchronous, non-blocking nature that makes them efficient also makes them harder to test. Traditional synchronous testing approaches don't work—you can't simply call a method and assert on the result when that result arrives at some unknown future time.

This chapter equips you with the tools and techniques to test reactive applications confidently. We'll explore StepVerifier for unit testing streams, virtual time for testing delays, WebTestClient for integration testing, and Testcontainers for testing with real dependencies.

## 17.1 The Testing Challenge

### Why Traditional Testing Fails

```java
// Traditional synchronous test - works
@Test
void traditionalTest() {
    String result = service.getData();
    assertEquals("expected", result);
}

// Naive reactive test - DOES NOT WORK
@Test
void brokenReactiveTest() {
    Mono<String> result = service.getData();
    assertEquals("expected", result);  // Compares Mono, not the value!
}
```

The problem: reactive types like `Mono` and `Flux` are lazy publishers. They don't execute until subscribed to, and the subscription is asynchronous.

### The Solution: Reactive Testing Tools

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE TESTING TOOLBOX                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Unit Testing                    Integration Testing                   │
│   ┌─────────────────────┐         ┌─────────────────────┐              │
│   │    StepVerifier     │         │   WebTestClient     │              │
│   │                     │         │                     │              │
│   │  • Verify signals   │         │  • Test endpoints   │              │
│   │  • Test operators   │         │  • Full HTTP stack  │              │
│   │  • Virtual time     │         │  • Exchange tests   │              │
│   └─────────────────────┘         └─────────────────────┘              │
│                                                                          │
│   Mocking                         Containers                            │
│   ┌─────────────────────┐         ┌─────────────────────┐              │
│   │   PublisherProbe    │         │   Testcontainers    │              │
│   │                     │         │                     │              │
│   │  • Verify subscribed│         │  • Real databases   │              │
│   │  • Cancellation     │         │  • Real Kafka       │              │
│   │  • Cold vs Hot      │         │  • Real Redis       │              │
│   └─────────────────────┘         └─────────────────────┘              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 17.2 StepVerifier Fundamentals

### Basic Verification

```java
import reactor.test.StepVerifier;

@Test
void testMonoSuccess() {
    Mono<String> mono = Mono.just("Hello");

    StepVerifier.create(mono)
        .expectNext("Hello")
        .verifyComplete();
}

@Test
void testFluxMultipleElements() {
    Flux<Integer> flux = Flux.just(1, 2, 3);

    StepVerifier.create(flux)
        .expectNext(1)
        .expectNext(2)
        .expectNext(3)
        .verifyComplete();
}

@Test
void testFluxWithExpectNextSequence() {
    Flux<Integer> flux = Flux.range(1, 5);

    StepVerifier.create(flux)
        .expectNextSequence(List.of(1, 2, 3, 4, 5))
        .verifyComplete();
}
```

### Testing Errors

```java
@Test
void testMonoError() {
    Mono<String> mono = Mono.error(new RuntimeException("Something went wrong"));

    StepVerifier.create(mono)
        .expectErrorMatches(e -> e instanceof RuntimeException &&
                                  e.getMessage().equals("Something went wrong"))
        .verify();
}

@Test
void testFluxWithErrorAfterElements() {
    Flux<Integer> flux = Flux.concat(
        Flux.just(1, 2, 3),
        Flux.error(new IllegalStateException("Boom!"))
    );

    StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .expectError(IllegalStateException.class)
        .verify();
}

@Test
void testErrorMessage() {
    Mono<String> mono = Mono.error(new IllegalArgumentException("Invalid input"));

    StepVerifier.create(mono)
        .expectErrorMessage("Invalid input")
        .verify();
}
```

### Asserting on Elements

```java
@Test
void testWithAssertions() {
    Flux<User> users = userService.findAll();

    StepVerifier.create(users)
        .assertNext(user -> {
            assertNotNull(user.getId());
            assertTrue(user.getEmail().contains("@"));
        })
        .assertNext(user -> assertEquals("Admin", user.getRole()))
        .expectComplete()
        .verify();
}

@Test
void testExpectNextMatches() {
    Flux<Integer> flux = Flux.just(2, 4, 6, 8);

    StepVerifier.create(flux)
        .expectNextMatches(n -> n % 2 == 0)
        .expectNextMatches(n -> n % 2 == 0)
        .expectNextMatches(n -> n % 2 == 0)
        .expectNextMatches(n -> n % 2 == 0)
        .verifyComplete();
}
```

### Count-Based Expectations

```java
@Test
void testExpectNextCount() {
    Flux<Integer> flux = Flux.range(1, 100);

    StepVerifier.create(flux)
        .expectNextCount(100)
        .verifyComplete();
}

@Test
void testConsumeNextWith() {
    Flux<User> users = Flux.just(
        new User("1", "Alice"),
        new User("2", "Bob")
    );

    StepVerifier.create(users)
        .consumeNextWith(user -> assertThat(user.getName()).isEqualTo("Alice"))
        .consumeNextWith(user -> assertThat(user.getName()).isEqualTo("Bob"))
        .verifyComplete();
}
```

## 17.3 Testing with Virtual Time

### The Problem with Real Time

```java
// This test takes 5 actual seconds to run!
@Test
void slowTestWithRealDelay() {
    Mono<String> delayed = Mono.just("result")
        .delayElement(Duration.ofSeconds(5));

    StepVerifier.create(delayed)
        .expectNext("result")
        .verifyComplete();  // Waits 5 real seconds
}
```

### Virtual Time to the Rescue

```java
@Test
void fastTestWithVirtualTime() {
    StepVerifier.withVirtualTime(() ->
        Mono.just("result").delayElement(Duration.ofSeconds(5))
    )
    .expectSubscription()
    .expectNoEvent(Duration.ofSeconds(4))  // Verify nothing happened for 4s
    .thenAwait(Duration.ofSeconds(1))      // Advance virtual clock by 1s
    .expectNext("result")
    .verifyComplete();  // Runs instantly!
}

@Test
void testIntervalWithVirtualTime() {
    StepVerifier.withVirtualTime(() ->
        Flux.interval(Duration.ofHours(1))
            .take(24)
    )
    .expectSubscription()
    .thenAwait(Duration.ofHours(24))
    .expectNextCount(24)
    .verifyComplete();  // Tests 24 hours of intervals instantly
}
```

### Testing Retry with Virtual Time

```java
@Test
void testRetryWithBackoff() {
    AtomicInteger attempts = new AtomicInteger(0);

    Mono<String> retriedMono = Mono.defer(() -> {
        if (attempts.incrementAndGet() < 3) {
            return Mono.error(new RuntimeException("Fail #" + attempts.get()));
        }
        return Mono.just("Success!");
    }).retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));

    StepVerifier.withVirtualTime(() -> retriedMono)
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(1))  // First retry delay
        .thenAwait(Duration.ofSeconds(2))  // Second retry delay (exponential)
        .expectNext("Success!")
        .verifyComplete();
}
```

### Testing Timeout

```java
@Test
void testTimeout() {
    Mono<String> neverEmits = Mono.never();

    StepVerifier.withVirtualTime(() ->
        neverEmits.timeout(Duration.ofSeconds(3))
    )
    .expectSubscription()
    .thenAwait(Duration.ofSeconds(3))
    .expectError(TimeoutException.class)
    .verify();
}
```

## 17.4 Testing Context

### Verifying Context Propagation

```java
@Test
void testContextRead() {
    Mono<String> mono = Mono.deferContextual(ctx ->
        Mono.just("User: " + ctx.get("userId"))
    );

    StepVerifier.create(mono.contextWrite(Context.of("userId", "12345")))
        .expectNext("User: 12345")
        .verifyComplete();
}

@Test
void testContextInChain() {
    Mono<String> service = someService.processRequest()
        .contextWrite(Context.of("traceId", "abc-123"));

    StepVerifier.create(service)
        .assertNext(result -> assertTrue(result.contains("abc-123")))
        .verifyComplete();
}
```

## 17.5 Testing Cold vs Hot Publishers

### Cold Publisher (Default)

```java
@Test
void testColdPublisher() {
    AtomicInteger counter = new AtomicInteger(0);

    Flux<Integer> cold = Flux.defer(() -> {
        counter.incrementAndGet();
        return Flux.just(1, 2, 3);
    });

    // Each subscription triggers fresh data generation
    StepVerifier.create(cold)
        .expectNext(1, 2, 3)
        .verifyComplete();

    StepVerifier.create(cold)
        .expectNext(1, 2, 3)
        .verifyComplete();

    assertEquals(2, counter.get());  // Called twice
}
```

### Hot Publisher

```java
@Test
void testHotPublisher() {
    Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    Flux<String> hot = sink.asFlux();

    // First subscriber
    StepVerifier.create(hot.take(2))
        .then(() -> {
            sink.tryEmitNext("A");
            sink.tryEmitNext("B");
        })
        .expectNext("A", "B")
        .verifyComplete();

    // Late subscriber misses previous elements
    StepVerifier.create(hot.take(1))
        .then(() -> sink.tryEmitNext("C"))
        .expectNext("C")
        .verifyComplete();
}
```

### Using PublisherProbe

```java
@Test
void testConditionalBranch() {
    PublisherProbe<Void> fallbackProbe = PublisherProbe.empty();

    Mono<String> result = someService.getData()
        .switchIfEmpty(fallbackProbe.mono().then(Mono.just("fallback")));

    StepVerifier.create(result)
        .expectNext("fallback")
        .verifyComplete();

    // Verify the fallback branch was actually taken
    fallbackProbe.assertWasSubscribed();
    fallbackProbe.assertWasRequested();
}
```

## 17.6 WebTestClient for Integration Testing

### Basic Setup

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void getUserById() {
        webTestClient.get()
            .uri("/api/users/{id}", "123")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(User.class)
            .value(user -> {
                assertEquals("123", user.getId());
                assertNotNull(user.getName());
            });
    }
}
```

### Testing POST Requests

```java
@Test
void createUser() {
    CreateUserRequest request = new CreateUserRequest("John", "john@example.com");

    webTestClient.post()
        .uri("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().exists("Location")
        .expectBody(User.class)
        .value(user -> {
            assertNotNull(user.getId());
            assertEquals("John", user.getName());
        });
}
```

### Testing Error Responses

```java
@Test
void getUserNotFound() {
    webTestClient.get()
        .uri("/api/users/{id}", "nonexistent")
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.error").isEqualTo("User not found")
        .jsonPath("$.timestamp").exists();
}

@Test
void createUserValidationError() {
    CreateUserRequest invalid = new CreateUserRequest("", "not-an-email");

    webTestClient.post()
        .uri("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalid)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.errors").isArray()
        .jsonPath("$.errors[*].field").value(hasItems("name", "email"));
}
```

### Testing Streaming Responses

```java
@Test
void streamUsers() {
    webTestClient.get()
        .uri("/api/users/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
        .returnResult(User.class)
        .getResponseBody()
        .as(StepVerifier::create)
        .expectNextCount(10)
        .verifyComplete();
}

@Test
void serverSentEvents() {
    Flux<ServerSentEvent<String>> events = webTestClient.get()
        .uri("/api/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
        .getResponseBody();

    StepVerifier.create(events.take(3))
        .assertNext(event -> assertEquals("heartbeat", event.event()))
        .assertNext(event -> assertNotNull(event.data()))
        .assertNext(event -> assertNotNull(event.data()))
        .verifyComplete();
}
```

### Testing with Authentication

```java
@Test
void authenticatedEndpoint() {
    webTestClient
        .mutate()
        .defaultHeader("Authorization", "Bearer " + validToken)
        .build()
        .get()
        .uri("/api/secure/data")
        .exchange()
        .expectStatus().isOk();
}

@Test
void unauthorizedAccess() {
    webTestClient.get()
        .uri("/api/secure/data")
        .exchange()
        .expectStatus().isUnauthorized();
}
```

### Binding to Controllers Directly

```java
@ExtendWith(SpringExtension.class)
@WebFluxTest(UserController.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @Test
    void getUser() {
        when(userService.findById("123"))
            .thenReturn(Mono.just(new User("123", "John")));

        webTestClient.get()
            .uri("/api/users/123")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("123")
            .jsonPath("$.name").isEqualTo("John");
    }
}
```

## 17.7 Testing with Testcontainers

### PostgreSQL with R2DBC

```java
@Testcontainers
@DataR2dbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/testdb");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindUser() {
        User user = new User(null, "Test User", "test@example.com");

        StepVerifier.create(
            userRepository.save(user)
                .flatMap(saved -> userRepository.findById(saved.getId()))
        )
        .assertNext(found -> {
            assertNotNull(found.getId());
            assertEquals("Test User", found.getName());
        })
        .verifyComplete();
    }
}
```

### Redis Testing

```java
@Testcontainers
@SpringBootTest
class RedisCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Test
    void cacheOperations() {
        StepVerifier.create(
            redisTemplate.opsForValue().set("key", "value")
                .then(redisTemplate.opsForValue().get("key"))
        )
        .expectNext("value")
        .verifyComplete();
    }
}
```

### Kafka Testing

```java
@Testcontainers
@SpringBootTest
class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ReactiveKafkaProducerTemplate<String, String> producer;

    @Autowired
    private ReactiveKafkaConsumerTemplate<String, String> consumer;

    @Test
    void produceAndConsume() {
        String topic = "test-topic";

        StepVerifier.create(
            producer.send(topic, "key", "message")
                .then(consumer.receive().next())
        )
        .assertNext(record -> {
            assertEquals("key", record.key());
            assertEquals("message", record.value());
        })
        .verifyComplete();
    }
}
```

## 17.8 Mocking Reactive Services

### Using Mockito with Reactor

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder() {
        User user = new User("123", "John");
        when(userRepository.findById("123")).thenReturn(Mono.just(user));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-1");
            return Mono.just(order);
        });

        CreateOrderRequest request = new CreateOrderRequest("123", List.of("item1"));

        StepVerifier.create(orderService.createOrder(request))
            .assertNext(order -> {
                assertNotNull(order.getId());
                assertEquals("123", order.getUserId());
            })
            .verifyComplete();

        verify(userRepository).findById("123");
        verify(orderRepository).save(any());
    }

    @Test
    void createOrderUserNotFound() {
        when(userRepository.findById("nonexistent")).thenReturn(Mono.empty());

        CreateOrderRequest request = new CreateOrderRequest("nonexistent", List.of("item1"));

        StepVerifier.create(orderService.createOrder(request))
            .expectError(UserNotFoundException.class)
            .verify();

        verify(orderRepository, never()).save(any());
    }
}
```

### Mocking WebClient

```java
@Test
void testExternalApiCall() {
    // Create a mock WebClient
    WebClient mockWebClient = mock(WebClient.class);
    WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

    when(mockWebClient.get()).thenReturn(mockUriSpec);
    when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
    when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
    when(mockResponseSpec.bodyToMono(ExternalData.class))
        .thenReturn(Mono.just(new ExternalData("result")));

    ExternalApiClient client = new ExternalApiClient(mockWebClient);

    StepVerifier.create(client.fetchData())
        .expectNext(new ExternalData("result"))
        .verifyComplete();
}
```

### Using MockWebServer

```java
class ExternalApiClientTest {

    private MockWebServer mockServer;
    private ExternalApiClient client;

    @BeforeEach
    void setup() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        WebClient webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build();
        client = new ExternalApiClient(webClient);
    }

    @AfterEach
    void teardown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void fetchDataSuccess() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"value\": \"test\"}"));

        StepVerifier.create(client.fetchData())
            .expectNext(new ExternalData("test"))
            .verifyComplete();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/data", request.getPath());
    }

    @Test
    void fetchDataServerError() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        StepVerifier.create(client.fetchData())
            .expectError(WebClientResponseException.InternalServerError.class)
            .verify();
    }
}
```

## 17.9 Testing Error Scenarios

### Testing Error Recovery

```java
@Test
void testOnErrorReturn() {
    Mono<String> mono = Mono.error(new RuntimeException("Fail"))
        .onErrorReturn("fallback");

    StepVerifier.create(mono)
        .expectNext("fallback")
        .verifyComplete();
}

@Test
void testOnErrorResume() {
    Mono<String> mono = Mono.error(new RuntimeException("Fail"))
        .onErrorResume(e -> Mono.just("recovered from: " + e.getMessage()));

    StepVerifier.create(mono)
        .expectNext("recovered from: Fail")
        .verifyComplete();
}
```

### Testing Retry Behavior

```java
@Test
void testRetryExhausted() {
    AtomicInteger attempts = new AtomicInteger(0);

    Mono<String> alwaysFails = Mono.defer(() -> {
        attempts.incrementAndGet();
        return Mono.error(new RuntimeException("Always fails"));
    }).retry(3);

    StepVerifier.create(alwaysFails)
        .expectError(RuntimeException.class)
        .verify();

    assertEquals(4, attempts.get());  // Initial + 3 retries
}
```

## 17.10 Best Practices

### Test Structure

```java
@Test
void wellStructuredTest() {
    // Arrange
    User user = new User("1", "Test");
    when(userRepository.findById("1")).thenReturn(Mono.just(user));

    // Act
    Mono<User> result = userService.getUser("1");

    // Assert
    StepVerifier.create(result)
        .assertNext(u -> assertEquals("Test", u.getName()))
        .verifyComplete();
}
```

### Test Naming

```java
@Nested
@DisplayName("UserService.createUser")
class CreateUserTests {

    @Test
    @DisplayName("should create user with valid input")
    void createsUserWithValidInput() { ... }

    @Test
    @DisplayName("should throw ValidationException for invalid email")
    void throwsForInvalidEmail() { ... }

    @Test
    @DisplayName("should generate unique ID for new user")
    void generatesUniqueId() { ... }
}
```

### Common Patterns

```java
// Pattern: Verify subscription happens
StepVerifier.create(publisher)
    .expectSubscription()
    .expectNext(...)
    .verifyComplete();

// Pattern: Timeout for tests that might hang
StepVerifier.create(publisher)
    .expectNext(...)
    .verifyComplete(Duration.ofSeconds(5));

// Pattern: First/Then for async assertions
StepVerifier.create(flux)
    .expectSubscription()
    .then(() -> sink.tryEmitNext("value"))
    .expectNext("value")
    .verifyComplete();
```

## Summary

Testing reactive applications requires specialized tools and techniques:

| Tool | Use Case |
|------|----------|
| **StepVerifier** | Verifying Mono/Flux behavior, signals, and timing |
| **Virtual Time** | Testing time-based operations without waiting |
| **WebTestClient** | Integration testing HTTP endpoints |
| **Testcontainers** | Testing with real databases, Kafka, Redis |
| **PublisherProbe** | Verifying conditional branches were executed |
| **MockWebServer** | Mocking external HTTP services |

**Key Principles:**

1. **Subscribe explicitly**: Use StepVerifier to trigger and verify reactive streams
2. **Use virtual time**: Don't wait for real delays in tests
3. **Test error paths**: Verify error handling and recovery
4. **Integration test with real dependencies**: Testcontainers give confidence
5. **Verify subscriptions**: Ensure your code actually subscribes when expected

Testing might seem harder in reactive code, but the tools available make it just as rigorous—and often more precise—than traditional testing. The explicit nature of StepVerifier forces you to think about every signal your code produces.
