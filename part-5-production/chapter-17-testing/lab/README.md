# Lab 17: Testing Reactive Applications

## Objectives

By the end of this lab, you will:

1. Master StepVerifier for unit testing reactive streams
2. Use virtual time to test time-based operations
3. Write integration tests with WebTestClient
4. Set up Testcontainers for database testing
5. Mock WebClient and external services
6. Test error scenarios and recovery
7. Implement contract testing for APIs

## Prerequisites

- Completed Chapter 16 (Integration Patterns)
- Docker installed (for Testcontainers)
- Understanding of JUnit 5 and Mockito

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup | 10 min |
| 2 | StepVerifier Basics | 25 min |
| 3 | Virtual Time Testing | 20 min |
| 4 | WebTestClient | 25 min |
| 5 | Testcontainers | 25 min |
| 6 | Mocking External Services | 20 min |
| 7 | Error Testing | 15 min |
| **Total** | | **140 min** |

---

## Part 1: Project Setup (10 min)

### Step 1.1: Create the Project

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>reactive-testing-lab</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
        <testcontainers.version>1.19.3</testcontainers.version>
    </properties>

    <dependencies>
        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- R2DBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Testing Core -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Reactor Test -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Testcontainers -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>r2dbc</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- MockWebServer -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
            <scope>test</scope>
        </dependency>

        <!-- AssertJ -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
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

### Step 1.2: Create Domain Classes

```java
package com.example.testing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Table("users")
public class User {

    @Id
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    public User() {}

    public User(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

```java
package com.example.testing.model;

public record CreateUserRequest(String name, String email) {}
public record UserResponse(Long id, String name, String email) {}
```

### Step 1.3: Create Repository and Service

```java
package com.example.testing.repository;

