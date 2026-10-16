package io.github.mihaelaaghirculesei.ocpp.call;

/** The connection of the charge point closed before it answered the call. */
public final class ConnectionClosedException extends RuntimeException {

  ConnectionClosedException(String chargePointId) {
    super("Connection of charge point " + chargePointId + " closed before it answered");
  }
}
