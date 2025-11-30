# First Principles: Deriving Observability

## Forget That Prometheus and Zipkin Exist

Your application is in production. Users are complaining it's slow. You need to figure out why. But you can't reproduce the problem locally. How do you understand what's happening inside a running system?

## Step 1: The Fundamental Challenge

You can't open the box and look inside:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE BLACK BOX PROBLEM                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                   │   │
│   │                      YOUR APPLICATION                            │   │
│   │                                                                   │   │
│   │   Request ──▶ ┌─────────────────────────────────┐ ──▶ Response  │   │
│   │               │                                 │                │   │
│   │               │    ??????????????????          │                │   │
│   │               │    ?? WHAT HAPPENS ??          │                │   │
│   │               │    ??    HERE     ??          │                │   │
│   │               │    ??????????????????          │                │   │
│   │               │                                 │                │   │
│   │               └─────────────────────────────────┘                │   │
│   │                                                                   │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   You can see:                                                          │
│   • Requests going in                                                   │
│   • Responses coming out                                                │
│   • Time between them                                                   │
│                                                                          │
│   You cannot see:                                                       │
│   • Internal state                                                      │
│   • Decision paths                                                      │
│   • Resource usage                                                      │
│   • Where time is spent                                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: What Would Make the System Observable?

Observation means looking at outputs to understand internal state. What outputs could help?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OUTPUTS THAT REVEAL INTERNALS                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. TEXT DESCRIPTIONS OF EVENTS (Logs)                                 │
│      "User 123 logged in at 10:30:15"                                   │
│      "Database query took 500ms"                                        │
│      "Payment failed: insufficient funds"                               │
│                                                                          │
│   2. NUMERICAL MEASUREMENTS (Metrics)                                   │
│      requests_per_second = 150                                          │
│      active_connections = 42                                            │
│      error_rate = 0.02                                                  │
│      latency_p99 = 230ms                                                │
│                                                                          │
│   3. REQUEST JOURNEY RECORDS (Traces)                                   │
│      Request ABC:                                                       │
│        → API Gateway (5ms)                                              │
│          → Auth Service (10ms)                                          │
│          → Order Service (100ms)                                        │
│            → Database (80ms)                                            │
│            → Cache (2ms)                                                │
│                                                                          │
│   Together: The Three Pillars of Observability                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: Deriving Logging

The most basic form of observability: tell me what happened.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WHAT MAKES A GOOD LOG?                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Bad log:                                                               │
│   "Processing..."                                                       │
│   "Error occurred"                                                      │
│   "Done"                                                                 │
│                                                                          │
│   Questions left unanswered:                                            │
│   • Processing WHAT?                                                    │
│   • WHAT error? For WHOM?                                               │
│   • Done with WHAT? Success or failure?                                 │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   Good log:                                                              │
│   "2024-01-15 10:30:15.123 INFO  [req-abc-123] OrderService:            │
│    Creating order for user=456, items=3, total=$127.50"                 │
│                                                                          │
│   Contains:                                                              │
│   • WHEN: Timestamp                                                     │
│   • SEVERITY: INFO, WARN, ERROR                                         │
│   • CONTEXT: Request ID, user ID                                        │
│   • WHAT: The action being taken                                        │
│   • DATA: Relevant values                                               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### The Reactive Logging Problem

In traditional systems, we use thread ID as context:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THREAD-BASED CONTEXT                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional:                                                          │
│   Thread-15: [user=123] Processing order                                │
│   Thread-15: [user=123] Checking inventory                              │
│   Thread-15: [user=123] Charging payment                                │
│   Thread-15: [user=123] Order complete                                  │
│                                                                          │
│   Thread = Request, so context follows naturally                        │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   Reactive:                                                              │
│   EventLoop-1: [???] Processing order for user 123                      │
│   EventLoop-1: [???] Processing order for user 456                      │
│   EventLoop-2: [???] Checking inventory                                 │
│   EventLoop-1: [???] Charging payment                                   │
│   EventLoop-2: [???] Order complete                                     │
│                                                                          │
│   Thread ≠ Request, context is LOST!                                    │
│                                                                          │
│   Solution: Carry context INSIDE the reactive stream                    │
│   (Reactor Context instead of Thread-Local)                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 4: Deriving Metrics

