# Chapter 15: Reactive Messaging

> "The goal is to minimize coupling between services while maximizing cohesion within them."
> — Sam Newman, Building Microservices

## Introduction

Throughout this book, we've built reactive applications that handle HTTP requests efficiently. But modern distributed systems often need more than request-response communication. They need **message-driven architecture**—the ability to communicate asynchronously through events and messages.

This chapter explores reactive messaging: how to produce and consume messages from brokers like Apache Kafka and RabbitMQ while maintaining the non-blocking, backpressure-aware principles we've established. We'll see how reactive streams and messaging naturally complement each other.

## 15.1 Why Message-Driven Architecture?

### The Limitations of Synchronous Communication

In a typical microservices architecture with HTTP:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SYNCHRONOUS: TIGHT COUPLING                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Order Service                                                          │
│        │                                                                 │
│        │──── HTTP ────▶ Inventory Service                               │
│        │                      │                                          │
│        │◀─────────────────────│                                          │
│        │                                                                 │
│        │──── HTTP ────▶ Payment Service                                 │
│        │                      │                                          │
│        │◀─────────────────────│                                          │
│        │                                                                 │
│        │──── HTTP ────▶ Notification Service                            │
│        │                      │                                          │
│        │◀─────────────────────│                                          │
│        │                                                                 │
│        ▼                                                                 │
│   Response to client                                                     │
│                                                                          │
│   Problems:                                                              │
│   • Order Service must wait for each call                               │
│   • If Notification Service is slow, entire order is slow               │
│   • If any service is down, order fails                                 │
│   • Order Service "knows" about all downstream services                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Asynchronous Messaging Solves These Problems

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ASYNCHRONOUS: LOOSE COUPLING                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Order Service                                                          │
│        │                                                                 │
│        │──── Publish "OrderCreated" ────▶ Message Broker                │
│        │                                         │                       │
│        ▼                                         │                       │
│   Response to client                             │                       │
│   (fast!)                                        │                       │
│                                         ┌───────┴───────┐               │
│                                         │               │               │
│                                         ▼               ▼               │
│                                  Inventory       Notification           │
│                                  Service         Service                │
│                                  (consumes)      (consumes)             │
│                                                                          │
│   Benefits:                                                              │
│   • Order Service responds immediately                                  │
│   • Services are decoupled—don't know about each other                 │
│   • If Notification is slow/down, orders still work                    │
│   • Easy to add new consumers without changing Order Service           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Messaging Concepts

| Concept | Description |
|---------|-------------|
| **Producer** | Sends messages to a broker |
| **Consumer** | Receives messages from a broker |
| **Topic/Queue** | Named destination for messages |
| **Broker** | Server that routes messages (Kafka, RabbitMQ) |
| **Partition** | Subset of a topic for parallelism (Kafka) |
| **Consumer Group** | Multiple consumers sharing work |

## 15.2 Reactive Kafka

### Why Kafka?

Apache Kafka is designed for high-throughput, fault-tolerant messaging:

- **Distributed**: Scales horizontally across many brokers
- **Durable**: Messages are persisted to disk
- **High-throughput**: Millions of messages per second
- **Ordered**: Messages within a partition maintain order
- **Replayable**: Consumers can re-read old messages

### Adding Spring Kafka Reactive

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>io.projectreactor.kafka</groupId>
        <artifactId>reactor-kafka</artifactId>
    </dependency>
