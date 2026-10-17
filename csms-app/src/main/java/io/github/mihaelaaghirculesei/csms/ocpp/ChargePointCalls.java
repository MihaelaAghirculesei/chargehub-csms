package io.github.mihaelaaghirculesei.csms.ocpp;

import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.node.ObjectNode;

/** Sends OCPP calls from the central system to connected charge points. */
public interface ChargePointCalls {

  /**
   * Sends a call and returns its outcome. The future fails with the exceptions of {@link
   * io.github.mihaelaaghirculesei.ocpp.call.PendingCalls#begin}, or with {@link
   * InvalidResponseException} when the answer breaks the response schema.
   *
   * @throws ChargePointNotConnectedException if the charge point has no open connection
   * @throws io.github.mihaelaaghirculesei.ocpp.call.CallInProgressException if the charge point is
   *     still answering a previous call (docs/adr/0009)
   * @throws IllegalArgumentException if the payload breaks the request schema of the action; it is
   *     a bug in the caller, so nothing is sent
   */
  CompletableFuture<CallResult> call(String chargePointId, Action action, ObjectNode payload);
}
