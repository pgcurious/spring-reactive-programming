# Lab 9: Building a Complete Reactive API

## Objective

In this lab, you'll build a complete reactive REST API using Spring WebFlux annotated controllers. You'll implement:

1. Full CRUD operations for a Task management system
2. Server-Sent Events for real-time task updates
3. Comprehensive input validation
4. Global error handling with `@ControllerAdvice`
5. Response entity patterns for proper HTTP semantics

By the end, you'll have a production-quality reactive API template.

---

## Prerequisites

- Java 17+ installed
- Maven or Gradle installed
- Completion of Chapter 8 lab
- Understanding of Chapter 9 concepts

---

## Lab Structure

| Part | Focus | Estimated Time |
|------|-------|----------------|
| 1 | Project Setup | 10 min |
| 2 | Domain Model and Repository | 15 min |
| 3 | Service Layer | 15 min |
| 4 | CRUD Controller | 25 min |
| 5 | Validation | 20 min |
| 6 | Error Handling | 20 min |
| 7 | Server-Sent Events | 15 min |
| 8 | Testing with WebTestClient | 25 min |
| 9 | Reflection and Key Takeaways | 5 min |

**Total: ~150 minutes**

---

## Part 1: Project Setup (10 min)

### Exercise 1.1: Create the Project

Create a new directory `task-api` with the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>task-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Validation -->
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

### Exercise 1.2: Create Main Application

Create `src/main/java/com/example/taskapi/TaskApiApplication.java`:

```java
package com.example.taskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
```

### Exercise 1.3: Application Configuration

Create `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

logging:
  level:
    com.example: DEBUG
    org.springframework.web.reactive: DEBUG
```

---

## Part 2: Domain Model and Repository (15 min)

### Exercise 2.1: Create the Task Model

Create `src/main/java/com/example/taskapi/model/Task.java`:

```java
package com.example.taskapi.model;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class Task {

    private String id;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Status is required")
    private TaskStatus status;

    @NotNull(message = "Priority is required")
    private TaskPriority priority;

    @Future(message = "Due date must be in the future")
    private LocalDateTime dueDate;

    @Email(message = "Invalid email format")
    private String assignee;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default constructor
    public Task() {
        this.status = TaskStatus.TODO;
        this.priority = TaskPriority.MEDIUM;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor for creating new tasks
    public Task(String title, String description) {
        this();
        this.title = title;
        this.description = description;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Task{id='" + id + "', title='" + title + "', status=" + status + "}";
    }
}

enum TaskStatus {
    TODO, IN_PROGRESS, REVIEW, DONE
}

enum TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}
```

### Exercise 2.2: Create Supporting DTOs

Create `src/main/java/com/example/taskapi/model/TaskStatus.java`:

```java
package com.example.taskapi.model;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,
    DONE
}
```

Create `src/main/java/com/example/taskapi/model/TaskPriority.java`:

```java
package com.example.taskapi.model;

public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
```

### Exercise 2.3: Create the Repository

Create `src/main/java/com/example/taskapi/repository/TaskRepository.java`:

