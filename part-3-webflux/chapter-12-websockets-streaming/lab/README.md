# Lab 12: Building Real-Time Applications with WebSockets and SSE

## Objectives

By the end of this lab, you will:

1. Implement Server-Sent Events (SSE) for live dashboard updates
2. Build a WebSocket-based chat application with rooms
3. Handle connection lifecycle, errors, and reconnection
4. Implement real-time notifications system
5. Stream large data sets efficiently
6. Test real-time endpoints

## Prerequisites

- Completed Chapters 8-11 (WebFlux fundamentals)
- Understanding of Project Reactor (Mono/Flux)
- Java 17+ and Maven installed
- Your favorite IDE

## Lab Structure

| Part | Topic | Duration |
|------|-------|----------|
| 1 | Project Setup | 10 min |
| 2 | Server-Sent Events for Live Dashboard | 25 min |
| 3 | WebSocket Chat Application | 35 min |
| 4 | Real-Time Notifications | 20 min |
| 5 | Streaming Large Data | 15 min |
| 6 | Testing Real-Time Endpoints | 15 min |
| 7 | Reflection | 5 min |
| **Total** | | **125 min** |

---

## Part 1: Project Setup (10 min)

### Step 1.1: Create the Project

Create a new directory and add the following `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>realtime-lab</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

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

### Step 1.2: Create the Project Structure

```
src/main/java/com/example/realtime/
├── RealtimeLabApplication.java
├── config/
│   └── WebSocketConfig.java
├── controller/
│   ├── DashboardController.java
│   └── PageController.java
├── handler/
│   ├── ChatWebSocketHandler.java
│   └── NotificationWebSocketHandler.java
├── service/
│   ├── MetricsService.java
│   ├── NotificationService.java
│   └── ChatService.java
├── dto/
│   ├── DashboardMetrics.java
│   ├── ChatMessage.java
│   └── Notification.java
└── event/
    └── EventPublisher.java

src/main/resources/
├── templates/
│   ├── dashboard.html
│   ├── chat.html
│   └── notifications.html
└── application.properties
```

### Step 1.3: Create the Main Application

```java
package com.example.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RealtimeLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeLabApplication.class, args);
    }
}
```

### Step 1.4: Create DTOs

```java
package com.example.realtime.dto;

import java.time.Instant;

public record DashboardMetrics(
    double cpuUsage,
    double memoryUsage,
    int activeConnections,
    int requestsPerSecond,
    double averageLatency,
    Instant timestamp
) {
    public static DashboardMetrics random() {
        return new DashboardMetrics(
            Math.random() * 100,
            30 + Math.random() * 50,
            (int) (100 + Math.random() * 200),
            (int) (50 + Math.random() * 150),
            10 + Math.random() * 90,
            Instant.now()
        );
    }
}
```

```java
package com.example.realtime.dto;

import java.time.Instant;

public record ChatMessage(
    String id,
    String roomId,
    String username,
    String content,
    MessageType type,
    Instant timestamp
) {
    public enum MessageType {
        CHAT, JOIN, LEAVE, SYSTEM
    }

    public static ChatMessage chat(String roomId, String username, String content) {
        return new ChatMessage(
            java.util.UUID.randomUUID().toString(),
            roomId,
            username,
            content,
            MessageType.CHAT,
            Instant.now()
        );
    }

    public static ChatMessage system(String roomId, String content) {
        return new ChatMessage(
            java.util.UUID.randomUUID().toString(),
            roomId,
            "System",
            content,
            MessageType.SYSTEM,
            Instant.now()
        );
    }

    public static ChatMessage join(String roomId, String username) {
        return new ChatMessage(
            java.util.UUID.randomUUID().toString(),
            roomId,
            username,
            username + " joined the room",
            MessageType.JOIN,
            Instant.now()
        );
    }

    public static ChatMessage leave(String roomId, String username) {
        return new ChatMessage(
            java.util.UUID.randomUUID().toString(),
            roomId,
            username,
            username + " left the room",
            MessageType.LEAVE,
            Instant.now()
        );
    }
}
```

```java
package com.example.realtime.dto;

import java.time.Instant;

public record Notification(
    String id,
    String userId,
    String title,
    String message,
    NotificationType type,
    boolean read,
    Instant timestamp
) {
    public enum NotificationType {
        INFO, SUCCESS, WARNING, ERROR
    }

    public static Notification info(String userId, String title, String message) {
        return new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            title,
            message,
            NotificationType.INFO,
            false,
            Instant.now()
        );
    }

    public static Notification success(String userId, String title, String message) {
        return new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            title,
            message,
            NotificationType.SUCCESS,
            false,
            Instant.now()
        );
    }

    public static Notification warning(String userId, String title, String message) {
        return new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            title,
            message,
            NotificationType.WARNING,
            false,
            Instant.now()
        );
    }

    public static Notification error(String userId, String title, String message) {
        return new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            title,
            message,
            NotificationType.ERROR,
            false,
            Instant.now()
        );
    }
}
```

---

## Part 2: Server-Sent Events for Live Dashboard (25 min)

### Step 2.1: Create the Metrics Service

```java
package com.example.realtime.service;

