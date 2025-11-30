# First Principles: Designing Reactive Systems

## Forget That Frameworks Exist

You need to build a system that handles real-time data, thousands of concurrent users, and integrates with multiple external services. Before reaching for any framework, let's derive the architecture from first principles.

## Step 1: Understanding the Requirements

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SYSTEM REQUIREMENTS ANALYSIS                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Trading Platform Requirements:                                         │
│                                                                          │
│   1. REAL-TIME DATA                                                     │
│      • Market prices update every second                                │
│      • Users need to see changes immediately                            │
│      • Delay > 100ms is unacceptable                                   │
│                                                                          │
│   2. HIGH CONCURRENCY                                                   │
│      • 10,000+ simultaneous users                                       │
│      • Each user watching multiple price streams                        │
│      • Each user may have multiple open orders                         │
│                                                                          │
│   3. EXTERNAL INTEGRATIONS                                              │
│      • Market data from external APIs                                   │
│      • Payment processing                                               │
│      • Notification services                                            │
│      • External services may be slow or fail                           │
│                                                                          │
│   4. DATA CONSISTENCY                                                   │
│      • Orders must not be lost                                          │
│      • Positions must be accurate                                       │
│      • Trades must be recorded correctly                               │
│                                                                          │
│   Question: What architecture naturally emerges from these requirements?│
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: Deriving the Communication Model

How should components communicate?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMMUNICATION PATTERNS                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Option A: Request-Response (Synchronous)                              │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Client ──request──▶ Server ──response──▶ Client            │      │
│   │                                                               │      │
│   │   Pros: Simple, familiar, easy to reason about               │      │
│   │   Cons: Client waits, doesn't scale for real-time            │      │
│   │                                                               │      │
│   │   Use for: Placing orders, querying portfolio                │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Option B: Streaming (Push)                                            │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Client ──subscribe──▶ Server                               │      │
│   │   Server ──data──▶ Client                                    │      │
│   │   Server ──data──▶ Client                                    │      │
│   │   Server ──data──▶ Client                                    │      │
│   │   ...                                                        │      │
│   │                                                               │      │
│   │   Pros: Real-time updates, efficient for continuous data     │      │
│   │   Cons: More complex, need to handle backpressure           │      │
│   │                                                               │      │
│   │   Use for: Price updates, portfolio changes                  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Option C: Bidirectional (Full-Duplex)                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Client ◀────────────▶ Server                               │      │
│   │                                                               │      │
│   │   Both sides can send at any time                            │      │
│   │                                                               │      │
│   │   Pros: Most flexible, immediate interaction                 │      │
│   │   Cons: Most complex to implement                            │      │
│   │                                                               │      │
│   │   Use for: Real-time trading UI, chat-like interactions     │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Insight: A real system needs ALL THREE patterns                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: Deriving the Concurrency Model

How do we handle 10,000+ concurrent users?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONCURRENCY MODELS                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Option A: Thread-Per-User                                             │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   10,000 users = 10,000 threads                              │      │
│   │                                                               │      │
│   │   Memory: 10,000 × 1MB = 10GB just for stacks!               │      │
│   │   Context switching: Massive overhead                         │      │
│   │                                                               │      │
│   │   Verdict: NOT VIABLE                                        │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Option B: Thread Pool with Blocking I/O                               │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   200 threads, queue incoming requests                       │      │
│   │                                                               │      │
│   │   Problem: Each request holds thread during I/O wait         │      │
│   │   If avg request = 100ms, 200 threads = 2,000 req/sec max   │      │
│   │                                                               │      │
│   │   Verdict: INSUFFICIENT for real-time streaming              │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Option C: Event Loop with Non-Blocking I/O                            │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   4-8 threads handle ALL requests                            │      │
│   │   I/O is non-blocking (callback on completion)               │      │
│   │                                                               │      │
│   │   Memory: Minimal (< 100MB for core runtime)                 │      │
│   │   Each connection: ~10KB instead of 1MB                     │      │
│   │                                                               │      │
│   │   10,000 connections = ~100MB additional                    │      │
│   │                                                               │      │
│   │   Verdict: SCALES BEAUTIFULLY                                │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Conclusion: Event loop model is the only viable option               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 4: Deriving Data Flow

