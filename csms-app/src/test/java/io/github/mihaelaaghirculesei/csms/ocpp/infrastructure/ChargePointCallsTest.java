package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointCalls;
import io.github.mihaelaaghirculesei.csms.ocpp.ChargePointNotConnectedException;
import io.github.mihaelaaghirculesei.ocpp.call.CallInProgressException;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
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

  static ObjectNode softReset() {
    return json("{\"type\":\"Soft\"}");
  }

  static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