import com.example.testing.model.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByEmail(String email);
    Flux<User> findByNameContainingIgnoreCase(String name);
}
```

```java
package com.example.testing.service;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import com.example.testing.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<User> createUser(CreateUserRequest request) {
        return userRepository.findByEmail(request.email())
            .flatMap(existing -> Mono.<User>error(
                new IllegalArgumentException("Email already exists: " + request.email())))
            .switchIfEmpty(Mono.defer(() -> {
                User user = new User(null, request.name(), request.email());
                return userRepository.save(user);
            }));
    }

    public Mono<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    public Flux<User> searchByName(String name) {
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    public Mono<User> updateUser(Long id, CreateUserRequest request) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
            .flatMap(existing -> {
                existing.setName(request.name());
                existing.setEmail(request.email());
                return userRepository.save(existing);
            });
    }

    public Mono<Void> deleteUser(Long id) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
            .flatMap(user -> userRepository.delete(user));
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long id) {
            super("User not found: " + id);
        }
    }
}
```

### Step 1.4: Create Controller

```java
package com.example.testing.controller;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import com.example.testing.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public Mono<ResponseEntity<User>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request)
            .map(user -> ResponseEntity
                .created(URI.create("/api/users/" + user.getId()))
                .body(user));
    }

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

    @GetMapping("/search")
    public Flux<User> searchUsers(@RequestParam String name) {
        return userService.searchByName(name);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<User>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody CreateUserRequest request) {
        return userService.updateUser(id, request)
            .map(ResponseEntity::ok)
            .onErrorResume(UserService.UserNotFoundException.class,
                e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }
}
```

---

## Part 2: StepVerifier Basics (25 min)

### Step 2.1: Basic Mono Tests

```java
package com.example.testing.stepverifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MonoBasicsTest {

    @Nested
    @DisplayName("Mono.just")
    class MonoJustTests {

        @Test
        @DisplayName("should emit single value and complete")
        void emitsSingleValue() {
            Mono<String> mono = Mono.just("Hello");

            StepVerifier.create(mono)
                .expectNext("Hello")
                .verifyComplete();
        }

        @Test
        @DisplayName("should verify subscription")
        void verifiesSubscription() {
            Mono<Integer> mono = Mono.just(42);

            StepVerifier.create(mono)
                .expectSubscription()
                .expectNext(42)
                .expectComplete()
                .verify();
        }
    }

    @Nested
    @DisplayName("Mono.empty")
    class MonoEmptyTests {

        @Test
        @DisplayName("should complete without emitting")
        void completesWithoutEmitting() {
            Mono<String> mono = Mono.empty();

            StepVerifier.create(mono)
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Mono.error")
    class MonoErrorTests {

        @Test
        @DisplayName("should emit error signal")
        void emitsError() {
            Mono<String> mono = Mono.error(new RuntimeException("Boom!"));

            StepVerifier.create(mono)
                .expectError(RuntimeException.class)
                .verify();
        }

        @Test
        @DisplayName("should verify error message")
        void verifiesErrorMessage() {
            Mono<String> mono = Mono.error(new IllegalArgumentException("Invalid input"));

            StepVerifier.create(mono)
                .expectErrorMessage("Invalid input")
                .verify();
        }

        @Test
        @DisplayName("should match error predicate")
        void matchesErrorPredicate() {
            Mono<String> mono = Mono.error(new RuntimeException("Error code: 42"));

            StepVerifier.create(mono)
                .expectErrorMatches(e ->
                    e instanceof RuntimeException &&
                    e.getMessage().contains("42"))
                .verify();
        }
    }
}
```

### Step 2.2: Flux Tests

```java
package com.example.testing.stepverifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

class FluxBasicsTest {

    @Test
    @DisplayName("should emit multiple values in order")
    void emitsMultipleValues() {
        Flux<Integer> flux = Flux.just(1, 2, 3);

        StepVerifier.create(flux)
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .verifyComplete();
    }

    @Test
    @DisplayName("should verify values as collection")
    void verifiesAsCollection() {
        Flux<Integer> flux = Flux.just(1, 2, 3);

        StepVerifier.create(flux)
            .expectNext(1, 2, 3)
            .verifyComplete();
    }

    @Test
    @DisplayName("should verify sequence")
    void verifiesSequence() {
        Flux<String> flux = Flux.just("a", "b", "c");

        StepVerifier.create(flux)
            .expectNextSequence(List.of("a", "b", "c"))
            .verifyComplete();
    }

    @Test
    @DisplayName("should count elements")
    void countsElements() {
        Flux<Integer> flux = Flux.range(1, 100);

        StepVerifier.create(flux)
            .expectNextCount(100)
            .verifyComplete();
    }

    @Test
    @DisplayName("should verify with predicate")
    void verifiesWithPredicate() {
        Flux<Integer> flux = Flux.just(2, 4, 6);

        StepVerifier.create(flux)
            .expectNextMatches(n -> n % 2 == 0)
            .expectNextMatches(n -> n > 2)
            .expectNextMatches(n -> n == 6)
            .verifyComplete();
    }

    @Test
    @DisplayName("should assert on each element")
    void assertsOnEachElement() {
        Flux<String> flux = Flux.just("Alice", "Bob", "Charlie");

        StepVerifier.create(flux)
            .assertNext(name -> {
                assert name.length() == 5;
                assert name.startsWith("A");
            })
            .assertNext(name -> {
                assert name.length() == 3;
            })
            .assertNext(name -> {
                assert name.contains("ar");
            })
            .verifyComplete();
    }
}
```

### Step 2.3: Service Layer Tests

```java
package com.example.testing.service;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import com.example.testing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("createUser should save new user when email doesn't exist")
    void createUserSuccess() {
        CreateUserRequest request = new CreateUserRequest("John", "john@example.com");
        User savedUser = new User(1L, "John", "john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Mono.empty());
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser));

        StepVerifier.create(userService.createUser(request))
            .assertNext(user -> {
                assert user.getId().equals(1L);
                assert user.getName().equals("John");
                assert user.getEmail().equals("john@example.com");
            })
            .verifyComplete();

        verify(userRepository).findByEmail("john@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser should fail when email already exists")
    void createUserDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest("John", "john@example.com");
        User existingUser = new User(1L, "Existing", "john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Mono.just(existingUser));

        StepVerifier.create(userService.createUser(request))
            .expectErrorMatches(e ->
                e instanceof IllegalArgumentException &&
                e.getMessage().contains("Email already exists"))
            .verify();

        verify(userRepository).findByEmail("john@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("findById should return user when found")
    void findByIdSuccess() {
        User user = new User(1L, "John", "john@example.com");
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));

        StepVerifier.create(userService.findById(1L))
            .expectNext(user)
            .verifyComplete();
    }

    @Test
    @DisplayName("findById should return empty when not found")
    void findByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(userService.findById(999L))
            .verifyComplete();  // No elements, just complete
    }

    @Test
    @DisplayName("findAll should return all users")
    void findAllSuccess() {
        User user1 = new User(1L, "Alice", "alice@example.com");
        User user2 = new User(2L, "Bob", "bob@example.com");

        when(userRepository.findAll()).thenReturn(Flux.just(user1, user2));

        StepVerifier.create(userService.findAll())
            .expectNext(user1)
            .expectNext(user2)
            .verifyComplete();
    }

    @Test
    @DisplayName("updateUser should fail when user not found")
    void updateUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Mono.empty());

        CreateUserRequest request = new CreateUserRequest("Updated", "updated@example.com");

        StepVerifier.create(userService.updateUser(999L, request))
            .expectError(UserService.UserNotFoundException.class)
            .verify();
    }
}
```

---

## Part 3: Virtual Time Testing (20 min)

### Step 3.1: Testing Delays

```java
package com.example.testing.virtualtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

