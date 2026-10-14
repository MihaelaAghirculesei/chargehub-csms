package io.github.mihaelaaghirculesei.ocpp.frame;

import tools.jackson.databind.node.ObjectNode;

/** {@code [3, messageId, payload]}: the successful answer to a CALL. */
public record CallResult(String messageId, ObjectNode payload) implements OcppFrame {

  public CallResult {
    MessageIds.requireValid(messageId);
  }
}
