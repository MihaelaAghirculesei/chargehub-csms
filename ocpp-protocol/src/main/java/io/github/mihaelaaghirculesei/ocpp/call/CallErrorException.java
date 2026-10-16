package io.github.mihaelaaghirculesei.ocpp.call;

import io.github.mihaelaaghirculesei.ocpp.frame.CallError;

/** The charge point answered the call with a CALLERROR. */
public final class CallErrorException extends RuntimeException {

  private final transient CallError error;

  CallErrorException(CallError error) {
    super(error.errorCode().wireName() + ": " + error.errorDescription());
    this.error = error;
  }

  public CallError error() {
    return error;
  }
}
