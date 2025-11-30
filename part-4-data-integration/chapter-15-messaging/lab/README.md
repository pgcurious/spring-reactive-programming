# Lab 15: Reactive Messaging with Kafka and RabbitMQ

## Objectives

By the end of this lab, you will:

1. Set up reactive Kafka producers and consumers
2. Implement event-driven order processing
3. Handle backpressure and batch processing
4. Implement error handling with Dead Letter Queues
5. Build idempotent consumers
6. Set up reactive RabbitMQ for task queues
7. Implement the Transactional Outbox pattern

## Prerequisites

- Completed Chapters 13-14 (Reactive Data Access, Transactions)
- Docker and Docker Compose installed
- Understanding of message brokers (conceptual)

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup & Infrastructure | 15 min |
| 2 | Reactive Kafka Producer | 20 min |
| 3 | Reactive Kafka Consumer | 25 min |
| 4 | Error Handling & DLQ | 20 min |
| 5 | Idempotent Processing | 20 min |
| 6 | Reactive RabbitMQ | 25 min |
| 7 | Transactional Outbox | 20 min |
| **Total** | | **145 min** |

---

## Part 1: Project Setup & Infrastructure (15 min)

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
    <artifactId>reactive-messaging-lab</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
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

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>io.projectreactor.kafka</groupId>
            <artifactId>reactor-kafka</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>io.projectreactor.rabbitmq</groupId>
            <artifactId>reactor-rabbitmq</artifactId>
            <version>1.5.6</version>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
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
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <version>1.19.3</version>
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

### Step 1.2: Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: messagingdb
      POSTGRES_USER: messaging
      POSTGRES_PASSWORD: messaging123
    ports:
      - "5432:5432"
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
```

### Step 1.3: Database Schema

Create `init.sql`:

```sql
-- Orders table
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) UNIQUE NOT NULL,
    user_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Processed events (for idempotency)
CREATE TABLE processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Outbox table
CREATE TABLE outbox (
    id SERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Index for outbox polling
CREATE INDEX idx_outbox_status ON outbox(status) WHERE status = 'PENDING';
```

### Step 1.4: Application Configuration

Create `src/main/resources/application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/messagingdb
    username: messaging
    password: messaging123

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: messaging-lab
      auto-offset-reset: earliest
    producer:
      acks: all

rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest

logging:
  level:
    org.springframework.kafka: INFO
    reactor.kafka: DEBUG
```

### Step 1.5: Start Infrastructure

```bash
docker-compose up -d

# Wait for services to be ready
sleep 30

# Create Kafka topics
docker exec -it $(docker ps -q -f name=kafka) kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic orders.created \
  --partitions 3 \
  --replication-factor 1

docker exec -it $(docker ps -q -f name=kafka) kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic orders.dlq \
  --partitions 1 \
  --replication-factor 1
```

---

## Part 2: Reactive Kafka Producer (20 min)

### Step 2.1: Create Event Classes

```java
package com.example.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent {

    private String eventId;
    private String orderId;
    private Long userId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private Instant createdAt;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(String orderId, Long userId, List<OrderItem> items, BigDecimal totalAmount) {
        this.eventId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public record OrderItem(Integer productId, String productName, Integer quantity, BigDecimal price) {}

    @Override
    public String toString() {
        return "OrderCreatedEvent{eventId='" + eventId + "', orderId='" + orderId + "'}";
    }
}
```

### Step 2.2: Configure Kafka Producer

```java
package com.example.messaging.config;

import com.example.messaging.event.OrderCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> orderKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        SenderOptions<String, OrderCreatedEvent> senderOptions = SenderOptions.create(props);
        return new ReactiveKafkaProducerTemplate<>(senderOptions);
    }
}
```

### Step 2.3: Create Event Publisher

```java
package com.example.messaging.publisher;

