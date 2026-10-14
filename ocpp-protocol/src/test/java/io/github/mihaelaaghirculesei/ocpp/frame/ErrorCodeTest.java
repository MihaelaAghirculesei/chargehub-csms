package io.github.mihaelaaghirculesei.ocpp.frame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void usesTheWireNamesOfTheSpecification() {
    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::wireName))
        .containsExactly(
            "NotImplemented",
            "NotSupported",
            "InternalError",
            "ProtocolError",
            "SecurityError",
            "FormationViolation",
            "PropertyConstraintViolation",
            "OccurenceConstraintViolation",
            "TypeConstraintViolation",
            "GenericError");
  }

  @Test
  void findsCodeByWireName() {
    assertThat(ErrorCode.fromWireName("OccurenceConstraintViolation"))
        .contains(ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION);
  }

  @Test
  void doesNotFindNamesFromOtherVersions() {
    assertThat(ErrorCode.fromWireName("FormatViolation")).isEmpty();
    assertThat(ErrorCode.fromWireName("OccurrenceConstraintViolation")).isEmpty();
  }
}
