# First Principles: Deriving Real-Time Communication

## Forget That WebSockets and SSE Exist

Imagine you've built a reactive web application. Users can make requests and get responses. But now a user asks: "I want to see updates in real time. When something changes on the server, I want to know immediately—without refreshing the page."

How would you solve this? Let's derive real-time communication from first principles.

## Step 1: The Fundamental Problem

HTTP is designed for request-response:

```
Client: "Give me the current stock price"
Server: "It's $150.00"
```

But what if the price changes 10 times per second? The client would need to:

```
Client: "Price?"  Server: "$150.00"
Client: "Price?"  Server: "$150.05"
Client: "Price?"  Server: "$150.03"
Client: "Price?"  Server: "$150.10"
...
```

This is **polling**—repeatedly asking "has anything changed?"

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    POLLING: INEFFICIENT                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── Any updates? ────────────────▶│                              │
│     │◀─── No ──────────────────────────│                              │
│     │                                     │                              │
│     │──── Any updates? ────────────────▶│                              │
│     │◀─── No ──────────────────────────│                              │
│     │                                     │                              │
│     │──── Any updates? ────────────────▶│                              │
│     │◀─── Yes! Here's data ────────────│                              │
│     │                                     │                              │
│   Wasteful: Most requests return nothing                                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

Problems with polling:
- **Wasteful**: Most requests return "no changes"
- **Latency**: Updates arrive on the next poll, not immediately
- **Server load**: Constant request processing

## Step 2: What If We Kept the Connection Open?

Instead of closing the connection after each response, what if we kept it open?

```
Client: "Tell me when the price changes"
Server: [keeps connection open]
Server: "Price is now $150.05"
Server: "Price is now $150.03"
Server: "Price is now $150.10"
...
```

This is **long-polling** or **streaming**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LONG-POLLING / STREAMING                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── Give me updates ─────────────▶│                              │
│     │                                     │  [connection stays open]     │
│     │                                     │                              │
│     │◀─── Update 1 ────────────────────│                              │
│     │                                     │                              │
│     │◀─── Update 2 ────────────────────│                              │
│     │                                     │                              │
│     │◀─── Update 3 ────────────────────│                              │
│     │                                     │                              │
│   Efficient: Server pushes when data is ready                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

This is better! The server sends data when it has something to send.

## Step 3: Server-Sent Events (SSE)

The web standardized this pattern as **Server-Sent Events**:

1. Client makes an HTTP request with `Accept: text/event-stream`
2. Server keeps the connection open
3. Server sends events in a simple text format
4. Browser's `EventSource` API handles parsing and reconnection

The wire format is simple:

```
event: priceUpdate
data: {"symbol":"AAPL","price":150.05}

event: priceUpdate
data: {"symbol":"AAPL","price":150.03}

event: priceUpdate
data: {"symbol":"AAPL","price":150.10}
```

In reactive terms, this is a `Flux` that never completes (until the client disconnects):

```java
Flux<PriceUpdate> prices = stockService.getPriceUpdates("AAPL");
// This Flux emits values over time, potentially forever
```

SSE maps perfectly to reactive streams:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SSE = FLUX TO HTTP                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Flux<Event>                         HTTP Response                      │
│                                                                          │
│   ┌───────────────┐                   ┌────────────────────────────┐    │
│   │ Event 1       │ ───────────────▶ │ event: update              │    │
│   │ (onNext)      │                   │ data: {...event1...}       │    │
│   └───────────────┘                   │                            │    │
│                                       │ event: update              │    │
│   ┌───────────────┐                   │ data: {...event2...}       │    │
│   │ Event 2       │ ───────────────▶ │                            │    │
│   │ (onNext)      │                   │ event: update              │    │
│   └───────────────┘                   │ data: {...event3...}       │    │
│                                       │                            │    │
│   ┌───────────────┐                   │                            │    │
│   │ Event 3       │ ───────────────▶ │                            │    │
│   │ (onNext)      │                   └────────────────────────────┘    │
│   └───────────────┘                                                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 4: What About Client-to-Server?

SSE solves server-to-client push. But what if the client needs to send data too?

Example: A chat application

```
Alice: "Hello Bob!"       → needs to reach server
Server: "Alice says..."   → needs to reach Bob's browser
Bob: "Hi Alice!"          → needs to reach server
Server: "Bob says..."     → needs to reach Alice's browser
```

With SSE, the client would need to make separate HTTP requests for sending:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SSE + HTTP POSTS (AWKWARD)                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │──── SSE: Subscribe to chat ──────▶│                              │
│     │◀─── SSE: Message from Bob ────────│                              │
│     │                                     │                              │
│     │──── POST: "Hello Bob!" ──────────▶│  ← Separate request!         │
│     │◀─── 200 OK ──────────────────────│                              │
│     │                                     │                              │
│     │◀─── SSE: Message from Alice ──────│                              │
│     │                                     │                              │
│   Two connections needed                                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

This works, but it's awkward. We need two different communication channels.

## Step 5: True Bidirectional Communication

What if we had a single connection where both sides could send messages?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BIDIRECTIONAL: THE DREAM                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client                                Server                           │
│     │                                     │                              │
│     │═══════════════════════════════════│  Single connection           │
│     │                                     │                              │
│     │──── "Hello Bob!" ────────────────▶│                              │
│     │◀─── "Message from Alice" ─────────│                              │
│     │◀─── "Bob is typing..." ───────────│                              │
│     │──── "How are you?" ──────────────▶│                              │
│     │◀─── "Message from Alice" ─────────│                              │
│     │◀─── "Hi Alice!" ──────────────────│                              │
│     │                                     │                              │
│   Either side can send at any time                                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

