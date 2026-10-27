package io.github.mihaelaaghirculesei.ocpp.message;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.REQUEST;
import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
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

  @Test
  void readsAStatusNotificationWithEveryField() {
    ObjectNode payload =
        json(
            "{\"connectorId\":1,\"errorCode\":\"GroundFailure\",\"info\":\"Leak detected\","
                + "\"status\":\"Faulted\",\"timestamp\":\"2013-02-01T20:53:32.486Z\","
                + "\"vendorId\":\"VendorX\",\"vendorErrorCode\":\"E42\"}");

    assertThat(validator.validate("StatusNotification", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, StatusNotificationRequest.class))
        .isEqualTo(
            new StatusNotificationRequest(
                1,
                ChargePointErrorCode.GROUND_FAILURE,
                "Leak detected",
                ChargePointStatus.FAULTED,
                TIME,
                "VendorX",
                "E42"));
  }

  @Test
  void readsAStatusNotificationWithoutTimestamp() {
    StatusNotificationRequest request =
        OcppMessages.read(
            json("{\"connectorId\":0,\"errorCode\":\"NoError\",\"status\":\"Available\"}"),
            StatusNotificationRequest.class);

    assertThat(request.timestamp()).isNull();
  }

  /** Each constant must carry the exact name of the schema, or real charge points are refused. */
  @ParameterizedTest
  @EnumSource(ChargePointStatus.class)
  void writesEveryStatusAsTheSchemaNamesIt(ChargePointStatus status) {
    assertThat(
            validator.validate(
                "StatusNotification",
                REQUEST,
                statusNotification(status, ChargePointErrorCode.NO_ERROR)))
        .isEmpty();
  }

  @ParameterizedTest
  @EnumSource(ChargePointErrorCode.class)
  void writesEveryErrorCodeAsTheSchemaNamesIt(ChargePointErrorCode errorCode) {
    assertThat(
            validator.validate(
                "StatusNotification",
                REQUEST,
                statusNotification(ChargePointStatus.FAULTED, errorCode)))
        .isEmpty();
  }

  @Test
  void anEmptyPayloadIsAValidStatusNotificationResponse() {
    assertThat(
            validator.validate(
                "StatusNotification", RESPONSE, JsonNodeFactory.instance.objectNode()))
        .isEmpty();
  }

  private static ObjectNode statusNotification(
      ChargePointStatus status, ChargePointErrorCode errorCode) {
    return OcppMessages.write(
        new StatusNotificationRequest(1, errorCode, null, status, TIME, null, null));
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