Logs are great for specific events, but what about trends over time?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LOGS VS METRICS                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Question: "Is the system getting slower?"                             │
│                                                                          │
│   Using logs:                                                           │
│   1. Download all logs (gigabytes)                                     │
│   2. Parse each line                                                    │
│   3. Extract timing information                                         │
│   4. Calculate statistics                                               │
│   5. Compare with previous period                                       │
│   → Slow, expensive, impractical                                        │
│                                                                          │
│   Using metrics:                                                         │
│   1. Query: avg(request_latency[1h])                                   │
│   2. Compare: avg(request_latency[1h] offset 1d)                       │
│   → Instant answer                                                      │
│                                                                          │
│   Insight: Pre-aggregate numerical data                                 │
│                                                                          │
│   Types of metrics:                                                      │
│   • COUNTER: Things that only go up (requests, errors)                 │
│   • GAUGE: Current value (active connections, queue size)              │
│   • HISTOGRAM: Distribution of values (latency percentiles)            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### What Should We Measure?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE FOUR GOLDEN SIGNALS                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. LATENCY                                                            │
│      How long requests take                                             │
│      • Distinguish successful vs failed requests                        │
│      • Use percentiles (p50, p95, p99), not averages                   │
│                                                                          │
│   2. TRAFFIC                                                            │
│      How much demand is on the system                                   │
│      • Requests per second                                              │
│      • Network bandwidth                                                │
│                                                                          │
│   3. ERRORS                                                             │
│      Rate of failed requests                                            │
│      • HTTP 5xx responses                                               │
│      • Exceptions thrown                                                │
│      • Timeout rate                                                     │
│                                                                          │
│   4. SATURATION                                                         │
│      How "full" is the system                                           │
│      • CPU utilization                                                  │
│      • Memory usage                                                     │
│      • Thread pool utilization                                          │
│      • Connection pool utilization                                      │
│                                                                          │
│   If you only measure four things, measure these.                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 5: Deriving Distributed Tracing

Metrics tell you THAT something is slow. But not WHERE or WHY.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE DISTRIBUTED DEBUGGING PROBLEM                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Scenario: User complains "checkout is slow"                           │
│                                                                          │
│   Metric says: 99th percentile checkout latency = 5 seconds             │
│                                                                          │
│   But checkout involves:                                                 │
│   • API Gateway                                                         │
│   • User Service                                                        │
│   • Cart Service                                                        │
│   • Inventory Service                                                   │
│   • Payment Service                                                     │
│   • Order Service                                                       │
│   • Notification Service                                                │
│                                                                          │
│   Which one is slow? Each service says "not me, I'm fast!"             │
│                                                                          │
│   Solution: Follow a single request through all services                │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Request abc-123:                                                │   │
│   │  ├── Gateway ────────────────────────────────── 5000ms total    │   │
│   │  │   ├── User Service ─────── 50ms                              │   │
│   │  │   ├── Cart Service ─────── 100ms                             │   │
│   │  │   ├── Inventory Service ── 200ms                             │   │
│   │  │   ├── Payment Service ──── 4500ms  ← HERE'S THE PROBLEM!    │   │
│   │  │   │   └── External API ─── 4400ms  ← External service slow   │   │
│   │  │   └── Order Service ────── 150ms                             │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   A "trace" is this complete picture of a request's journey.           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### How Tracing Works

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TRACE MECHANICS                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Concept: Each unit of work is a "SPAN"                                │
│   Spans form a tree structure (the "TRACE")                             │
│                                                                          │
│   When a request starts:                                                 │
│   1. Generate unique Trace ID (e.g., "abc-123")                        │
│   2. Generate Span ID for this work (e.g., "span-1")                   │
│   3. Record: start time, operation name, metadata                       │
│                                                                          │
│   When calling another service:                                          │
│   1. Generate new Span ID (e.g., "span-2")                             │
│   2. Pass Trace ID + Parent Span ID in headers                         │
│   3. Child service creates span with same Trace ID                     │
│                                                                          │
│   HTTP Headers for propagation:                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  X-B3-TraceId: abc-123                                           │   │
│   │  X-B3-SpanId: span-2                                             │   │
│   │  X-B3-ParentSpanId: span-1                                       │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Result: All spans with same Trace ID = one request's journey         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: The Reactive Tracing Challenge

