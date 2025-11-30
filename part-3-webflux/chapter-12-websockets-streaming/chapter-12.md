# Chapter 12: WebSockets and Streaming

> "The stream of thought flows on; but most of its segments fall into the bottomless abyss of oblivion."
> — William James

## Introduction

Throughout this book, we've built reactive applications that respond to requests. But what about applications that need to push data to clients continuously? Real-time dashboards, chat applications, live notifications, collaborative editing—these all require a different communication pattern.

This chapter explores **real-time communication** in WebFlux: WebSockets for bidirectional streaming and Server-Sent Events (SSE) for server-to-client push. Both integrate naturally with reactive streams, allowing you to stream data to clients using the same Flux abstractions you've already mastered.

## 12.1 Understanding Real-Time Communication

### Request-Response vs Real-Time

Traditional HTTP follows a request-response pattern:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REQUEST-RESPONSE PATTERN                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── Request 1 ────────────────────▶│                              │
│     │◀─── Response 1 ───────────────────│                              │
│     │                                     │                              │
│     │──── Request 2 ────────────────────▶│                              │
│     │◀─── Response 2 ───────────────────│                              │
│     │                                     │                              │
│   Client initiates every exchange                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

Real-time communication allows the server to initiate:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    REAL-TIME PATTERN                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── Connect ──────────────────────▶│                              │
│     │◀─── Event 1 ──────────────────────│                              │
│     │◀─── Event 2 ──────────────────────│                              │
│     │──── Message ──────────────────────▶│  (WebSocket only)            │
│     │◀─── Event 3 ──────────────────────│                              │
│     │◀─── Event 4 ──────────────────────│                              │
│     │                                     │                              │
│   Server can push data at any time                                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Two Technologies for Real-Time

| Feature | WebSocket | Server-Sent Events (SSE) |
|---------|-----------|--------------------------|
| Direction | Bidirectional | Server to client only |
| Protocol | WebSocket (ws://, wss://) | HTTP |
| Reconnection | Manual | Automatic |
| Binary data | Yes | Text only |
| Browser support | All modern | All modern |
| Through proxies | Can be tricky | Works everywhere |
| Use case | Chat, games, collaboration | Notifications, feeds, dashboards |

### When to Use Each

**Use WebSockets when:**
- Clients need to send data frequently
- Low latency bidirectional communication is required
- Binary data needs to be transmitted
- Building chat, multiplayer games, collaborative editors

**Use SSE when:**
- Server pushes data, client only receives
- Working with text/JSON data
- Need automatic reconnection
- Building live feeds, notifications, dashboards

## 12.2 Server-Sent Events (SSE)

### The Simplest Real-Time Pattern

SSE is just HTTP with a specific content type (`text/event-stream`) that keeps the connection open:

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamEvents() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(i -> "Event " + i);
}
```

That's it! The browser's `EventSource` API handles the rest.

### SSE with Structured Data

For structured data, use `ServerSentEvent`:

```java
@GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Notification>> streamNotifications() {
    return notificationService.getNotifications()
        .map(notification -> ServerSentEvent.<Notification>builder()
            .id(notification.getId())
            .event(notification.getType())
            .data(notification)
            .retry(Duration.ofSeconds(5))
            .build());
}
```

### SSE Event Format

The `ServerSentEvent` builder maps to the SSE wire format:

```
id: 123
event: notification
data: {"type":"MESSAGE","content":"Hello"}
retry: 5000

```

- **id**: Used for reconnection (Last-Event-ID header)
- **event**: Event type (for client-side event listeners)
- **data**: The actual payload (JSON serialized)
- **retry**: Reconnection interval in milliseconds

### Practical SSE Example: Live Dashboard

```java
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MetricsService metricsService;

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DashboardMetrics>> streamMetrics() {
        return Flux.interval(Duration.ofSeconds(1))
            .flatMap(tick -> metricsService.getCurrentMetrics())
            .map(metrics -> ServerSentEvent.<DashboardMetrics>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("metrics")
                .data(metrics)
                .build())
            .doOnCancel(() -> log.info("Client disconnected from metrics stream"));
    }

    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Alert>> streamAlerts() {
        return alertService.getAlertStream()
            .map(alert -> ServerSentEvent.<Alert>builder()
                .id(alert.getId())
                .event(alert.getSeverity().name().toLowerCase())
                .data(alert)
                .build());
    }
}
```

### Client-Side SSE (JavaScript)

```javascript
const eventSource = new EventSource('/api/dashboard/metrics/stream');

