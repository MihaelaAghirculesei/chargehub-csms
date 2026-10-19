package io.github.mihaelaaghirculesei.simulator.chargepoint;

import org.jspecify.annotations.Nullable;

/** The session with the central system cannot go on: a call failed or the connection closed. */
public final class SessionFailedException extends RuntimeException {

  SessionFailedException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