Context must flow through async operations:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONTEXT PROPAGATION IN REACTIVE                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Synchronous (easy):                                                    │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  void processOrder(String traceId) {                         │      │
│   │      Span span = tracer.start(traceId);                     │      │
│   │      orderService.create();     // Same thread, tracer works│      │
│   │      paymentService.charge();   // Same thread, still works │      │
│   │      span.end();                                             │      │
│   │  }                                                           │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Reactive (hard):                                                       │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Mono<Order> processOrder(String traceId) {                  │      │
│   │      Span span = tracer.start(traceId);                     │      │
│   │      return orderService.create()                            │      │
│   │          // Thread switch! Tracer loses context!            │      │
│   │          .flatMap(o -> paymentService.charge(o))            │      │
│   │          // Another thread! Context lost again!             │      │
│   │          .doFinally(s -> span.end());                       │      │
│   │  }                                                           │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Solution: Carry trace context in Reactor Context                      │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  return orderService.create()                                │      │
│   │      .flatMap(o -> paymentService.charge(o))                │      │
│   │      .contextWrite(ctx -> ctx.put("span", span));           │      │
│   │      // Now span is available in any operator!              │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 7: How the Three Pillars Work Together

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PILLARS IN ACTION                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Problem: "Users report slow checkouts"                                │
│                                                                          │
│   Step 1: CHECK METRICS                                                 │
│   "Checkout p99 latency jumped from 500ms to 5000ms at 14:00"          │
│   → Confirms problem, identifies WHEN it started                        │
│                                                                          │
│   Step 2: CHECK TRACES                                                  │
│   "Sample slow checkout trace shows 4500ms in Payment Service,          │
│    specifically in external credit card API"                            │
│   → Identifies WHERE the time is spent                                  │
│                                                                          │
│   Step 3: CHECK LOGS                                                    │
│   "Payment service logs at 14:00 show: 'Credit card API returning       │
│    rate limit errors, retrying with backoff'"                           │
│   → Identifies WHY (external API rate limiting)                         │
│                                                                          │
│   Resolution path discovered:                                            │
│   Metrics → Traces → Logs → Root cause → Fix                           │
│                                                                          │
│   Each pillar answers different questions:                              │
│   • Metrics: Is there a problem? How bad?                              │
│   • Traces: Where is the problem?                                       │
│   • Logs: What specifically went wrong?                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Debugging Reactive Code

Traditional debugging doesn't work well with reactive:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REACTIVE DEBUGGING TECHNIQUES                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Problem: Stack traces are useless                                     │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  java.lang.RuntimeException: Something failed                │      │
│   │    at reactor.core.publisher.Operators...                    │      │
│   │    at reactor.core.publisher.FluxFlatMap...                  │      │
│   │    at reactor.core.publisher.FluxMap...                      │      │
│   │    // Where's MY code???                                     │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Solution: Add checkpoints                                              │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  return fetchUser(id)                                        │      │
│   │      .checkpoint("After fetching user")                     │      │
│   │      .flatMap(this::processOrder)                           │      │
│   │      .checkpoint("After processing order")                  │      │
│   │      .flatMap(this::sendNotification)                       │      │
│   │      .checkpoint("After sending notification");             │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Now stack trace shows:                                                 │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │  Error has been observed at:                                 │      │
│   │    checkpoint ⇢ After fetching user                         │      │
│   │    checkpoint ⇢ After processing order ← Error was HERE     │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: The Complete Observability Picture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Your Reactive Application                                              │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                                                                   │   │
│   │   Request                                                         │   │
│   │      │                                                            │   │
│   │      ▼                                                            │   │
│   │   [Generate Request ID / Extract Trace Context]                  │   │
│   │      │                                                            │   │
│   │      ▼                                                            │   │
│   │   [Business Logic]                                                │   │
│   │      │                                                            │   │
│   │      ├──▶ EMIT LOGS (structured, with context)                   │   │
│   │      ├──▶ UPDATE METRICS (counters, gauges, histograms)         │   │
│   │      └──▶ EMIT SPANS (with timing and metadata)                  │   │
│   │      │                                                            │   │
│   │      ▼                                                            │   │
│   │   Response                                                        │   │
│   │                                                                   │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│              │                    │                    │                 │
│              ▼                    ▼                    ▼                 │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│   │  Log Aggregator │  │ Metrics Server  │  │ Tracing Backend │        │
│   │  (Elasticsearch)│  │  (Prometheus)   │  │   (Zipkin)      │        │
│   └─────────────────┘  └─────────────────┘  └─────────────────┘        │
│              │                    │                    │                 │
│              └────────────────────┴────────────────────┘                 │
│                                   │                                      │
│                                   ▼                                      │
│                          ┌─────────────────┐                            │
│                          │   Dashboards    │                            │
│                          │   & Alerting    │                            │
│                          │    (Grafana)    │                            │
│                          └─────────────────┘                            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Key Insight

Observability is not about having tools—it's about being able to ask arbitrary questions about your system and get answers from the outputs.

| Question | Pillar | Example Query |
|----------|--------|---------------|
| What happened to request X? | Logs | `requestId:abc-123` |
| How many errors in last hour? | Metrics | `sum(errors_total[1h])` |
| Where did request X spend time? | Traces | Trace ID lookup |
| Is the system getting slower? | Metrics | `histogram_quantile(0.99, ...)` |
| Why did request X fail? | Logs + Traces | Find trace, then related logs |

## Summary

From first principles, we derived that:

1. **Observability requires outputs** that reveal internal state
2. **Three pillars complement each other**: logs (events), metrics (aggregates), traces (journeys)
3. **Reactive breaks traditional context** propagation
4. **Reactor Context replaces thread-locals** for carrying request context
5. **Debugging needs checkpoints** to trace through async operations
6. **Correlation IDs tie everything together** across pillars and services

The goal isn't just to collect data—it's to be able to understand what's happening inside your system when things go wrong (or even when they go right). With proper observability, your reactive application becomes a transparent system where you can diagnose any issue by examining its outputs.
