package io.github.mihaelaaghirculesei.ocpp.payload;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.REQUEST;
import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class OfficialSchemasTest {

  private static final String DIRECTORY = "/ocpp16/schemas/";

  private final PayloadValidator validator = PayloadValidator.ocpp16();

  /** The license (CC BY-ND 4.0) forbids changes, and so does docs/adr/0008. */
  @Test
  void schemasAreExactlyAsPublished() {
    List<String> lines = new String(read("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList();

    assertThat(lines).hasSize(2 * Action.values().length);
    for (String line : lines) {
      String expected = line.substring(0, 64);
      String file = line.substring(66);
      assertThat(sha256(read(file))).as(file).isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  void everyActionHasARequestAndAResponseSchema(Action action) {
    ObjectNode empty = JsonNodeFactory.instance.objectNode();

    assertThatNoException().isThrownBy(() -> validator.validate(action.wireName(), REQUEST, empty));
    assertThatNoException()
        .isThrownBy(() -> validator.validate(action.wireName(), RESPONSE, empty));
  }

  @Test
  void acceptsTheBootNotificationOfTheSpecification() {
    ObjectNode request =
        json("{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}");
    ObjectNode response =
        json(
            "{\"status\":\"Accepted\",\"currentTime\":\"2013-02-01T20:53:32.486Z\","
                + "\"interval\":300}");

    assertThat(validator.validate("BootNotification", REQUEST, request)).isEmpty();
    assertThat(validator.validate("BootNotification", RESPONSE, response)).isEmpty();
  }

  /** Fixed by the OCPP-J 1.6 errata: the published file first said "number". */
  @Test
  void usesTheCorrectedIntervalTypeOfTheErrata() {
    ObjectNode response =
        json(
            "{\"status\":\"Accepted\",\"currentTime\":\"2013-02-01T20:53:32.486Z\","
                + "\"interval\":300.5}");

    assertThat(validator.validate("BootNotification", RESPONSE, response))
        .get()
        .extracting(PayloadViolation::errorCode)
        .isEqualTo(ErrorCode.TYPE_CONSTRAINT_VIOLATION);
  }

  @Test
  void acceptsAnEmptyHeartbeat() {
    assertThat(validator.validate("Heartbeat", REQUEST, JsonNodeFactory.instance.objectNode()))
        .isEmpty();
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }

  private static byte[] read(String file) {
    try (InputStream in = OfficialSchemasTest.class.getResourceAsStream(DIRECTORY + file)) {
      assertThat(in).as(file).isNotNull();
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