</dependencies>
```

### Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: my-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.events"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

### Reactive Kafka Producer

```java
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ReactiveKafkaProducerTemplate<String, Object> reactiveKafkaProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        SenderOptions<String, Object> senderOptions = SenderOptions.create(props);
        return new ReactiveKafkaProducerTemplate<>(senderOptions);
    }
}
```

```java
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final ReactiveKafkaProducerTemplate<String, Object> kafkaTemplate;

    public EventPublisher(ReactiveKafkaProducerTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> publishOrderCreated(OrderCreatedEvent event) {
        return kafkaTemplate.send("orders.created", event.getOrderId(), event)
            .doOnSuccess(result -> log.info("Published OrderCreated: {}, partition: {}, offset: {}",
                event.getOrderId(),
                result.recordMetadata().partition(),
                result.recordMetadata().offset()))
            .doOnError(e -> log.error("Failed to publish OrderCreated: {}", event.getOrderId(), e))
            .then();
    }

    public Flux<SenderResult<Void>> publishBatch(List<OrderCreatedEvent> events) {
        return Flux.fromIterable(events)
            .flatMap(event -> kafkaTemplate.send("orders.created", event.getOrderId(), event));
    }
}
```

### Reactive Kafka Consumer

```java
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
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.events");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return ReceiverOptions.<String, OrderCreatedEvent>create(props)
            .subscription(Collections.singletonList("orders.created"));
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> orderConsumer(
            ReceiverOptions<String, OrderCreatedEvent> options) {
        return new ReactiveKafkaConsumerTemplate<>(options);
    }
}
```

```java
@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final InventoryService inventoryService;

    public OrderEventConsumer(
            ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer,
            InventoryService inventoryService) {
        this.consumer = consumer;
        this.inventoryService = inventoryService;
    }

    @PostConstruct
    public void startConsuming() {
        consumer.receiveAutoAck()
            .doOnNext(record -> log.info("Received: key={}, value={}, partition={}, offset={}",
                record.key(), record.value(), record.partition(), record.offset()))
            .flatMap(record -> processOrder(record.value())
                .onErrorResume(e -> {
                    log.error("Error processing order {}: {}", record.key(), e.getMessage());
                    return Mono.empty();  // Continue processing other messages
                }))
            .subscribe();
    }

    private Mono<Void> processOrder(OrderCreatedEvent event) {
        return inventoryService.reserveItems(event.getItems())
            .doOnSuccess(v -> log.info("Inventory reserved for order {}", event.getOrderId()))
            .doOnError(e -> log.error("Failed to reserve inventory for order {}", event.getOrderId(), e));
    }
}
```

### Manual Acknowledgment for At-Least-Once Processing

```java
@Service
public class ReliableOrderConsumer {

    private final ReactiveKafkaConsumerTemplate<String, OrderCreatedEvent> consumer;
    private final OrderProcessor orderProcessor;

    @PostConstruct
    public void startConsuming() {
        consumer.receive()  // Manual ack mode
            .flatMap(record ->
                orderProcessor.process(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())  // Ack after processing
                    .doOnError(e -> log.error("Processing failed, will retry: {}", record.key()))
                    .onErrorResume(e -> Mono.empty())  // Don't ack on error
            , 16)  // Concurrency: process 16 records in parallel
            .subscribe();
    }
}
```

### Backpressure-Aware Consumption

```java
@Service
public class BackpressureAwareConsumer {

    @PostConstruct
    public void startConsuming() {
        consumer.receive()
            // Limit in-flight messages
            .limitRate(100)
            // Group messages for batch processing
            .bufferTimeout(50, Duration.ofMillis(100))
            // Process batches
            .flatMap(batch -> processBatch(batch), 4)  // 4 concurrent batches
            .subscribe();
    }

    private Mono<Void> processBatch(List<ReceiverRecord<String, OrderCreatedEvent>> batch) {
        return Flux.fromIterable(batch)
            .flatMap(record ->
                orderProcessor.process(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
            )
            .then();
    }
}
```

## 15.3 Reactive RabbitMQ

### When to Choose RabbitMQ

RabbitMQ excels in different scenarios than Kafka:

| Feature | Kafka | RabbitMQ |
|---------|-------|----------|
| **Model** | Log-based | Queue-based |
| **Message retention** | Configurable (days/weeks) | Until consumed |
| **Replay** | Yes | No (by default) |
| **Routing** | Topic-based | Complex routing rules |
| **Ordering** | Per-partition | Per-queue |
| **Best for** | Event streaming, high volume | Task queues, RPC, complex routing |

### Adding Reactor RabbitMQ

```xml
<dependency>
    <groupId>io.projectreactor.rabbitmq</groupId>
    <artifactId>reactor-rabbitmq</artifactId>
</dependency>
```

### Configuration

```java
@Configuration
public class RabbitConfig {

    @Bean
    public Mono<Connection> connectionMono() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        return Mono.fromCallable(() -> connectionFactory.newConnection("reactive-app"));
    }

    @Bean
    public Sender sender(Mono<Connection> connectionMono) {
        return RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }

    @Bean
    public Receiver receiver(Mono<Connection> connectionMono) {
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }
}
```

### Reactive RabbitMQ Producer

```java
@Service
public class RabbitEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final Sender sender;
    private final ObjectMapper objectMapper;

    public RabbitEventPublisher(Sender sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publishOrderCreated(OrderCreatedEvent event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            OutboundMessage message = new OutboundMessage(
                "orders",           // exchange
                "order.created",    // routing key
                body
            );

            return sender.send(Mono.just(message))
                .doOnSuccess(v -> log.info("Published order created: {}", event.getOrderId()))
                .doOnError(e -> log.error("Failed to publish: {}", event.getOrderId(), e));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Flux<Void> publishBatch(List<OrderCreatedEvent> events) {
        return Flux.fromIterable(events)
            .map(event -> {
                try {
                    byte[] body = objectMapper.writeValueAsBytes(event);
                    return new OutboundMessage("orders", "order.created", body);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .as(sender::send);
    }
}
```

### Reactive RabbitMQ Consumer

```java
@Service
public class RabbitOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderConsumer.class);

    private final Receiver receiver;
    private final ObjectMapper objectMapper;
    private final OrderProcessor orderProcessor;

    @PostConstruct
    public void startConsuming() {
        receiver.consumeAutoAck("orders.queue")
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
                        log.error("Error processing order: {}", event.getOrderId(), e);
                        return Mono.empty();
                    })
            )
            .subscribe();
    }
}
```

### Manual Acknowledgment

```java
@Service
public class ReliableRabbitConsumer {

    @PostConstruct
    public void startConsuming() {
        receiver.consumeManualAck("orders.queue")
            .flatMap(delivery -> {
                try {
                    OrderCreatedEvent event = objectMapper.readValue(
                        delivery.getBody(), OrderCreatedEvent.class);

                    return orderProcessor.process(event)
                        .doOnSuccess(v -> delivery.ack())  // Acknowledge on success
                        .doOnError(e -> delivery.nack(true));  // Requeue on error
                } catch (IOException e) {
                    delivery.nack(false);  // Don't requeue unparseable messages
                    return Mono.empty();
                }
            })
            .subscribe();
    }
}
```

## 15.4 Error Handling in Messaging

### Dead Letter Queues

Messages that fail repeatedly should go to a Dead Letter Queue (DLQ):

```java
@Service
public class DLQAwareConsumer {

    private static final int MAX_RETRIES = 3;

    private final Receiver receiver;
    private final Sender sender;
    private final OrderProcessor processor;

    @PostConstruct
    public void startConsuming() {
        receiver.consumeManualAck("orders.queue")
            .flatMap(delivery -> {
                int retryCount = getRetryCount(delivery);

                return processMessage(delivery)
                    .doOnSuccess(v -> delivery.ack())
                    .onErrorResume(e -> {
                        if (retryCount >= MAX_RETRIES) {
                            // Send to DLQ
                            return sendToDLQ(delivery, e)
                                .then(Mono.fromRunnable(() -> delivery.ack()));
                        } else {
                            // Requeue with incremented retry count
                            delivery.nack(true);
                            return Mono.empty();
                        }
                    });
            })
            .subscribe();
    }

    private Mono<Void> sendToDLQ(AcknowledgableDelivery delivery, Throwable error) {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
            .headers(Map.of(
                "x-original-queue", "orders.queue",
                "x-error", error.getMessage(),
                "x-failed-at", Instant.now().toString()
            ))
            .build();

        OutboundMessage dlqMessage = new OutboundMessage(
            "",                    // default exchange
            "orders.dlq",          // DLQ routing key
            props,
            delivery.getBody()
        );

        return sender.send(Mono.just(dlqMessage));
    }

    private int getRetryCount(AcknowledgableDelivery delivery) {
        Map<String, Object> headers = delivery.getProperties().getHeaders();
        if (headers == null) return 0;
        Object count = headers.get("x-retry-count");
        return count == null ? 0 : ((Number) count).intValue();
    }
}
```

### Retry with Backoff

```java
@Service
public class RetryingKafkaConsumer {