class VirtualTimeTest {

    @Test
    @DisplayName("should test delay without waiting")
    void testDelayWithVirtualTime() {
        // This would take 10 seconds with real time
        StepVerifier.withVirtualTime(() ->
            Mono.just("delayed")
                .delayElement(Duration.ofSeconds(10))
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofSeconds(9))  // Nothing for 9 seconds
        .thenAwait(Duration.ofSeconds(1))      // Advance to 10 seconds
        .expectNext("delayed")
        .verifyComplete();
    }

    @Test
    @DisplayName("should test interval flux")
    void testIntervalWithVirtualTime() {
        // Test 24 hours of hourly events in milliseconds
        StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofHours(1))
                .map(i -> "tick-" + i)
                .take(24)
        )
        .expectSubscription()
        .thenAwait(Duration.ofHours(24))
        .expectNextCount(24)
        .verifyComplete();
    }

    @Test
    @DisplayName("should test timeout")
    void testTimeout() {
        StepVerifier.withVirtualTime(() ->
            Mono.never()
                .timeout(Duration.ofSeconds(5))
        )
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(5))
        .expectError(java.util.concurrent.TimeoutException.class)
        .verify();
    }

    @Test
    @DisplayName("should test delay between elements")
    void testDelayBetweenElements() {
        StepVerifier.withVirtualTime(() ->
            Flux.just(1, 2, 3)
                .delayElements(Duration.ofSeconds(1))
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(900))
        .thenAwait(Duration.ofMillis(100))
        .expectNext(1)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(2)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(3)
        .verifyComplete();
    }
}
```

### Step 3.2: Testing Retry with Backoff

```java
package com.example.testing.virtualtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

class RetryVirtualTimeTest {