import com.example.realtime.dto.DashboardMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    // Sink for publishing custom events
    private final Sinks.Many<DashboardMetrics> metricsSink =
        Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Stream metrics every second
     */
    public Flux<DashboardMetrics> getMetricsStream() {
        return Flux.interval(Duration.ofSeconds(1))
            .map(tick -> generateMetrics())
            .doOnSubscribe(s -> {
                int count = subscriberCount.incrementAndGet();
                log.info("New subscriber to metrics stream. Total: {}", count);
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.info("Subscriber cancelled. Total: {}", count);
            })
            .doOnError(e -> log.error("Error in metrics stream", e));
    }

    /**
     * Get current metrics (snapshot)
     */
    public DashboardMetrics getCurrentMetrics() {
        return generateMetrics();
    }

    /**
     * Publish a custom metric (for testing)
     */
    public void publishMetric(DashboardMetrics metrics) {
        metricsSink.tryEmitNext(metrics);
    }

    /**
     * Stream that includes both regular and custom metrics
     */
    public Flux<DashboardMetrics> getEnhancedMetricsStream() {
        Flux<DashboardMetrics> regular = Flux.interval(Duration.ofSeconds(1))
            .map(tick -> generateMetrics());

        return Flux.merge(regular, metricsSink.asFlux());
    }

    public int getSubscriberCount() {
        return subscriberCount.get();
    }

    private DashboardMetrics generateMetrics() {
        // Simulate realistic metrics with some continuity
        return DashboardMetrics.random();
    }
}
```

### Step 2.2: Create the Dashboard Controller

```java
package com.example.realtime.controller;

