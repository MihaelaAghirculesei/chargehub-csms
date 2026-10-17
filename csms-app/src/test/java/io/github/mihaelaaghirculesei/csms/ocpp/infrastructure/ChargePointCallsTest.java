package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointCalls;
import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointNotConnectedException;
import io.github.mihaelaaghirculesei.csms.ocpp.InvalidResponseException;
import io.github.mihaelaaghirculesei.ocpp.call.CallErrorException;
import io.github.mihaelaaghirculesei.ocpp.call.CallInProgressException;
import io.github.mihaelaaghirculesei.ocpp.call.ConnectionClosedException;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChargePointCallsTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  @LocalServerPort private int port;
  @Autowired private ChargePointCalls calls;
  @Autowired private ChargePointConnections connections;
  @Autowired private FrameCodec codec;

  @Test
  void sendsTheCallToTheChargePoint() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-SEND", connections)) {
      CompletableFuture<CallResult> outcome = calls.call("CP-SEND", Action.RESET, softReset());

      Call call = (Call) codec.decode(Objects.requireNonNull(chargePoint.nextFrame(WAIT)));
      assertThat(outcome).isNotDone();
      assertThat(call.action()).isEqualTo("Reset");
      assertThat(call.payload()).isEqualTo(softReset());
      assertThat(call.messageId()).hasSize(36);
    }
  }

  @Test
  void refusesAChargePointThatIsNotConnected() {
    ObjectNode payload = softReset();

    assertThatThrownBy(() -> calls.call("CP-ABSENT", Action.RESET, payload))
        .isInstanceOf(ChargePointNotConnectedException.class);
  }

  @Test
  void doesNotSendAPayloadThatBreaksTheSchema() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-INVALID", connections)) {
      ObjectNode invalid = json("{\"type\":\"Gentle\"}");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> calls.call("CP-INVALID", Action.RESET, invalid))
          .withMessageContaining("Reset");
      assertThat(chargePoint.nextFrame(Duration.ofMillis(300))).isNull();
    }
  }

  @Test
  void refusesASecondCallWhileTheFirstIsUnanswered() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-BUSY", connections)) {
      CompletableFuture<CallResult> first = calls.call("CP-BUSY", Action.RESET, softReset());
      ObjectNode payload = softReset();

      assertThatThrownBy(() -> calls.call("CP-BUSY", Action.RESET, payload))
          .isInstanceOf(CallInProgressException.class);
      assertThat(chargePoint.nextFrame(WAIT)).isNotNull();
      assertThat(chargePoint.nextFrame(Duration.ofMillis(300))).isNull();
      assertThat(first).isNotDone();
    }
  }

  @Test
  void completesWithTheAnswerOfTheChargePoint() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-ANSWER", connections)) {
      CompletableFuture<CallResult> outcome = calls.call("CP-ANSWER", Action.RESET, softReset());
      String messageId =
          codec.decode(Objects.requireNonNull(chargePoint.nextFrame(WAIT))).messageId();

      chargePoint.send(codec.encode(new CallResult(messageId, json("{\"status\":\"Accepted\"}"))));

      assertThat(outcome)
          .succeedsWithin(WAIT)
          .extracting(result -> result.payload().get("status").stringValue())
          .isEqualTo("Accepted");
    }
  }

  @Test
  void failsWithTheErrorOfTheChargePoint() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-ERROR", connections)) {
      CompletableFuture<CallResult> outcome = calls.call("CP-ERROR", Action.RESET, softReset());
      String messageId =
          codec.decode(Objects.requireNonNull(chargePoint.nextFrame(WAIT))).messageId();

      chargePoint.send(
          codec.encode(new CallError(messageId, ErrorCode.NOT_SUPPORTED, "No reset", json("{}"))));

      assertThat(outcome)
          .failsWithin(WAIT)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(CallErrorException.class);
    }
  }

  @Test
  void failsWhenTheAnswerBreaksTheResponseSchema() throws Exception {
    try (TestChargePoint chargePoint =
        TestChargePoint.connect(port, "CP-BAD-ANSWER", connections)) {
      CompletableFuture<CallResult> outcome =
          calls.call("CP-BAD-ANSWER", Action.RESET, softReset());
      String messageId =
          codec.decode(Objects.requireNonNull(chargePoint.nextFrame(WAIT))).messageId();

      chargePoint.send(codec.encode(new CallResult(messageId, json("{\"status\":\"Maybe\"}"))));

      assertThat(outcome)
          .failsWithin(WAIT)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(InvalidResponseException.class);
    }
  }

  @Test
  void failsAtOnceWhenTheConnectionCloses() throws Exception {
    TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-GONE", connections);
    CompletableFuture<CallResult> outcome = calls.call("CP-GONE", Action.RESET, softReset());

    chargePoint.close();

    assertThat(outcome)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConnectionClosedException.class);
  }

  @Test
  void failsTheCallOfAReplacedConnectionAndAcceptsANewOne() throws Exception {
    TestChargePoint old = TestChargePoint.connect(port, "CP-SWAP", connections);
    CompletableFuture<CallResult> outcome = calls.call("CP-SWAP", Action.RESET, softReset());

    try (TestChargePoint current = new TestChargePoint(port, "CP-SWAP")) {
      assertThat(outcome)
          .failsWithin(WAIT)
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(ConnectionClosedException.class);
      CompletableFuture<CallResult> next = calls.call("CP-SWAP", Action.RESET, softReset());
      assertThat(current.nextFrame(WAIT)).isNotNull();
      assertThat(next).isNotDone();
    } finally {
      old.close();
    }
  }

  static ObjectNode softReset() {
    return json("{\"type\":\"Soft\"}");
  }

  static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
