package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointCalls;
import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointNotConnectedException;
import io.github.mihaelaaghirculesei.ocpp.call.PendingCalls;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadType;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.node.ObjectNode;

@Component
class WebSocketChargePointCalls implements ChargePointCalls {

  private static final Logger log = LoggerFactory.getLogger(WebSocketChargePointCalls.class);

  private final ChargePointConnections connections;
  private final PendingCalls pendingCalls;
  private final FrameCodec codec;
  private final PayloadValidator validator;

  WebSocketChargePointCalls(
      ChargePointConnections connections,
      PendingCalls pendingCalls,
      FrameCodec codec,
      PayloadValidator validator) {
    this.connections = connections;
    this.pendingCalls = pendingCalls;
    this.codec = codec;
    this.validator = validator;
  }

  @Override
  public CompletableFuture<CallResult> call(
      String chargePointId, Action action, ObjectNode payload) {
    // What we send is checked against the same schemas as what we receive (docs/adr/0008).
    validator
        .validate(action.wireName(), PayloadType.REQUEST, payload)
        .ifPresent(
            violation -> {
              throw new IllegalArgumentException(
                  "Invalid " + action.wireName() + " request: " + violation.description());
            });
    WebSocketSession session =
        connections
            .find(chargePointId)
            .orElseThrow(() -> new ChargePointNotConnectedException(chargePointId));
    // A UUID is 36 characters, the longest message id OCPP-J allows.
    Call call = new Call(UUID.randomUUID().toString(), action.wireName(), payload);
    CompletableFuture<CallResult> outcome = pendingCalls.begin(chargePointId, call);
    try {
      session.sendMessage(new TextMessage(codec.encode(call)));
    } catch (IOException | RuntimeException e) {
      // The connection is unusable, so no answer can come: free the charge point now, not at the
      // timeout. Sending past the buffer or time limits ends up here too.
      log.warn("Sending {} failed", action.wireName(), e);
      pendingCalls.connectionClosed(chargePointId);
    }
    return outcome;
  }
}
