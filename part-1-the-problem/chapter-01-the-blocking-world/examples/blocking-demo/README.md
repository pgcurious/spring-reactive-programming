# Blocking Demo Application

This Spring Boot application demonstrates the limitations of the traditional thread-per-request blocking model.

## Purpose

This demo helps you **experience** (not just read about) the problems with blocking I/O:

1. **Thread occupation**: Watch threads sit idle during blocking calls
2. **Thread exhaustion**: Observe what happens when all threads are busy
3. **Cascading delays**: See how slow services affect overall throughput

## Running the Application

```bash
# From this directory
./mvnw spring-boot:run

# Or with Maven
mvn spring-boot:run
```

The application starts on port 8080.

## Key Configuration

The application is configured with **intentionally small thread pool** (10 threads) to make thread exhaustion visible:

```yaml
server:
  tomcat:
    threads:
      max: 10  # Normally 200 in production
```

## Endpoints

### Business Endpoints

| Endpoint | Description | Blocking Time |
|----------|-------------|---------------|
| `GET /api/users/{id}` | Simple user lookup | ~50ms |
| `GET /api/users/{id}/orders` | User with orders (4 sequential calls) | ~350ms |
| `GET /api/users/{id}/dashboard` | Dashboard (could be parallel) | ~500ms |

### Diagnostic Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/threads` | Current thread pool status |
| `GET /api/slow?delayMs=N` | Configurable blocking endpoint |
| `GET /api/fast` | Fast endpoint for comparison |

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health |
| `GET /actuator/metrics` | All metrics |
| `GET /actuator/prometheus` | Prometheus format metrics |

## Experiments to Try

### Experiment 1: Observe Thread Blocking

```bash
# In terminal 1, watch the logs
# In terminal 2:
curl http://localhost:8080/api/users/1/orders
```

Watch the logs to see:
- Which thread handles the request
- How long each blocking operation takes
- The thread does nothing during waits

### Experiment 2: Thread Pool Exhaustion

```bash
# Open 10 terminals and run simultaneously:
curl "http://localhost:8080/api/slow?delayMs=5000"

# In another terminal, try to get a fast response:
curl http://localhost:8080/api/fast
# This will wait until a thread becomes available!
```

### Experiment 3: Load Testing

```bash
# Install Apache Bench (ab) or wrk, then:
ab -n 100 -c 20 http://localhost:8080/api/users/1/orders

# Watch thread exhaustion in action!
```

### Experiment 4: Monitor with Actuator

```bash
# While load testing, check metrics:
curl http://localhost:8080/actuator/metrics/tomcat.threads.busy
curl http://localhost:8080/actuator/metrics/tomcat.threads.current
```

## What You'll Learn

After running these experiments, you'll understand:

1. **Why** threads are the bottleneck (not CPU)
2. **How** blocking I/O wastes resources
3. **What** happens when thread pools exhaust
4. **Why** reactive programming was invented

## Next Steps

After experiencing these problems, proceed to the **Hands-On Lab** to:
- Measure thread utilization quantitatively
- Visualize the problem with graphs
- Compare blocking vs. non-blocking approaches
