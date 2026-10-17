package io.github.mihaelaaghirculesei.csms.ocpp;

/** The charge point answered with a CALLRESULT that breaks the response schema of the action. */
public final class InvalidResponseException extends RuntimeException {

  public InvalidResponseException(String action, String description) {
    super("Invalid " + action + " response: " + description);
  }
}