```java
package com.example.taskapi.repository;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TaskRepository {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    // Sink for broadcasting task updates (for SSE)
    private final Sinks.Many<Task> taskUpdates = Sinks.many().multicast().onBackpressureBuffer();

    public TaskRepository() {
        // Seed with sample data
        seedData();
    }

    private void seedData() {
        Task task1 = new Task("Complete project setup", "Set up Spring WebFlux project with all dependencies");
        task1.setId(UUID.randomUUID().toString());
        task1.setStatus(TaskStatus.DONE);
        tasks.put(task1.getId(), task1);

        Task task2 = new Task("Implement CRUD API", "Build complete CRUD endpoints for Task resource");
        task2.setId(UUID.randomUUID().toString());
        task2.setStatus(TaskStatus.IN_PROGRESS);
        tasks.put(task2.getId(), task2);

        Task task3 = new Task("Add validation", "Implement request validation with Bean Validation");
        task3.setId(UUID.randomUUID().toString());
        tasks.put(task3.getId(), task3);

        Task task4 = new Task("Write tests", "Add comprehensive unit and integration tests");
        task4.setId(UUID.randomUUID().toString());
        tasks.put(task4.getId(), task4);
    }

    public Mono<Task> findById(String id) {
        return Mono.justOrEmpty(tasks.get(id))
            .delayElement(Duration.ofMillis(50));  // Simulate DB latency
    }

    public Flux<Task> findAll() {
        return Flux.fromIterable(tasks.values())
            .delayElements(Duration.ofMillis(10));
    }

    public Flux<Task> findByStatus(TaskStatus status) {
        return Flux.fromIterable(tasks.values())
            .filter(task -> task.getStatus() == status)
            .delayElements(Duration.ofMillis(10));
    }

    public Flux<Task> findByAssignee(String assignee) {
        return Flux.fromIterable(tasks.values())
            .filter(task -> assignee.equals(task.getAssignee()))
            .delayElements(Duration.ofMillis(10));
    }

    public Mono<Task> save(Task task) {
        return Mono.fromCallable(() -> {
            if (task.getId() == null) {
                task.setId(UUID.randomUUID().toString());
                task.setCreatedAt(LocalDateTime.now());
            }
            task.setUpdatedAt(LocalDateTime.now());
            tasks.put(task.getId(), task);

            // Broadcast the update
            taskUpdates.tryEmitNext(task);

            return task;
        }).delayElement(Duration.ofMillis(50));
    }

    public Mono<Boolean> deleteById(String id) {
        return Mono.fromCallable(() -> tasks.remove(id) != null)
            .delayElement(Duration.ofMillis(50));
    }

    public Mono<Boolean> existsById(String id) {
        return Mono.just(tasks.containsKey(id));
    }

    public Mono<Long> count() {
        return Mono.just((long) tasks.size());
    }

    // For SSE
    public Flux<Task> getTaskUpdates() {
        return taskUpdates.asFlux();
    }
}
```

---

## Part 3: Service Layer (15 min)

### Exercise 3.1: Create the Service

Create `src/main/java/com/example/taskapi/service/TaskService.java`:

```java
package com.example.taskapi.service;

import com.example.taskapi.exception.TaskNotFoundException;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Mono<Task> findById(String id) {
        log.debug("Finding task by id: {}", id);
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new TaskNotFoundException(id)));
    }

    public Flux<Task> findAll() {
        log.debug("Finding all tasks");
        return repository.findAll();
    }

    public Flux<Task> findByStatus(TaskStatus status) {
        log.debug("Finding tasks by status: {}", status);
        return repository.findByStatus(status);
    }

    public Flux<Task> findByAssignee(String assignee) {
        log.debug("Finding tasks by assignee: {}", assignee);
        return repository.findByAssignee(assignee);
    }

    public Mono<Task> create(Task task) {
        log.debug("Creating task: {}", task.getTitle());
        return repository.save(task)
            .doOnSuccess(saved -> log.info("Task created with id: {}", saved.getId()));
    }

    public Mono<Task> update(String id, Task task) {
        log.debug("Updating task: {}", id);
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
            .flatMap(existing -> {
                task.setId(id);
                task.setCreatedAt(existing.getCreatedAt());
                return repository.save(task);
            })
            .doOnSuccess(updated -> log.info("Task updated: {}", id));
    }

    public Mono<Task> partialUpdate(String id, Task updates) {
        log.debug("Partially updating task: {}", id);
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
            .flatMap(existing -> {
                if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
                if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
                if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
                if (updates.getPriority() != null) existing.setPriority(updates.getPriority());
                if (updates.getDueDate() != null) existing.setDueDate(updates.getDueDate());
                if (updates.getAssignee() != null) existing.setAssignee(updates.getAssignee());
                return repository.save(existing);
            })
            .doOnSuccess(updated -> log.info("Task partially updated: {}", id));
    }

    public Mono<Void> delete(String id) {
        log.debug("Deleting task: {}", id);
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
            .flatMap(existing -> repository.deleteById(id))
            .then()
            .doOnSuccess(v -> log.info("Task deleted: {}", id));
    }

    public Flux<Task> getTaskUpdates() {
        return repository.getTaskUpdates();
    }

    public Mono<Long> count() {
        return repository.count();
    }
}
```

### Exercise 3.2: Create Custom Exception

Create `src/main/java/com/example/taskapi/exception/TaskNotFoundException.java`:

```java
package com.example.taskapi.exception;

public class TaskNotFoundException extends RuntimeException {

    private final String taskId;

    public TaskNotFoundException(String taskId) {
        super("Task not found with id: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
```

---

## Part 4: CRUD Controller (25 min)

