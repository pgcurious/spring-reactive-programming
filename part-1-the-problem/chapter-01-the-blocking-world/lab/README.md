# Hands-On Lab 1: Experiencing Thread Exhaustion

## Overview

In this lab, you will:
1. Run a blocking Spring MVC application
2. Load test it to observe thread behavior
3. Witness thread pool exhaustion firsthand
4. Measure and visualize the problem
5. Understand why reactive programming is needed

**Estimated time**: 30-45 minutes

## Prerequisites

- Java 17+
- Maven or Gradle
- Terminal/command line
- (Optional) Load testing tool: `ab` (Apache Bench), `wrk`, or `hey`
- (Optional) `curl` and `jq` for API testing

## Part 1: Setup and Exploration

### Step 1.1: Start the Blocking Demo Application

```bash
cd ../examples/blocking-demo
./mvnw spring-boot:run
```

You should see:
```
╔════════════════════════════════════════════════════════════════╗
║                    BLOCKING DEMO APPLICATION                   ║
╠════════════════════════════════════════════════════════════════╣
║  This app demonstrates thread-per-request blocking behavior.   ║
...
```

### Step 1.2: Verify the Application is Running

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{"status":"UP","thread":"http-nio-8080-exec-1"}
```

Note the thread name - you'll see these thread names throughout the lab.

### Step 1.3: Check Thread Pool Configuration

```bash
curl http://localhost:8080/api/threads | jq
```

You should see ~10 HTTP threads (our intentionally small pool).

---

## Part 2: Understanding Single Request Blocking

### Step 2.1: Make a Simple Request

```bash
curl http://localhost:8080/api/users/1
```

Watch the application logs. You'll see:
```
[http-nio-8080-exec-1] REQUEST START: GET /api/users/1
[http-nio-8080-exec-1] Starting user lookup for id=1
[http-nio-8080-exec-1] Completed user lookup for id=1 in 50ms
[http-nio-8080-exec-1] REQUEST END: Completed in 52ms
```

**Key observation**: The thread was blocked for ~50ms doing nothing.

### Step 2.2: Make a Complex Request

```bash
curl http://localhost:8080/api/users/1/orders | jq
```

Watch the logs for the 4 sequential blocking calls:
```
Step 1/4: Fetching user...
Step 2/4: Fetching orders...
Step 3/4: Fetching products...
Step 4/4: Calling external pricing service...
REQUEST END: Completed in 352ms
Thread was BLOCKED (idle) for ~351ms of that time
```

**Key observation**: 4 sequential blocking calls = 4x the blocking time.

### Step 2.3: The Parallel Opportunity

```bash
curl http://localhost:8080/api/users/1/dashboard | jq
```

Check the response for the "note" field:
```json
{
  "note": "Pricing and recommendations were fetched SEQUENTIALLY. In reactive code, they would run in PARALLEL, saving ~200ms."
}
```

**Key observation**: With blocking code, parallelization is hard. With reactive, it's natural.

---

## Part 3: Thread Pool Exhaustion

This is where things get interesting!

### Step 3.1: Check Current Thread Status

```bash
curl http://localhost:8080/api/threads | jq
```

Note the number of active threads (should be low).

### Step 3.2: Exhaust the Thread Pool

Open **11 terminal windows** and run this in each (quickly, one after another):

```bash
curl "http://localhost:8080/api/slow?delayMs=10000"
```

Or use this bash one-liner to simulate concurrent requests:

```bash
# Run 15 concurrent requests, each blocking for 10 seconds
for i in {1..15}; do
  curl -s "http://localhost:8080/api/slow?delayMs=10000" &
done
wait
```

### Step 3.3: Try to Access a Fast Endpoint

While the slow requests are running, in a new terminal:

```bash
time curl http://localhost:8080/api/fast
```

**What you'll observe**: The "fast" endpoint takes seconds to respond because it's waiting for a thread!

### Step 3.4: Watch the Logs

In the application logs, you'll see:
```
[http-nio-8080-exec-1] SLOW REQUEST: Blocking for 10000ms
[http-nio-8080-exec-2] SLOW REQUEST: Blocking for 10000ms
...
[http-nio-8080-exec-10] SLOW REQUEST: Blocking for 10000ms
```

All 10 threads are occupied. New requests must wait.

---

## Part 4: Load Testing

### Step 4.1: Install a Load Testing Tool

Choose one:

```bash
# Apache Bench (comes with Apache HTTPD)
apt-get install apache2-utils  # Ubuntu/Debian
brew install httpd              # macOS

# Or hey (Go-based, easy to install)
go install github.com/rakyll/hey@latest

