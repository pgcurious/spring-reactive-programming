# Spring Reactive Programming: The Why Before The How

## Book Philosophy

> "Tell me and I forget, teach me and I may remember, involve me and I learn." — Benjamin Franklin

This book takes a fundamentally different approach to teaching Spring Reactive Programming. Instead of jumping straight into code and APIs, we build **deep intuition** by answering "why" at every step. By the time you write your first reactive code, you'll understand *why* it exists, *why* it's designed the way it is, and *why* you'd choose it over traditional approaches.

---

## Target Audience

- Java developers with Spring Boot experience (MVC/traditional)
- Developers curious about reactive programming but overwhelmed by the paradigm shift
- Engineers who want to understand the fundamentals, not just copy-paste code
- Architects evaluating reactive for their systems

---

## Book Structure Overview

The book is divided into **5 Parts** with **20 Chapters**, progressing from foundational concepts to production-ready applications.

```
Part I   : The Problem (Chapters 1-3)     → Why reactive exists
Part II  : The Foundation (Chapters 4-7)  → Core reactive concepts
Part III : Spring WebFlux (Chapters 8-12) → Spring's reactive web stack
Part IV  : Data & Integration (Ch 13-16)  → Reactive data access & messaging
Part V   : Production (Chapters 17-20)    → Real-world concerns
```

---

# PART I: THE PROBLEM
## *Understanding Why Reactive Programming Exists*

---

## Chapter 1: The Blocking World We Live In

### Why This Chapter?
Before understanding reactive, you must deeply understand what's "wrong" with traditional blocking I/O. This chapter creates the visceral understanding of the problem reactive programming solves.

### Chapter Outline

#### 1.1 A Day in the Life of a Thread
- What actually happens when your code calls `userRepository.findById()`?
- The journey from Java code → JVM → OS → Network → Database → back
- **Visualization**: Timeline diagram showing a thread's life during a blocking call
- **Key Insight**: The thread is doing *nothing* for 99% of the time

#### 1.2 The Thread-Per-Request Model
- How traditional servlet containers (Tomcat) handle requests
- Why this model worked for 20 years
- **Hands-on Exercise**: Measure thread pool behavior under load
- Code example: Simple Spring MVC app with Thread.currentThread() logging

#### 1.3 The Cost of Waiting
- Memory cost: 1MB+ per thread (stack size)
- Context switching overhead: CPU cycles lost
- Scalability ceiling: Why 200 threads ≠ 200 concurrent users at scale
- **Calculation Exercise**: How many concurrent users can your server really handle?

#### 1.4 The Breaking Point
- What happens when threads run out?
- Connection timeouts, cascading failures, and the "thundering herd"
- **Real-world story**: How a slow database brought down an entire microservices cluster
- **Mental Model**: The restaurant analogy (waiters waiting for the kitchen)

#### 1.5 Summary: The Questions We Must Answer
- How can we handle 10,000 concurrent connections with 10 threads?
- How can we stop wasting resources on waiting?
- How can we build systems that gracefully handle backpressure?

### Hands-On Lab 1
- Create a Spring MVC application
- Add deliberate delays to simulate slow external services
- Load test with Apache Bench or wrk
- Observe thread pool exhaustion
- **Deliverable**: Metrics showing the problem visually

---

## Chapter 2: The Evolution of Concurrency in Java

### Why This Chapter?
Reactive programming didn't appear in a vacuum. Understanding the evolution of Java's concurrency models helps you appreciate why reactive is the logical next step.

### Chapter Outline

#### 2.1 The Thread Era (Java 1.0 - 1.4)
- Manual thread management
- The problems: complexity, resource waste, hard to reason about
- Code example: Classic producer-consumer with wait/notify

#### 2.2 The Executor Era (Java 5)
- Thread pools and the Executor framework
- Better, but still fundamentally blocking
- Code example: Same problem solved with ExecutorService

#### 2.3 The Future Era (Java 5-7)
- Futures and Callable
- The problem: Future.get() still blocks!
- Why callbacks emerged and their problems

