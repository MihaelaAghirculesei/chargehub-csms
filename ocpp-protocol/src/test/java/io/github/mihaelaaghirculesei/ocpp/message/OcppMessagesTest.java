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

  @Test
  void readsAnAuthorizeRequest() {
    ObjectNode payload = json("{\"idTag\":\"B4F62CEF\"}");

    assertThat(validator.validate("Authorize", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, AuthorizeRequest.class))
        .isEqualTo(new AuthorizeRequest("B4F62CEF"));
  }

  @Test
  void writesAnAuthorizeResponseWithEveryField() {
    ObjectNode payload =
        OcppMessages.write(
            new AuthorizeResponse(new IdTagInfo(AuthorizationStatus.ACCEPTED, TIME, "FLEET-01")));

    assertThat(payload)
        .isEqualTo(
            json(
                "{\"idTagInfo\":{\"status\":\"Accepted\","
                    + "\"expiryDate\":\"2013-02-01T20:53:32.486Z\",\"parentIdTag\":\"FLEET-01\"}}"));
    assertThat(validator.validate("Authorize", RESPONSE, payload)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(AuthorizationStatus.class)
  void writesEveryAuthorizationStatusAsTheSchemaNamesIt(AuthorizationStatus status) {
    ObjectNode payload =
        OcppMessages.write(new AuthorizeResponse(new IdTagInfo(status, null, null)));

    assertThat(payload.get("idTagInfo").propertyNames()).containsExactly("status");
    assertThat(validator.validate("Authorize", RESPONSE, payload)).isEmpty();
  }

  @Test
  void readsAStartTransactionRequest() {
    ObjectNode payload =
        json(
            "{\"connectorId\":1,\"idTag\":\"B4F62CEF\",\"meterStart\":3000000000,"
                + "\"timestamp\":\"2013-02-01T20:53:32.486Z\"}");

    assertThat(validator.validate("StartTransaction", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, StartTransactionRequest.class))
        .isEqualTo(new StartTransactionRequest(1, "B4F62CEF", 3_000_000_000L, null, TIME));
  }

  @Test
  void writesAValidStartTransactionResponse() {
    ObjectNode payload =
        OcppMessages.write(
            new StartTransactionResponse(
                new IdTagInfo(AuthorizationStatus.CONCURRENT_TX, null, null), 42));

    assertThat(payload)
        .isEqualTo(json("{\"idTagInfo\":{\"status\":\"ConcurrentTx\"},\"transactionId\":42}"));
    assertThat(validator.validate("StartTransaction", RESPONSE, payload)).isEmpty();
  }

  @Test
  void readsAStopTransactionWithOnlyTheRequiredFields() {
    ObjectNode payload =
        json(
            "{\"transactionId\":42,\"meterStop\":3000000000,"
                + "\"timestamp\":\"2013-02-01T20:53:32.486Z\"}");

    assertThat(validator.validate("StopTransaction", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, StopTransactionRequest.class))
        .isEqualTo(new StopTransactionRequest(null, 3_000_000_000L, TIME, 42, null));
  }

  @Test
  void readsAStopTransactionWithTransactionData() {
    ObjectNode payload =
        json(
            "{\"idTag\":\"B4F62CEF\",\"transactionId\":42,\"meterStop\":1500,"
                + "\"timestamp\":\"2013-02-01T20:53:32.486Z\",\"reason\":\"EVDisconnected\","
                + "\"transactionData\":[{\"timestamp\":\"2013-02-01T20:53:32.486Z\","
                + "\"sampledValue\":[{\"value\":\"1500\"}]}]}");

    assertThat(validator.validate("StopTransaction", REQUEST, payload)).isEmpty();
    assertThat(OcppMessages.read(payload, StopTransactionRequest.class))
        .isEqualTo(new StopTransactionRequest("B4F62CEF", 1500, TIME, 42, Reason.EV_DISCONNECTED));
  }

  @ParameterizedTest
  @EnumSource(Reason.class)
  void writesEveryReasonAsTheSchemaNamesIt(Reason reason) {
    ObjectNode payload = OcppMessages.write(new StopTransactionRequest(null, 0, TIME, 1, reason));

    assertThat(validator.validate("StopTransaction", REQUEST, payload)).isEmpty();
  }

  @Test
  void writesAStopTransactionResponseWithAndWithoutTagInfo() {
    ObjectNode withTag =
        OcppMessages.write(
            new StopTransactionResponse(new IdTagInfo(AuthorizationStatus.ACCEPTED, null, null)));
    ObjectNode withoutTag = OcppMessages.write(new StopTransactionResponse(null));

    assertThat(withTag).isEqualTo(json("{\"idTagInfo\":{\"status\":\"Accepted\"}}"));
    assertThat(withoutTag).isEqualTo(json("{}"));
    assertThat(validator.validate("StopTransaction", RESPONSE, withTag)).isEmpty();
    assertThat(validator.validate("StopTransaction", RESPONSE, withoutTag)).isEmpty();
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