### Exercise 4.1: Create the Controller

Create `src/main/java/com/example/taskapi/controller/TaskController.java`:

```java
package com.example.taskapi.controller;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // CREATE
    @PostMapping
    public Mono<ResponseEntity<Task>> create(@Valid @RequestBody Task task) {
        log.info("POST /api/tasks - Creating task: {}", task.getTitle());
        return taskService.create(task)
            .map(created -> ResponseEntity
                .created(URI.create("/api/tasks/" + created.getId()))
                .body(created));
    }

    // READ ONE
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Task>> getById(@PathVariable String id) {
        log.info("GET /api/tasks/{}", id);
        return taskService.findById(id)
            .map(ResponseEntity::ok);
        // Note: TaskNotFoundException will be thrown if not found
        // and handled by GlobalExceptionHandler
    }

    // READ ALL with optional filtering
    @GetMapping
    public Flux<Task> getAll(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String assignee) {

        log.info("GET /api/tasks - status={}, assignee={}", status, assignee);

        if (status != null) {
            return taskService.findByStatus(status);
        }
        if (assignee != null) {
            return taskService.findByAssignee(assignee);
        }
        return taskService.findAll();
    }

    // UPDATE (full replacement)
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Task>> update(
            @PathVariable String id,
            @Valid @RequestBody Task task) {

        log.info("PUT /api/tasks/{}", id);
        return taskService.update(id, task)
            .map(ResponseEntity::ok);
    }

    // PATCH (partial update)
    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Task>> partialUpdate(
            @PathVariable String id,
            @RequestBody Task updates) {

        log.info("PATCH /api/tasks/{}", id);
        return taskService.partialUpdate(id, updates)
            .map(ResponseEntity::ok);
    }

    // DELETE
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        log.info("DELETE /api/tasks/{}", id);
        return taskService.delete(id);
    }

    // COUNT
    @GetMapping("/count")
    public Mono<Long> count() {
        return taskService.count();
    }

    // STREAM updates via SSE
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Task> streamUpdates() {
        log.info("GET /api/tasks/stream - SSE connection established");
        return taskService.getTaskUpdates()
            .doOnCancel(() -> log.info("SSE connection closed"));
    }
}
```

### Exercise 4.2: Test the Basic Endpoints

Start the application:

```bash
mvn spring-boot:run
```

Test with curl:

```bash
# Get all tasks
curl http://localhost:8080/api/tasks | jq

# Get task by ID (use an ID from the previous response)
curl http://localhost:8080/api/tasks/{id} | jq

# Create a new task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Learn WebFlux",
    "description": "Complete all WebFlux chapters",
    "status": "TODO",
    "priority": "HIGH"
  }' | jq

# Update a task
curl -X PUT http://localhost:8080/api/tasks/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Learn WebFlux (Updated)",
    "description": "Complete all WebFlux chapters and labs",
    "status": "IN_PROGRESS",
    "priority": "HIGH"
  }' | jq

# Partial update
curl -X PATCH http://localhost:8080/api/tasks/{id} \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}' | jq

# Delete a task
curl -X DELETE http://localhost:8080/api/tasks/{id}

# Filter by status
curl http://localhost:8080/api/tasks?status=TODO | jq
```

---

## Part 5: Validation (20 min)

### Exercise 5.1: Create Error Response DTO

Create `src/main/java/com/example/taskapi/dto/ErrorResponse.java`:

```java
package com.example.taskapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> errors;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String code, String message) {
        this();
        this.code = code;
        this.message = message;
    }

    public ErrorResponse(String code, String message, List<FieldError> errors) {
        this(code, message);
        this.errors = errors;
    }

    // Getters and Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<FieldError> getErrors() { return errors; }
    public void setErrors(List<FieldError> errors) { this.errors = errors; }

    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;

        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
        public Object getRejectedValue() { return rejectedValue; }
    }
}
```

### Exercise 5.2: Test Validation

Test that validation works:

```bash
# Try to create task with empty title
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "",
    "status": "TODO",
    "priority": "HIGH"
  }' | jq

# Try to create task with invalid email
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Task",
    "assignee": "not-an-email",
    "status": "TODO",
    "priority": "HIGH"
  }' | jq

# Try to create task with past due date
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Task",
    "dueDate": "2020-01-01T00:00:00",
    "status": "TODO",
    "priority": "HIGH"
  }' | jq
```

---

