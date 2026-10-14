package io.github.mihaelaaghirculesei.ocpp.frame;

import tools.jackson.databind.node.ObjectNode;

/**
 * {@code [4, messageId, errorCode, errorDescription, errorDetails]}: the answer to a CALL that
 * could not be processed. The specification allows an empty description and empty details.
 */
public record CallError(
    String messageId, ErrorCode errorCode, String errorDescription, ObjectNode errorDetails)
    implements OcppFrame {

  public CallError {
    MessageIds.requireValid(messageId);
  }
}
