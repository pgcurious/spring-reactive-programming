# First Principles: Deriving Integration Patterns

## Forget That Resilience4j Exists

You're building a service that depends on other services. Sometimes they're fast, sometimes slow, sometimes completely down. How do you handle this? Let's derive the patterns from first principles.

## Step 1: The Fundamental Problem

Your service calls another service:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE DEPENDENCY PROBLEM                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Your Service                     External Service                     │
│        │                                 │                               │
│        │──── Request ──────────────────▶│                               │
│        │                                 │                               │
│        │          (waiting...)           │                               │
│        │                                 │                               │
│        │◀─── Response ──────────────────│                               │
│        │                                 │                               │
│                                                                          │
│   What can go wrong?                                                     │
│   1. External service is down (no response)                             │
│   2. External service is slow (delayed response)                        │
│   3. External service returns errors (bad response)                     │
│   4. Network issues (request lost)                                      │
│                                                                          │
│   Each of these can bring YOUR service down.                            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 2: Why Not Just Retry?

First instinct: if it fails, try again!

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    NAIVE RETRY                                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Your Service                     External Service (failing)           │
│        │                                 │                               │
│        │──── Request 1 ─────────────────▶│ ✗ Failure                    │
│        │──── Request 2 ─────────────────▶│ ✗ Failure                    │
│        │──── Request 3 ─────────────────▶│ ✗ Failure                    │
│        │──── Request 4 ─────────────────▶│ ✗ Failure                    │
│        │──── Request 5 ─────────────────▶│ ✗ Failure                    │
│        │                                 │                               │
│                                                                          │
│   Problem: You're making the failing service's situation WORSE          │
│   by bombarding it with requests while it's struggling.                 │
│                                                                          │
│   AND you're tying up YOUR resources waiting for a service              │
│   that clearly isn't responding.                                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: What If We Waited Between Retries?

Better idea: add delays between retries.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    RETRY WITH BACKOFF                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Your Service                     External Service                     │
│        │                                 │                               │
│        │──── Request 1 ─────────────────▶│ ✗ Failure                    │
│        │                                 │                               │
│        │     [wait 1 second]             │                               │
│        │                                 │                               │
│        │──── Request 2 ─────────────────▶│ ✗ Failure                    │
│        │                                 │                               │
│        │     [wait 2 seconds]            │                               │
│        │                                 │                               │
│        │──── Request 3 ─────────────────▶│ ✓ Success!                   │
│        │                                 │                               │
│                                                                          │
│   Exponential backoff: 1s → 2s → 4s → 8s...                            │
│                                                                          │
│   Better! We give the service time to recover.                          │
│   But what if the service is down for an hour?                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 4: Deriving the Circuit Breaker

What if the service is down for a long time? We'd waste resources forever.

Observation: If the last N requests failed, the next one probably will too.

Insight: **Stop trying when it's clearly not working.**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DERIVING CIRCUIT BREAKER                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Concept: An electrical circuit breaker trips when overloaded          │
│   to prevent damage. Same idea for service calls.                       │
│                                                                          │
│   Track recent results:                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Call 1: ✗  Call 2: ✗  Call 3: ✗  Call 4: ✗  Call 5: ✗        │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   5 failures in a row? STOP CALLING. "Open" the circuit.               │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │  Your Service ──X──▶ [CIRCUIT OPEN] ──X──▶ External Service  │      │
│   │                                                               │      │
│   │  Requests fail IMMEDIATELY without calling external service  │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   After some time, test if service recovered (half-open state)          │
│   If test succeeds, "close" the circuit and resume normal operation    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 5: What About Slow Services?