    @PostConstruct
    public void startConsuming() {
        consumer.receive()
            .flatMap(record ->
                processWithRetry(record.value())
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(e -> {
                        log.error("Exhausted retries for {}", record.key());
                        record.receiverOffset().acknowledge();  // Move on
                        return sendToErrorTopic(record);
                    })
            )
            .subscribe();
    }

    private Mono<Void> processWithRetry(OrderCreatedEvent event) {
        return orderProcessor.process(event)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(e -> isRetryable(e))
                .doBeforeRetry(signal -> log.warn("Retrying: attempt {}",
                    signal.totalRetries() + 1)));
    }

    private boolean isRetryable(Throwable e) {
        return e instanceof TemporaryException ||
               e instanceof TimeoutException;
    }
}
```

## 15.5 Exactly-Once Semantics

### The Challenge

Exactly-once processing is hard because:

1. **Network failures**: Producer doesn't know if message was received
2. **Consumer failures**: Consumer might crash after processing but before acknowledging
3. **Duplicate delivery**: Broker might redeliver on timeout

### Idempotency: The Practical Solution

Instead of true exactly-once, design for **at-least-once with idempotent processing**:

```java
@Service
public class IdempotentOrderProcessor {

    private final ProcessedEventRepository processedEvents;
    private final OrderService orderService;

    public Mono<Void> process(OrderCreatedEvent event) {
        String eventId = event.getEventId();

        return processedEvents.existsById(eventId)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Skipping duplicate event: {}", eventId);
                    return Mono.empty();
                }
                return processAndRecord(event);
            });
    }

    private Mono<Void> processAndRecord(OrderCreatedEvent event) {
        return orderService.createOrder(event)
            .then(processedEvents.save(new ProcessedEvent(event.getEventId())))
            .then()
            .doOnSuccess(v -> log.info("Processed event: {}", event.getEventId()));
    }
}
```

### Transactional Outbox Pattern

For guaranteed message publishing with database transactions:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTIONAL OUTBOX                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   1. Single Transaction:                                                │
│   ┌────────────────────────────────────────────────────────────────┐    │
│   │ INSERT INTO orders (...)                                        │    │
│   │ INSERT INTO outbox (event_type, payload, status)                │    │
│   │ COMMIT                                                          │    │
│   └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│   2. Background Publisher (polls outbox table):                         │
│   ┌────────────────────────────────────────────────────────────────┐    │
│   │ SELECT * FROM outbox WHERE status = 'PENDING'                   │    │
│   │ For each event:                                                 │    │
│   │   - Publish to Kafka/RabbitMQ                                   │    │
│   │   - UPDATE outbox SET status = 'PUBLISHED' WHERE id = ?         │    │
│   └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│   Benefits:                                                              │
│   • Order creation and event publishing are atomic                      │
│   • No dual-write problem                                               │
│   • Messages guaranteed to be published (eventually)                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

```java
@Service
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final ReactiveKafkaProducerTemplate<String, String> kafkaTemplate;

    // Run periodically
    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        outboxRepository.findByStatus("PENDING")
            .flatMap(this::publishEvent)
            .subscribe();
    }

    private Mono<Void> publishEvent(OutboxEvent event) {
        return kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload())
            .flatMap(result -> {
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                return outboxRepository.save(event);
            })
            .onErrorResume(e -> {
                log.error("Failed to publish event {}: {}", event.getId(), e.getMessage());
                return Mono.empty();
            })
            .then();
    }
}
```

## 15.6 Patterns for Reactive Messaging

### Event Sourcing

Store events as the source of truth:

```java
@Service
public class EventSourcedOrderService {

    private final EventStore eventStore;

    public Mono<Order> createOrder(CreateOrderCommand command) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            UUID.randomUUID().toString(),
            command.getUserId(),
            command.getItems(),
            Instant.now()
        );

        return eventStore.append("orders", event)
            .thenReturn(Order.fromEvent(event));
    }

    public Mono<Order> getOrder(String orderId) {
        return eventStore.getEvents("orders", orderId)
            .reduce(new Order(), Order::apply);
    }
}
```

### CQRS with Messaging

Separate read and write models:

```java
// Command side: publishes events
@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public Mono<Order> createOrder(CreateOrderCommand command) {
        Order order = new Order(command);
        return orderRepository.save(order)
            .flatMap(saved -> eventPublisher.publish(new OrderCreatedEvent(saved))
                .thenReturn(saved));
    }
}