import com.example.messaging.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC = "orders.created";

    private final ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<SenderResult<Void>> publish(OrderCreatedEvent event) {
        log.info("Publishing event: {}", event);

        return kafkaTemplate.send(TOPIC, event.getOrderId(), event)
            .doOnSuccess(result -> log.info(
                "Published successfully: topic={}, partition={}, offset={}",
                result.recordMetadata().topic(),
                result.recordMetadata().partition(),
                result.recordMetadata().offset()
            ))
            .doOnError(e -> log.error("Failed to publish event: {}", event.getOrderId(), e));
    }
}
```

### Step 2.4: Create Order Service

```java
package com.example.messaging.service;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.publisher.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Mono<OrderCreatedEvent> createOrder(CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        BigDecimal total = request.items().stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderCreatedEvent.OrderItem> items = request.items().stream()
            .map(item -> new OrderCreatedEvent.OrderItem(
                item.productId(), item.productName(), item.quantity(), item.price()))
            .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(orderId, request.userId(), items, total);

        // In real app: save to database first, then publish
        return eventPublisher.publish(event)
            .thenReturn(event)
            .doOnSuccess(e -> log.info("Order created and event published: {}", orderId));
    }

    public record CreateOrderRequest(Long userId, List<OrderItemRequest> items) {}
    public record OrderItemRequest(Integer productId, String productName, Integer quantity, BigDecimal price) {}
}
```

### Step 2.5: Create Controller

```java
package com.example.messaging.controller;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderCreatedEvent> createOrder(@RequestBody OrderService.CreateOrderRequest request) {
        return orderService.createOrder(request);
    }
}
```

### Step 2.6: Test the Producer

```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      {"productId": 1, "productName": "Widget", "quantity": 2, "price": 29.99}
    ]
  }'

# Check Kafka topic
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders.created \
  --from-beginning
```

---

## Part 3: Reactive Kafka Consumer (25 min)

### Step 3.1: Configure Kafka Consumer

```java
package com.example.messaging.config;

import com.example.messaging.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ReceiverOptions<String, OrderCreatedEvent> orderReceiverOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.messaging.event");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return ReceiverOptions.<String, OrderCreatedEvent>create(props)
            .subscription(Collections.singletonList("orders.created"));
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> orderConsumerTemplate(
            ReceiverOptions<String, OrderCreatedEvent> options) {
        return new ReactiveKafkaConsumerTemplate<>(options);
    }
}
```

### Step 3.2: Create Order Processor

```java
package com.example.messaging.processor;

import com.example.messaging.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    public Mono<Void> process(OrderCreatedEvent event) {
        log.info("Processing order: {}", event.getOrderId());

        // Simulate processing time
        return Mono.delay(Duration.ofMillis(100))
            .doOnNext(v -> {
                // Simulate occasional failures for testing
                if (event.getOrderId().endsWith("fail")) {
                    throw new RuntimeException("Simulated processing failure");
                }
            })
            .then()
            .doOnSuccess(v -> log.info("Order processed successfully: {}", event.getOrderId()))
            .doOnError(e -> log.error("Failed to process order: {}", event.getOrderId(), e));
    }
}
```

### Step 3.3: Create Consumer Service

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.processor.OrderProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final OrderProcessor orderProcessor;
    private Disposable subscription;

    public OrderEventConsumer(
            ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer,
            OrderProcessor orderProcessor) {
        this.consumer = consumer;
        this.orderProcessor = orderProcessor;
    }

    @PostConstruct
    public void startConsuming() {
        subscription = consumer.receive()
            .doOnNext(record -> log.debug(
                "Received: key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset()
            ))
            .flatMap(record ->
                orderProcessor.process(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(e -> {
                        log.error("Error processing record: {}", record.key(), e);
                        // Don't acknowledge - will be redelivered
                        return Mono.empty();
                    })
            , 16)  // Process 16 records concurrently
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofMinutes(1)))
            .subscribe();

        log.info("Order event consumer started");
    }

    @PreDestroy
    public void stopConsuming() {
        if (subscription != null) {
            subscription.dispose();
        }
        log.info("Order event consumer stopped");
    }
}
```

### Step 3.4: Create Backpressure-Aware Consumer

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.processor.OrderProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Duration;
import java.util.List;

@Service
public class BatchOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchOrderConsumer.class);

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final OrderProcessor orderProcessor;

    public BatchOrderConsumer(
            ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer,
            OrderProcessor orderProcessor) {
        this.consumer = consumer;
        this.orderProcessor = orderProcessor;
    }

    // Alternative consumer with batching (uncomment to use instead)
    // @PostConstruct
    public void startBatchConsuming() {
        consumer.receive()
            .limitRate(100)  // Backpressure: max 100 in-flight
            .bufferTimeout(50, Duration.ofMillis(500))  // Batch up to 50 or 500ms
            .flatMap(this::processBatch, 4)  // 4 concurrent batches
            .subscribe();
    }

    private Flux<Void> processBatch(List<ReceiverRecord<String, OrderCreatedEvent>> batch) {
        log.info("Processing batch of {} records", batch.size());

        return Flux.fromIterable(batch)
            .flatMap(record ->
                orderProcessor.process(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(e -> {
                        log.error("Batch processing error: {}", record.key());
                        return Flux.empty();
                    })
            );
    }
}
```

---

## Part 4: Error Handling & DLQ (20 min)

### Step 4.1: Create DLQ Publisher

```java
package com.example.messaging.publisher;

