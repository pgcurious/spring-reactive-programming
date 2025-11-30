# First Principles: Deriving the Annotated Controller Pattern

*Forget that Spring MVC or WebFlux exist. Let's derive how a web framework should handle HTTP requests from fundamental requirements.*

---

## The Starting Point

We have a fundamental problem: translate HTTP requests into application logic.

**Given:**
- HTTP requests arrive as text (method, path, headers, body)
- We have Java methods that implement business logic
- We need to connect HTTP to Java cleanly

**Goal:**
- Make writing web handlers as easy as writing regular methods
- Keep the "web" parts separate from business logic
- Handle the repetitive work automatically

---

## Step 1: The Raw HTTP Problem

Let's see what HTTP actually looks like:

```
HTTP Request (raw text):
─────────────────────────────────────────────────────────

POST /api/users HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
Authorization: Bearer eyJhbGc...

{"name": "Alice", "email": "alice@example.com"}


HTTP Response (raw text):
─────────────────────────────────────────────────────────

HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/users/123

{"id": "123", "name": "Alice", "email": "alice@example.com"}
```

To handle this without a framework, you'd need:

```java
// Without a framework (pseudo-code)
public void handleRequest(RawHttpRequest request, RawHttpResponse response) {
    // 1. Parse the HTTP method
    String method = parseMethod(request);

    // 2. Parse the URL path
    String path = parsePath(request);

    // 3. Match path to handler
    if ("POST".equals(method) && path.matches("/api/users")) {
        // 4. Parse headers
        String contentType = request.getHeader("Content-Type");
        String authorization = request.getHeader("Authorization");

        // 5. Validate content type
        if (!"application/json".equals(contentType)) {
            response.setStatus(415);  // Unsupported Media Type
            return;
        }

        // 6. Parse request body
        String body = readBody(request);
        User user = jsonParser.parse(body, User.class);

        // 7. Validate the user object
        List<String> errors = validate(user);
        if (!errors.isEmpty()) {
            response.setStatus(400);
            response.setBody(errorsToJson(errors));
            return;
        }

        // 8. Actually do the business logic
        User saved = userService.save(user);

        // 9. Serialize the response
        String responseJson = jsonParser.toJson(saved);

        // 10. Set response headers
        response.setStatus(201);
        response.setHeader("Content-Type", "application/json");
        response.setHeader("Location", "/api/users/" + saved.getId());
        response.setBody(responseJson);
    }
    // ... handle other routes
}
```

**Problems:**
- Massive boilerplate for every endpoint
- Business logic buried in infrastructure code
- Easy to forget error handling, headers, etc.
- Hard to test (everything is tangled)

---

## Step 2: What We Really Want

Look at the business logic buried in Step 1:

```java
User saved = userService.save(user);
```

That's ONE line. Everything else is ceremony.

**Insight #1**: We want to write just the business logic. The framework should handle the rest.

The ideal API would be:

```java
// DREAM: What we WANT to write
public User createUser(User user) {
    return userService.save(user);
}
```

But how does the framework know:
- What HTTP method triggers this?
- What URL path maps to it?
- Where does `user` come from?
- What status code to return?

---

## Step 3: Deriving Metadata Annotations

We need to attach metadata to our methods. Java provides annotations for this.

### Route Metadata

```java
// We need to say: "This method handles POST to /api/users"

@HttpMethod("POST")
@Path("/api/users")
public User createUser(User user) {
    return userService.save(user);
}
```

But method + path often go together. Let's combine them:

```java
// Cleaner: Combined annotation
@Route(method = "POST", path = "/api/users")
public User createUser(User user) {
    return userService.save(user);
}

// Even cleaner: Method-specific annotation
@PostMapping("/api/users")
public User createUser(User user) {
    return userService.save(user);
}
```

### Parameter Metadata

Where does `user` come from? We need to tell the framework:

```java
// "Read the request body and deserialize as User"
@PostMapping("/api/users")
public User createUser(@RequestBody User user) {
    return userService.save(user);
}
```

For URL parameters:

```java
// "Extract 'id' from /api/users/{id}"
@GetMapping("/api/users/{id}")
public User getUser(@PathVariable String id) {
    return userService.findById(id);
}

// "Extract 'status' from ?status=active"
@GetMapping("/api/users")
public List<User> getUsers(@RequestParam String status) {
    return userService.findByStatus(status);
}
```

