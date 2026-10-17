package io.github.mihaelaaghirculesei.csms.ocpp;

import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import tools.jackson.databind.node.ObjectNode;

/**
 * Answers one OCPP 1.6 action that charge points send. The module that owns the action implements
 * it as a Spring bean; the ocpp module only routes the call to it.
 */
public interface ActionHandler {

  Action action();

  /**
   * Handles a request that already passed the request schema of the action.
   *
   * @return the CALLRESULT payload, which is checked against the response schema before sending
   */
  ObjectNode handle(String chargePointId, ObjectNode request);
}