eventSource.addEventListener('metrics', (event) => {
    const metrics = JSON.parse(event.data);
    updateDashboard(metrics);
});

eventSource.addEventListener('error', (event) => {
    console.log('Connection error, will auto-reconnect');
});

// Clean up when leaving page
window.addEventListener('beforeunload', () => {
    eventSource.close();
});
```

### SSE with Functional Endpoints

```java
@Bean
public RouterFunction<ServerResponse> sseRoutes(MetricsHandler handler) {
    return route()
        .GET("/sse/metrics", handler::streamMetrics)
        .GET("/sse/alerts", handler::streamAlerts)
        .build();
}

@Component
public class MetricsHandler {

    public Mono<ServerResponse> streamMetrics(ServerRequest request) {
        Flux<ServerSentEvent<Metrics>> events = Flux.interval(Duration.ofSeconds(1))
            .map(i -> ServerSentEvent.<Metrics>builder()
                .data(collectMetrics())
                .build());

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(events, ServerSentEvent.class);
    }
}
```

## 12.3 WebSocket Fundamentals

### How WebSockets Work

WebSocket starts as HTTP, then upgrades to a persistent bidirectional connection:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WEBSOCKET HANDSHAKE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── HTTP GET /ws ─────────────────▶│                              │
│     │     Upgrade: websocket              │                              │
│     │     Connection: Upgrade             │                              │
│     │                                     │                              │
│     │◀─── HTTP 101 Switching Protocols ──│                              │
│     │     Upgrade: websocket              │                              │
│     │                                     │                              │
│     │◀═══════════════════════════════════│  WebSocket connection        │
│     │═══════════════════════════════════▶│  established                 │
│     │◀═══════════════════════════════════│                              │
│     │                                     │                              │
│   Full-duplex binary/text communication                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### WebSocket in Spring WebFlux

Spring WebFlux provides `WebSocketHandler` for handling WebSocket connections:

```java
@Component
public class EchoWebSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Echo back all received messages
        return session.send(
            session.receive()
                .map(msg -> session.textMessage("Echo: " + msg.getPayloadAsText()))
        );
    }
}
```

### Registering WebSocket Handlers

```java
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(EchoWebSocketHandler echoHandler,
                                                   ChatWebSocketHandler chatHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/echo", echoHandler);
        map.put("/ws/chat", chatHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // Before other handlers
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
```

### Understanding WebSocketSession

The `WebSocketSession` provides:

```java
public interface WebSocketSession {
    // Unique session identifier
    String getId();

    // Connection info
    HandshakeInfo getHandshakeInfo();
    URI getUri();

    // Receive messages as Flux
    Flux<WebSocketMessage> receive();

    // Send messages
    Mono<Void> send(Publisher<WebSocketMessage> messages);

    // Create messages
    WebSocketMessage textMessage(String payload);
    WebSocketMessage binaryMessage(DataBuffer payload);
    WebSocketMessage pingMessage(DataBuffer payload);
    WebSocketMessage pongMessage(DataBuffer payload);

    // Close connection
    Mono<Void> close(CloseStatus status);
}
```

## 12.4 Building a Chat Application

### The Chat Handler

```java
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    // Broadcast sink for distributing messages to all connected clients
    private final Sinks.Many<ChatMessage> chatSink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        String username = extractUsername(session);

        log.info("User '{}' connected (session: {})", username, sessionId);

        // Announce user joined
        broadcastSystemMessage(username + " joined the chat");

        // Handle incoming messages from this client
        Mono<Void> input = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .map(text -> parseMessage(username, text))
            .doOnNext(this::broadcast)
            .doOnError(e -> log.error("Error processing message from {}: {}", username, e.getMessage()))
            .doOnComplete(() -> {
                log.info("User '{}' disconnected", username);
                broadcastSystemMessage(username + " left the chat");
            })
            .then();

        // Send messages to this client
        Mono<Void> output = session.send(
            chatSink.asFlux()
                .map(msg -> session.textMessage(serializeMessage(msg)))
        );

        // Run both input and output processing concurrently
        return Mono.zip(input, output).then();
    }

    private void broadcast(ChatMessage message) {
        chatSink.tryEmitNext(message);
    }

    private void broadcastSystemMessage(String content) {
        broadcast(new ChatMessage("system", "System", content, Instant.now()));
    }

    private String extractUsername(WebSocketSession session) {
        // Extract from query param or header
        return session.getHandshakeInfo().getUri().getQuery() != null
            ? parseQueryParam(session.getHandshakeInfo().getUri().getQuery(), "username")
            : "Anonymous-" + session.getId().substring(0, 4);
    }

    private String parseQueryParam(String query, String param) {
        return Arrays.stream(query.split("&"))
            .filter(p -> p.startsWith(param + "="))
            .map(p -> p.substring(param.length() + 1))
            .findFirst()
            .orElse(null);
    }

    private ChatMessage parseMessage(String username, String text) {
        // Simple text message; could parse JSON for structured messages
        return new ChatMessage(
            UUID.randomUUID().toString(),
            username,
            text,
            Instant.now()
        );
    }

    private String serializeMessage(ChatMessage message) {
        try {
            return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Serialization failed\"}";
        }
    }

    public record ChatMessage(String id, String username, String content, Instant timestamp) {}
}
```

### Chat with Room Support

```java
@Component
public class ChatRoomHandler implements WebSocketHandler {