### Response Metadata

```java
// "Return HTTP 201 Created"
@PostMapping("/api/users")
@ResponseStatus(HttpStatus.CREATED)
public User createUser(@RequestBody User user) {
    return userService.save(user);
}
```

---

## Step 4: Deriving Controller Grouping

Related endpoints share common properties:

```java
@PostMapping("/api/users")
public User createUser(...) { ... }

@GetMapping("/api/users/{id}")
public User getUser(...) { ... }

@GetMapping("/api/users")
public List<User> getAllUsers(...) { ... }

@DeleteMapping("/api/users/{id}")
public void deleteUser(...) { ... }
```

All share `/api/users` as a base path. Let's factor that out:

```java
@RequestMapping("/api/users")  // Common base path
public class UserController {

    @PostMapping  // Inherits /api/users
    public User createUser(...) { ... }

    @GetMapping("/{id}")  // Becomes /api/users/{id}
    public User getUser(...) { ... }

    @GetMapping  // Stays /api/users
    public List<User> getAllUsers(...) { ... }

    @DeleteMapping("/{id}")  // Becomes /api/users/{id}
    public void deleteUser(...) { ... }
}
```

We need to mark this class as a web handler:

```java
@Controller  // "This class contains web handlers"
@RequestMapping("/api/users")
public class UserController { ... }
```

For REST APIs, responses should be JSON by default:

```java
@RestController  // @Controller + @ResponseBody (always return JSON)
@RequestMapping("/api/users")
public class UserController { ... }
```

---

## Step 5: The Framework's Job

Now the framework has all the metadata it needs. Here's what it does:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FRAMEWORK RESPONSIBILITIES                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  At Startup:                                                               │
│  ───────────                                                               │
│  1. Scan for @Controller/@RestController classes                          │
│  2. Build a routing table from @RequestMapping annotations                │
│  3. Analyze method parameters for binding rules                           │
│                                                                            │
│  Per Request:                                                              │
│  ────────────                                                              │
│  1. Parse HTTP request (method, path, headers, body)                      │
│  2. Match to handler method using routing table                           │
│  3. Extract parameters (@PathVariable, @RequestParam, etc.)               │
│  4. Deserialize @RequestBody if present                                   │
│  5. Validate if @Valid is present                                         │
│  6. Invoke the handler method                                             │
│  7. Serialize the return value to JSON                                    │
│  8. Set appropriate response headers and status                           │
│  9. Handle exceptions if any                                              │
│                                                                            │
│  What YOU Write:                                                           │
│  ───────────────                                                           │
│  • Business logic only                                                    │
│  • Annotations describing the HTTP contract                               │
│  • Exception handlers for error cases                                     │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 6: Adapting for Reactive

Everything we derived so far works for traditional blocking code. Now let's adapt for reactive.

### The Challenge

In blocking code:

```java
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userService.findById(id);  // Returns User, blocks until ready
}
```

Thread waits inside `findById()`. Response is sent when method returns.

In reactive code:

```java
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id);  // Returns Mono<User> immediately!
}
```

Method returns immediately with a `Mono<User>`. The user isn't available yet.

### The Solution

The framework must:
1. Accept reactive return types (`Mono`, `Flux`)
2. Subscribe to them when ready to write the response
3. Stream the response as data becomes available

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE REQUEST FLOW                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Traditional (Blocking):                                                   │
│  ──────────────────────                                                    │
│                                                                            │
│  Request ──► Call handler ──► [Wait for data] ──► Return ──► Response     │
│              │                    │                  │                    │
│              └────────────────────┴──────────────────┘                    │
│                    Thread blocked entire time                              │
│                                                                            │
│  Reactive (Non-blocking):                                                  │
│  ───────────────────────                                                   │
│                                                                            │
│  Request ──► Call handler ──► Return Mono ──► (thread free)              │
│              │                  │                                         │
│              │                  │  Framework subscribes                   │
│              │                  │         │                               │
│              │                  │         ▼                               │
│              │                  │  When data ready: serialize & send      │
│              │                  │                                         │
│              └──────────────────┴─────────────────────────────────────   │
│                    Method returns instantly, thread moves on              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Return Type Mapping

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    RETURN TYPE ADAPTATION                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Blocking Return Type          Reactive Return Type                       │
│  ─────────────────────         ─────────────────────                       │
│                                                                            │
│  T                       ────► Mono<T>                                    │
│  - Single value                - 0 or 1 value, async                      │
│                                                                            │
│  List<T>                 ────► Flux<T>                                    │
│  - Collection                  - 0 to N values, async                     │
│                                                                            │
│  void                    ────► Mono<Void>                                 │
│  - No return                   - Just completion signal                   │
│                                                                            │
│  ResponseEntity<T>       ────► Mono<ResponseEntity<T>>                    │
│  - Control headers/status      - Same, but async                          │
│                                                                            │
│  Framework handles:                                                        │
│  - Subscribing to the Mono/Flux                                          │
│  - Writing response when data arrives                                    │
│  - Streaming for Flux (with text/event-stream)                          │
│  - Error handling if Mono/Flux emits error                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 7: Special Case - Streaming Responses

