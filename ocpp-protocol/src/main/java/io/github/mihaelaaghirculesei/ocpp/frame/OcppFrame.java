package io.github.mihaelaaghirculesei.ocpp.frame;

/**
 * An OCPP-J message as it travels on the WebSocket (OCPP-J 1.6, section 4). The payload is still
 * plain JSON: turning it into an action is the job of the layer above.
 */
public sealed interface OcppFrame permits Call, CallResult, CallError {

  /** Pairs a CALLRESULT or CALLERROR with the CALL it answers. */
  String messageId();
}