## Part 6: Error Handling (20 min)

### Exercise 6.1: Create Global Exception Handler

Create `src/main/java/com/example/taskapi/exception/GlobalExceptionHandler.java`:

```java
package com.example.taskapi.exception;

import com.example.taskapi.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTaskNotFound(
            TaskNotFoundException ex,
            ServerWebExchange exchange) {

        log.warn("Task not found: {}", ex.getTaskId());

        ErrorResponse error = new ErrorResponse(
            "TASK_NOT_FOUND",
            ex.getMessage()
        );
        error.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationError(
            WebExchangeBindException ex,
            ServerWebExchange exchange) {

        log.warn("Validation error: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(
                fe.getField(),
                fe.getDefaultMessage(),
                fe.getRejectedValue()
            ))
            .collect(Collectors.toList());

        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Request validation failed",
            fieldErrors
        );
        error.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.badRequest().body(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex,
            ServerWebExchange exchange) {

        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            "BAD_REQUEST",
            ex.getMessage()
        );
        error.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.badRequest().body(error));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange) {

        log.error("Unexpected error", ex);

        ErrorResponse error = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred"
        );
        error.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error));
    }
}
```

### Exercise 6.2: Test Error Handling

```bash
# Test 404 - Task not found
curl http://localhost:8080/api/tasks/nonexistent-id | jq

# Test validation error
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": ""}' | jq

# Expected response for validation error:
# {
#   "code": "VALIDATION_ERROR",
#   "message": "Request validation failed",
#   "timestamp": "2024-01-01T12:00:00",
#   "path": "/api/tasks",
#   "errors": [
#     {
#       "field": "title",
#       "message": "Title is required",
#       "rejectedValue": ""
#     }
#   ]
# }
```

---

## Part 7: Server-Sent Events (15 min)

### Exercise 7.1: Test SSE Endpoint

Open a terminal and start listening to the SSE stream:

```bash
curl -N http://localhost:8080/api/tasks/stream
```

In another terminal, create/update tasks:

```bash
# Create a task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "SSE Test Task",
    "status": "TODO",
    "priority": "HIGH"
  }'

# Update a task
curl -X PATCH http://localhost:8080/api/tasks/{id} \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
```

You should see the updates appearing in the SSE terminal!

### Exercise 7.2: Create an HTML Client for SSE

Create `src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Task Updates - SSE Demo</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        h1 {
            color: #333;
        }
        .status {
            padding: 10px;
            border-radius: 4px;
            margin-bottom: 10px;
        }
        .connected {
            background-color: #d4edda;
            color: #155724;
        }
        .disconnected {
            background-color: #f8d7da;
            color: #721c24;
        }
        .task-list {
            list-style: none;
            padding: 0;
        }
        .task-item {
            background: white;
            padding: 15px;
            margin-bottom: 10px;
            border-radius: 4px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            animation: fadeIn 0.3s ease-in;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(-10px); }
            to { opacity: 1; transform: translateY(0); }
        }
        .task-title {
            font-weight: bold;
            font-size: 1.1em;
        }
        .task-meta {
            color: #666;
            font-size: 0.9em;
            margin-top: 5px;
        }
        .badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.8em;
            font-weight: 500;
        }
        .badge-todo { background: #ffc107; color: #000; }
        .badge-in-progress { background: #17a2b8; color: #fff; }
        .badge-review { background: #6f42c1; color: #fff; }
        .badge-done { background: #28a745; color: #fff; }
    </style>
</head>
<body>
    <h1>Task Updates (SSE Demo)</h1>

    <div id="status" class="status disconnected">
        Connecting...
    </div>

    <h2>Recent Updates</h2>
    <ul id="taskList" class="task-list"></ul>

    <script>
        const statusEl = document.getElementById('status');
        const taskListEl = document.getElementById('taskList');

        function getBadgeClass(status) {
            const classes = {
                'TODO': 'badge-todo',
                'IN_PROGRESS': 'badge-in-progress',
                'REVIEW': 'badge-review',
                'DONE': 'badge-done'
            };
            return classes[status] || 'badge-todo';
        }

        function addTaskUpdate(task) {
            const li = document.createElement('li');
            li.className = 'task-item';
            li.innerHTML = `
                <div class="task-title">${task.title}</div>
                <div class="task-meta">
                    <span class="badge ${getBadgeClass(task.status)}">${task.status}</span>
                    Priority: ${task.priority} |
                    Updated: ${new Date(task.updatedAt).toLocaleTimeString()}
                </div>
            `;
            taskListEl.insertBefore(li, taskListEl.firstChild);

            // Keep only last 10 updates
            while (taskListEl.children.length > 10) {
                taskListEl.removeChild(taskListEl.lastChild);
            }
        }

        function connect() {
            const eventSource = new EventSource('/api/tasks/stream');

            eventSource.onopen = () => {
                statusEl.className = 'status connected';
                statusEl.textContent = 'Connected - Listening for updates...';
            };

            eventSource.onmessage = (event) => {
                const task = JSON.parse(event.data);
                console.log('Received task update:', task);
                addTaskUpdate(task);
            };

            eventSource.onerror = (error) => {
                console.error('SSE Error:', error);
                statusEl.className = 'status disconnected';
                statusEl.textContent = 'Disconnected - Reconnecting...';
                eventSource.close();
                setTimeout(connect, 3000);
            };
        }

        connect();
    </script>
</body>
</html>
```