    @Test
    @DisplayName("should test retry with exponential backoff")
    void testRetryWithBackoff() {
        AtomicInteger attempts = new AtomicInteger(0);

        Mono<String> failsTwice = Mono.defer(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt <= 2) {
                return Mono.error(new RuntimeException("Fail #" + attempt));
            }
            return Mono.just("Success on attempt " + attempt);
        });

        StepVerifier.withVirtualTime(() ->
            failsTwice.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(4)))
        )
        .expectSubscription()
        // First failure, wait 1 second
        .thenAwait(Duration.ofSeconds(1))
        // Second failure, wait 2 seconds (exponential)
        .thenAwait(Duration.ofSeconds(2))
        // Third attempt succeeds
        .expectNext("Success on attempt 3")
        .verifyComplete();
    }

    @Test
    @DisplayName("should test retry exhaustion")
    void testRetryExhausted() {
        AtomicInteger attempts = new AtomicInteger(0);

        Mono<String> alwaysFails = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new RuntimeException("Always fails"));
        });

        StepVerifier.withVirtualTime(() ->
            alwaysFails.retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
        )
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(1))  // First retry
        .thenAwait(Duration.ofSeconds(2))  // Second retry
        .thenAwait(Duration.ofSeconds(4))  // Third retry
        .expectError(RuntimeException.class)
        .verify();

        // Verify all retries were attempted
        assert attempts.get() == 4;  // Initial + 3 retries
    }
}
```

---

## Part 4: WebTestClient (25 min)

### Step 4.1: Controller Integration Tests

```java
package com.example.testing.controller;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import com.example.testing.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(UserController.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("POST /api/users should create user")
    void createUser() {
        User savedUser = new User(1L, "John", "john@example.com");
        when(userService.createUser(any())).thenReturn(Mono.just(savedUser));

        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateUserRequest("John", "john@example.com"))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectHeader().valueEquals("Location", "/api/users/1")
            .expectBody(User.class)
            .value(user -> {
                assert user.getId().equals(1L);
                assert user.getName().equals("John");
            });
    }

    @Test
    @DisplayName("GET /api/users/{id} should return user when found")
    void getUserFound() {
        User user = new User(1L, "John", "john@example.com");
        when(userService.findById(1L)).thenReturn(Mono.just(user));

        webTestClient.get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.name").isEqualTo("John")
            .jsonPath("$.email").isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("GET /api/users/{id} should return 404 when not found")
    void getUserNotFound() {
        when(userService.findById(999L)).thenReturn(Mono.empty());

        webTestClient.get()
            .uri("/api/users/999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /api/users should return all users")
    void getAllUsers() {
        User user1 = new User(1L, "Alice", "alice@example.com");
        User user2 = new User(2L, "Bob", "bob@example.com");
        when(userService.findAll()).thenReturn(Flux.just(user1, user2));

        webTestClient.get()
            .uri("/api/users")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(User.class)
            .hasSize(2)
            .contains(user1, user2);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} should return 204")
    void deleteUser() {
        when(userService.deleteUser(1L)).thenReturn(Mono.empty());

        webTestClient.delete()
            .uri("/api/users/1")
            .exchange()
            .expectStatus().isNoContent();
    }
}
```

### Step 4.2: Validation Tests

```java
package com.example.testing.controller;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(UserController.class)
class ValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("should reject empty name")
    void rejectsEmptyName() {
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateUserRequest("", "john@example.com"))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("should reject invalid email")
    void rejectsInvalidEmail() {
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateUserRequest("John", "not-an-email"))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("should reject missing fields")
    void rejectsMissingFields() {
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isBadRequest();
    }
}
```

---

## Part 5: Testcontainers (25 min)

### Step 5.1: PostgreSQL Integration Test

```java
package com.example.testing.integration;

