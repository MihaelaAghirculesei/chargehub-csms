package io.github.mihaelaaghirculesei.ocpp.frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class FrameCodecTest {

  private static final String BOOT_NOTIFICATION =
      """
      [2,"19223201","BootNotification",\
      {"chargePointVendor":"VendorX","chargePointModel":"SingleSocketCharger"}]""";

  private static final String BOOT_NOTIFICATION_RESULT =
      """
      [3,"19223201",\
      {"status":"Accepted","currentTime":"2013-02-01T20:53:32.486Z","heartbeatInterval":300}]""";

  private static final String NOT_IMPLEMENTED =
      """
      [4,"19223201","NotImplemented","Action DataTransfer is not supported",{}]""";

  private final FrameCodec codec = new FrameCodec();

  @Test
  void decodesCall() {
    Call call = (Call) codec.decode(BOOT_NOTIFICATION);

    assertThat(call.messageId()).isEqualTo("19223201");
    assertThat(call.action()).isEqualTo("BootNotification");
    assertThat(call.payload().get("chargePointVendor").stringValue()).isEqualTo("VendorX");
  }

  @Test
  void decodesCallResult() {
    CallResult result = (CallResult) codec.decode(BOOT_NOTIFICATION_RESULT);

    assertThat(result.messageId()).isEqualTo("19223201");
    assertThat(result.payload().get("heartbeatInterval").intValue()).isEqualTo(300);
  }

  @Test
  void decodesCallError() {
    CallError error = (CallError) codec.decode(NOT_IMPLEMENTED);

    assertThat(error.errorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED);
    assertThat(error.errorDescription()).isEqualTo("Action DataTransfer is not supported");
    assertThat(error.errorDetails().isEmpty()).isTrue();
  }

  @Test
  void acceptsWhitespaceBetweenElements() {
    assertThat(codec.decode("[ 3 , \"1\" , { } ]")).isInstanceOf(CallResult.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {BOOT_NOTIFICATION, BOOT_NOTIFICATION_RESULT, NOT_IMPLEMENTED})
  void encodesBackToTheSameText(String text) {
    assertThat(codec.encode(codec.decode(text))).isEqualTo(text);
  }

  @Test
  void encodesErrorCodeWithItsWireName() {
    ObjectNode details = JsonNodeFactory.instance.objectNode();
    CallError error =
        new CallError("42", ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION, "Missing field", details);

    assertThat(codec.encode(error))
        .isEqualTo("[4,\"42\",\"OccurenceConstraintViolation\",\"Missing field\",{}]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not json",
        "{\"messageTypeId\":2}",
        "[]",
        "[2,\"1\",\"Heartbeat\",{}] trailing",
        "[\"2\",\"1\",\"Heartbeat\",{}]",
        "[2.0,\"1\",\"Heartbeat\",{}]",
        "[5,\"1\",{}]",
        "[2,1,\"Heartbeat\",{}]",
        "[2,\"1\",\"Heartbeat\"]",
        "[2,\"1\",\"Heartbeat\",{},{}]",
        "[2,\"1\",\"Heartbeat\",[]]",
        "[2,\"1\",\"\",{}]",
        "[2,\"1\",7,{}]",
        "[3,\"1\",null]",
        "[4,\"1\",\"FormatViolation\",\"\",{}]",
        "[4,\"1\",\"GenericError\",null,{}]",
        "[2,\"1234567890123456789012345678901234567\",\"Heartbeat\",{}]"
      })
  void rejectsInvalidFrameAsFormationViolation(String text) {
    FrameDecodingException e = catchThrowableOfType(FrameDecodingException.class, decoding(text));

    assertThat(e.errorCode()).isEqualTo(ErrorCode.FORMATION_VIOLATION);
  }

  @Test
  void keepsMessageIdSoTheErrorCanBeAnswered() {
    FrameDecodingException e =
        catchThrowableOfType(FrameDecodingException.class, decoding("[2,\"abc\",\"Heartbeat\"]"));

    assertThat(e.messageId()).contains("abc");
  }

  @Test
  void hasNoMessageIdWhenItCannotBeRead() {
    FrameDecodingException e =
        catchThrowableOfType(FrameDecodingException.class, decoding("[2,1,\"Heartbeat\",{}]"));

    assertThat(e.messageId()).isEmpty();
  }

  /** OCPP-J expects a CALLERROR only for a CALL; nothing waits for an answer to an answer. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "[2,\"abc\",\"Heartbeat\"]|true",
        "[2,\"abc\",\"\",{}]|true",
        "[2,1,\"Heartbeat\",{}]|false",
        "[3,\"abc\"]|false",
        "[4,\"abc\",\"Nonsense\",\"\",{}]|false",
        "[9,\"abc\",{}]|false",
        "not json|false"
      })
  void saysWhetherAMalformedFrameExpectsAnAnswer(String text, boolean answerable) {
    FrameDecodingException e = catchThrowableOfType(FrameDecodingException.class, decoding(text));

    assertThat(e.answerable()).isEqualTo(answerable);
    assertThat(e.errorCode()).isEqualTo(ErrorCode.FORMATION_VIOLATION);
  }

  private ThrowingCallable decoding(String text) {
    return () -> codec.decode(text);
  }
}