// Query side: consumes events to build read model
@Service
public class OrderQueryService {

    private final OrderSummaryRepository summaryRepository;

    @PostConstruct
    public void consumeEvents() {
        kafkaConsumer.receiveAutoAck()
            .flatMap(record -> updateReadModel(record.value()))
            .subscribe();
    }

    private Mono<Void> updateReadModel(OrderCreatedEvent event) {
        OrderSummary summary = new OrderSummary(
            event.getOrderId(),
            event.getUserId(),
            event.getTotalAmount(),
            "CREATED"
        );
        return summaryRepository.save(summary).then();
    }
}
```

### Saga Orchestration with Messages

```java
@Service
public class OrderSagaOrchestrator {

    private final Sender sender;
    private final Receiver receiver;

    public Mono<String> startOrderSaga(CreateOrderRequest request) {
        String sagaId = UUID.randomUUID().toString();

        // Start saga by publishing first command
        return publishCommand(new ReserveInventoryCommand(sagaId, request.getItems()))
            .thenReturn(sagaId);
    }

    @PostConstruct
    public void listenForEvents() {
        receiver.consumeAutoAck("saga.events")
            .flatMap(this::handleSagaEvent)
            .subscribe();
    }

    private Mono<Void> handleSagaEvent(Delivery delivery) {
        SagaEvent event = deserialize(delivery.getBody());

        return switch (event) {
            case InventoryReservedEvent e -> publishCommand(
                new ProcessPaymentCommand(e.getSagaId(), e.getAmount()));
            case PaymentProcessedEvent e -> publishCommand(
                new CreateShipmentCommand(e.getSagaId(), e.getOrderDetails()));
            case PaymentFailedEvent e -> publishCommand(
                new ReleaseInventoryCommand(e.getSagaId()));
            // ... handle other events
            default -> Mono.empty();
        };
    }
}
```

## 15.7 Monitoring and Observability

### Metrics for Messaging

```java
@Configuration
public class MessagingMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> kafkaMetrics() {
        return registry -> {
            // Consumer lag
            Gauge.builder("kafka.consumer.lag", consumer, this::calculateLag)
                .tag("topic", "orders.created")
                .register(registry);

            // Messages processed
            Counter.builder("kafka.messages.processed")
                .tag("topic", "orders.created")
                .register(registry);
        };
    }
}
```

### Distributed Tracing

```java
@Service
public class TracingEventPublisher {

    private final ReactiveKafkaProducerTemplate<String, Object> kafkaTemplate;
    private final Tracer tracer;

    public Mono<Void> publish(String topic, Object event) {
        return Mono.deferContextual(ctx -> {
            // Extract trace context
            Span span = tracer.spanBuilder("kafka.publish")
                .setParent(ctx.getOrDefault(TraceContext.class, Context.current()))
                .startSpan();

            // Add trace headers to message
            Headers headers = new RecordHeaders();
            tracer.inject(span.getSpanContext(), headers);

            return kafkaTemplate.send(topic, headers, event)
                .doOnSuccess(r -> span.end())
                .doOnError(e -> {
                    span.recordException(e);
                    span.end();
                })
                .then();
        });
    }
}
```

## Summary

Reactive messaging enables truly decoupled, scalable architectures. Key takeaways:

| Broker | Best For | Spring Support |
|--------|----------|----------------|
| **Kafka** | Event streaming, high throughput | Reactor Kafka |
| **RabbitMQ** | Task queues, complex routing | Reactor RabbitMQ |

**Remember:**

1. **Backpressure matters** in messaging too. Use `limitRate()` and batch processing to control consumption rate.

2. **Handle failures gracefully** with Dead Letter Queues, retries with backoff, and idempotent processing.

3. **Don't aim for exactly-once**—design for at-least-once with idempotency instead.

4. **Use the Transactional Outbox** pattern when you need guaranteed event publishing with database transactions.

5. **Monitor everything**: consumer lag, processing rate, error rates, and latency.

Reactive messaging combines the best of both worlds: the efficiency of non-blocking I/O with the resilience of message-driven architecture. Your services stay decoupled, your throughput stays high, and your system stays resilient.