How should data flow through the system?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DATA FLOW PATTERNS                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Market Data Flow:                                                      │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   External API                                                │      │
│   │       │                                                       │      │
│   │       ▼                                                       │      │
│   │   ┌─────────────┐                                            │      │
│   │   │ Price Feed  │  (One source of truth)                     │      │
│   │   │  Service    │                                            │      │
│   │   └──────┬──────┘                                            │      │
│   │          │                                                    │      │
│   │   ┌──────┴──────┐                                            │      │
│   │   ▼             ▼                                            │      │
│   │ ┌─────┐    ┌────────┐                                        │      │
│   │ │Cache│    │Broadcast│                                        │      │
│   │ │(Redis)   │  Hub    │                                        │      │
│   │ └─────┘    └────┬───┘                                        │      │
│   │                 │                                             │      │
│   │          ┌──────┼──────┐                                     │      │
│   │          ▼      ▼      ▼                                     │      │
│   │        User1  User2  User3  (Fan-out to subscribers)        │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Order Flow:                                                            │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   User                                                        │      │
│   │     │                                                         │      │
│   │     ▼                                                         │      │
│   │   ┌─────────────┐                                            │      │
│   │   │ Validation  │  (Validate before processing)              │      │
│   │   └──────┬──────┘                                            │      │
│   │          │                                                    │      │
│   │          ▼                                                    │      │
│   │   ┌─────────────┐                                            │      │
│   │   │Order Service│  (Business logic)                          │      │
│   │   └──────┬──────┘                                            │      │
│   │          │                                                    │      │
│   │   ┌──────┴──────┐                                            │      │
│   │   ▼             ▼                                            │      │
│   │ ┌─────────┐  ┌──────────┐                                    │      │
│   │ │Persist  │  │Update    │                                    │      │
│   │ │(Database)  │Portfolio │                                    │      │
│   │ └─────────┘  └──────┬───┘                                    │      │
│   │                     │                                         │      │
│   │                     ▼                                         │      │
│   │              ┌────────────┐                                   │      │
│   │              │ Notify User│                                   │      │
│   │              └────────────┘                                   │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Key Insight: Data flows as streams, not individual requests           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 5: Deriving Error Handling

External services will fail. How do we handle this?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    FAILURE MODES                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   External API Failures:                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Problem: External market data API goes down                 │      │
│   │                                                               │      │
│   │   Naive: Let errors propagate → All users see errors         │      │
│   │                                                               │      │
│   │   Better: Graceful degradation                               │      │
│   │                                                               │      │
│   │   ┌───────────────────────────────────────────────────────┐  │      │
│   │   │  Request → Circuit Breaker → External API             │  │      │
│   │   │                    │                                   │  │      │
│   │   │                    ▼ (if open)                         │  │      │
│   │   │              ┌─────────────┐                           │  │      │
│   │   │              │  Fallback   │                           │  │      │
│   │   │              │ (last known │                           │  │      │
│   │   │              │   price)    │                           │  │      │
│   │   │              └─────────────┘                           │  │      │
│   │   └───────────────────────────────────────────────────────┘  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Database Failures:                                                     │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Problem: Database connection pool exhausted                 │      │
│   │                                                               │      │
│   │   With blocking: Requests queue, latency spikes, timeouts    │      │
│   │                                                               │      │
│   │   With reactive: Backpressure propagates upstream            │      │
│   │                                                               │      │
│   │   Order Flow:                                                 │      │
│   │   User → Order Service → Database (slow)                     │      │
│   │              │                                                │      │
│   │              │ (backpressure signal)                         │      │
│   │              ▼                                                │      │
│   │         "System busy, please retry"                          │      │
│   │                                                               │      │
│   │   Users get immediate feedback instead of hanging            │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Insight: Reactive systems fail gracefully through backpressure        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: Deriving the Layer Architecture

