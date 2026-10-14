package io.github.mihaelaaghirculesei.ocpp.frame;

import tools.jackson.databind.node.ObjectNode;

/** {@code [2, messageId, action, payload]}: a request that expects a CALLRESULT or CALLERROR. */
public record Call(String messageId, String action, ObjectNode payload) implements OcppFrame {

  public Call {
    MessageIds.requireValid(messageId);
    if (action.isEmpty()) {
      throw new IllegalArgumentException("Action must not be empty");
    }
  }
}