A service that responds in 30 seconds is often worse than one that fails:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE SLOW SERVICE PROBLEM                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Without timeout:                                                       │
│                                                                          │
│   User Request ─▶ Your Service ─▶ External Service (slow)              │
│        │                │              │                                │
│        │                │   [30 seconds pass...]                        │
│        │                │              │                                │
│        │                │◀─────────────│                                │
│        │◀───────────────│                                               │
│        │                                                                │
│   User waited 30 seconds. Your service tied up resources.              │
│   If many users do this, your service becomes unresponsive.            │
│                                                                          │
│   ─────────────────────────────────────────────────────────────────     │
│                                                                          │
│   With timeout:                                                          │
│                                                                          │
│   User Request ─▶ Your Service ─▶ External Service                     │
│        │                │              │                                │
│        │                │   [2 second timeout!]                         │
│        │◀─ Error ───────│                                               │
│                                                                          │
│   User gets a fast failure. Your service stays responsive.             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: What If One Client Consumes All Resources?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE RESOURCE EXHAUSTION PROBLEM                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Your service has 100 connections to share:                            │
│                                                                          │
│   Normal User A: 1 request/second                                       │
│   Normal User B: 1 request/second                                       │
│   Abusive Client: 1000 requests/second                                  │
│                                                                          │
│   Result:                                                                │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Connection Pool: [Abusive][Abusive][Abusive]...[Abusive]       │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   Normal users can't get connections - your service appears down       │
│   even though it's running fine!                                        │
│                                                                          │
│   Solution: RATE LIMITING                                               │
│                                                                          │
│   Allow max 10 requests/second per client:                              │
│   Normal User A: 1/s ✓                                                  │
│   Normal User B: 1/s ✓                                                  │
│   Abusive Client: 10/s allowed, 990/s rejected with 429                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 7: The Token Bucket (Intuitive Rate Limiting)

How do we implement "10 requests per second" fairly?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TOKEN BUCKET INTUITION                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Imagine a bucket that holds tokens:                                   │
│                                                                          │
│   ┌─────────────────────────────────────────┐                           │
│   │           BUCKET (max 10 tokens)        │                           │
│   │   ┌───┬───┬───┬───┬───┬───┬───┬───┐    │                           │
│   │   │ T │ T │ T │ T │ T │   │   │   │    │   ← Tokens added          │
│   │   └───┴───┴───┴───┴───┴───┴───┴───┘    │     at fixed rate         │
│   └─────────────────────────────────────────┘     (e.g., 10/second)     │
│                                                                          │
│   Rules:                                                                 │
│   1. Each request takes 1 token from bucket                            │
│   2. If no tokens available, request is rejected                       │
│   3. Tokens are added at a fixed rate (e.g., 10 per second)           │
│   4. Bucket has max capacity (allows short bursts)                     │
│                                                                          │
│   Example scenario:                                                      │
│   - Bucket starts full (10 tokens)                                      │
│   - Burst of 10 requests: all succeed, bucket empty                    │
│   - 11th request: rejected (no tokens)                                 │
│   - Wait 1 second: 10 new tokens added                                 │
│   - Next 10 requests can proceed                                       │
│                                                                          │
│   This naturally allows bursts while enforcing long-term rate.         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Why Cache?

Every call to external service has latency and failure risk. What if we remember results?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CACHING INTUITION                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Without cache:                                                         │
│   Request 1 for User #123 ──▶ Database (100ms)                         │
│   Request 2 for User #123 ──▶ Database (100ms)                         │
│   Request 3 for User #123 ──▶ Database (100ms)                         │
│   Total: 300ms, 3 database calls                                        │
│                                                                          │
│   With cache:                                                            │
│   Request 1 for User #123 ──▶ Cache miss ──▶ Database (100ms)          │
│                                   └──▶ Store in cache                   │
│   Request 2 for User #123 ──▶ Cache hit! (1ms)                         │
│   Request 3 for User #123 ──▶ Cache hit! (1ms)                         │
│   Total: 102ms, 1 database call                                         │
│                                                                          │
│   Benefits:                                                              │
│   • 100x faster for cache hits                                          │
│   • Database load reduced by 67%                                        │
│   • Works even if database is temporarily down (for cached data)       │
│                                                                          │
│   Trade-off: Data might be slightly stale                               │
│   Solution: Set appropriate TTL (time to live)                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: Deriving the Bulkhead Pattern