`Flux<T>` enables a new capability: streaming responses.

### Regular Flux (JSON Array)

```java
@GetMapping
public Flux<User> getAllUsers() {
    return userService.findAll();
}

// Response: Collects all, then sends as JSON array
// Content-Type: application/json
// Body: [{"id":"1","name":"Alice"},{"id":"2","name":"Bob"}]
```

### Streaming Flux (SSE)

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<User> streamUsers() {
    return userService.findAll();
}

// Response: Sends each item as it arrives
// Content-Type: text/event-stream
// Body:
//   data: {"id":"1","name":"Alice"}
//
//   data: {"id":"2","name":"Bob"}
//
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    FLUX RESPONSE MODES                                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Content-Type: application/json                                           │
│  ──────────────────────────────                                            │
│                                                                            │
│  Flux emits:  [1] ──► [2] ──► [3] ──► |                                   │
│                                                                            │
│  Framework:   Collects ─────────────► Serializes array ──► Sends          │
│                                                                            │
│  Client sees: [1,2,3] (all at once, after complete)                       │
│                                                                            │
│  ─────────────────────────────────────────────────────────────────────    │
│                                                                            │
│  Content-Type: text/event-stream (SSE)                                    │
│  ─────────────────────────────────────                                     │
│                                                                            │
│  Flux emits:  [1] ──────────► [2] ──────────► [3] ──► |                   │
│                │                │                │                        │
│  Framework:    │                │                │                        │
│                ▼                ▼                ▼                        │
│               Send            Send             Send                       │
│                                                                            │
│  Client sees: "data: 1" ... "data: 2" ... "data: 3" (as they arrive)     │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 8: Exception Handling in Reactive Context

Exceptions in reactive streams are signals, not thrown exceptions.

### The Problem

```java
// This doesn't work the way you might expect
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .switchIfEmpty(???);  // How to return 404?
}
```

### The Solution

Errors are signals. The framework converts error signals to HTTP responses:

```java
@GetMapping("/{id}")
public Mono<User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    // Error signal will be caught by exception handler
}

@ExceptionHandler(UserNotFoundException.class)
public Mono<ResponseEntity<ErrorResponse>> handleNotFound(UserNotFoundException ex) {
    return Mono.just(ResponseEntity.notFound().build());
}
```

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ERROR FLOW IN REACTIVE                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Mono chain:                                                               │
│                                                                            │
│  findById(id) ──► switchIfEmpty(Mono.error(...)) ──► X (error signal)     │
│                                                      │                    │
│  Framework catches error signal                      │                    │
│                                                      ▼                    │
│                                               @ExceptionHandler          │
│                                                      │                    │
│                                                      ▼                    │
│                                            Mono<ResponseEntity>           │
│                                                      │                    │
│                                                      ▼                    │
│                                              HTTP 404 Response            │
│                                                                            │
│  Traditional try-catch DOESN'T WORK:                                      │
│  ────────────────────────────────────                                      │
│                                                                            │
│  try {                                                                     │
│      return userService.findById(id);  // Returns immediately!           │
│  } catch (UserNotFoundException e) {   // Never catches anything         │
│      // Exception happens LATER when Mono is subscribed                  │
│  }                                                                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 9: Why Annotations Work So Well