    private final Map<String, Sinks.Many<ChatMessage>> roomSinks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String roomId = extractRoomId(session);
        String username = extractUsername(session);

        // Get or create room sink
        Sinks.Many<ChatMessage> roomSink = roomSinks.computeIfAbsent(
            roomId, k -> Sinks.many().multicast().onBackpressureBuffer());

        // Track user in room
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);

        // Announce join
        roomSink.tryEmitNext(systemMessage(username + " joined room " + roomId));

        // Handle incoming messages
        Mono<Void> input = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .map(text -> userMessage(username, text))
            .doOnNext(roomSink::tryEmitNext)
            .doOnComplete(() -> handleDisconnect(roomId, username, roomSink))
            .then();

        // Send room messages to this client
        Mono<Void> output = session.send(
            roomSink.asFlux()
                .map(msg -> session.textMessage(toJson(msg)))
        );

        return Mono.zip(input, output).then();
    }

    private void handleDisconnect(String roomId, String username, Sinks.Many<ChatMessage> roomSink) {
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(username);
            roomSink.tryEmitNext(systemMessage(username + " left the room"));

            // Clean up empty rooms
            if (users.isEmpty()) {
                roomSinks.remove(roomId);
                roomUsers.remove(roomId);
            }
        }
    }

    // ... helper methods
}
```

### Client-Side WebSocket (JavaScript)

```javascript
class ChatClient {
    constructor(roomId, username) {
        this.roomId = roomId;
        this.username = username;
        this.socket = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
    }

    connect() {
        const url = `ws://localhost:8080/ws/chat?room=${this.roomId}&username=${this.username}`;
        this.socket = new WebSocket(url);

        this.socket.onopen = () => {
            console.log('Connected to chat');
            this.reconnectAttempts = 0;
        };

        this.socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            this.onMessage(message);
        };

        this.socket.onclose = (event) => {
            console.log('Disconnected:', event.code, event.reason);
            this.attemptReconnect();
        };