Open http://localhost:8080/index.html in your browser and create/update tasks to see live updates!

---

## Part 8: Testing with WebTestClient (25 min)

### Exercise 8.1: Create Integration Tests

Create `src/test/java/com/example/taskapi/TaskApiIntegrationTest.java`:

```java
package com.example.taskapi;

import com.example.taskapi.dto.ErrorResponse;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskPriority;
import com.example.taskapi.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TaskApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldGetAllTasks() {
        webTestClient.get()
            .uri("/api/tasks")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Task.class)
            .hasSize(4);  // Seeded data
    }

    @Test
    void shouldCreateTask() {
        Task newTask = new Task("Integration Test Task", "Created by test");
        newTask.setStatus(TaskStatus.TODO);
        newTask.setPriority(TaskPriority.HIGH);

        webTestClient.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newTask)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody(Task.class)
            .value(task -> {
                assertThat(task.getId()).isNotNull();
                assertThat(task.getTitle()).isEqualTo("Integration Test Task");
                assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            });
    }

    @Test
    void shouldReturnNotFoundForNonexistentTask() {
        webTestClient.get()
            .uri("/api/tasks/nonexistent-id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(ErrorResponse.class)
            .value(error -> {
                assertThat(error.getCode()).isEqualTo("TASK_NOT_FOUND");
                assertThat(error.getMessage()).contains("nonexistent-id");
            });
    }

    @Test
    void shouldValidateTaskOnCreate() {
        Task invalidTask = new Task();
        invalidTask.setTitle("");  // Empty title - should fail validation

        webTestClient.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidTask)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody(ErrorResponse.class)
            .value(error -> {
                assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR");
                assertThat(error.getErrors()).isNotEmpty();
            });
    }

    @Test
    void shouldUpdateTask() {
        // First, create a task
        Task newTask = new Task("Task to Update", "Will be updated");
        newTask.setStatus(TaskStatus.TODO);
        newTask.setPriority(TaskPriority.MEDIUM);

        String taskId = webTestClient.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newTask)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Task.class)
            .returnResult()
            .getResponseBody()
            .getId();

        // Now update it
        Task updatedTask = new Task("Updated Title", "Updated description");
        updatedTask.setStatus(TaskStatus.DONE);
        updatedTask.setPriority(TaskPriority.LOW);

        webTestClient.put()
            .uri("/api/tasks/{id}", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updatedTask)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Task.class)
            .value(task -> {
                assertThat(task.getTitle()).isEqualTo("Updated Title");
                assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
            });
    }

    @Test
    void shouldPartiallyUpdateTask() {
        // First, create a task
        Task newTask = new Task("Task for Patch", "Original description");
        newTask.setStatus(TaskStatus.TODO);
        newTask.setPriority(TaskPriority.HIGH);

        String taskId = webTestClient.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newTask)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Task.class)
            .returnResult()
            .getResponseBody()
            .getId();

        // Partial update - only change status
        Task patch = new Task();
        patch.setStatus(TaskStatus.IN_PROGRESS);

        webTestClient.patch()
            .uri("/api/tasks/{id}", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(patch)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Task.class)
            .value(task -> {
                assertThat(task.getTitle()).isEqualTo("Task for Patch");  // Unchanged
                assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);  // Changed
                assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);  // Unchanged
            });
    }

    @Test
    void shouldDeleteTask() {
        // First, create a task
        Task newTask = new Task("Task to Delete", "Will be deleted");
        newTask.setStatus(TaskStatus.TODO);
        newTask.setPriority(TaskPriority.LOW);

        String taskId = webTestClient.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newTask)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Task.class)
            .returnResult()
            .getResponseBody()
            .getId();

        // Delete it
        webTestClient.delete()
            .uri("/api/tasks/{id}", taskId)
            .exchange()
            .expectStatus().isNoContent();

        // Verify it's gone
        webTestClient.get()
            .uri("/api/tasks/{id}", taskId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void shouldFilterTasksByStatus() {
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/tasks")
                .queryParam("status", "TODO")
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Task.class)
            .value(tasks -> {
                assertThat(tasks).allMatch(task -> task.getStatus() == TaskStatus.TODO);
            });
    }
}
```

