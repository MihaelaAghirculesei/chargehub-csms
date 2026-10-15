# 0007. Use plain WebSocket for OCPP, not STOMP

- Status: Accepted
- Date: 2026-10-15

## Context

Charge points connect to the central system over WebSocket. Spring offers two ways to build a
WebSocket server: a plain `WebSocketHandler` that receives text frames, or STOMP, a messaging
protocol on top of WebSocket with destinations, subscriptions and a message broker.

Most Spring WebSocket examples use STOMP, so it looks like the default choice. But the client
here is not a browser we write: it is a charge point that speaks OCPP-J, and OCPP-J already
defines what goes inside each frame (`[2, id, action, payload]` and the rest).

OCPP-J also has rules for the handshake: the subprotocol must be `ocpp1.6`, and a connection
that does not agree on it must be closed right away.

## Considered options

1. **Plain WebSocket handler.** We receive and send the raw text of each frame and decode it
   with our own OCPP-J codec. We control the handshake and the closing of the connection.
   Chosen.
2. **STOMP.** STOMP adds its own frames (`CONNECT`, `SEND`, `SUBSCRIBE`) around every message.
   A charge point does not send them, so it could not even connect. The broker features would
   go unused.
3. **Spring WebFlux reactive WebSocket.** It would mean a second web stack next to Spring MVC,
   which the rest of the application uses. With virtual threads, blocking handlers already scale
   to many connections.

## Decision

- Charge points connect to `/ocpp/{chargePointId}` through a plain Spring MVC
  `WebSocketHandler`.
- The handshake accepts only the `ocpp1.6` subprotocol. A connection that does not negotiate it
  is closed at once with the WebSocket close status `1002` (protocol error), as OCPP-J 1.6
  requires, and the charge point id and offered subprotocols are logged.
- The text of each frame goes through the codec in `ocpp-protocol`.

## Consequences

- Good: what travels on the wire is exactly what the specification describes, with nothing in
  between.
- Good: the handshake rules are in our code and covered by integration tests with a real
  WebSocket client.
- Bad: what a broker would give for free is ours to build: the registry of connected charge
  points and the matching of answers to calls. Both are planned in Phase 1.
- Bad: running more than one instance later needs a way to route a call to the instance that
  holds the connection. Out of scope for a single instance.