import com.example.testing.model.User;
import com.example.testing.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
            "r2dbc:postgresql://%s:%d/%s",
            postgres.getHost(),
            postgres.getFirstMappedPort(),
            postgres.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // Create table if not exists
        databaseClient.sql("""
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE
            )
            """)
            .then()
            .block();

        // Clear data
        databaseClient.sql("DELETE FROM users").then().block();
    }

    @Test
    @DisplayName("should save and retrieve user")
    void saveAndRetrieveUser() {
        User user = new User(null, "Integration Test", "integration@test.com");

        StepVerifier.create(
            userRepository.save(user)
                .flatMap(saved -> userRepository.findById(saved.getId()))
        )
        .assertNext(found -> {
            Assertions.assertNotNull(found.getId());
            Assertions.assertEquals("Integration Test", found.getName());
            Assertions.assertEquals("integration@test.com", found.getEmail());
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("should find user by email")
    void findByEmail() {
        User user = new User(null, "Email Test", "email@test.com");

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.findByEmail("email@test.com"))
        )
        .assertNext(found -> {
            Assertions.assertEquals("Email Test", found.getName());
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("should search users by name")
    void searchByName() {
        User user1 = new User(null, "Alice Smith", "alice@test.com");
        User user2 = new User(null, "Bob Smith", "bob@test.com");
        User user3 = new User(null, "Charlie Jones", "charlie@test.com");

        StepVerifier.create(
            userRepository.saveAll(java.util.List.of(user1, user2, user3))
                .thenMany(userRepository.findByNameContainingIgnoreCase("smith"))
        )
        .expectNextCount(2)
        .verifyComplete();
    }
}
```

### Step 5.2: Full Stack Integration Test

```java
package com.example.testing.integration;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FullStackIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
            "r2dbc:postgresql://%s:%d/%s",
            postgres.getHost(),
            postgres.getFirstMappedPort(),
            postgres.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE
            )
            """)
            .then()
            .block();
        databaseClient.sql("DELETE FROM users").then().block();
    }

    @Test
    @DisplayName("full user lifecycle - create, read, update, delete")
    void userLifecycle() {
        // Create
        User created = webTestClient.post()
            .uri("/api/users")
            .bodyValue(new CreateUserRequest("Lifecycle Test", "lifecycle@test.com"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(User.class)
            .returnResult()
            .getResponseBody();

        Assertions.assertNotNull(created);
        Assertions.assertNotNull(created.getId());

        // Read
        webTestClient.get()
            .uri("/api/users/{id}", created.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Lifecycle Test");

        // Update
        webTestClient.put()
            .uri("/api/users/{id}", created.getId())
            .bodyValue(new CreateUserRequest("Updated Name", "lifecycle@test.com"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated Name");

        // Delete
        webTestClient.delete()
            .uri("/api/users/{id}", created.getId())
            .exchange()
            .expectStatus().isNoContent();

        // Verify deleted
        webTestClient.get()
            .uri("/api/users/{id}", created.getId())
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

---

## Part 6: Mocking External Services (20 min)

### Step 6.1: Create External API Client

```java
package com.example.testing.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ExternalApiClient {

    private final WebClient webClient;

    public ExternalApiClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://api.external.com").build();
    }

    // Constructor for testing
    public ExternalApiClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<ExternalData> fetchData(String id) {
        return webClient.get()
            .uri("/data/{id}", id)
            .retrieve()
            .bodyToMono(ExternalData.class)
            .timeout(Duration.ofSeconds(5));
    }

    public record ExternalData(String id, String value, long timestamp) {}
}
```

### Step 6.2: MockWebServer Tests

```java
package com.example.testing.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class ExternalApiClientTest {

    private MockWebServer mockServer;
    private ExternalApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        WebClient webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build();
        client = new ExternalApiClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    @DisplayName("should fetch data successfully")
    void fetchDataSuccess() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "id": "123",
                    "value": "test-value",
                    "timestamp": 1234567890
                }
                """));

        StepVerifier.create(client.fetchData("123"))
            .assertNext(data -> {
                Assertions.assertEquals("123", data.id());
                Assertions.assertEquals("test-value", data.value());
                Assertions.assertEquals(1234567890L, data.timestamp());
            })
            .verifyComplete();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("/data/123", request.getPath());
    }

    @Test
    @DisplayName("should handle 404 response")
    void fetchDataNotFound() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody("Not Found"));

        StepVerifier.create(client.fetchData("unknown"))
            .expectError(WebClientResponseException.NotFound.class)
            .verify();
    }

    @Test
    @DisplayName("should handle server error")
    void fetchDataServerError() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        StepVerifier.create(client.fetchData("123"))
            .expectError(WebClientResponseException.InternalServerError.class)
            .verify();
    }

    @Test
    @DisplayName("should handle slow response with timeout")
    void fetchDataTimeout() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{}")
            .setBodyDelay(10, TimeUnit.SECONDS));  // Delay > timeout

        StepVerifier.create(client.fetchData("123"))
            .expectError(java.util.concurrent.TimeoutException.class)
            .verify();
    }
}
```

---

## Part 7: Error Testing (15 min)

### Step 7.1: Error Recovery Tests

```java
package com.example.testing.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ErrorRecoveryTest {

    @Test
    @DisplayName("onErrorReturn provides fallback value")
    void onErrorReturnTest() {
        Mono<String> mono = Mono.error(new RuntimeException("Fail"))
            .onErrorReturn("fallback");

        StepVerifier.create(mono)
            .expectNext("fallback")
            .verifyComplete();
    }

    @Test
    @DisplayName("onErrorResume provides fallback publisher")
    void onErrorResumeTest() {
        Mono<String> mono = Mono.<String>error(new RuntimeException("Original error"))
            .onErrorResume(e -> Mono.just("Recovered from: " + e.getMessage()));

        StepVerifier.create(mono)
            .expectNext("Recovered from: Original error")
            .verifyComplete();
    }

    @Test
    @DisplayName("onErrorResume with specific exception type")
    void onErrorResumeSpecificType() {
        Mono<String> mono = Mono.<String>error(new IllegalArgumentException("Bad input"))
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just("Handled bad input"))
            .onErrorResume(RuntimeException.class, e -> Mono.just("Handled runtime error"));

        StepVerifier.create(mono)
            .expectNext("Handled bad input")
            .verifyComplete();
    }

    @Test
    @DisplayName("onErrorMap transforms error")
    void onErrorMapTest() {
        Mono<String> mono = Mono.<String>error(new RuntimeException("Original"))
            .onErrorMap(RuntimeException.class,
                e -> new IllegalStateException("Transformed: " + e.getMessage()));

        StepVerifier.create(mono)
            .expectErrorMatches(e ->
                e instanceof IllegalStateException &&
                e.getMessage().equals("Transformed: Original"))
            .verify();
    }

    @Test
    @DisplayName("doOnError for side effects")
    void doOnErrorTest() {
        java.util.concurrent.atomic.AtomicBoolean errorLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        Mono<String> mono = Mono.<String>error(new RuntimeException("Error"))
            .doOnError(e -> errorLogged.set(true));

        StepVerifier.create(mono)
            .expectError(RuntimeException.class)
            .verify();

        Assertions.assertTrue(errorLogged.get());
    }

    @Test
    @DisplayName("Flux continues after onErrorContinue")
    void onErrorContinueTest() {
        Flux<Integer> flux = Flux.just(1, 2, 3, 4, 5)
            .map(n -> {
                if (n == 3) throw new RuntimeException("Skip 3");
                return n * 2;
            })
            .onErrorContinue((e, o) -> System.out.println("Skipping: " + o));

        StepVerifier.create(flux)
            .expectNext(2, 4, 8, 10)  // 3 is skipped
            .verifyComplete();
    }
}
```

### Step 7.2: Testing Service Error Scenarios

```java
package com.example.testing.error;