        this.socket.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    send(content) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(content);
        }
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
            console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);
            setTimeout(() => this.connect(), delay);
        }
    }

    onMessage(message) {
        // Override in application
        console.log('Message:', message);
    }

    disconnect() {
        if (this.socket) {
            this.socket.close();
        }
    }
}

// Usage
const chat = new ChatClient('general', 'Alice');
chat.onMessage = (msg) => displayMessage(msg);
chat.connect();
chat.send('Hello everyone!');
```

## 12.5 Streaming Patterns

### Streaming Large Data Sets

```java
@GetMapping(value = "/export/users", produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<User> exportUsers() {
    return userRepository.findAll()
        .delayElements(Duration.ofMillis(10)); // Prevent overwhelming client
}

@GetMapping(value = "/export/orders", produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<Order> exportOrders(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    return orderRepository.findByDateRange(from, to)
        .buffer(100) // Process in batches
        .flatMap(Flux::fromIterable)
        .doOnComplete(() -> log.info("Export completed"));
}
```

### Streaming File Uploads

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Mono<UploadResponse> uploadFile(@RequestPart("file") Flux<DataBuffer> fileContent,
                                        @RequestHeader("Content-Length") long contentLength,
                                        @RequestParam String filename) {

    Path destination = uploadDirectory.resolve(filename);

    return DataBufferUtils.write(fileContent, destination)
        .then(Mono.fromCallable(() -> {
            long fileSize = Files.size(destination);
            return new UploadResponse(filename, fileSize, "Upload successful");
        }));
}
```

### Streaming File Downloads

```java
@GetMapping("/download/{filename}")
public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFile(@PathVariable String filename) {
    Path file = downloadDirectory.resolve(filename);

    if (!Files.exists(file)) {
        return Mono.just(ResponseEntity.notFound().build());
    }

    Flux<DataBuffer> fileContent = DataBufferUtils.read(
        file,
        new DefaultDataBufferFactory(),
        4096 // Buffer size
    );

    return Mono.just(ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(fileContent));
}
```

### Streaming Progress Updates

```java
@PostMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ProcessingStatus>> processWithProgress(@RequestBody ProcessRequest request) {

    return Flux.create(sink -> {
        // Start processing in background
        processAsync(request, progress -> {
            sink.next(ServerSentEvent.<ProcessingStatus>builder()
                .event("progress")
                .data(progress)
                .build());
        }).subscribe(
            result -> {
                sink.next(ServerSentEvent.<ProcessingStatus>builder()
                    .event("complete")
                    .data(result)
                    .build());
                sink.complete();
            },
            error -> {
                sink.next(ServerSentEvent.<ProcessingStatus>builder()
                    .event("error")
                    .data(new ProcessingStatus(-1, error.getMessage()))
                    .build());
                sink.complete();
            }
        );
    });
}

public record ProcessingStatus(int percentComplete, String message) {}
```

## 12.6 Advanced WebSocket Patterns

### Typed Message Protocol

```java
@Component
public class TypedWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, MessageHandler<?>> handlers = new HashMap<>();

    public TypedWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Register message handlers
        handlers.put("chat", new ChatMessageHandler());
        handlers.put("command", new CommandMessageHandler());
        handlers.put("ping", new PingMessageHandler());
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Sinks.Many<WebSocketMessage> outbound = Sinks.many().unicast().onBackpressureBuffer();

        SessionContext context = new SessionContext(session, outbound);

        Mono<Void> input = session.receive()
            .flatMap(msg -> processMessage(context, msg))
            .then();

        Mono<Void> output = session.send(outbound.asFlux());

        return Mono.zip(input, output).then();
    }

    private Mono<Void> processMessage(SessionContext context, WebSocketMessage message) {
        try {
            Envelope envelope = objectMapper.readValue(
                message.getPayloadAsText(),
                Envelope.class
            );

            MessageHandler<?> handler = handlers.get(envelope.type());
            if (handler == null) {
                return sendError(context, "Unknown message type: " + envelope.type());
            }

            return handler.handle(context, envelope.payload());

        } catch (JsonProcessingException e) {
            return sendError(context, "Invalid message format");
        }
    }

    private Mono<Void> sendError(SessionContext context, String error) {
        String response = "{\"type\":\"error\",\"payload\":\"" + error + "\"}";
        context.send(context.session().textMessage(response));
        return Mono.empty();
    }

    public record Envelope(String type, JsonNode payload) {}

    public record SessionContext(WebSocketSession session, Sinks.Many<WebSocketMessage> outbound) {
        public void send(WebSocketMessage message) {
            outbound.tryEmitNext(message);
        }
    }

    public interface MessageHandler<T> {
        Mono<Void> handle(SessionContext context, JsonNode payload);
    }
}
```

### Heartbeat and Connection Health

```java
@Component
public class HeartbeatWebSocketHandler implements WebSocketHandler {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Send heartbeat pings
        Flux<WebSocketMessage> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
            .map(i -> session.pingMessage(
                session.bufferFactory().wrap("ping".getBytes())));

        // Handle incoming messages
        Flux<Void> input = session.receive()
            .doOnNext(msg -> {
                if (msg.getType() == WebSocketMessage.Type.PONG) {
                    log.debug("Received pong from session {}", session.getId());
                }
            })
            .flatMap(this::processMessage);

        // Merge heartbeat with regular output
        Flux<WebSocketMessage> output = Flux.merge(heartbeat, getOutputMessages(session));

        return Mono.zip(
            input.then(),
            session.send(output)
        ).then();
    }
}
```

### Session Management

```java
@Component
public class SessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> sessionUsers = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, UserInfo user) {
        sessions.put(session.getId(), session);
        sessionUsers.put(session.getId(), user);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        sessionUsers.remove(session.getId());
    }

    public Mono<Void> sendToUser(String userId, String message) {
        return Flux.fromIterable(sessions.values())
            .filter(session -> {
                UserInfo user = sessionUsers.get(session.getId());
                return user != null && user.id().equals(userId);
            })
            .flatMap(session -> session.send(Mono.just(session.textMessage(message))))
            .then();
    }

    public Mono<Void> broadcast(String message) {
        return Flux.fromIterable(sessions.values())
            .flatMap(session -> session.send(Mono.just(session.textMessage(message))))
            .then();
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public record UserInfo(String id, String username) {}
}
```

## 12.7 Error Handling in Streams

### SSE Error Handling

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Object>> streamEventsWithErrors() {
    return eventSource.getEvents()
        .map(event -> ServerSentEvent.builder()
            .event("data")
            .data(event)
            .build())
        .onErrorResume(e -> {
            log.error("Error in event stream", e);
            return Flux.just(ServerSentEvent.builder()
                .event("error")
                .data(Map.of("message", "Stream error: " + e.getMessage()))
                .build());
        })
        .doOnCancel(() -> log.info("Client cancelled stream"))
        .doOnComplete(() -> log.info("Stream completed"));
}
```

### WebSocket Error Handling

```java
@Override
public Mono<Void> handle(WebSocketSession session) {
    return session.receive()
        .flatMap(message -> processMessage(session, message)
            .onErrorResume(e -> {
                log.error("Error processing message", e);
                return sendError(session, e.getMessage());
            }))
        .then()
        .doOnError(e -> log.error("WebSocket error for session {}", session.getId(), e))
        .onErrorResume(e -> session.close(CloseStatus.SERVER_ERROR))
        .doFinally(signal -> cleanup(session));
}

private Mono<Void> sendError(WebSocketSession session, String error) {
    String errorMessage = "{\"type\":\"error\",\"message\":\"" + error + "\"}";
    return session.send(Mono.just(session.textMessage(errorMessage)));
}

private void cleanup(WebSocketSession session) {
    sessionManager.unregister(session);
    log.info("Session {} cleaned up", session.getId());
}
```

### Graceful Shutdown

```java
@Component
public class GracefulShutdownHandler {

    private final SessionManager sessionManager;
    private final Sinks.Many<Void> shutdownSignal = Sinks.many().multicast().onBackpressureBuffer();

    @PreDestroy
    public void shutdown() {
        log.info("Initiating graceful shutdown...");

        // Notify all connected clients
        sessionManager.broadcast("{\"type\":\"shutdown\",\"message\":\"Server shutting down\"}")
            .then(Mono.delay(Duration.ofSeconds(5)))
            .doOnSuccess(v -> {
                // Close all sessions
                shutdownSignal.tryEmitComplete();
            })
            .subscribe();
    }

    public Flux<Void> getShutdownSignal() {
        return shutdownSignal.asFlux();
    }
}
```

## 12.8 Testing Real-Time Endpoints

### Testing SSE Endpoints

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SSEControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void streamEvents_receivesMultipleEvents() {
        List<String> events = webTestClient.get()
            .uri("/api/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .take(5)
            .collectList()
            .block(Duration.ofSeconds(10));

        assertThat(events).hasSize(5);
    }

    @Test
    void streamMetrics_returnsServerSentEvents() {
        Flux<ServerSentEvent<String>> events = webTestClient.get()
            .uri("/api/dashboard/metrics/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .getResponseBody()
            .take(3);

        StepVerifier.create(events)
            .expectNextCount(3)
            .verifyComplete();
    }
}
```

### Testing WebSocket Handlers

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketHandlerTest {

    @LocalServerPort
    private int port;

    private WebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new ReactorNettyWebSocketClient();
    }

    @Test
    void echoHandler_echosMessages() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/echo");

        List<String> received = new ArrayList<>();

        client.execute(uri, session ->
            session.send(Flux.just(session.textMessage("Hello")))
                .thenMany(session.receive()
                    .take(1)
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(received::add))
                .then()
        ).block(Duration.ofSeconds(5));

        assertThat(received).containsExactly("Echo: Hello");
    }

    @Test
    void chatHandler_broadcastsMessages() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/chat?username=TestUser");

        List<String> received = new CopyOnWriteArrayList<>();

        Mono<Void> session1 = client.execute(uri, session ->
            session.receive()
                .take(Duration.ofSeconds(3))
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(received::add)
                .then()
        );

        Mono<Void> session2 = client.execute(uri, session ->
            Mono.delay(Duration.ofMillis(500))
                .then(session.send(Mono.just(session.textMessage("Hello from session 2"))))
                .then(Mono.delay(Duration.ofSeconds(1)))
        );

        Mono.zip(session1, session2).block(Duration.ofSeconds(5));

        assertThat(received).anyMatch(msg -> msg.contains("Hello from session 2"));
    }
}
```

## Summary

Real-time communication completes the WebFlux picture:

| Technology | Direction | Use Case | Spring Support |
|------------|-----------|----------|----------------|
| **SSE** | Server → Client | Notifications, feeds, dashboards | `Flux<ServerSentEvent>` |
| **WebSocket** | Bidirectional | Chat, games, collaboration | `WebSocketHandler` |
| **Streaming** | Server → Client | Large data export, file transfer | `Flux<T>` |

Key insights from this chapter:

1. **SSE is simple and effective** for server-to-client push. It's just HTTP with a special content type, and browsers handle reconnection automatically.

2. **WebSockets enable bidirectional communication** but require more infrastructure (connection management, heartbeats, error handling).

3. **Reactive streams integrate naturally** with both SSE and WebSockets. A `Flux` becomes a stream of events to the client.

4. **Sinks bridge imperative and reactive** code. Use them to push events from traditional code into reactive streams.

5. **Error handling is critical** in long-lived connections. Always plan for disconnections, timeouts, and failures.

6. **Test real-time endpoints** with `WebTestClient` for SSE and `WebSocketClient` for WebSockets.

This concludes Part III of our journey through Spring WebFlux. You now have the tools to build complete reactive web applications—from handling HTTP requests with annotated or functional endpoints, to calling external services with WebClient, to real-time communication with SSE and WebSockets.