This is what **WebSockets** provide.

## Step 6: WebSocket Protocol

WebSocket is a different protocol that starts as HTTP, then upgrades:

```
GET /chat HTTP/1.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==

HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

[Now speaking WebSocket protocol, not HTTP]
```

After the handshake:
- Both sides can send messages at any time
- Messages are framed (not raw TCP)
- Can be text or binary
- Connection stays open until explicitly closed

## Step 7: Reactive Modeling of WebSockets

In reactive terms, a WebSocket is two streams:

```java
// Incoming messages from client
Flux<Message> incoming = session.receive();

// Outgoing messages to client
Mono<Void> sending = session.send(outgoingFlux);
```

The handler processes incoming while simultaneously sending outgoing:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WEBSOCKET AS TWO FLUXES                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│                    WebSocket Connection                                  │
│                                                                          │
│   ┌─────────────┐        ┌─────────────────────┐        ┌─────────────┐│
│   │   Client    │───────▶│    Flux<Message>    │───────▶│   Handler   ││
│   │             │        │    (incoming)       │        │             ││
│   │             │        └─────────────────────┘        │             ││
│   │             │                                        │             ││
│   │             │        ┌─────────────────────┐        │             ││
│   │             │◀───────│    Flux<Message>    │◀───────│             ││
│   │             │        │    (outgoing)       │        │             ││
│   └─────────────┘        └─────────────────────┘        └─────────────┘│
│                                                                          │
│   Two independent streams, running concurrently                         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step 8: Broadcasting to Multiple Clients

For a chat room, we need to:
1. Receive messages from any client
2. Send those messages to all clients

This requires a **broadcast** mechanism:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    BROADCAST PATTERN                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Client A ────▶│                      │───▶ Client A                   │
│                 │                      │                                 │
│   Client B ────▶│  ┌──────────────┐   │───▶ Client B                   │
│                 │  │  Broadcast   │   │                                 │
│   Client C ────▶│  │    Sink      │   │───▶ Client C                   │
│                 │  └──────────────┘   │                                 │
│   Client D ────▶│                      │───▶ Client D                   │
│                                                                          │
│   Messages go in, copies go out to everyone                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

In Reactor, this is a `Sinks.Many` with multicast:

```java
Sinks.Many<Message> broadcast = Sinks.many().multicast().onBackpressureBuffer();

// When any client sends a message
void onMessage(Message msg) {
    broadcast.tryEmitNext(msg);
}

// Each client subscribes to the broadcast
Flux<Message> clientStream = broadcast.asFlux();
```

## Step 9: The Complete Picture

We've derived two technologies:

**SSE (Server-Sent Events)**
- Server pushes to client
- Uses standard HTTP
- Simple text protocol
- Automatic reconnection
- Browser-native support

**WebSocket**
- Bidirectional communication
- Separate protocol (upgraded from HTTP)
- Binary or text frames
- Manual reconnection
- Full-duplex

Both map naturally to reactive streams:

| Pattern | SSE | WebSocket |
|---------|-----|-----------|
| Server → Client | `Flux<Event>` as response body | `session.send(Flux)` |
| Client → Server | Separate HTTP requests | `session.receive()` returns `Flux` |
| Broadcast | N/A (one client per stream) | `Sinks.Many.multicast()` |

## Step 10: When to Use Each

**Use SSE when:**
- Server pushes data to client
- Client doesn't send data (or rarely, via separate requests)
- Need automatic reconnection
- Working through HTTP proxies
- Simple text/JSON data

**Use WebSocket when:**
- Both sides send frequently
- Need low-latency bidirectional
- Binary data required
- Building chat, games, collaboration

**Decision flowchart:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CHOOSING SSE vs WEBSOCKET                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Does the client need to send data to the server frequently?           │
│                         │                                                │
│              ┌──────────┴──────────┐                                    │
│              │                     │                                    │
│             Yes                   No                                    │
│              │                     │                                    │
│              ▼                     ▼                                    │
│        ┌──────────┐         ┌──────────┐                               │
│        │ WebSocket│         │   SSE    │                               │
│        └──────────┘         └──────────┘                               │
│                                                                          │
│   Additional considerations:                                            │
│   • Binary data needed? → WebSocket                                     │
│   • Through tricky proxies? → SSE                                       │
│   • Need auto-reconnect? → SSE has it built-in                         │
│   • Latency critical? → WebSocket                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## The Key Insight

Real-time communication isn't magic—it's just keeping connections open longer and having a protocol for sending data over time.

- **Polling** asks repeatedly: "Anything new?"
- **SSE** says: "Keep the connection open, I'll tell you when there's something"
- **WebSocket** says: "Let's keep a two-way channel open"

All three can be modeled as reactive streams:
- Polling: `Flux.interval().flatMap(pollServer)`
- SSE: Server returns `Flux<Event>` directly
- WebSocket: Two `Flux` instances (incoming and outgoing)

The reactive model makes real-time communication straightforward: a stream that emits values over time is exactly what real-time applications need.

## Summary

From first principles, we derived that:

1. **Polling is wasteful** - constantly asking "has anything changed?"
2. **Keeping connections open** allows server-initiated push
3. **SSE standardizes** server-to-client streaming over HTTP
4. **WebSocket enables** true bidirectional communication
5. **Reactive streams** model both naturally as `Flux`
6. **Broadcasting** requires a shared sink that multicasts to subscribers

The choice between SSE and WebSocket depends on whether clients need to send data frequently. Both integrate seamlessly with the reactive programming model we've built throughout this book.