import com.example.testing.model.CreateUserRequest;
import com.example.testing.model.User;
import com.example.testing.repository.UserRepository;
import com.example.testing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceErrorTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("should propagate repository errors")
    void repositoryErrorPropagation() {
        when(userRepository.findById(1L))
            .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        StepVerifier.create(userService.findById(1L))
            .expectErrorMatches(e ->
                e instanceof RuntimeException &&
                e.getMessage().contains("Database"))
            .verify();
    }

    @Test
    @DisplayName("should handle concurrent modification")
    void concurrentModificationError() {
        User user = new User(1L, "Original", "original@test.com");
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(any()))
            .thenReturn(Mono.error(new RuntimeException("Optimistic locking failure")));

        CreateUserRequest update = new CreateUserRequest("Updated", "updated@test.com");

        StepVerifier.create(userService.updateUser(1L, update))
            .expectErrorMessage("Optimistic locking failure")
            .verify();
    }
}
```

---

## Reflection

### Key Testing Patterns

1. **StepVerifier**: The fundamental tool for reactive testing
2. **Virtual Time**: Test time-based operations without waiting
3. **WebTestClient**: Test HTTP endpoints non-blocking
4. **Testcontainers**: Integration test with real dependencies
5. **MockWebServer**: Mock external HTTP services

### Best Practices

1. Test all signal types: onNext, onComplete, onError
2. Use virtual time for any delay > 100ms
3. Verify side effects with doOnXxx assertions
4. Use Testcontainers for database tests
5. Name tests descriptively

### Common Pitfalls

1. Forgetting to call `.verify()` or `.verifyComplete()`
2. Using `.block()` in tests when StepVerifier is better
3. Not testing error scenarios
4. Over-mocking instead of using Testcontainers

---

## Summary

In this lab, you:

1. Mastered StepVerifier for unit testing Mono and Flux
2. Used virtual time to test delays and timeouts
3. Wrote WebTestClient tests for controllers
4. Set up PostgreSQL with Testcontainers
5. Mocked external APIs with MockWebServer
6. Tested error handling and recovery

Your reactive applications are now thoroughly testable!
