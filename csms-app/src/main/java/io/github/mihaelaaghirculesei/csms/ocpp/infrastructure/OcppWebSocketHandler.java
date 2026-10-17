package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import io.github.mihaelaaghirculesei.ocpp.call.PendingCalls;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameDecodingException;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.node.JsonNodeFactory;

class OcppWebSocketHandler extends TextWebSocketHandler {

  static final String SUBPROTOCOL = "ocpp1.6";

  // The structured log field, so every line about a charge point can be found with one query.
  private static final String CHARGE_POINT_ID = "chargePointId";

  private static final Logger log = LoggerFactory.getLogger(OcppWebSocketHandler.class);

  private final ChargePointConnections connections;
  private final PendingCalls pendingCalls;
  private final FrameCodec codec;
  private final CallDispatcher dispatcher;

  OcppWebSocketHandler(
      ChargePointConnections connections,
      PendingCalls pendingCalls,
      FrameCodec codec,
      CallDispatcher dispatcher) {
    this.connections = connections;
    this.pendingCalls = pendingCalls;
    this.codec = codec;
    this.dispatcher = dispatcher;
  }

  /**
   * OCPP-J 1.6 asks the server to finish the handshake without a subprotocol and then close at once
   * when it supports none of the offered ones. Spring does the first part, not the second.
   */
  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    if (!SUBPROTOCOL.equals(session.getAcceptedProtocol())) {
      String chargePointId = loggable(chargePointId(session));
      List<String> offered =
          session
              .getHandshakeHeaders()
              .getOrEmpty(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL)
              .stream()
              .map(OcppWebSocketHandler::loggable)
              .toList();
      log.atWarn()
          .addKeyValue(CHARGE_POINT_ID, chargePointId)
          .addKeyValue("offeredSubprotocols", offered)
          .log(
              "Closing connection of {}: it offered {} instead of {}",
              chargePointId,
              offered,
              SUBPROTOCOL);
      session.close(CloseStatus.PROTOCOL_ERROR);
      return;
    }
    String chargePointId = chargePointId(session);
    if (connections.register(chargePointId, session)) {
      // The answer to a call sent on the old connection can never arrive on this one.
      pendingCalls.connectionClosed(chargePointId);
      log.atInfo()
          .addKeyValue(CHARGE_POINT_ID, loggable(chargePointId))
          .log("Charge point {} reconnected, previous connection closed", loggable(chargePointId));
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String chargePointId = chargePointId(session);
    OcppFrame frame;
    try {
      frame = codec.decode(message.getPayload());
    } catch (FrameDecodingException e) {
      log.atWarn()
          .addKeyValue(CHARGE_POINT_ID, loggable(chargePointId))
          .log("Malformed frame from {}: {}", loggable(chargePointId), e.errorCode());
      if (e.answerable()) {
        reply(session, malformedCallError(e));
      }
      return;
    }
    boolean matched =
        switch (frame) {
          case CallResult result -> pendingCalls.answer(chargePointId, result);
          case CallError error -> pendingCalls.answer(chargePointId, error);
          case Call call -> {
            reply(session, dispatcher.dispatch(chargePointId, call));
            yield true;
          }
        };
    if (!matched) {
      log.atWarn()
          .addKeyValue(CHARGE_POINT_ID, loggable(chargePointId))
          .log(
              "Dropping an answer from {} that matches no outstanding call: {}",
              loggable(chargePointId),
              loggable(frame.messageId()));
    }
  }

  /**
   * Answers through the registered, send-safe session, and only while it is the current one: a
   * replaced connection is about to close and nothing would read the answer.
   */
  private void reply(WebSocketSession session, OcppFrame answer) {
    String chargePointId = chargePointId(session);
    Optional<WebSocketSession> current =
        connections.find(chargePointId).filter(c -> c.getId().equals(session.getId()));
    if (current.isEmpty()) {
      return;
    }
    try {
      current.get().sendMessage(new TextMessage(codec.encode(answer)));
    } catch (IOException | RuntimeException e) {
      log.atWarn()
          .addKeyValue(CHARGE_POINT_ID, loggable(chargePointId))
          .setCause(e)
          .log("Answering {} failed", loggable(chargePointId));
    }
  }

  private static CallError malformedCallError(FrameDecodingException e) {
    return new CallError(
        e.messageId().orElseThrow(),
        e.errorCode(),
        String.valueOf(e.getMessage()),
        JsonNodeFactory.instance.objectNode());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String chargePointId = chargePointId(session);
    // A replaced connection closing late must not fail a call sent on the new one.
    if (connections.unregister(chargePointId, session)) {
      pendingCalls.connectionClosed(chargePointId);
    }
  }

  // The values come from the client, and the path is already percent-decoded: a line break or a
  // terminal escape would let it forge lines in a plain-text log. Charge point ids and subprotocol
  // names need nothing outside this allow list.
  private static String loggable(String value) {
    return value.replaceAll("[^A-Za-z0-9._:-]", "_");
  }

  // Always present: ChargePointIdInterceptor refuses the handshake otherwise.
  static String chargePointId(WebSocketSession session) {
    return (String)
        Objects.requireNonNull(session.getAttributes().get(ChargePointIdInterceptor.ATTRIBUTE));
  }
}