import com.example.messaging.event.OrderCreatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class DLQPublisher {

    private static final Logger log = LoggerFactory.getLogger(DLQPublisher.class);
    private static final String DLQ_TOPIC = "orders.dlq";

    private final ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public DLQPublisher(ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> sendToDLQ(OrderCreatedEvent event, Throwable error, int retryCount) {
        log.warn("Sending to DLQ: {}, error: {}", event.getOrderId(), error.getMessage());

        RecordHeaders headers = new RecordHeaders();
        headers.add("x-error-message", error.getMessage().getBytes(StandardCharsets.UTF_8));
        headers.add("x-retry-count", String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8));
        headers.add("x-failed-at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        headers.add("x-original-topic", "orders.created".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, OrderCreatedEvent> record = new ProducerRecord<>(
            DLQ_TOPIC,
            null,
            event.getOrderId(),
            event,
            headers
        );

        return kafkaTemplate.send(record)
            .doOnSuccess(r -> log.info("Sent to DLQ: {}", event.getOrderId()))
            .then();
    }
}
```

### Step 4.2: Create Resilient Consumer

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.processor.OrderProcessor;
import com.example.messaging.publisher.DLQPublisher;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class ResilientOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResilientOrderConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final OrderProcessor orderProcessor;
    private final DLQPublisher dlqPublisher;

    public ResilientOrderConsumer(
            ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer,
            OrderProcessor orderProcessor,
            DLQPublisher dlqPublisher) {
        this.consumer = consumer;
        this.orderProcessor = orderProcessor;
        this.dlqPublisher = dlqPublisher;
    }

    // Uncomment to use this consumer instead
    // @PostConstruct
    public void startConsuming() {
        consumer.receive()
            .flatMap(this::processWithRetry)
            .subscribe();
    }

    private Mono<Void> processWithRetry(ReceiverRecord<String, OrderCreatedEvent> record) {
        int currentRetry = getRetryCount(record);

        return orderProcessor.process(record.value())
            .retryWhen(Retry.backoff(MAX_RETRIES - currentRetry, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(e -> isRetryable(e))
                .doBeforeRetry(signal -> log.warn(
                    "Retrying order {}: attempt {}",
                    record.key(), signal.totalRetries() + 1 + currentRetry
                )))
            .doOnSuccess(v -> record.receiverOffset().acknowledge())
            .onErrorResume(e -> {
                log.error("Exhausted retries for order: {}", record.key());
                return dlqPublisher.sendToDLQ(record.value(), e, MAX_RETRIES)
                    .doOnSuccess(v -> record.receiverOffset().acknowledge());
            });
    }

    private int getRetryCount(ReceiverRecord<String, OrderCreatedEvent> record) {
        Header header = record.headers().lastHeader("x-retry-count");
        if (header == null) return 0;
        return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
    }

    private boolean isRetryable(Throwable e) {
        // Add your retry logic here
        return !(e instanceof IllegalArgumentException);
    }
}
```

### Step 4.3: Create DLQ Consumer (for manual inspection)

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
public class DLQMonitor {

    private static final Logger log = LoggerFactory.getLogger(DLQMonitor.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @GetMapping("/messages")
    public Flux<DLQMessage> getDLQMessages() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-monitor-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.messaging.event");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ReceiverOptions<String, OrderCreatedEvent> options = ReceiverOptions.<String, OrderCreatedEvent>create(props)
            .subscription(Collections.singletonList("orders.dlq"));

        return KafkaReceiver.create(options)
            .receive()
            .take(Duration.ofSeconds(5))
            .map(record -> new DLQMessage(
                record.key(),
                record.value(),
                getHeaderValue(record, "x-error-message"),
                getHeaderValue(record, "x-failed-at")
            ));
    }

    private String getHeaderValue(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, String key) {
        var header = record.headers().lastHeader(key);
        return header != null ? new String(header.value()) : null;
    }