# Or wrk
apt-get install wrk
brew install wrk
```

### Step 4.2: Baseline Test - Simple Endpoint

```bash
# 100 requests, 10 concurrent
ab -n 100 -c 10 http://localhost:8080/api/users/1
```

Or with hey:
```bash
hey -n 100 -c 10 http://localhost:8080/api/users/1
```

**Record these metrics:**
- Requests per second: _______
- Mean latency: _______
- 99th percentile: _______

### Step 4.3: Stress Test - Complex Endpoint

```bash
ab -n 100 -c 20 http://localhost:8080/api/users/1/orders
```

With 20 concurrent users but only 10 threads, you'll see:
- Half the requests waiting for threads
- Latency increases significantly

**Record these metrics:**
- Requests per second: _______
- Mean latency: _______
- 99th percentile: _______

### Step 4.4: Breaking Point Test

```bash
# Ramp up concurrency until the system struggles
ab -n 500 -c 50 http://localhost:8080/api/users/1/orders
```

**Observe:**
- How does throughput change?
- What happens to latency?
- Are any requests failing?

---

## Part 5: Metrics Analysis

### Step 5.1: Check Thread Metrics

While running load tests:

```bash
# Busy threads
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy | jq

# Current threads
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.current | jq

# Max threads (should be 10)
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.config.max | jq
```

### Step 5.2: Calculate Thread Utilization

During a load test:
```bash
# Watch thread utilization live
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy | jq ".measurements[0].value"'
```

When busy threads = max threads (10), the system is saturated.

---

## Part 6: Calculating the Math

Let's do the math to understand the scalability ceiling.

### Given:
- Max threads: 10
- Average request time for `/api/users/{id}/orders`: ~350ms
- Average CPU work per request: ~1ms

### Calculate:

**1. Maximum throughput:**
```
Throughput = Threads / Request Time
Throughput = 10 / 0.35s = 28.5 requests/second
```

**2. Thread utilization:**
```
Thread Idle Time = Total Time - CPU Time = 350ms - 1ms = 349ms
Thread Utilization = CPU Time / Total Time = 1ms / 350ms = 0.29%
```

**Threads are doing useful work only 0.29% of the time!**

**3. CPU utilization:**
```
With 4 CPU cores and 28.5 req/s at 1ms each:
CPU Usage = (28.5 * 0.001) / 4 = 0.7%
```

**CPU is 99.3% idle because we're thread-limited, not CPU-limited!**

---

## Part 7: Discussion Questions

After completing this lab, answer these questions:

1. **What was the maximum throughput you achieved?**
   How does it compare to the theoretical maximum?

2. **What happened when concurrent users exceeded thread count?**
   How did latency change?

3. **If you needed to handle 10x more users, what would you do?**
   - Add more threads? (What are the limits?)
   - Add more servers? (Cost implications?)
   - Something else?

4. **What if the external service (200ms) became 10x slower?**
   How would that affect your system?

5. **What would happen in a microservices architecture?**
   With each service making blocking calls to others?

---

## Part 8: Key Takeaways

Fill in the blanks based on your observations:

1. A thread handling a 350ms request is idle for ____% of the time.

2. With 10 threads and 350ms requests, max throughput is ~____ req/s.

3. When all threads are busy, new requests must _______.

4. Increasing threads has diminishing returns because of _______ and _______.

5. The fundamental problem is that threads _______ during I/O operations.

---

## Summary

You've now experienced firsthand why traditional blocking I/O doesn't scale:

```
┌────────────────────────────────────────────────────────────────┐
│                     WHAT YOU LEARNED                           │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ✓ Threads block during I/O, wasting resources                │
│  ✓ Thread pools have hard limits (~hundreds)                  │
│  ✓ When threads run out, ALL requests suffer                  │
│  ✓ CPU is often idle while threads are blocked                │
│  ✓ Sequential blocking calls compound the problem             │
│                                                                │
│  The question: How can we use resources more efficiently?      │
│  The answer: Reactive programming (coming in Part II!)        │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

## Next Steps

Proceed to **Chapter 2: The Evolution of Concurrency in Java** to see how the industry has tried to solve this problem over time, leading to reactive programming.

---

## Cleanup

```bash
# Stop the application (Ctrl+C in the terminal running it)
# Or if running in background:
kill $(lsof -t -i:8080)
```

## Troubleshooting

**Application won't start:**
- Check if port 8080 is available: `lsof -i:8080`
- Try a different port: add `--server.port=8081` to the run command

**Load test tool not working:**
- Ensure the application is running
- Check firewall settings
- Try with curl first to verify connectivity

**Can't see thread exhaustion:**
- Make sure the delay is long enough (10+ seconds)
- Start requests quickly before others complete
- Check that max-threads is set to 10