Let's appreciate what we've derived:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> create(@Valid @RequestBody User user) {
        return userService.save(user);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return Mono.just(new ErrorResponse(ex.getMessage()));
    }
}
```

**What the annotations tell us:**
- `@RestController`: This is a REST API controller
- `@RequestMapping("/api/users")`: Base path is /api/users
- `@GetMapping("/{id}")`: GET request to /api/users/{id}
- `@PathVariable`: Extract `id` from the URL
- `@PostMapping`: POST request to /api/users
- `@Valid @RequestBody`: Deserialize and validate request body
- `@ResponseStatus(HttpStatus.CREATED)`: Return 201 on success
- `@ExceptionHandler`: Handle this exception type

**What the code does:**
- Just business logic!
- `userService.findById(id)` - find a user
- `userService.save(user)` - save a user

**Separation achieved:**
- HTTP concerns are in annotations (metadata)
- Business logic is in code
- Framework handles the translation

---

## Step 10: The Complete Mental Model

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ANNOTATED CONTROLLER MENTAL MODEL                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Annotations = Contract with the Framework                                │
│  ────────────────────────────────────────                                  │
│                                                                            │
│  @GetMapping("/{id}")     "When GET /api/users/{id} arrives..."           │
│  @PathVariable String id  "...extract 'id' from URL..."                   │
│  → Mono<User>             "...and I'll give you a Mono that produces User"│
│                                                                            │
│  Framework's Promise:                                                      │
│  ───────────────────                                                       │
│  "I will:"                                                                 │
│  • Route matching requests to your method                                 │
│  • Extract and convert parameters                                         │
│  • Deserialize request bodies                                             │
│  • Validate if @Valid is present                                          │
│  • Subscribe to your Mono/Flux                                            │
│  • Serialize responses to JSON                                            │
│  • Set appropriate headers and status codes                               │
│  • Convert errors to HTTP error responses                                 │
│                                                                            │
│  Your Promise:                                                             │
│  ─────────────                                                             │
│  "I will:"                                                                 │
│  • Return a Mono/Flux that eventually produces the data                   │
│  • Signal errors as error signals, not thrown exceptions                  │
│  • Never block inside the method                                          │
│                                                                            │
│  The Result:                                                               │
│  ───────────                                                               │
│  • Clean separation between HTTP and business logic                       │
│  • Testable business methods (just test the Mono/Flux)                   │
│  • Non-blocking throughout                                                │
│  • Familiar to Spring MVC developers                                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## What We've Derived

Starting from "how to connect HTTP to Java," we derived:

1. **Annotations for metadata**: Tell the framework what each method handles
2. **Parameter annotations**: Tell the framework where data comes from
3. **Controller grouping**: Share common configuration across related endpoints
4. **Reactive adaptation**: Return `Mono`/`Flux` instead of direct values
5. **Streaming**: `Flux` + SSE media type enables real-time streaming
6. **Error as signal**: Exception handlers catch error signals

This IS how Spring WebFlux annotated controllers work. The design is a logical consequence of separating HTTP concerns from business logic.

---

## Why This Design Scales

```
┌────────────────────────────────────────────────────────────────────────────┐
│  THE BENEFITS OF THIS DESIGN:                                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. Familiarity                                                            │
│     Same patterns as Spring MVC. Low learning curve.                      │
│                                                                            │
│  2. Testability                                                            │
│     Test business logic in isolation.                                     │
│     Test HTTP mapping separately.                                         │
│                                                                            │
│  3. Maintainability                                                        │
│     HTTP contract is visible in annotations.                              │
│     Business logic is clean and focused.                                  │
│                                                                            │
│  4. Flexibility                                                            │
│     Return Mono<T> for single values.                                     │
│     Return Flux<T> for collections or streams.                            │
│     Return Mono<ResponseEntity<T>> for full control.                      │
│                                                                            │
│  5. Performance                                                            │
│     Non-blocking end-to-end.                                              │
│     Stream large responses without buffering.                             │
│     SSE for real-time updates.                                            │
│                                                                            │
│  6. Consistency                                                            │
│     Same exception handling patterns.                                     │
│     Same validation patterns.                                             │
│     Same security patterns.                                               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## The Path Forward

With this foundation:
- **Chapter 10**: Functional endpoints (an alternative to annotations)
- **Chapter 11**: WebClient (calling external services reactively)
- **Chapter 12**: WebSockets and streaming

Annotated controllers are the most familiar approach. Functional endpoints offer more flexibility when needed.

---

*"Simplicity is the ultimate sophistication." — Leonardo da Vinci*

Annotations hide the complexity of HTTP handling behind simple declarations. The sophistication is in the framework that interprets them.
