package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * The Definition of Done of Phase 1: a charge point connects, is accepted and sends heartbeats,
 * with the frames of the OCPP-J 1.6 examples.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChargePointSessionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final Instant NOW = Instant.parse("2026-10-17T10:15:30.123Z");

  @LocalServerPort private int port;

  // Only declared to replace the Clock bean with clock() below; the test never reads it.
  @SuppressWarnings("UnusedVariable")
  @TestBean
  private Clock clock;

  static Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  @Test
  void connectsIsAcceptedAndSendsHeartbeats() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-DOD")) {
      chargePoint.send(
          "[2,\"19223201\",\"BootNotification\","
              + "{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}]");
      assertThat(chargePoint.nextFrame(WAIT))
          .isEqualTo(
              "[3,\"19223201\",{\"status\":\"Accepted\","
                  + "\"currentTime\":\"2026-10-17T10:15:30.123Z\",\"interval\":300}]");

      chargePoint.send("[2,\"19223202\",\"Heartbeat\",{}]");
      assertThat(chargePoint.nextFrame(WAIT))
          .isEqualTo("[3,\"19223202\",{\"currentTime\":\"2026-10-17T10:15:30.123Z\"}]");
    }
  }
}
