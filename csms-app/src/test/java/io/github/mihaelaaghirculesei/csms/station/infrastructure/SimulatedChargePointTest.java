package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import io.github.mihaelaaghirculesei.simulator.chargepoint.FaultPolicy;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.node.ObjectNode;

/**
 * The central system against the simulator, the same charge point the demo and the load tests run.
 * The frames of the specification examples are checked in {@link ChargePointSessionTest}.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    // The shortest interval OCPP can express keeps the test to a few seconds.
    properties = "chargehub.station.heartbeat-interval=1s")
@Import(TestcontainersConfiguration.class)
class SimulatedChargePointTest {

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
  private static final ReconnectPolicy RECONNECT =
      new ReconnectPolicy(Duration.ofMillis(100), Duration.ofMillis(100), 3);

  @LocalServerPort private int port;
  @MockitoSpyBean private BootNotificationHandler boots;
  @MockitoSpyBean private HeartbeatHandler heartbeats;
  @Autowired private ChargingStationRepository stations;

  @Test
  void isAcceptedOnceAndKeepsSendingHeartbeats() throws Exception {
    try (var _ = start("CP-SIM", FaultPolicy.NONE)) {
      verify(heartbeats, timeout(WAIT.toMillis()).atLeast(2))
          .handle(eq("CP-SIM"), any(ObjectNode.class));
      verify(boots).handle(eq("CP-SIM"), any(ObjectNode.class));
    }
    assertThat(stations.findById("CP-SIM"))
        .map(ChargingStation::details)
        .contains(new StationDetails("ChargeHub", "Simulator", null, null));
  }

  @Test
  void keepsServingAChargePointThatDropsItsConnectionAndRepeatsCalls() throws Exception {
    // Mean longer than the heartbeat interval, so most connections live long enough to send one.
    FaultPolicy faults = new FaultPolicy(Duration.ofSeconds(2), 1);
    try (var _ = start("CP-CHAOS", faults)) {
      // Every call arrives twice, and each connection boots: six boots are three connections.
      verify(boots, timeout(WAIT.toMillis()).atLeast(6))
          .handle(eq("CP-CHAOS"), any(ObjectNode.class));
      // Only the heartbeats after the reconnects count.
      clearInvocations(heartbeats);
      verify(heartbeats, timeout(WAIT.toMillis()).atLeastOnce())
          .handle(eq("CP-CHAOS"), any(ObjectNode.class));
    }
  }

  /** Runs a simulated charge point on its own thread until the returned handle is closed. */
  private AutoCloseable start(String chargePointId, FaultPolicy faults) {
    HttpClient client = HttpClient.newHttpClient();
    ChargePoint chargePoint =
        new ChargePoint(
            client,
            URI.create("ws://localhost:" + port + "/ocpp"),
            chargePointId,
            CALL_TIMEOUT,
            RECONNECT,
            faults);
    Thread thread =
        Thread.ofVirtual()
            .name(chargePointId)
            .start(
                () -> {
                  try {
                    chargePoint.run();
                  } catch (InterruptedException e) {
                    // Interrupted by the test: the charge point is done.
                  }
                });
    return () -> {
      thread.interrupt();
      thread.join(WAIT);
      client.close();
    };
  }
}
