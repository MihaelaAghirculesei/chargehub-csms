package io.github.mihaelaaghirculesei.csms.ocpp;

/** The charge point has no open OCPP connection, so nothing can be sent to it. */
public final class ChargePointNotConnectedException extends RuntimeException {

  public ChargePointNotConnectedException(String chargePointId) {
    super("Charge point " + chargePointId + " is not connected");
  }
}