How should the system be organized?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LAYERED ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Principle: Each layer has a single responsibility                     │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                      PRESENTATION LAYER                          │   │
│   │   • HTTP endpoints (REST)                                       │   │
│   │   • WebSocket handlers                                          │   │
│   │   • SSE endpoints                                               │   │
│   │   • Request/Response conversion                                 │   │
│   │                                                                  │   │
│   │   Responsibility: Handle client communication                   │   │
│   │   Returns: Reactive types (Mono/Flux)                          │   │
│   └────────────────────────────┬────────────────────────────────────┘   │
│                                │                                        │
│   ┌────────────────────────────▼────────────────────────────────────┐   │
│   │                      DOMAIN LAYER                                │   │
│   │   • Business logic                                               │   │
│   │   • Domain services                                              │   │
│   │   • Validation rules                                             │   │
│   │                                                                  │   │
│   │   Responsibility: Execute business rules                        │   │
│   │   Knows nothing about: HTTP, databases, external APIs           │   │
│   └────────────────────────────┬────────────────────────────────────┘   │
│                                │                                        │
│   ┌────────────────────────────▼────────────────────────────────────┐   │
│   │                      INTEGRATION LAYER                           │   │
│   │   • Repositories (database access)                              │   │
│   │   • External API clients                                        │   │
│   │   • Message publishers                                          │   │
│   │                                                                  │   │
│   │   Responsibility: Communicate with external systems             │   │
│   │   Provides: Reactive wrappers for all I/O                      │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   All layers communicate via Reactive Streams                           │
│   Backpressure flows from bottom to top                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 7: Deriving State Management

Where does state live in a reactive system?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    STATE IN REACTIVE SYSTEMS                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional (Thread-Local State):                                      │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Thread-1 {                                                  │      │
│   │     currentUser = User(123)                                  │      │
│   │     requestId = "abc-xyz"                                    │      │
│   │     transaction = open                                       │      │
│   │   }                                                           │      │
│   │                                                               │      │
│   │   State "follows" the thread                                 │      │
│   │   Works because thread = request                             │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive (Context-Propagated State):                                   │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Pipeline {                                                  │      │
│   │     Context: {                                               │      │
│   │       currentUser: User(123)                                 │      │
│   │       requestId: "abc-xyz"                                   │      │
│   │       traceId: "trace-001"                                   │      │
│   │     }                                                         │      │
│   │   }                                                           │      │
│   │                                                               │      │
│   │   State flows THROUGH the pipeline                           │      │
│   │   Available regardless of which thread executes              │      │
│   │                                                               │      │
│   │   // Setting context                                         │      │
│   │   mono.contextWrite(ctx -> ctx.put("userId", 123))          │      │
│   │                                                               │      │
│   │   // Reading context                                         │      │
│   │   Mono.deferContextual(ctx -> {                             │      │
│   │     Long userId = ctx.get("userId");                        │      │
│   │     return process(userId);                                  │      │
│   │   })                                                         │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Shared State (Hot Publishers):                                         │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Price updates need to be shared across subscribers         │      │
│   │                                                               │      │
│   │   Sinks.Many<Price> priceSink = Sinks.many()                │      │
│   │       .multicast()                                           │      │
│   │       .directBestEffort();                                   │      │
│   │                                                               │      │
│   │   // Publisher pushes                                        │      │
│   │   priceSink.tryEmitNext(newPrice);                          │      │
│   │                                                               │      │
│   │   // Multiple subscribers                                    │      │
│   │   Flux<Price> prices = priceSink.asFlux();                  │      │
│   │   user1.subscribeTo(prices);                                │      │
│   │   user2.subscribeTo(prices);                                │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Deriving Testing Strategy

How do we test asynchronous systems?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TESTING REACTIVE CODE                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Challenge: Asynchronous code is hard to test                          │
│                                                                          │
│   Traditional Test:                                                      │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Result result = service.process(input);                    │      │
│   │   assertEquals(expected, result);                            │      │
│   │                                                               │      │
│   │   Simple: Call returns immediately with result               │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive Test Problem:                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Mono<Result> result = service.process(input);              │      │
│   │   // result is a DESCRIPTION of computation, not the result │      │
│   │   // Nothing has happened yet!                               │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Solution: StepVerifier                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   StepVerifier.create(service.process(input))               │      │
│   │       .expectNext(expected)    // Assert on elements        │      │
│   │       .verifyComplete();       // Assert completion          │      │
│   │                                                               │      │
│   │   StepVerifier subscribes, runs the pipeline, and verifies  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Testing Time-Based Operations:                                         │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   // Without virtual time: Test takes 10 real seconds       │      │
│   │   Mono.delay(Duration.ofSeconds(10))                        │      │
│   │                                                               │      │
│   │   // With virtual time: Test completes instantly            │      │
│   │   StepVerifier.withVirtualTime(() ->                        │      │
│   │           Mono.delay(Duration.ofSeconds(10)))               │      │
│   │       .thenAwait(Duration.ofSeconds(10))                    │      │
│   │       .expectComplete()                                      │      │
│   │       .verify();                                             │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: Deriving Production Concerns