    public record DLQMessage(String key, OrderCreatedEvent event, String error, String failedAt) {}
}
```

---

## Part 5: Idempotent Processing (20 min)

### Step 5.1: Create Processed Event Repository

```java
package com.example.messaging.repository;

import com.example.messaging.entity.ProcessedEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ProcessedEventRepository extends ReactiveCrudRepository<ProcessedEvent, String> {
    Mono<Boolean> existsByEventId(String eventId);
}
```

```java
package com.example.messaging.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("processed_events")
public class ProcessedEvent {

    @Id
    @Column("event_id")
    private String eventId;

    @Column("event_type")
    private String eventType;

    @Column("processed_at")
    private Instant processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    // Getters and setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
```

### Step 5.2: Create Idempotent Processor

```java
package com.example.messaging.processor;

import com.example.messaging.entity.ProcessedEvent;
import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class IdempotentOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentOrderProcessor.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderProcessor orderProcessor;

    public IdempotentOrderProcessor(
            ProcessedEventRepository processedEventRepository,
            OrderProcessor orderProcessor) {
        this.processedEventRepository = processedEventRepository;
        this.orderProcessor = orderProcessor;
    }

    @Transactional
    public Mono<Void> processIdempotently(OrderCreatedEvent event) {
        String eventId = event.getEventId();

        return processedEventRepository.existsById(eventId)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Duplicate event detected, skipping: {}", eventId);
                    return Mono.empty();
                }
                return processAndRecord(event);
            });
    }

    private Mono<Void> processAndRecord(OrderCreatedEvent event) {
        return orderProcessor.process(event)
            .then(processedEventRepository.save(new ProcessedEvent(event.getEventId(), "OrderCreated")))
            .then()
            .doOnSuccess(v -> log.info("Event processed and recorded: {}", event.getEventId()));
    }
}
```

### Step 5.3: Update Consumer to Use Idempotent Processor

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.processor.IdempotentOrderProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IdempotentOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentOrderConsumer.class);

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final IdempotentOrderProcessor processor;

    public IdempotentOrderConsumer(
            ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer,
            IdempotentOrderProcessor processor) {
        this.consumer = consumer;
        this.processor = processor;
    }

    // Uncomment to use this consumer
    // @PostConstruct
    public void startConsuming() {
        consumer.receive()
            .flatMap(record ->
                processor.processIdempotently(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(e -> {
                        log.error("Failed to process: {}", record.key(), e);
                        return Mono.empty();
                    })
            )
            .subscribe();
    }
}
```

---

## Part 6: Reactive RabbitMQ (25 min)

### Step 6.1: Configure RabbitMQ

```java
package com.example.messaging.config;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.host}")
    private String host;

    @Value("${rabbitmq.port}")
    private int port;

    @Value("${rabbitmq.username}")
    private String username;

    @Value("${rabbitmq.password}")
    private String password;

    @Bean
    public Mono<Connection> connectionMono() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        return Mono.fromCallable(() -> connectionFactory.newConnection("reactive-messaging"));
    }

    @Bean
    public Sender rabbitSender(Mono<Connection> connectionMono) {
        return RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }

    @Bean
    public Receiver rabbitReceiver(Mono<Connection> connectionMono) {
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }
}
```

### Step 6.2: Create RabbitMQ Publisher

```java
package com.example.messaging.publisher;

import com.example.messaging.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

@Service
public class RabbitOrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderPublisher.class);
    private static final String EXCHANGE = "orders";
    private static final String ROUTING_KEY = "order.created";

    private final Sender sender;
    private final ObjectMapper objectMapper;

    public RabbitOrderPublisher(Sender sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publish(OrderCreatedEvent event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            OutboundMessage message = new OutboundMessage(EXCHANGE, ROUTING_KEY, body);

            return sender.send(Mono.just(message))
                .doOnSuccess(v -> log.info("Published to RabbitMQ: {}", event.getOrderId()))
                .doOnError(e -> log.error("Failed to publish to RabbitMQ: {}", event.getOrderId(), e));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
```

### Step 6.3: Create RabbitMQ Consumer

