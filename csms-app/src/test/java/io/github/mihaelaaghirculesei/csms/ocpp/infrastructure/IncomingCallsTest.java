package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** Frames as a charge point sends them, through the real endpoint and the real schemas. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IncomingCallsTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  @LocalServerPort private int port;
  @Autowired private ChargePointConnections connections;
  @Autowired private FrameCodec codec;

  @Test
  void answersEachKindOfFailureWithItsErrorCode() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-IN", connections)) {
      chargePoint.send("[2,\"m1\",\"Unknown\",{}]");
      assertThat(answer(chargePoint)).isEqualTo(error("m1", ErrorCode.NOT_IMPLEMENTED));

      // Reservations are outside the scope of the project, so this action stays unsupported.
      chargePoint.send("[2,\"m2\",\"CancelReservation\",{\"reservationId\":7}]");
      assertThat(answer(chargePoint)).isEqualTo(error("m2", ErrorCode.NOT_SUPPORTED));

      chargePoint.send("[2,\"m3\",\"BootNotification\",{\"chargePointVendor\":\"VendorX\"}]");
      assertThat(answer(chargePoint))
          .isEqualTo(error("m3", ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION));

      chargePoint.send("[2,\"m4\",\"Heartbeat\"]");
      assertThat(answer(chargePoint)).isEqualTo(error("m4", ErrorCode.FORMATION_VIOLATION));
    }
  }

  @Test
  void doesNotAnswerAMalformedAnswer() throws Exception {
    try (TestChargePoint chargePoint = TestChargePoint.connect(port, "CP-QUIET", connections)) {
      chargePoint.send("[3,\"m1\"]");
      chargePoint.send("not json");

      assertThat(chargePoint.nextFrame(Duration.ofMillis(500))).isNull();
      chargePoint.send("[2,\"m2\",\"Unknown\",{}]");
      assertThat(answer(chargePoint)).isEqualTo(error("m2", ErrorCode.NOT_IMPLEMENTED));
    }
  }

  private String answer(TestChargePoint chargePoint) throws InterruptedException {
    CallError error = (CallError) codec.decode(Objects.requireNonNull(chargePoint.nextFrame(WAIT)));
    return error(error.messageId(), error.errorCode());
  }

  private static String error(String messageId, ErrorCode code) {
    return messageId + " " + code.wireName();
  }
}
