package io.github.mihaelaaghirculesei.ocpp.call;

/** The charge point has not answered its previous call yet, so a new one cannot be sent. */
public final class CallInProgressException extends RuntimeException {

  CallInProgressException(String chargePointId) {
    super("Charge point " + chargePointId + " is still answering a previous call");
  }
}