### Exercise 8.2: Run the Tests

```bash
mvn test
```

### Exercise 8.3: Add SSE Test

Add to the test class:

```java
@Test
void shouldStreamTaskUpdates() {
    // Create a FluxExchangeResult to consume SSE
    webTestClient.get()
        .uri("/api/tasks/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
        .returnResult(Task.class)
        .getResponseBody()
        .take(1)  // Just take one event for the test
        .timeout(Duration.ofSeconds(10))
        .blockFirst();  // OK in tests
}
```

---

## Part 9: Reflection and Key Takeaways (5 min)

### Discussion Questions

1. **How does returning `Mono<ResponseEntity<T>>` differ from `Mono<T>`?**
   - When would you use each pattern?

2. **What happens if validation fails?**
   - How does the error flow through the system?

3. **Why is the repository using `ConcurrentHashMap` with `Mono.fromCallable()`?**
   - What would happen with a real reactive database?

4. **How would you add pagination to the `getAll` endpoint?**
   - What reactive operators would you use?

5. **What happens if a client disconnects from the SSE stream?**
   - How did we handle this in the code?

### Key Takeaways

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Most Spring MVC patterns transfer directly                            │
│     @RestController, @GetMapping, @Valid all work the same.               │
│                                                                            │
│  2. Return Mono/Flux from controller methods                              │
│     Framework handles subscription automatically.                          │
│                                                                            │
│  3. Exception handling uses familiar patterns                              │
│     @ExceptionHandler and @ControllerAdvice work reactively.              │
│                                                                            │
│  4. SSE is trivially easy                                                  │
│     Just return Flux<T> with TEXT_EVENT_STREAM media type.                │
│                                                                            │
│  5. WebTestClient makes reactive testing clean                            │
│     Fluent API for testing reactive endpoints.                            │
│                                                                            │
│  6. ResponseEntity gives fine-grained control                              │
│     Use Mono<ResponseEntity<T>> when you need headers/status.             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Bonus Challenges

### Challenge 1: Add Pagination

Implement proper pagination with:
- `page` and `size` query parameters
- Return `Page<Task>` or `Slice<Task>` equivalent
- Include total count in response

### Challenge 2: Add Sorting

Add sorting capability:
- `sort` query parameter (e.g., `sort=createdAt,desc`)
- Multiple sort fields
- Validate sort field names

### Challenge 3: Add Search

Implement full-text search:
- Search across title and description
- Case-insensitive
- Return ranked results

### Challenge 4: Add Audit Logging

Add audit logging using:
- `@Around` AOP advice
- Log all create/update/delete operations
- Include user info (from headers)

---

## Running the Application

```bash
# Start the application
mvn spring-boot:run

# Run tests
mvn test

# Package
mvn package

# Run the jar
java -jar target/task-api-1.0.0-SNAPSHOT.jar
```

---

## What's Next?

In Chapter 10, we'll explore **functional endpoints**—an alternative to annotated controllers that offers more flexibility for complex routing. Both styles are equally reactive; the choice is about coding preferences.

---

## Summary

In this lab, you built:

1. ✅ A complete CRUD API with annotated controllers
2. ✅ Input validation with Bean Validation
3. ✅ Global error handling with `@ControllerAdvice`
4. ✅ Server-Sent Events for real-time updates
5. ✅ A simple HTML client for SSE
6. ✅ Integration tests with WebTestClient

**Congratulations!** You now have a production-quality template for building reactive REST APIs with Spring WebFlux.