What if one external service is slow and consumes all your resources?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE BULKHEAD INSIGHT                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Real-world analogy: Ship compartments (bulkheads)                     │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │   │
│   │  │ Compart │  │ Compart │  │ Compart │  │ Compart │            │   │
│   │  │   1     │  │   2     │  │   3     │  │   4     │            │   │
│   │  │  FLOOD  │  │  dry    │  │  dry    │  │  dry    │            │   │
│   │  └─────────┘  └─────────┘  └─────────┘  └─────────┘            │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   One compartment floods, but bulkheads prevent it from sinking        │
│   the entire ship. Same principle for services:                        │
│                                                                          │
│   Without bulkhead:                                                      │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Thread Pool (100 threads)                                       │   │
│   │  All threads stuck waiting on Slow Service A                    │   │
│   │  → Requests to Service B and C also fail (no threads!)          │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   With bulkhead:                                                         │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                    │
│   │ Service A   │  │ Service B   │  │ Service C   │                    │
│   │ (40 threads)│  │ (40 threads)│  │ (20 threads)│                    │
│   │   STUCK     │  │   Working   │  │   Working   │                    │
│   └─────────────┘  └─────────────┘  └─────────────┘                    │
│                                                                          │
│   Service A problems are isolated. B and C continue working!           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 10: Putting It All Together

Each pattern solves a specific failure mode:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PATTERN SUMMARY                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   FAILURE MODE           │  PATTERN            │  ACTION                │
│   ───────────────────────│─────────────────────│────────────────────── │
│   Transient failure      │  Retry + Backoff    │  Try again later      │
│   Sustained failure      │  Circuit Breaker    │  Stop trying, fail fast│
│   Slow response          │  Timeout            │  Give up after limit  │
│   Resource exhaustion    │  Rate Limiter       │  Reject excess requests│
│   Expensive operations   │  Cache              │  Reuse previous results│
│   Cascading failure      │  Bulkhead           │  Isolate failures     │
│                                                                          │
│   Real-World Application Stack:                                         │
│                                                                          │
│   Request                                                                │
│      │                                                                   │
│      ▼                                                                   │
│   Rate Limiter ──▶ Reject if over limit                                │
│      │                                                                   │
│      ▼                                                                   │
│   Cache ──▶ Return cached if available                                  │
│      │                                                                   │
│      ▼                                                                   │
│   Bulkhead ──▶ Reject if pool full                                     │
│      │                                                                   │
│      ▼                                                                   │
│   Circuit Breaker ──▶ Fail fast if open                                │
│      │                                                                   │
│      ▼                                                                   │
│   Timeout + Retry ──▶ Call with protection                             │
│      │                                                                   │
│      ▼                                                                   │
│   External Service                                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 11: Implementing in Code

```java
public Mono<Data> fetchDataResilient(String id) {
    // 1. Check cache first
    return cacheService.get(id)
        .switchIfEmpty(Mono.defer(() ->
            // 2. Apply bulkhead (limit concurrent calls)
            externalApiCall(id)
                .transformDeferred(BulkheadOperator.of(bulkhead))
                // 3. Apply circuit breaker
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                // 4. Apply retry with backoff
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                    .filter(this::isRetryable))
                // 5. Apply timeout
                .timeout(Duration.ofSeconds(5))
                // 6. Store in cache on success
                .doOnNext(data -> cacheService.put(id, data))
        ))
        // 7. Fallback if everything fails
        .onErrorResume(e -> getFallbackData(id));
}
```

## The Key Insight

Every pattern follows the same philosophy:

1. **Accept that failures will happen** - Don't pretend they won't
2. **Detect failures quickly** - Monitor and measure
3. **Fail fast when appropriate** - Don't waste resources on lost causes
4. **Isolate failures** - Don't let one problem become everyone's problem
5. **Recover gracefully** - Have fallbacks and degraded modes

Distributed systems are not about preventing failures—they're about **embracing failures and designing systems that work despite them**.

## Summary

We derived from first principles:

| Pattern | From This Question |
|---------|-------------------|
| **Retry** | "What if it was a temporary glitch?" |
| **Backoff** | "What if retrying immediately makes things worse?" |
| **Circuit Breaker** | "What if the service is down for a long time?" |
| **Timeout** | "What if the service never responds?" |
| **Rate Limiter** | "What if one client consumes all resources?" |
| **Cache** | "What if we could avoid calling at all?" |
| **Bulkhead** | "What if one slow service affects all others?" |

These patterns weren't invented arbitrarily—they emerged from asking "what could go wrong?" and then "how do we handle that?" The same questions you'd ask when building any robust system.
