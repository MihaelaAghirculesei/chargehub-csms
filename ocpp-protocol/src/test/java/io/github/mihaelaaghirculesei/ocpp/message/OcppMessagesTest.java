package io.github.mihaelaaghirculesei.ocpp.message;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.REQUEST;
import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Every message written here must pass its official schema. */
class OcppMessagesTest {

  private static final Instant TIME = Instant.parse("2013-02-01T20:53:32.486Z");

  private final PayloadValidator validator = PayloadValidator.ocpp16();

  @Test
  void writesTheBootNotificationResponseOfTheSpecification() {
    ObjectNode payload =
        OcppMessages.write(new BootNotificationResponse(RegistrationStatus.ACCEPTED, TIME, 300));

    assertThat(payload)
        .isEqualTo(
            json(
                "{\"status\":\"Accepted\",\"currentTime\":\"2013-02-01T20:53:32.486Z\","
                    + "\"interval\":300}"));
    assertThat(validator.validate("BootNotification", RESPONSE, payload)).isEmpty();
  }

  @Test
  void writesAValidHeartbeatResponse() {
    ObjectNode payload = OcppMessages.write(new HeartbeatResponse(TIME));

    assertThat(payload.get("currentTime").stringValue()).isEqualTo("2013-02-01T20:53:32.486Z");
    assertThat(validator.validate("Heartbeat", RESPONSE, payload)).isEmpty();
  }

  @Test
  void writesTimesWithMillisecondsAtMost() {
    Instant precise = Instant.parse("2026-10-17T10:15:30.123456789Z");

    assertThat(OcppMessages.write(new HeartbeatResponse(precise)).get("currentTime").stringValue())
        .isEqualTo("2026-10-17T10:15:30.123Z");
  }

  @Test
  void readsARequestWithOnlyTheRequiredFields() {
    BootNotificationRequest request =
        OcppMessages.read(
            json(
                "{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}"),
            BootNotificationRequest.class);

    assertThat(request.chargePointVendor()).isEqualTo("VendorX");
    assertThat(request.chargePointModel()).isEqualTo("SingleSocketCharger");
    assertThat(request.firmwareVersion()).isNull();
  }

  /** A missing optional field must be left out: written as null it would break the schema. */
  @Test
  void leavesOutMissingOptionalFields() {
    BootNotificationRequest request =
        new BootNotificationRequest(
            "VendorX", "SingleSocketCharger", null, null, "1.0.2", null, null, null, null);

    ObjectNode payload = OcppMessages.write(request);

    assertThat(payload.propertyNames())
        .containsExactlyInAnyOrder("chargePointVendor", "chargePointModel", "firmwareVersion");
    assertThat(validator.validate("BootNotification", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, BootNotificationRequest.class)).isEqualTo(request);
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
