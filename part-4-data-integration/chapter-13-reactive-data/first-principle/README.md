# First Principles: Deriving Reactive Data Access

## Forget That R2DBC and Reactive Drivers Exist

Imagine you've built a beautiful reactive web application. Requests flow through non-blocking handlers, operators transform data without waiting, and a few event loop threads handle thousands of concurrent users.

Then you need to query a database. And everything falls apart.

Let's understand why from first principles, and derive what a truly reactive data access layer must look like.

## Step 1: What Happens During a Database Query?

When your application queries a database, here's the journey:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE DATABASE QUERY JOURNEY                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Application                Network              Database               │
│                                                                          │
│   1. Build query       ───▶                                             │
│   2. Acquire connection ───▶                                            │
│   3. Send SQL          ─────────────────▶                               │
│   4. Wait...           ←- - - - - - - - - - - - - - - -                 │
│   5. Wait...                                        Parse SQL           │
│   6. Wait...                                        Plan query          │
│   7. Wait...                                        Execute plan        │
│   8. Wait...                                        Fetch rows          │
│   9. Receive first row ◀─────────────────                               │
│  10. Wait for next...  ←- - - - - - - - -                               │
│  11. Receive next row  ◀─────────────────                               │
│  12. ... repeat ...                                                      │
│  13. Close connection                                                    │
│                                                                          │
│   Total time: 10-100ms (or more)                                        │
│   Time spent waiting: 95%+                                               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

The critical insight: **Your thread spends almost all its time waiting**. It's not computing. It's not processing. It's just... idle. Blocked. Useless.

## Step 2: The Blocking Thread Problem

JDBC was designed when threads were the unit of concurrency:

```java
// Traditional JDBC - every line blocks
Connection conn = dataSource.getConnection();  // Block: wait for connection
Statement stmt = conn.createStatement();       // Minor
ResultSet rs = stmt.executeQuery("SELECT...");  // Block: wait for database
while (rs.next()) {                            // Block: wait for each row
    String name = rs.getString("name");        // Minor
    // process...
}
rs.close();
conn.close();
```

Each blocking call "parks" the thread. The thread can do nothing else. It's occupied but not working.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING: THREAD TIMELINE                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1:                                                              │
│   ════════════════════╗                                                  │
│   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓║  ← BLOCKED waiting for database                │
│   ════════════════════╝                                                  │
│                       ▓▓▓ ← Actually processing result (tiny fraction)  │
│                                                                          │
│   Legend: ▓ = Thread occupied                                            │
│                                                                          │
│   Thread utilization: ~5% actual work, 95% waiting                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 3: Why This Breaks Reactive

In a reactive system with WebFlux:

- You have a small number of event loop threads (typically 2 * CPU cores)
- These threads handle ALL requests
- If one of these threads blocks, it can't process other requests

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BLOCKING IN REACTIVE: DISASTER                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Event Loop Thread (1 of 4):                                           │
│                                                                          │
│   Request 1 ─▶ ════════════════════════════════ ← JDBC query blocks!   │
│   Request 2 ─▶   [QUEUED - can't process]                               │
│   Request 3 ─▶   [QUEUED - can't process]                               │
│   Request 4 ─▶   [QUEUED - can't process]                               │
│   Request 5 ─▶   [QUEUED - can't process]                               │
│   Request 6 ─▶   [QUEUED - can't process]                               │
│   ...                                                                    │
│                                                                          │
│   If all 4 event loop threads block:                                    │
│   → ENTIRE APPLICATION FROZEN                                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

This is why you can't use JDBC in a WebFlux application. One slow query on an event loop thread and your entire application stops responding.

## Step 4: The Workaround That Doesn't Work

"I'll just wrap JDBC in a `Mono` and run it on a different scheduler!"

```java
public Mono<User> getUser(Long id) {
    return Mono.fromCallable(() -> jdbcUserRepository.findById(id))
        .subscribeOn(Schedulers.boundedElastic());  // Run on elastic pool
}
```

This "works" but you've just recreated the thread-per-request model:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    THE SCHEDULER WORKAROUND                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Event Loop Thread:                                                     │
│   Request ─▶ Creates Mono ─▶ Hands off to elastic thread ─▶ Returns    │
│                                                                          │
│   Elastic Thread Pool:                                                   │
│   Thread 1: ════════════ ← Blocked on JDBC                              │
│   Thread 2: ════════════ ← Blocked on JDBC                              │
│   Thread 3: ════════════ ← Blocked on JDBC                              │
│   ...                                                                    │
│                                                                          │
│   You're back to needing many threads for many concurrent users!        │
│   The reactive benefit is lost.                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

You've protected the event loop, but you're still consuming threads proportional to concurrent database operations. This is not truly reactive.

## Step 5: What Would Truly Non-Blocking Look Like?

In a truly non-blocking model:

1. **Initiate query**: Send SQL to database, then immediately return
2. **Register callback**: Tell the system "call me when result is ready"
3. **Do other work**: The thread is free to process other requests
4. **Handle result**: When data arrives, a thread picks up and processes it

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    NON-BLOCKING DATABASE ACCESS                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Thread 1:                                                              │
│   ─────────────────────────────────────────────────────────────────▶    │
│   │ Req A   │ Req B   │ Req C   │ Req A   │ Req D   │ Req B   │         │
│   │ (send)  │ (send)  │ (send)  │ (result)│ (send)  │ (result)│         │
│                                                                          │
│   No blocking! Thread processes many requests interleaved.              │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ Time      Request A    Request B    Request C    Request D      │   │
│   │ t=0       Send query   -            -            -              │   │
│   │ t=1       -            Send query   -            -              │   │
│   │ t=2       -            -            Send query   -              │   │
│   │ t=10      Receive!     -            -            -              │   │
│   │ t=11      Process      -            -            Send query     │   │
│   │ t=15      -            Receive!     -            -              │   │
│   │ t=16      -            Process      -            -              │   │
│   │ ...                                                              │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│   All 4 requests handled by 1 thread!                                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 6: How Non-Blocking I/O Actually Works

At the operating system level, non-blocking I/O uses mechanisms like:

- **epoll** (Linux)
- **kqueue** (macOS/BSD)
- **IOCP** (Windows)

These allow a single thread to monitor thousands of connections:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OPERATING SYSTEM LEVEL                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Traditional (Blocking):                                                │
│   Thread 1 ──── Connection 1 ──── Socket ──── [blocked read]           │
│   Thread 2 ──── Connection 2 ──── Socket ──── [blocked read]           │
│   Thread 3 ──── Connection 3 ──── Socket ──── [blocked read]           │
│                                                                          │
│   Modern (Non-Blocking with epoll/kqueue):                              │
│                    ┌── Connection 1 ──── Socket                         │
│   Selector ───────┼── Connection 2 ──── Socket                         │
│   (1 thread)      ├── Connection 3 ──── Socket                         │
│                    └── Connection 4 ──── Socket                         │
│                                                                          │
│   Selector: "Hey, connection 2 has data ready!"                         │
│   Thread: processes connection 2, then asks selector again              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 7: What Must a Reactive Database Driver Do?

For database access to be truly reactive:

1. **Non-blocking network I/O**: Use NIO, not blocking sockets
2. **Asynchronous protocol**: Database protocol must support async (most do)
3. **Reactive Streams interface**: Return `Publisher<Row>` not `ResultSet`
4. **Backpressure**: Let consumers control the flow of rows

```java
// Conceptually, what a reactive driver does:

interface ReactiveConnection {
    // Don't block - return immediately with a Publisher
    Publisher<Result> execute(String sql);
}

interface Result {
    // Stream rows with backpressure
    Publisher<Row> getRows();

    // Get row count for UPDATE/DELETE
    Publisher<Long> getRowsUpdated();
}
```

## Step 8: Mapping to Reactive Streams

A database query maps perfectly to reactive streams:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DATABASE QUERY AS REACTIVE STREAM                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   SELECT * FROM users WHERE active = true                                │
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                                                               │      │
│   │   Database ──▶ Row 1 ──▶ Row 2 ──▶ Row 3 ──▶ ... ──▶ Complete │      │
│   │               onNext   onNext   onNext        onComplete      │      │
│   │                                                               │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Subscriber can request rows at its own pace (backpressure):           │
│                                                                          │
│   Subscriber: request(10)                                                │
│   Publisher:  Row 1, Row 2, ... Row 10                                  │
│   Subscriber: request(10)                                                │
│   Publisher:  Row 11, Row 12, ... Row 20                                │
│   ...                                                                    │
│                                                                          │
│   No memory overflow from fetching millions of rows at once!            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 9: R2DBC - The Specification

R2DBC (Reactive Relational Database Connectivity) standardizes this:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    R2DBC SPECIFICATION                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Core Interfaces:                                                       │
│                                                                          │
│   ConnectionFactory                                                      │
│     └── create() → Publisher<Connection>                                │
│                                                                          │
│   Connection                                                             │
│     ├── createStatement(sql) → Statement                                │
│     ├── beginTransaction() → Publisher<Void>                            │
│     ├── commitTransaction() → Publisher<Void>                           │
│     └── close() → Publisher<Void>                                       │
│                                                                          │
│   Statement                                                              │
│     ├── bind(index, value) → Statement                                  │
│     ├── add() → Statement  (batch)                                      │
│     └── execute() → Publisher<Result>                                   │
│                                                                          │
│   Result                                                                 │
│     ├── getRowsUpdated() → Publisher<Long>                              │
│     └── map(function) → Publisher<T>                                    │
│                                                                          │
│   Everything returns Publishers - nothing blocks!                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 10: The Complete Picture

Putting it all together:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    FULLY REACTIVE DATA PATH                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   HTTP Request                                                           │
│        │                                                                 │
│        ▼                                                                 │
│   ┌─────────────┐                                                        │
│   │  WebFlux    │ ← Non-blocking (Netty)                                │
│   │  Controller │                                                        │
│   └──────┬──────┘                                                        │
│          │                                                               │
│          ▼                                                               │
│   ┌─────────────┐                                                        │
│   │  Service    │ ← Reactive operators (Reactor)                        │
│   │  Layer      │                                                        │
│   └──────┬──────┘                                                        │
│          │                                                               │
│          ▼                                                               │
│   ┌─────────────┐                                                        │
│   │  Repository │ ← Returns Mono<T> / Flux<T>                           │
│   │  (R2DBC)    │                                                        │
│   └──────┬──────┘                                                        │
│          │                                                               │
│          ▼                                                               │
│   ┌─────────────┐                                                        │
│   │  R2DBC      │ ← Non-blocking driver                                 │
│   │  Driver     │                                                        │
│   └──────┬──────┘                                                        │
│          │                                                               │
│          ▼                                                               │
│   ┌─────────────┐                                                        │
│   │  Database   │ ← PostgreSQL, MySQL, etc.                             │
│   └─────────────┘                                                        │
│                                                                          │
│   From HTTP request to database response: NO BLOCKING ANYWHERE          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Key Insight

The fundamental problem isn't the database—databases can handle asynchronous requests. The problem was **JDBC's blocking API design**.

JDBC forces you to wait:
```java
ResultSet rs = stmt.executeQuery();  // You must wait here
while (rs.next()) {                  // And here
    ...
}
```

R2DBC lets you continue:
```java
Flux<Row> rows = statement.execute()  // Returns immediately!
    .flatMapMany(Result::map);         // Rows will flow when ready
```

This is why you can handle 10,000 concurrent database queries with 10 threads using R2DBC, but would need 10,000 threads (or a large thread pool) with JDBC.

## Summary

From first principles, we derived that:

1. **JDBC blocking is fundamental**, not fixable by wrapping in `Mono`
2. **True reactive requires non-blocking I/O** at the driver level
3. **Database results are natural streams** - rows arrive over time
4. **Backpressure prevents memory issues** - don't fetch faster than you process
5. **R2DBC is the specification** that standardizes reactive database access

The result: your application can handle massive concurrency with minimal threads, from the HTTP layer all the way down to the database. No blocking anywhere in the chain.

This is what reactive data access provides: the final piece of the non-blocking puzzle.