import com.example.realtime.dto.DashboardMetrics;
import com.example.realtime.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final MetricsService metricsService;

    public DashboardController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Stream metrics as Server-Sent Events
     */
    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DashboardMetrics>> streamMetrics() {
        return metricsService.getMetricsStream()
            .map(metrics -> ServerSentEvent.<DashboardMetrics>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("metrics")
                .data(metrics)
                .retry(Duration.ofSeconds(5))
                .build())
            .doOnSubscribe(s -> log.info("Client subscribed to metrics stream"))
            .doOnCancel(() -> log.info("Client disconnected from metrics stream"));
    }

    /**
     * Stream with different event types
     */
    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamEvents() {
        // Heartbeat every 30 seconds
        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(30))
            .map(tick -> ServerSentEvent.builder()
                .event("heartbeat")
                .data(Map.of("timestamp", System.currentTimeMillis()))
                .build());

        // Metrics every second
        Flux<ServerSentEvent<Object>> metrics = metricsService.getMetricsStream()
            .map(m -> ServerSentEvent.builder()
                .event("metrics")
                .data(m)
                .build());

        // Random alerts
        Flux<ServerSentEvent<Object>> alerts = Flux.interval(Duration.ofSeconds(10))
            .filter(tick -> Math.random() > 0.7) // 30% chance
            .map(tick -> ServerSentEvent.builder()
                .event("alert")
                .data(Map.of(
                    "level", Math.random() > 0.5 ? "warning" : "critical",
                    "message", "Sample alert at tick " + tick
                ))
                .build());

        return Flux.merge(heartbeat, metrics, alerts);
    }

    /**
     * Get current metrics (non-streaming)
     */
    @GetMapping("/metrics/current")
    public Mono<DashboardMetrics> getCurrentMetrics() {
        return Mono.just(metricsService.getCurrentMetrics());
    }

    /**
     * Get subscriber count
     */
    @GetMapping("/metrics/subscribers")
    public Mono<Map<String, Integer>> getSubscriberCount() {
        return Mono.just(Map.of("count", metricsService.getSubscriberCount()));
    }
}
```

### Step 2.3: Create the Dashboard HTML Page

Create `src/main/resources/templates/dashboard.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Live Dashboard</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a2e;
            color: #eee;
            margin: 0;
            padding: 20px;
        }
        h1 { color: #00d4ff; margin-bottom: 20px; }
        .dashboard {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            max-width: 1200px;
        }
        .metric-card {
            background: #16213e;
            border-radius: 10px;
            padding: 20px;
            text-align: center;
        }
        .metric-value {
            font-size: 2.5em;
            font-weight: bold;
            color: #00d4ff;
        }
        .metric-label {
            color: #888;
            margin-top: 10px;
        }
        .status {
            margin-top: 20px;
            padding: 10px;
            border-radius: 5px;
        }
        .status.connected { background: #1b4332; color: #95d5b2; }
        .status.disconnected { background: #641220; color: #f4a3a8; }
        .status.connecting { background: #3d405b; color: #f4f1de; }
        .alerts {
            margin-top: 20px;
            max-height: 200px;
            overflow-y: auto;
        }
        .alert {
            padding: 10px;
            margin: 5px 0;
            border-radius: 5px;
        }
        .alert.warning { background: #854d0e; }
        .alert.critical { background: #7f1d1d; }
    </style>
</head>
<body>
    <h1>Live Dashboard</h1>

    <div id="status" class="status connecting">Connecting...</div>

    <div class="dashboard">
        <div class="metric-card">
            <div class="metric-value" id="cpu">--</div>
            <div class="metric-label">CPU Usage %</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="memory">--</div>
            <div class="metric-label">Memory Usage %</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="connections">--</div>
            <div class="metric-label">Active Connections</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="rps">--</div>
            <div class="metric-label">Requests/sec</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="latency">--</div>
            <div class="metric-label">Avg Latency (ms)</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="timestamp">--</div>
            <div class="metric-label">Last Update</div>
        </div>
    </div>

    <div class="alerts" id="alerts"></div>

    <script>
        let eventSource;
        let reconnectAttempts = 0;

        function connect() {
            updateStatus('connecting', 'Connecting...');

            eventSource = new EventSource('/api/dashboard/events/stream');

            eventSource.addEventListener('metrics', (event) => {
                const metrics = JSON.parse(event.data);
                updateMetrics(metrics);
            });

            eventSource.addEventListener('alert', (event) => {
                const alert = JSON.parse(event.data);
                addAlert(alert);
            });

            eventSource.addEventListener('heartbeat', (event) => {
                console.log('Heartbeat received');
            });

            eventSource.onopen = () => {
                updateStatus('connected', 'Connected');
                reconnectAttempts = 0;
            };

            eventSource.onerror = (error) => {
                console.error('SSE error:', error);
                eventSource.close();
                updateStatus('disconnected', 'Disconnected - Reconnecting...');
                scheduleReconnect();
            };
        }

        function updateMetrics(metrics) {
            document.getElementById('cpu').textContent = metrics.cpuUsage.toFixed(1);
            document.getElementById('memory').textContent = metrics.memoryUsage.toFixed(1);
            document.getElementById('connections').textContent = metrics.activeConnections;
            document.getElementById('rps').textContent = metrics.requestsPerSecond;
            document.getElementById('latency').textContent = metrics.averageLatency.toFixed(1);
            document.getElementById('timestamp').textContent = new Date(metrics.timestamp).toLocaleTimeString();
        }

        function addAlert(alert) {
            const alertsDiv = document.getElementById('alerts');
            const alertEl = document.createElement('div');
            alertEl.className = `alert ${alert.level}`;
            alertEl.textContent = `[${alert.level.toUpperCase()}] ${alert.message}`;
            alertsDiv.insertBefore(alertEl, alertsDiv.firstChild);

            // Keep only last 10 alerts
            while (alertsDiv.children.length > 10) {
                alertsDiv.removeChild(alertsDiv.lastChild);
            }
        }

        function updateStatus(className, text) {
            const statusEl = document.getElementById('status');
            statusEl.className = `status ${className}`;
            statusEl.textContent = text;
        }

        function scheduleReconnect() {
            reconnectAttempts++;
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
            console.log(`Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`);
            setTimeout(connect, delay);
        }

        // Start connection
        connect();

        // Cleanup on page unload
        window.addEventListener('beforeunload', () => {
            if (eventSource) {
                eventSource.close();
            }
        });
    </script>
</body>
</html>
```

### Step 2.4: Create Page Controller

```java
package com.example.realtime.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }

    @GetMapping("/notifications")
    public String notifications() {
        return "notifications";
    }
}
```

### Step 2.5: Test the Dashboard

1. Start the application
2. Open http://localhost:8080/dashboard
3. Watch the metrics update in real-time
4. Open multiple tabs to see the subscriber count increase

---

## Part 3: WebSocket Chat Application (35 min)

### Step 3.1: Create the Chat Service

```java
package com.example.realtime.service;

import com.example.realtime.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // Room ID -> Message Sink
    private final Map<String, Sinks.Many<ChatMessage>> roomSinks = new ConcurrentHashMap<>();

    // Room ID -> Set of usernames
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    /**
     * Get or create a room's message sink
     */
    public Sinks.Many<ChatMessage> getRoomSink(String roomId) {
        return roomSinks.computeIfAbsent(roomId,
            k -> Sinks.many().multicast().onBackpressureBuffer(1000));
    }

    /**
     * Subscribe to a room's messages
     */
    public Flux<ChatMessage> joinRoom(String roomId, String username) {
        // Add user to room
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);

        // Broadcast join message
        broadcast(roomId, ChatMessage.join(roomId, username));

        log.info("User '{}' joined room '{}'. Users in room: {}",
            username, roomId, roomUsers.get(roomId).size());

        return getRoomSink(roomId).asFlux();
    }

    /**
     * Leave a room
     */
    public void leaveRoom(String roomId, String username) {
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(username);

            // Broadcast leave message
            broadcast(roomId, ChatMessage.leave(roomId, username));

            log.info("User '{}' left room '{}'. Users in room: {}",
                username, roomId, users.size());

            // Clean up empty rooms
            if (users.isEmpty()) {
                roomSinks.remove(roomId);
                roomUsers.remove(roomId);
                log.info("Room '{}' is now empty and has been removed", roomId);
            }
        }
    }

    /**
     * Send a message to a room
     */
    public void sendMessage(String roomId, String username, String content) {
        ChatMessage message = ChatMessage.chat(roomId, username, content);
        broadcast(roomId, message);
    }

    /**
     * Broadcast a message to a room
     */
    public void broadcast(String roomId, ChatMessage message) {
        Sinks.Many<ChatMessage> sink = roomSinks.get(roomId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }

    /**
     * Get users in a room
     */
    public Set<String> getRoomUsers(String roomId) {
        return roomUsers.getOrDefault(roomId, Set.of());
    }

    /**
     * Get all active rooms
     */
    public Set<String> getActiveRooms() {
        return roomSinks.keySet();
    }

    /**
     * Get room count
     */
    public int getRoomCount() {
        return roomSinks.size();
    }
}
```

### Step 3.2: Create the Chat WebSocket Handler

```java
package com.example.realtime.handler;

import com.example.realtime.dto.ChatMessage;
import com.example.realtime.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract room and username from query params
        String query = session.getHandshakeInfo().getUri().getQuery();
        String roomId = extractParam(query, "room", "general");
        String username = extractParam(query, "username", "Anonymous-" + session.getId().substring(0, 4));

        log.info("WebSocket connection: room={}, username={}, sessionId={}",
            roomId, username, session.getId());

        // Subscribe to room messages
        var messagesFlux = chatService.joinRoom(roomId, username);

        // Handle incoming messages from this client
        Mono<Void> input = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(text -> handleIncomingMessage(roomId, username, text))
            .doOnError(e -> log.error("Error receiving message from {}: {}", username, e.getMessage()))
            .doOnComplete(() -> chatService.leaveRoom(roomId, username))
            .doOnCancel(() -> chatService.leaveRoom(roomId, username))
            .then();

        // Send room messages to this client
        Mono<Void> output = session.send(
            messagesFlux
                .map(msg -> session.textMessage(toJson(msg)))
                .doOnError(e -> log.error("Error sending message to {}: {}", username, e.getMessage()))
        );

        // Run both input and output concurrently
        return Mono.zip(input, output)
            .doOnError(e -> log.error("WebSocket error for {}: {}", username, e.getMessage()))
            .doFinally(signal -> log.info("WebSocket closed for {}: {}", username, signal))
            .then();
    }

    private void handleIncomingMessage(String roomId, String username, String text) {
        try {
            JsonNode json = objectMapper.readTree(text);
            String type = json.has("type") ? json.get("type").asText() : "chat";
            String content = json.has("content") ? json.get("content").asText() : text;

            if ("chat".equals(type)) {
                chatService.sendMessage(roomId, username, content);
            }
            // Can handle other message types here (typing, etc.)
        } catch (JsonProcessingException e) {
            // Treat as plain text message
            chatService.sendMessage(roomId, username, text);
        }
    }

    private String extractParam(String query, String param, String defaultValue) {
        if (query == null) return defaultValue;
        return Arrays.stream(query.split("&"))
            .filter(p -> p.startsWith(param + "="))
            .map(p -> p.substring(param.length() + 1))
            .findFirst()
            .orElse(defaultValue);
    }

    private String toJson(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Serialization failed\"}";
        }
    }
}
```

### Step 3.3: Create the WebSocket Configuration

```java
package com.example.realtime.config;