```java
package com.example.messaging.consumer;

import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.processor.OrderProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

import java.io.IOException;

@Service
public class RabbitOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderConsumer.class);
    private static final String QUEUE = "orders.queue";

    private final Receiver receiver;
    private final ObjectMapper objectMapper;
    private final OrderProcessor orderProcessor;

    public RabbitOrderConsumer(Receiver receiver, ObjectMapper objectMapper, OrderProcessor orderProcessor) {
        this.receiver = receiver;
        this.objectMapper = objectMapper;
        this.orderProcessor = orderProcessor;
    }

    // Uncomment to enable RabbitMQ consumption
    // @PostConstruct
    public void startConsuming() {
        receiver.consumeAutoAck(QUEUE)
            .map(delivery -> {
                try {
                    return objectMapper.readValue(delivery.getBody(), OrderCreatedEvent.class);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deserialize", e);
                }
            })
            .flatMap(event ->
                orderProcessor.process(event)
                    .onErrorResume(e -> {
                        log.error("Error processing RabbitMQ message: {}", event.getOrderId(), e);
                        return Mono.empty();
                    })
            )
            .subscribe();

        log.info("RabbitMQ consumer started on queue: {}", QUEUE);
    }
}
```

---

## Part 7: Transactional Outbox (20 min)

### Step 7.1: Create Outbox Entity and Repository

```java
package com.example.messaging.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("outbox")
public class OutboxEvent {

    @Id
    private Long id;

    @Column("aggregate_type")
    private String aggregateType;

    @Column("aggregate_id")
    private String aggregateId;

    @Column("event_type")
    private String eventType;

    private String payload;

    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("published_at")
    private Instant publishedAt;

    public OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
```

```java
package com.example.messaging.repository;

import com.example.messaging.entity.OutboxEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OutboxRepository extends ReactiveCrudRepository<OutboxEvent, Long> {
    Flux<OutboxEvent> findByStatus(String status);
}
```

### Step 7.2: Create Outbox Service

```java
package com.example.messaging.service;

import com.example.messaging.entity.OutboxEvent;
import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Mono<OutboxEvent> saveToOutbox(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                "Order",
                event.getOrderId(),
                "OrderCreated",
                payload
            );

            return outboxRepository.save(outboxEvent)
                .doOnSuccess(saved -> log.info("Saved to outbox: {}", event.getOrderId()));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
```

### Step 7.3: Create Outbox Publisher

```java
package com.example.messaging.publisher;

import com.example.messaging.entity.OutboxEvent;
import com.example.messaging.event.OrderCreatedEvent;
import com.example.messaging.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            ReactiveKafkaProducerTemplate<String, OrderCreatedEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        outboxRepository.findByStatus("PENDING")
            .flatMap(this::publishEvent)
            .subscribe();
    }

    private Mono<Void> publishEvent(OutboxEvent outboxEvent) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(
                outboxEvent.getPayload(), OrderCreatedEvent.class);

            return kafkaTemplate.send("orders.created", event.getOrderId(), event)
                .flatMap(result -> {
                    outboxEvent.setStatus("PUBLISHED");
                    outboxEvent.setPublishedAt(Instant.now());
                    return outboxRepository.save(outboxEvent);
                })
                .doOnSuccess(saved -> log.info("Published from outbox: {}", outboxEvent.getAggregateId()))
                .onErrorResume(e -> {
                    log.error("Failed to publish from outbox: {}", outboxEvent.getId(), e);
                    return Mono.empty();
                })
                .then();
        } catch (Exception e) {
            log.error("Failed to deserialize outbox event: {}", outboxEvent.getId(), e);
            return Mono.empty();
        }
    }
}
```

### Step 7.4: Enable Scheduling

Add to main application class:

```java
@SpringBootApplication
@EnableScheduling
public class ReactiveMessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveMessagingApplication.class, args);
    }
}
```

---

## Reflection

### Key Takeaways

1. **Reactive Kafka integrates naturally** with WebFlux - messages become Flux
2. **Backpressure works end-to-end** from broker to processing
3. **Error handling is critical** - use DLQs, retries, and idempotency
4. **Transactional Outbox guarantees delivery** without distributed transactions
5. **Choose the right broker** - Kafka for streaming, RabbitMQ for queues

### Common Pitfalls

1. Not acknowledging messages properly
2. Forgetting idempotency in at-least-once scenarios
3. Blocking operations in reactive consumers
4. Not handling deserialization errors

---

## Summary

In this lab, you:

1. Built reactive Kafka producers and consumers
2. Implemented backpressure-aware batch processing
3. Created robust error handling with DLQs
4. Made consumers idempotent for safe reprocessing
5. Set up reactive RabbitMQ for task queues
6. Implemented the Transactional Outbox pattern

You now have the skills to build reliable, scalable messaging systems with reactive Spring!
