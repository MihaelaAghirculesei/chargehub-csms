package io.github.mihaelaaghirculesei.ocpp.payload;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.REQUEST;
import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Runs on small test schemas; the official ones are covered by their own test. */
class PayloadValidatorTest {

  private final PayloadValidator validator = new PayloadValidator("/schemas/");

  @Test
  void acceptsValidRequest() {
    assertThat(validator.validate("BootNotification", REQUEST, bootNotification())).isEmpty();
  }

  @Test
  void reportsMissingFieldAsOccurrenceConstraintViolation() {
    ObjectNode payload = bootNotification();
    payload.remove("chargePointModel");

    assertThat(errorCode("BootNotification", REQUEST, payload))
        .isEqualTo(ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION);
  }

  @Test
  void reportsWrongTypeAsTypeConstraintViolation() {
    ObjectNode payload = bootNotification().put("chargePointModel", 42);

    assertThat(errorCode("BootNotification", REQUEST, payload))
        .isEqualTo(ErrorCode.TYPE_CONSTRAINT_VIOLATION);
  }

  @Test
  void reportsTooLongValueAsPropertyConstraintViolation() {
    ObjectNode payload = bootNotification().put("chargePointVendor", "a".repeat(21));

    assertThat(errorCode("BootNotification", REQUEST, payload))
        .isEqualTo(ErrorCode.PROPERTY_CONSTRAINT_VIOLATION);
  }

  @Test
  void reportsUnknownFieldAsPropertyConstraintViolation() {
    ObjectNode payload = bootNotification().put("firmware", "1.0");

    assertThat(errorCode("BootNotification", REQUEST, payload))
        .isEqualTo(ErrorCode.PROPERTY_CONSTRAINT_VIOLATION);
  }

  @Test
  void describesEveryError() {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();

    assertThat(validator.validate("BootNotification", REQUEST, payload))
        .get()
        .extracting(PayloadViolation::description)
        .asString()
        .contains("chargePointVendor", "chargePointModel");
  }

  @Test
  void usesTheResponseSchemaForResponses() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("status", "Accepted").put("interval", 300);

    assertThat(validator.validate("BootNotification", RESPONSE, response)).isEmpty();
    assertThat(validator.validate("BootNotification", REQUEST, response)).isPresent();
  }

  /** The action comes from the network, so it must never be turned into a file path. */
  @ParameterizedTest
  @ValueSource(strings = {"Unknown", "bootNotification", "../schemas/BootNotification", ""})
  void answersUnknownActionWithNotImplemented(String action) {
    assertThat(errorCode(action, REQUEST, bootNotification())).isEqualTo(ErrorCode.NOT_IMPLEMENTED);
  }

  @Test
  void failsLoudlyWhenTheSchemaOfAKnownActionIsMissing() {
    ObjectNode empty = JsonNodeFactory.instance.objectNode();

    assertThatIllegalStateException()
        .isThrownBy(() -> validator.validate("Heartbeat", REQUEST, empty))
        .withMessageContaining("/schemas/Heartbeat.json");
  }

  private ErrorCode errorCode(String action, PayloadType type, ObjectNode payload) {
    return validator.validate(action, type, payload).orElseThrow().errorCode();
  }

  private static ObjectNode bootNotification() {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("chargePointVendor", "ChargeHub").put("chargePointModel", "Simulator X");
    return payload;
  }
}