import com.example.realtime.handler.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(ChatWebSocketHandler chatHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/chat", chatHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
```

### Step 3.4: Create the Chat HTML Page

Create `src/main/resources/templates/chat.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chat Room</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a2e;
            color: #eee;
            margin: 0;
            padding: 20px;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        h1 { color: #00d4ff; margin: 0 0 20px 0; }
        .chat-container {
            flex: 1;
            display: flex;
            flex-direction: column;
            max-width: 800px;
            width: 100%;
            margin: 0 auto;
        }
        .controls {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
        }
        .controls input, .controls button {
            padding: 10px 15px;
            border: none;
            border-radius: 5px;
            font-size: 14px;
        }
        .controls input {
            background: #16213e;
            color: #eee;
            flex: 1;
        }
        .controls button {
            background: #00d4ff;
            color: #1a1a2e;
            cursor: pointer;
        }
        .controls button:hover {
            background: #00b4d8;
        }
        .status {
            padding: 10px;
            border-radius: 5px;
            margin-bottom: 10px;
            text-align: center;
        }
        .status.connected { background: #1b4332; color: #95d5b2; }
        .status.disconnected { background: #641220; color: #f4a3a8; }
        .messages {
            flex: 1;
            overflow-y: auto;
            background: #16213e;
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .message {
            margin-bottom: 15px;
            padding: 10px;
            border-radius: 5px;
        }
        .message.chat { background: #0f3460; }
        .message.system { background: #3d405b; font-style: italic; }
        .message.join { background: #1b4332; }
        .message.leave { background: #641220; }
        .message .username {
            font-weight: bold;
            color: #00d4ff;
        }
        .message .time {
            color: #888;
            font-size: 12px;
            float: right;
        }
        .message .content {
            margin-top: 5px;
        }
        .send-form {
            display: flex;
            gap: 10px;
        }
        .send-form input {
            flex: 1;
            padding: 15px;
            border: none;
            border-radius: 5px;
            background: #16213e;
            color: #eee;
            font-size: 16px;
        }
        .send-form button {
            padding: 15px 30px;
            border: none;
            border-radius: 5px;
            background: #00d4ff;
            color: #1a1a2e;
            font-size: 16px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="chat-container">
        <h1>Chat Room: <span id="roomName">general</span></h1>

        <div class="controls">
            <input type="text" id="roomInput" placeholder="Room name" value="general">
            <input type="text" id="usernameInput" placeholder="Username">
            <button onclick="joinRoom()">Join Room</button>
        </div>

        <div id="status" class="status disconnected">Disconnected</div>

        <div class="messages" id="messages"></div>

        <form class="send-form" onsubmit="sendMessage(event)">
            <input type="text" id="messageInput" placeholder="Type a message..." disabled>
            <button type="submit" id="sendBtn" disabled>Send</button>
        </form>
    </div>

    <script>
        let socket;
        let currentRoom = 'general';
        let currentUsername = '';

        function joinRoom() {
            // Close existing connection
            if (socket) {
                socket.close();
            }

            currentRoom = document.getElementById('roomInput').value || 'general';
            currentUsername = document.getElementById('usernameInput').value || 'Anonymous';

            document.getElementById('roomName').textContent = currentRoom;

            const url = `ws://${window.location.host}/ws/chat?room=${encodeURIComponent(currentRoom)}&username=${encodeURIComponent(currentUsername)}`;

            socket = new WebSocket(url);

            socket.onopen = () => {
                updateStatus('connected', `Connected as ${currentUsername}`);
                document.getElementById('messageInput').disabled = false;
                document.getElementById('sendBtn').disabled = false;
                document.getElementById('messages').innerHTML = '';
            };

            socket.onmessage = (event) => {
                const message = JSON.parse(event.data);
                displayMessage(message);
            };

            socket.onclose = (event) => {
                updateStatus('disconnected', 'Disconnected');
                document.getElementById('messageInput').disabled = true;
                document.getElementById('sendBtn').disabled = true;
            };

            socket.onerror = (error) => {
                console.error('WebSocket error:', error);
            };
        }

        function sendMessage(event) {
            event.preventDefault();
            const input = document.getElementById('messageInput');
            const content = input.value.trim();

            if (content && socket && socket.readyState === WebSocket.OPEN) {
                socket.send(JSON.stringify({
                    type: 'chat',
                    content: content
                }));
                input.value = '';
            }
        }

        function displayMessage(message) {
            const messagesDiv = document.getElementById('messages');
            const msgEl = document.createElement('div');
            msgEl.className = `message ${message.type.toLowerCase()}`;

            const time = new Date(message.timestamp).toLocaleTimeString();

            if (message.type === 'CHAT') {
                msgEl.innerHTML = `
                    <span class="time">${time}</span>
                    <span class="username">${escapeHtml(message.username)}</span>
                    <div class="content">${escapeHtml(message.content)}</div>
                `;
            } else {
                msgEl.innerHTML = `
                    <span class="time">${time}</span>
                    <div class="content">${escapeHtml(message.content)}</div>
                `;
            }

            messagesDiv.appendChild(msgEl);
            messagesDiv.scrollTop = messagesDiv.scrollHeight;
        }

        function updateStatus(className, text) {
            const statusEl = document.getElementById('status');
            statusEl.className = `status ${className}`;
            statusEl.textContent = text;
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // Generate random username
        document.getElementById('usernameInput').value = 'User' + Math.floor(Math.random() * 1000);
    </script>
</body>
</html>
```

### Step 3.5: Add Chat API Endpoints

```java
package com.example.realtime.controller;

import com.example.realtime.service.ChatService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public Mono<Set<String>> getActiveRooms() {
        return Mono.just(chatService.getActiveRooms());
    }

    @GetMapping("/rooms/{roomId}/users")
    public Mono<Set<String>> getRoomUsers(@PathVariable String roomId) {
        return Mono.just(chatService.getRoomUsers(roomId));
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> getStats() {
        return Mono.just(Map.of(
            "activeRooms", chatService.getRoomCount(),
            "rooms", chatService.getActiveRooms()
        ));
    }
}
```

### Step 3.6: Test the Chat Application

1. Open http://localhost:8080/chat in multiple browser windows
2. Enter different usernames and join the same room
3. Send messages and see them appear in all windows
4. Try different rooms

---

## Part 4: Real-Time Notifications (20 min)

### Step 4.1: Create the Notification Service

```java
package com.example.realtime.service;

import com.example.realtime.dto.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // Global notification sink (for all users)
    private final Sinks.Many<Notification> globalSink =
        Sinks.many().multicast().onBackpressureBuffer(100);

    // User-specific notification sinks
    private final Map<String, Sinks.Many<Notification>> userSinks = new ConcurrentHashMap<>();

    /**
     * Subscribe to notifications for a specific user
     */
    public Flux<Notification> subscribeToUserNotifications(String userId) {
        Sinks.Many<Notification> userSink = userSinks.computeIfAbsent(userId,
            k -> Sinks.many().multicast().onBackpressureBuffer(100));

        // Merge global and user-specific notifications
        return Flux.merge(
            globalSink.asFlux().filter(n -> n.userId() == null || n.userId().equals(userId)),
            userSink.asFlux()
        );
    }

    /**
     * Send a notification to a specific user
     */
    public void sendToUser(String userId, Notification notification) {
        Sinks.Many<Notification> userSink = userSinks.get(userId);
        if (userSink != null) {
            userSink.tryEmitNext(notification);
            log.info("Sent notification to user {}: {}", userId, notification.title());
        } else {
            log.warn("No active subscription for user {}", userId);
        }
    }

    /**
     * Send a notification to all users
     */
    public void broadcast(Notification notification) {
        globalSink.tryEmitNext(notification);
        log.info("Broadcast notification: {}", notification.title());
    }

    /**
     * Remove user subscription (cleanup)
     */
    public void unsubscribe(String userId) {
        userSinks.remove(userId);
        log.info("User {} unsubscribed from notifications", userId);
    }

    /**
     * Get count of active subscribers
     */
    public int getSubscriberCount() {
        return userSinks.size();
    }
}
```

### Step 4.2: Create Notification Controller

```java
package com.example.realtime.controller;

import com.example.realtime.dto.Notification;
import com.example.realtime.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Subscribe to notifications via SSE
     */
    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Notification>> streamNotifications(@PathVariable String userId) {
        return notificationService.subscribeToUserNotifications(userId)
            .map(notification -> ServerSentEvent.<Notification>builder()
                .id(notification.id())
                .event(notification.type().name().toLowerCase())
                .data(notification)
                .build())
            .doOnCancel(() -> notificationService.unsubscribe(userId));
    }

    /**
     * Send a notification to a specific user
     */
    @PostMapping("/send/{userId}")
    public Mono<Map<String, String>> sendNotification(
            @PathVariable String userId,
            @RequestBody NotificationRequest request) {

        Notification notification = switch (request.type()) {
            case "success" -> Notification.success(userId, request.title(), request.message());
            case "warning" -> Notification.warning(userId, request.title(), request.message());
            case "error" -> Notification.error(userId, request.title(), request.message());
            default -> Notification.info(userId, request.title(), request.message());
        };

        notificationService.sendToUser(userId, notification);
        return Mono.just(Map.of("status", "sent", "id", notification.id()));
    }

    /**
     * Broadcast a notification to all users
     */
    @PostMapping("/broadcast")
    public Mono<Map<String, String>> broadcastNotification(@RequestBody NotificationRequest request) {
        Notification notification = Notification.info(null, request.title(), request.message());
        notificationService.broadcast(notification);
        return Mono.just(Map.of("status", "broadcast", "id", notification.id()));
    }

    /**
     * Get subscriber stats
     */
    @GetMapping("/stats")
    public Mono<Map<String, Integer>> getStats() {
        return Mono.just(Map.of("subscribers", notificationService.getSubscriberCount()));
    }

    public record NotificationRequest(String type, String title, String message) {}
}
```

### Step 4.3: Create Notifications HTML Page

Create `src/main/resources/templates/notifications.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Notifications</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a2e;
            color: #eee;
            margin: 0;
            padding: 20px;
        }
        h1 { color: #00d4ff; }
        .container { max-width: 800px; margin: 0 auto; }
        .controls {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
            flex-wrap: wrap;
        }
        .controls input, .controls select, .controls button {
            padding: 10px 15px;
            border: none;
            border-radius: 5px;
            font-size: 14px;
        }
        .controls input, .controls select {
            background: #16213e;
            color: #eee;
        }
        .controls button {
            background: #00d4ff;
            color: #1a1a2e;
            cursor: pointer;
        }
        .status {
            padding: 10px;
            border-radius: 5px;
            margin-bottom: 20px;
        }
        .status.connected { background: #1b4332; color: #95d5b2; }
        .status.disconnected { background: #641220; color: #f4a3a8; }
        .notifications {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .notification {
            padding: 15px;
            border-radius: 5px;
            animation: slideIn 0.3s ease;
        }
        @keyframes slideIn {
            from { transform: translateX(-20px); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
        .notification.info { background: #16213e; border-left: 4px solid #00d4ff; }
        .notification.success { background: #1b4332; border-left: 4px solid #95d5b2; }
        .notification.warning { background: #854d0e; border-left: 4px solid #fbbf24; }
        .notification.error { background: #7f1d1d; border-left: 4px solid #f87171; }
        .notification .title { font-weight: bold; margin-bottom: 5px; }
        .notification .time { color: #888; font-size: 12px; float: right; }
        .send-section {
            background: #16213e;
            padding: 20px;
            border-radius: 10px;
            margin-bottom: 20px;
        }
        .send-section h3 { margin-top: 0; color: #00d4ff; }
        .send-form {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .send-form input, .send-form select, .send-form textarea {
            padding: 10px;
            border: none;
            border-radius: 5px;
            background: #0f3460;
            color: #eee;
        }
        .send-form button {
            padding: 10px;
            border: none;
            border-radius: 5px;
            background: #00d4ff;
            color: #1a1a2e;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Real-Time Notifications</h1>

        <div class="controls">
            <input type="text" id="userId" placeholder="User ID" value="user1">
            <button onclick="subscribe()">Subscribe</button>
            <button onclick="unsubscribe()">Unsubscribe</button>
        </div>

        <div id="status" class="status disconnected">Not subscribed</div>

        <div class="send-section">
            <h3>Send Test Notification</h3>
            <div class="send-form">
                <input type="text" id="targetUser" placeholder="Target User ID (leave empty for broadcast)">
                <select id="notificationType">
                    <option value="info">Info</option>
                    <option value="success">Success</option>
                    <option value="warning">Warning</option>
                    <option value="error">Error</option>
                </select>
                <input type="text" id="notificationTitle" placeholder="Title" value="Test Notification">
                <textarea id="notificationMessage" placeholder="Message" rows="2">This is a test notification message.</textarea>
                <button onclick="sendNotification()">Send Notification</button>
            </div>
        </div>

        <h2>Received Notifications</h2>
        <div class="notifications" id="notifications"></div>
    </div>

    <script>
        let eventSource;

        function subscribe() {
            unsubscribe();

            const userId = document.getElementById('userId').value || 'user1';
            eventSource = new EventSource(`/api/notifications/stream/${userId}`);

            eventSource.addEventListener('info', handleNotification);
            eventSource.addEventListener('success', handleNotification);
            eventSource.addEventListener('warning', handleNotification);
            eventSource.addEventListener('error', handleNotification);

            eventSource.onopen = () => {
                updateStatus('connected', `Subscribed as ${userId}`);
            };

            eventSource.onerror = () => {
                updateStatus('disconnected', 'Connection error');
            };
        }

        function unsubscribe() {
            if (eventSource) {
                eventSource.close();
                eventSource = null;
                updateStatus('disconnected', 'Not subscribed');
            }
        }

        function handleNotification(event) {
            const notification = JSON.parse(event.data);
            displayNotification(notification);
        }

        function displayNotification(notification) {
            const container = document.getElementById('notifications');
            const el = document.createElement('div');
            el.className = `notification ${notification.type.toLowerCase()}`;

            const time = new Date(notification.timestamp).toLocaleTimeString();
            el.innerHTML = `
                <span class="time">${time}</span>
                <div class="title">${escapeHtml(notification.title)}</div>
                <div class="message">${escapeHtml(notification.message)}</div>
            `;

            container.insertBefore(el, container.firstChild);

            // Keep only last 20 notifications
            while (container.children.length > 20) {
                container.removeChild(container.lastChild);
            }
        }

        async function sendNotification() {
            const targetUser = document.getElementById('targetUser').value;
            const type = document.getElementById('notificationType').value;
            const title = document.getElementById('notificationTitle').value;
            const message = document.getElementById('notificationMessage').value;

            const url = targetUser
                ? `/api/notifications/send/${targetUser}`
                : '/api/notifications/broadcast';

            try {
                const response = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ type, title, message })
                });
                const result = await response.json();
                console.log('Notification sent:', result);
            } catch (error) {
                console.error('Failed to send notification:', error);
            }
        }

        function updateStatus(className, text) {
            const statusEl = document.getElementById('status');
            statusEl.className = `status ${className}`;
            statusEl.textContent = text;
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
```

### Step 4.4: Test Notifications

1. Open http://localhost:8080/notifications in multiple tabs
2. Subscribe with different user IDs
3. Send targeted and broadcast notifications
4. Watch notifications appear in real-time

---

## Part 5: Streaming Large Data (15 min)

### Step 5.1: Create Streaming Endpoints

```java
package com.example.realtime.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/stream")
public class StreamingController {

    /**
     * Stream large dataset as NDJSON
     */
    @GetMapping(value = "/data", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Map<String, Object>> streamData(
            @RequestParam(defaultValue = "1000") int count,
            @RequestParam(defaultValue = "10") int delayMs) {

        return Flux.fromStream(IntStream.range(0, count).boxed())
            .delayElements(Duration.ofMillis(delayMs))
            .map(i -> Map.of(
                "id", i,
                "timestamp", System.currentTimeMillis(),
                "data", "Record " + i,
                "value", Math.random() * 100
            ));
    }

    /**
     * Stream with progress updates via SSE
     */
    @GetMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> processWithProgress(
            @RequestParam(defaultValue = "100") int total) {

        return Flux.range(1, total)
            .delayElements(Duration.ofMillis(50))
            .map(i -> {
                int percent = (int) ((i / (double) total) * 100);
                Map<String, Object> data = Map.of(
                    "current", i,
                    "total", total,
                    "percent", percent,
                    "status", i == total ? "complete" : "processing"
                );
                return ServerSentEvent.<Map<String, Object>>builder()
                    .event(i == total ? "complete" : "progress")
                    .data(data)
                    .build();
            });
    }

    /**
     * Infinite stream (for testing backpressure)
     */
    @GetMapping(value = "/infinite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> infiniteStream() {
        return Flux.interval(Duration.ofMillis(100))
            .map(i -> ServerSentEvent.<Map<String, Object>>builder()
                .id(String.valueOf(i))
                .event("tick")
                .data(Map.of("tick", i, "timestamp", System.currentTimeMillis()))
                .build());
    }
}
```

### Step 5.2: Test Streaming

```bash
# Stream data as NDJSON
curl http://localhost:8080/api/stream/data?count=100&delayMs=50

# Watch progress updates
curl http://localhost:8080/api/stream/process?total=50

# Infinite stream (Ctrl+C to stop)
curl http://localhost:8080/api/stream/infinite
```

---

## Part 6: Testing Real-Time Endpoints (15 min)

### Step 6.1: Test SSE Endpoints

```java
package com.example.realtime;

import com.example.realtime.dto.DashboardMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SSEEndpointsTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void metricsStream_emitsEvents() {
        var events = webTestClient.get()
            .uri("/api/dashboard/metrics/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(DashboardMetrics.class)
            .getResponseBody()
            .take(3);

        StepVerifier.create(events)
            .expectNextCount(3)
            .verifyComplete();
    }

    @Test
    void streamProcess_completesSuccessfully() {
        var events = webTestClient.get()
            .uri("/api/stream/process?total=10")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .take(Duration.ofSeconds(5));

        StepVerifier.create(events)
            .expectNextCount(10)
            .verifyComplete();
    }
}
```

### Step 6.2: Test WebSocket Endpoints

```java
package com.example.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketEndpointsTest {

    @LocalServerPort
    private int port;

    private final WebSocketClient client = new ReactorNettyWebSocketClient();

    @Test
    void chatWebSocket_sendsAndReceivesMessages() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/chat?room=test&username=TestUser");

        List<String> received = new CopyOnWriteArrayList<>();

        Mono<Void> session = client.execute(uri, wsSession ->
            wsSession.send(
                Mono.delay(Duration.ofMillis(500))
                    .then(Mono.just(wsSession.textMessage("{\"type\":\"chat\",\"content\":\"Hello!\"}")))
            )
            .thenMany(wsSession.receive()
                .take(2) // Join message + our message
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(received::add))
            .then()
        );

        StepVerifier.create(session)
            .verifyComplete();

        assertThat(received).hasSize(2);
        assertThat(received.get(0)).contains("JOIN");
        assertThat(received.get(1)).contains("Hello!");
    }

    @Test
    void chatWebSocket_multipleUsers_receivesBroadcasts() {
        String room = "broadcast-test-" + System.currentTimeMillis();
        URI uri1 = URI.create("ws://localhost:" + port + "/ws/chat?room=" + room + "&username=User1");
        URI uri2 = URI.create("ws://localhost:" + port + "/ws/chat?room=" + room + "&username=User2");

        List<String> user1Messages = new CopyOnWriteArrayList<>();
        List<String> user2Messages = new CopyOnWriteArrayList<>();

        // User 1 connects and listens
        Mono<Void> user1 = client.execute(uri1, session ->
            session.receive()
                .take(Duration.ofSeconds(3))
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(user1Messages::add)
                .then()
        );

        // User 2 connects, sends a message
        Mono<Void> user2 = client.execute(uri2, session ->
            Mono.delay(Duration.ofMillis(500))
                .then(session.send(Mono.just(session.textMessage("{\"type\":\"chat\",\"content\":\"Hi from User2!\"}"))))
                .thenMany(session.receive()
                    .take(Duration.ofSeconds(2))
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(user2Messages::add))
                .then()
        );

        Mono.zip(user1, user2).block(Duration.ofSeconds(10));

        // User 1 should receive User 2's message
        assertThat(user1Messages).anyMatch(msg -> msg.contains("Hi from User2!"));
    }
}
```

### Step 6.3: Run Tests

```bash
mvn test
```

---

## Part 7: Reflection (5 min)

### Questions to Consider

1. **When would you choose SSE over WebSocket?**
   - SSE: Server-to-client only, auto-reconnect, works through HTTP proxies
   - WebSocket: Bidirectional, lower latency, binary data support

2. **How do Sinks work with multiple subscribers?**
   - `multicast()`: All subscribers receive same elements
   - `unicast()`: Only one subscriber allowed
   - Backpressure buffer prevents slow subscribers from blocking

3. **What happens when a client disconnects unexpectedly?**
   - SSE: Browser auto-reconnects, Last-Event-ID header for resumption
   - WebSocket: Need manual reconnection logic

4. **How would you scale WebSocket connections across servers?**
   - Use Redis pub/sub or message broker
   - Sticky sessions for state
   - Or externalize state completely

5. **What are the security considerations for real-time endpoints?**
   - Authentication in WebSocket handshake
   - Rate limiting on message sending
   - Input validation for all messages

### Key Takeaways

1. **SSE is simpler** for server-to-client push - use it when you don't need bidirectional communication

2. **WebSockets enable real-time interaction** but require more infrastructure (connection management, heartbeats)

3. **Sinks bridge imperative and reactive** - use them to push events from any code into reactive streams

4. **Error handling is critical** in long-lived connections - plan for disconnects and failures

5. **Testing real-time is tricky** but essential - use `StepVerifier` and `WebSocketClient`

### Next Steps

- Add authentication to WebSocket connections
- Implement message persistence (chat history)
- Add typing indicators to chat
- Implement presence detection (who's online)
- Add rate limiting for messages

---

## Summary

In this lab, you:

1. Built a live dashboard with Server-Sent Events
2. Created a multi-room chat application with WebSockets
3. Implemented a real-time notification system
4. Streamed large data sets with progress tracking
5. Tested real-time endpoints

Real-time communication completes your WebFlux toolkit - you can now build fully interactive reactive applications!