What does production-ready mean for reactive systems?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION READINESS                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. OBSERVABILITY                                                       │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Challenge: Async execution makes tracing hard              │      │
│   │                                                               │      │
│   │   Solution:                                                   │      │
│   │   • Trace IDs propagated via Reactor Context                │      │
│   │   • Metrics on all reactive operations                       │      │
│   │   • Structured logging with request context                  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   2. RESILIENCE                                                          │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   External Service ──▶ Circuit Breaker ──▶ Retry ──▶ Fallback│      │
│   │                                                               │      │
│   │   • Circuit breaker prevents cascade failures               │      │
│   │   • Retry handles transient errors                          │      │
│   │   • Fallback provides degraded functionality                │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   3. RESOURCE MANAGEMENT                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   • Bounded connection pools                                 │      │
│   │   • Bounded buffers with overflow strategies                │      │
│   │   • Timeouts on all external calls                          │      │
│   │   • Graceful shutdown handling                               │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   4. SCALABILITY                                                         │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Horizontal scaling:                                        │      │
│   │   • Stateless services                                       │      │
│   │   • Shared state in Redis/database                          │      │
│   │   • Load balancer distributes connections                   │      │
│   │                                                               │      │
│   │   Vertical scaling:                                          │      │
│   │   • Add CPU cores → more event loop threads                 │      │
│   │   • Reactive scales efficiently with cores                  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Complete Picture

Putting it all together:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE SYSTEM ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   From our first-principles derivation:                                 │
│                                                                          │
│   1. Event Loop Model                                                    │
│      └─▶ Few threads, many connections                                  │
│      └─▶ Non-blocking I/O throughout                                    │
│                                                                          │
│   2. Reactive Streams                                                    │
│      └─▶ Backpressure from consumer to producer                         │
│      └─▶ Bounded buffers prevent memory exhaustion                      │
│                                                                          │
│   3. Multiple Communication Patterns                                     │
│      └─▶ REST for commands                                              │
│      └─▶ SSE/WebSocket for streams                                      │
│                                                                          │
│   4. Layered Architecture                                                │
│      └─▶ Presentation → Domain → Integration                            │
│      └─▶ Each layer reactive                                            │
│                                                                          │
│   5. Context Propagation                                                 │
│      └─▶ State flows through pipeline                                   │
│      └─▶ Not tied to threads                                            │
│                                                                          │
│   6. Resilience Patterns                                                 │
│      └─▶ Circuit breakers                                               │
│      └─▶ Retries with backoff                                           │
│      └─▶ Fallbacks                                                       │
│                                                                          │
│   7. Observability                                                       │
│      └─▶ Distributed tracing                                            │
│      └─▶ Reactive-aware metrics                                         │
│      └─▶ Structured logging                                             │
│                                                                          │
│   The architecture EMERGES from the requirements and constraints.       │
│   Reactive programming is not a choice—it's the NECESSARY solution.    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Summary

By deriving from first principles:

1. **High concurrency** requirements eliminate thread-per-user model
2. **Real-time updates** require streaming communication patterns
3. **External integrations** need resilience patterns (circuit breakers)
4. **Asynchronous execution** requires context propagation instead of thread-local
5. **Testing async code** requires specialized tools (StepVerifier)
6. **Production readiness** needs observability designed in from the start

The reactive architecture is not arbitrary—it's the logical consequence of building systems that must handle high concurrency, real-time data, and unreliable external dependencies while remaining efficient and maintainable.
