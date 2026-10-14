package io.github.mihaelaaghirculesei.ocpp.frame;

import java.util.Optional;

/** The CALLERROR codes defined by OCPP-J 1.6. */
public enum ErrorCode {
  NOT_IMPLEMENTED("NotImplemented"),
  NOT_SUPPORTED("NotSupported"),
  INTERNAL_ERROR("InternalError"),
  PROTOCOL_ERROR("ProtocolError"),
  SECURITY_ERROR("SecurityError"),
  // OCPP 2.0 renamed it FormatViolation; a 1.6 charge point only knows this name.
  FORMATION_VIOLATION("FormationViolation"),
  PROPERTY_CONSTRAINT_VIOLATION("PropertyConstraintViolation"),
  // Misspelled in the 1.6 specification. Charge points send it this way, so it stays.
  OCCURRENCE_CONSTRAINT_VIOLATION("OccurenceConstraintViolation"),
  TYPE_CONSTRAINT_VIOLATION("TypeConstraintViolation"),
  GENERIC_ERROR("GenericError");

  private final String wireName;

  ErrorCode(String wireName) {
    this.wireName = wireName;
  }

  /** The name used inside the frame. */
  public String wireName() {
    return wireName;
  }

  public static Optional<ErrorCode> fromWireName(String wireName) {
    for (ErrorCode code : values()) {
      if (code.wireName.equals(wireName)) {
        return Optional.of(code);
      }
    }
    return Optional.empty();
  }
}