#### 2.4 The CompletableFuture Era (Java 8)
- Composition without blocking
- **Key Insight**: This is proto-reactive!
- Code example: Chaining async operations
- Limitations: No backpressure, limited operators

#### 2.5 The Reactive Era (Java 9+)
- java.util.concurrent.Flow
- The standardization of reactive streams
- Why this became necessary

#### 2.6 The Pattern Emerges
- **Mental Model**: From "waiting for results" to "reacting to results"
- The paradigm shift: Don't call us, we'll call you

### Hands-On Lab 2
- Implement the same service call pattern using:
  - Blocking thread
  - Future with get()
  - CompletableFuture chains
  - Compare and contrast the approaches

---

## Chapter 3: What is Reactive Programming, Really?

### Why This Chapter?
Strip away the frameworks and libraries. What is the core idea? This chapter builds the mental model you'll use throughout the book.

### Chapter Outline

#### 3.1 Reactive: A Definition That Actually Makes Sense
- Not about speed (sometimes it's slower!)
- Not about callbacks (that's an implementation detail)
- **Core Definition**: Programming with asynchronous data streams
- **Key Insight**: Everything can be a stream

#### 3.2 The Four Pillars of Reactive Systems
- Responsive: Always respond in a timely manner
- Resilient: Stay responsive in the face of failure
- Elastic: Stay responsive under varying workload
- Message-Driven: Rely on asynchronous message passing
- **Why This Matters**: Reactive programming enables reactive systems

#### 3.3 The Publisher-Subscriber Model
- Publishers: Sources of data
- Subscribers: Consumers of data
- Subscriptions: The contract between them
- **Analogy**: YouTube subscriptions vs. refreshing a page manually

#### 3.4 Backpressure: The Superpower
- What happens when producers are faster than consumers?
- Traditional approach: Buffer until OOM, or drop silently
- Reactive approach: Subscribers control the flow
- **Analogy**: Drinking from a fire hose vs. a water fountain you control
- **Key Insight**: Backpressure is what makes reactive "safe"

#### 3.5 Push vs. Pull vs. Push-Pull
- Iterator pattern: Pull (I ask for next)
- Observer pattern: Push (You give me next)
- Reactive Streams: Hybrid (I tell you how many I want)
- **Diagram**: Comparing the three patterns

#### 3.6 The Reactive Streams Specification
- The four interfaces: Publisher, Subscriber, Subscription, Processor
- Why standardization matters
- The rules (and why they exist)

### Hands-On Lab 3
- Implement a simple Publisher and Subscriber from scratch (no libraries)
- Experience the complexity firsthand
- **Insight**: Now you understand why we need libraries like Reactor

---

# PART II: THE FOUNDATION
## *Mastering Project Reactor*

---

## Chapter 4: Meet Project Reactor

### Why This Chapter?
Reactor is the engine that powers Spring WebFlux. Understanding Reactor deeply is essential before using WebFlux.

### Chapter Outline

#### 4.1 Why Reactor? (Among All Reactive Libraries)
- Brief history: RxJava, Reactor, Akka Streams
- Reactor's position: Native to Spring, optimized for Java 8+
- Why Spring chose Reactor

#### 4.2 Mono and Flux: The Two Building Blocks
- **Mono**: A stream of 0 or 1 element
  - Why not just use Optional? (Async + lazy + operators)
  - When to use Mono
- **Flux**: A stream of 0 to N elements
  - Why not just use List? (Async + lazy + operators + backpressure)
  - When to use Flux
- **Key Insight**: Think of them as "future collections that can push to you"

#### 4.3 Cold vs. Hot Publishers
- Cold: Each subscriber gets all the data from the start (replaying a video)
- Hot: Subscribers get data from when they subscribe (live TV)
- **Why This Matters**: Completely changes how you design systems
- Code examples showing the difference

#### 4.4 Nothing Happens Until You Subscribe
- Laziness as a feature, not a bug
- The reactive assembly line vs. the assembly line running
- **Common Mistake #1**: Building a Mono/Flux but never subscribing
- **Mental Model**: Blueprints vs. buildings

#### 4.5 Setting Up Your Development Environment
- Project setup with Maven/Gradle
- Essential dependencies
- IDE plugins and tips

### Hands-On Lab 4
- Create a simple Reactor project
- Build your first Mono and Flux
- Experiment with subscription
- Prove to yourself that nothing happens without subscribe

---

## Chapter 5: Thinking in Streams

### Why This Chapter?
The biggest hurdle in reactive programming isn't the API—it's the mental shift. This chapter rewires your thinking from imperative to reactive.

### Chapter Outline

#### 5.1 From Loops to Streams
- Imperative: `for (User u : users) { process(u); }`
- Reactive: `users.flatMap(this::process)`
- **Key Insight**: Describe what should happen, not how to do it step by step

#### 5.2 The Operator Mental Model
- Operators as assembly line stations
- Data flows through, operators transform
- **Diagram**: The marble diagram explained
- How to read marble diagrams (you'll see them everywhere)

#### 5.3 Immutability and the Pipeline
- Each operator returns a new Publisher
- The original is unchanged
- Why this enables composition and reasoning

#### 5.4 Transforming Data: map, flatMap, filter
- `map`: Transform each element (1:1)
- `flatMap`: Transform each element to a stream, then flatten (1:N)
- `filter`: Keep elements matching a predicate
- **Common Mistake #2**: Using map when you need flatMap

#### 5.5 Combining Streams: zip, merge, concat
- `zip`: Pair elements from multiple streams (wait for all)
- `merge`: Interleave elements (first come, first served)
- `concat`: One stream after another (sequential)
- **When to Use Each**: Real-world scenarios

#### 5.6 Reducing and Collecting: reduce, collect, count
- Aggregating stream elements
- Terminal vs. intermediate operations
- The output type changes (Flux<T> → Mono<R>)

### Hands-On Lab 5
- Transform a traditional for-loop program into reactive streams
- Combine data from multiple sources
- Practice with marble diagrams
- **Challenge**: Build a reactive pipeline that would be complex imperatively

---

## Chapter 6: Error Handling and Resilience

### Why This Chapter?
Real applications fail. Reactive programming has unique patterns for error handling that differ significantly from try-catch. Master these to build resilient systems.

### Chapter Outline

#### 6.1 Errors as First-Class Citizens
- In reactive streams, errors are signals like data
- The onError signal terminates the stream
- **Why Traditional Try-Catch Doesn't Work**: Async execution context

#### 6.2 The Error Channel
- Every stream has three channels: onNext, onError, onComplete
- Errors propagate downstream
- **Diagram**: Error flow through a pipeline

#### 6.3 Recovering from Errors: onErrorReturn, onErrorResume
- `onErrorReturn`: Provide a fallback value
- `onErrorResume`: Switch to a fallback stream
- `onErrorMap`: Transform the error
- **When to Use Each**: Decision tree

#### 6.4 Retry Strategies: retry, retryWhen
- Simple retry: `retry(3)`
- Retry with backoff: `retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))`
- **Key Insight**: Don't hammer failing services
- Retry budgets and circuit breakers

#### 6.5 Timeouts: timeout
- Why timeouts are critical in reactive systems
- Setting appropriate timeouts
- Combining timeout with fallback

#### 6.6 The doOn* Operators for Debugging
- `doOnError`: Side effect on error (logging)
- `doOnNext`: Side effect on each element
- `doOnComplete`: Side effect on completion
- **Best Practice**: Use for logging, not business logic

### Hands-On Lab 6
- Build a resilient service client with:
  - Timeout handling
  - Retry with exponential backoff
  - Fallback responses
  - Comprehensive error logging

---

## Chapter 7: Advanced Reactor Patterns

### Why This Chapter?
Real-world reactive applications need more than basic operators. This chapter covers the patterns you'll actually use in production.

### Chapter Outline

#### 7.1 Controlling Concurrency: flatMap with Concurrency Parameter
- `flatMap(x -> process(x), 10)`: Limit concurrent inner subscriptions
- Why unlimited concurrency can be dangerous
- Finding the right concurrency level

#### 7.2 Batching and Buffering: buffer, window
- `buffer`: Collect elements into Lists
- `window`: Split into sub-streams
- Use cases: Bulk database inserts, rate limiting

#### 7.3 Grouping: groupBy
- Splitting a stream by key
- Processing groups independently
- **Gotcha**: GroupedFlux must be consumed or cancelled

#### 7.4 Context Propagation
- The problem: No thread-local storage in reactive world
- Reactor Context: A immutable key-value store passed through the chain
- Use cases: Trace IDs, user authentication
- **Code Pattern**: Writing and reading context

#### 7.5 Schedulers: Where Does Code Run?
- Default: The subscribing thread
- `subscribeOn`: Where the subscription happens
- `publishOn`: Where subsequent operators run
- Scheduler types: immediate, single, parallel, boundedElastic
- **Key Insight**: Control over threading without managing threads

#### 7.6 Testing Reactive Code: StepVerifier
- Why traditional testing doesn't work
- StepVerifier: Assert on elements, errors, completion
- Virtual time: Testing delays without waiting
- **Best Practice**: Always test your reactive pipelines

#### 7.7 Debugging Reactive Code
- Stack traces in reactive world (the problem)
- `checkpoint()`: Add checkpoints to the pipeline
- Hooks.onOperatorDebug(): Full assembly trace (dev only)
- **Tips**: Strategies for finding bugs

### Hands-On Lab 7
- Implement a rate-limited batch processor
- Add context propagation for tracing
- Write comprehensive tests with StepVerifier
- Debug an intentionally broken pipeline

---

# PART III: SPRING WEBFLUX
## *Building Reactive Web Applications*

---

## Chapter 8: Why WebFlux?

### Why This Chapter?
Understand when and why you would choose WebFlux over Spring MVC. This isn't about "WebFlux is better"—it's about the right tool for the job.

### Chapter Outline

#### 8.1 Spring MVC vs. Spring WebFlux: The Architecture
- Spring MVC: Servlet API, thread-per-request
- Spring WebFlux: Reactive Streams, event-loop
- **Diagram**: Request handling in both models

#### 8.2 The Event Loop Model
- How Netty (default WebFlux server) works
- Few threads, many connections
- **Analogy**: A single chef with many pots vs. many chefs with one pot each
- **Key Insight**: Fewer threads ≠ slower processing

#### 8.3 When to Choose WebFlux
- High-concurrency scenarios (many waiting operations)
- Streaming data (server-sent events, websockets)
- Integration with reactive libraries (R2DBC, reactive MongoDB)
- Microservices with many external calls

#### 8.4 When to Stick with Spring MVC
- CPU-bound workloads
- Team familiarity
- Blocking-only dependencies (JDBC, JPA)
- **Honest Assessment**: WebFlux isn't always the answer

#### 8.5 The Functional vs. Annotated Model
- Annotated controllers: Familiar `@RestController` style
- Functional endpoints: RouterFunction/HandlerFunction
- When to use which
- **Key Insight**: Both are fully reactive under the hood

#### 8.6 Non-Blocking All the Way Down
- Why one blocking call ruins everything
- The chain must be fully reactive
- Tools to detect blocking calls

### Hands-On Lab 8
- Create a WebFlux project with Spring Initializr
- Build a simple endpoint with both styles
- Compare behavior with equivalent MVC app

---

## Chapter 9: Annotated Controllers in WebFlux

### Why This Chapter?
If you know Spring MVC, you can transfer most of that knowledge. This chapter shows what's the same and what's different.

### Chapter Outline

#### 9.1 What Stays the Same
- `@RestController`, `@RequestMapping`, `@GetMapping`, etc.
- `@PathVariable`, `@RequestParam`, `@RequestBody`
- `@ResponseStatus`, `@ExceptionHandler`

#### 9.2 What Changes: Return Types
- Return `Mono<T>` instead of `T`
- Return `Flux<T>` instead of `List<T>`
- Framework handles subscription
- **Code Comparison**: Same endpoint in MVC vs. WebFlux

#### 9.3 Request Body Handling
- `@RequestBody Mono<User>`: Reactive input
- When to use `Mono<T>` vs. `T` for request body
- Streaming request bodies with `Flux<T>`

#### 9.4 Server-Sent Events
- Returning `Flux<T>` with `text/event-stream`
- Real-time updates to clients
- **Use Case**: Live dashboards, notifications

#### 9.5 Exception Handling
- `@ExceptionHandler` still works
- Returning `Mono<ResponseEntity<Error>>`
- `@ControllerAdvice` for global handling

#### 9.6 Validation
- Bean validation with reactive types
- Custom validators
- Handling validation errors

### Hands-On Lab 9
- Build a complete CRUD API with WebFlux
- Add SSE endpoint for real-time updates
- Implement validation and error handling

---

## Chapter 10: Functional Endpoints

### Why This Chapter?
Functional endpoints offer a different programming model that some prefer. Understanding both styles makes you versatile.

### Chapter Outline

#### 10.1 The Philosophy: Functions as Building Blocks
- Routes are data, not annotations
- Composition over decoration
- **When This Shines**: Dynamic routes, complex routing logic

#### 10.2 RouterFunction: Defining Routes
- Building routes with `RouterFunctions.route()`
- Nesting and combining routers
- **Code Example**: Equivalent to an annotated controller

#### 10.3 HandlerFunction: Handling Requests
- Receiving `ServerRequest`
- Returning `Mono<ServerResponse>`
- Building responses

#### 10.4 Request Predicates
- Matching paths, methods, headers, content types
- Combining predicates
- Custom predicates

#### 10.5 Filters and Error Handling
- Applying filters to routes
- Error handling in functional style
- `onError` handlers

#### 10.6 Organizing Functional Endpoints
- Keeping handlers organized
- Combining with annotated controllers
- **Best Practice**: Structuring larger applications

### Hands-On Lab 10
- Rebuild the Chapter 9 API with functional endpoints
- Add filters for logging and authentication
- Create custom request predicates

---

## Chapter 11: WebClient: The Reactive HTTP Client

### Why This Chapter?
Calling external services is where reactive really shines. WebClient is the reactive alternative to RestTemplate.

### Chapter Outline

#### 11.1 Why WebClient Over RestTemplate?
- RestTemplate is blocking
- WebClient is non-blocking
- Future: RestTemplate is in maintenance mode
- **Key Insight**: WebClient can be used in MVC apps too!

#### 11.2 Building WebClient Instances
- Simple construction
- Using builder for defaults
- Base URL, headers, cookies

#### 11.3 Making Requests
- GET, POST, PUT, DELETE
- Request bodies
- Headers and query parameters
- **Code Examples**: Common patterns

#### 11.4 Handling Responses
- Retrieving body as Mono/Flux
- Response entity for headers/status
- Streaming responses
- **Common Mistake #3**: Blocking on the response

#### 11.5 Error Handling
- HTTP errors vs. exceptions
- `onStatus()` for specific status codes
- Retry and fallback strategies

#### 11.6 Exchanging for Full Control
- `exchangeToMono()` and `exchangeToFlux()`
- When you need headers and body together
- Resource management

#### 11.7 Performance Tuning
- Connection pooling
- Timeouts
- Metrics and logging

### Hands-On Lab 11
- Build a service that aggregates data from multiple external APIs
- Call APIs in parallel with error handling
- Add retry logic and fallbacks

---

## Chapter 12: WebSockets and Streaming

### Why This Chapter?
Full-duplex communication is where reactive patterns feel most natural. Learn to build real-time applications.

### Chapter Outline

#### 12.1 WebSockets: Two-Way Communication
- How WebSockets differ from HTTP
- Connection lifecycle
- **Use Cases**: Chat, live updates, gaming

#### 12.2 WebSocket Handler
- Implementing `WebSocketHandler`
- Sending and receiving messages
- Session management

#### 12.3 Message Handling with Flux
- Messages as a Flux
- Processing incoming messages
- Sending outgoing messages

#### 12.4 STOMP over WebSocket
- What STOMP adds
- Using with Spring messaging
- Pub/sub patterns

#### 12.5 Server-Sent Events Deep Dive
- When SSE is better than WebSocket
- Implementing complex streaming endpoints
- Client reconnection handling

#### 12.6 Streaming File Downloads/Uploads
- Streaming large files reactively
- `DataBuffer` for efficient memory usage
- **Key Insight**: Never load entire files into memory

### Hands-On Lab 12
- Build a real-time chat application with WebSockets
- Add presence detection (who's online)
- Implement SSE for notifications

---

# PART IV: DATA AND INTEGRATION
## *Reactive Data Access and Messaging*

---

## Chapter 13: Reactive Data Access Patterns

### Why This Chapter?
A reactive web layer with a blocking database defeats the purpose. This chapter covers how to access data reactively.

### Chapter Outline

#### 13.1 The Database Dilemma
- Why traditional JDBC is blocking
- The impedance mismatch
- Options: R2DBC, reactive drivers, CQRS

#### 13.2 R2DBC: Reactive Relational Database Connectivity
- What R2DBC is and isn't
- Driver availability (PostgreSQL, MySQL, SQL Server, H2)
- **Honest Assessment**: Maturity and limitations

#### 13.3 Spring Data R2DBC
- Repository pattern for R2DBC
- Familiar `@Repository` interfaces
- Custom queries

#### 13.4 Reactive MongoDB
- Natural fit for reactive
- Spring Data MongoDB Reactive
- Repository operations

#### 13.5 Reactive Redis
- Caching and data storage
- Lettuce client
- Spring Data Redis Reactive

#### 13.6 Reactive Elasticsearch
- Search operations reactively
- Spring Data Elasticsearch

#### 13.7 Connection Management
- Connection pooling in reactive world
- Resource cleanup
- Transaction considerations

### Hands-On Lab 13
- Set up R2DBC with PostgreSQL
- Build a reactive repository
- Compare with JPA/JDBC approach

---

## Chapter 14: Transactions in Reactive World

### Why This Chapter?
Transactions become tricky without thread-local storage. Learn how reactive transactions work.

### Chapter Outline

#### 14.1 The Thread-Local Problem
- Traditional transactions use ThreadLocal
- Reactive streams can switch threads
- How transaction context is lost

#### 14.2 Reactive Transaction Manager
- `TransactionAwareOperator`
- How Spring manages reactive transactions
- The `Transactional` annotation in reactive

#### 14.3 Programmatic Transactions
- `TransactionalOperator`
- Wrapping operations in transactions
- When to use declarative vs. programmatic

#### 14.4 Transaction Boundaries
- Where transactions start and end
- Nested transactions
- Read-only transactions

#### 14.5 Error Handling and Rollback
- Rollback on error
- Controlling rollback behavior
- **Gotcha**: Errors in reactive chains

#### 14.6 Distributed Transactions
- The challenge
- Saga pattern
- Event-driven consistency

### Hands-On Lab 14
- Implement a multi-step transaction
- Handle errors and rollback
- Test transaction boundaries

---

## Chapter 15: Reactive Messaging

### Why This Chapter?
Message-driven architecture is a pillar of reactive systems. Learn to integrate with message brokers reactively.

### Chapter Outline

#### 15.1 Why Message-Driven?
- Decoupling services
- Temporal decoupling
- Scalability and resilience

#### 15.2 Reactive Kafka
- Spring for Apache Kafka reactive
- Producing messages reactively
- Consuming as a Flux
- Backpressure and commits

#### 15.3 Reactive RabbitMQ
- Reactor RabbitMQ
- Publisher and receiver
- Message acknowledgment

#### 15.4 Project Reactor and Messaging Patterns
- Request-reply pattern
- Publish-subscribe
- Work queues

#### 15.5 Error Handling in Messaging
- Dead letter queues
- Retry strategies
- Poison messages

#### 15.6 Exactly-Once Semantics
- The challenge
- Idempotency
- Transactional outbox

### Hands-On Lab 15
- Set up reactive Kafka producer and consumer
- Handle failures with dead letter topics
- Implement backpressure-aware consumption

---

## Chapter 16: Integration Patterns

### Why This Chapter?
Real systems integrate with many external services. Learn patterns for reactive integration.

### Chapter Outline

#### 16.1 Gateway Pattern
- Aggregating calls to external services
- Error isolation
- Caching

#### 16.2 Circuit Breaker
- Why circuit breakers matter
- Resilience4j integration
- Reactive circuit breaker

#### 16.3 Rate Limiting
- Protecting external services
- Token bucket implementation
- Reactive rate limiter

#### 16.4 Caching
- Reactive caching strategies
- Cache aside pattern
- Caffeine with Reactor

#### 16.5 Reactive Connectors
- SOAP services
- gRPC reactive
- GraphQL subscriptions

#### 16.6 File Processing
- Large file handling
- Streaming parsers
- Backpressure with files

### Hands-On Lab 16
- Build a gateway that aggregates three services
- Add circuit breaker and rate limiting
- Implement caching layer

---

# PART V: PRODUCTION
## *Taking Reactive Applications to Production*

---

## Chapter 17: Testing Reactive Applications

### Why This Chapter?
Testing reactive code requires specific techniques. Master testing to build reliable applications.

### Chapter Outline

#### 17.1 Testing Philosophy
- What to test
- Unit vs. integration
- Test slice approach

#### 17.2 StepVerifier Deep Dive
- Advanced assertions
- Expecting errors
- Context verification

#### 17.3 Virtual Time Testing
- Testing delays without waiting
- `StepVerifier.withVirtualTime()`
- **Common Mistake #4**: Real time leaking into tests

#### 17.4 WebTestClient
- Testing endpoints
- Integration with MockMvc style
- Response assertions

#### 17.5 Testing with Testcontainers
- Real databases in tests
- Reactive database testing
- Container reuse

#### 17.6 Contract Testing
- Consumer-driven contracts
- Spring Cloud Contract
- Reactive stubs

#### 17.7 Performance Testing
- Load testing reactive apps
- Gatling for reactive
- Interpreting results

### Hands-On Lab 17
- Write comprehensive tests for a WebFlux service
- Use virtual time for scheduled operations
- Integration test with Testcontainers

---

## Chapter 18: Observability

### Why This Chapter?
You can't manage what you can't measure. Reactive applications need specific observability approaches.

### Chapter Outline

#### 18.1 The Observability Challenge
- Async execution context
- Trace propagation
- Metrics granularity

#### 18.2 Logging in Reactive World
- MDC doesn't work (thread-hopping)
- Using Reactor Context for logging
- Structured logging

#### 18.3 Distributed Tracing
- Micrometer Tracing
- Trace context propagation
- Zipkin/Jaeger integration

#### 18.4 Metrics
- Reactor metrics built-in
- Custom metrics
- Micrometer with reactive

#### 18.5 Health Checks
- Reactive health indicators
- Liveness vs. readiness
- Custom health checks

#### 18.6 Dashboards and Alerting
- Prometheus and Grafana
- Key metrics to monitor
- Alert strategies

### Hands-On Lab 18
- Add comprehensive observability
- Set up Prometheus and Grafana
- Create useful dashboards

---

## Chapter 19: Performance Tuning

### Why This Chapter?
Reactive doesn't automatically mean fast. Learn to optimize reactive applications.

### Chapter Outline

#### 19.1 Understanding Reactive Performance
- What to measure
- Throughput vs. latency
- The scalability curve

#### 19.2 Profiling Reactive Applications
- Async-aware profilers
- Reactor debug agent
- Finding bottlenecks

#### 19.3 Event Loop Optimization
- Thread pool sizing
- Avoiding blocking
- Detecting blocking calls

#### 19.4 Memory Optimization
- Buffer strategies
- Object pooling
- GC tuning for reactive

#### 19.5 Network Optimization
- Connection pooling
- Keep-alive settings
- HTTP/2 considerations

#### 19.6 Database Optimization
- Connection pool sizing
- Query optimization
- Reactive SQL vs. NoSQL

#### 19.7 Common Performance Pitfalls
- Blocking calls in reactive chain
- Unbounded buffers
- Hot publisher leaks

### Hands-On Lab 19
- Profile and optimize a reactive application
- Find and fix blocking calls
- Tune for high throughput

---

## Chapter 20: Building a Complete Application

### Why This Chapter?
Tie everything together with a real-world application that demonstrates all the concepts.

### Chapter Outline

#### 20.1 Application Overview
- **Project**: Real-time trading platform
- Features: Market data streaming, order management, portfolio tracking
- Architecture overview

#### 20.2 Project Setup
- Module structure
- Dependencies
- Configuration

#### 20.3 Domain Layer
- Domain models
- Business logic
- Reactive domain services

#### 20.4 Data Layer
- R2DBC for order persistence
- Reactive MongoDB for market data
- Redis for caching

#### 20.5 Web Layer
- REST endpoints for orders
- WebSocket for real-time prices
- SSE for portfolio updates

#### 20.6 Integration Layer
- External market data API
- Message queue for order events
- Circuit breakers and resilience

#### 20.7 Testing Strategy
- Unit tests
- Integration tests
- End-to-end tests

#### 20.8 Production Deployment
- Container setup
- Kubernetes considerations
- Monitoring setup

#### 20.9 Lessons Learned
- What worked well
- What was challenging
- Future improvements

### Hands-On Lab 20
- Build the complete application
- Deploy to local Kubernetes
- Load test and optimize

---

# Appendices

## Appendix A: Operator Quick Reference
- Categorized list of all common operators
- When to use each
- Examples

## Appendix B: From Blocking to Reactive: Migration Guide
- Strategies for gradual migration
- Common patterns
- Pitfalls to avoid

## Appendix C: Reactive Streams Specification Details
- Full specification
- Rules and their rationale
- Compliance testing

## Appendix D: Troubleshooting Guide
- Common errors and solutions
- Debugging techniques
- Performance issues

## Appendix E: Reactor vs. RxJava Quick Comparison
- Naming differences
- Concept mapping
- Migration tips

---

# Implementation Plan

## Phase 1: Foundation (Chapters 1-7)
Each chapter will include:
- `/chapter-XX/` directory
- Markdown content (`chapter-XX.md`)
- Code examples in `/chapter-XX/examples/`
- Hands-on lab in `/chapter-XX/lab/`

## Phase 2: WebFlux (Chapters 8-12)
- Complete Spring WebFlux examples
- Running applications for each chapter

## Phase 3: Data & Integration (Chapters 13-16)
- Docker Compose for databases
- Full integration examples

## Phase 4: Production (Chapters 17-20)
- Testing framework
- Observability stack
- Complete capstone project

## Repository Structure
```
spring-reactive-programming/
├── BOOK_PLAN.md
├── part-1-the-problem/
│   ├── chapter-01-the-blocking-world/
│   │   ├── chapter-01.md
│   │   ├── examples/
│   │   └── lab/
│   ├── chapter-02-evolution-of-concurrency/
│   └── chapter-03-what-is-reactive/
├── part-2-foundation/
│   ├── chapter-04-project-reactor/
│   ├── chapter-05-thinking-in-streams/
│   ├── chapter-06-error-handling/
│   └── chapter-07-advanced-patterns/
├── part-3-webflux/
│   ├── chapter-08-why-webflux/
│   ├── chapter-09-annotated-controllers/
│   ├── chapter-10-functional-endpoints/
│   ├── chapter-11-webclient/
│   └── chapter-12-websockets-streaming/
├── part-4-data-integration/
│   ├── chapter-13-reactive-data/
│   ├── chapter-14-transactions/
│   ├── chapter-15-messaging/
│   └── chapter-16-integration-patterns/
├── part-5-production/
│   ├── chapter-17-testing/
│   ├── chapter-18-observability/
│   ├── chapter-19-performance/
│   └── chapter-20-complete-application/
├── appendices/
│   ├── appendix-a-operators.md
│   ├── appendix-b-migration.md
│   ├── appendix-c-spec.md
│   ├── appendix-d-troubleshooting.md
│   └── appendix-e-rxjava-comparison.md
└── capstone-project/
    └── trading-platform/
```

---

# Next Steps

1. **Review this plan** - Let me know if you want to add, remove, or modify any chapters
2. **Start with Chapter 1** - Begin writing content and examples
3. **Iterate** - Each chapter builds on the previous; we'll refine as we go

Ready to proceed?
