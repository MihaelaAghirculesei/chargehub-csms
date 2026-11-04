package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.Connector;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatusChanged;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import io.github.mihaelaaghirculesei.simulator.chargepoint.FaultPolicy;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.node.ObjectNode;

/**
 * The definition of done of Phase 3: once the simulator stops changing, the central system holds
 * the status the simulator reported last for every connector, also after forced disconnections.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    // The shortest interval OCPP can express keeps the test to a few seconds.
    properties = "chargehub.station.heartbeat-interval=1s")
@Import({TestcontainersConfiguration.class, StatusNotificationTest.RecordedChanges.class})
class ConnectorStatusAgainstSimulatorTest {

  private static final Duration WAIT = Duration.ofSeconds(60);
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
  private static final ReconnectPolicy RECONNECT =
      new ReconnectPolicy(Duration.ofMillis(100), Duration.ofMillis(100), 3);
  private static final int CONNECTORS = 2;
  private static final List<String> IDS = List.of("CP-MATCH-1", "CP-MATCH-2", "CP-MATCH-3");
  // Mean longer than the heartbeat interval, so most connections live long enough to report.
  private static final FaultPolicy CHAOS = new FaultPolicy(Duration.ofSeconds(3), 0, 0.5);

  @LocalServerPort private int port;
  @MockitoSpyBean private BootNotificationHandler boots;
  @Autowired private ChargingStationRepository stations;
  @Autowired private StatusNotificationTest.RecordedChanges changes;

  @Test
  void holdsTheStatusTheSimulatorReportedLastAfterDropsAndFaults() throws Exception {
    List<ChargePoint> chargePoints = new ArrayList<>();
    List<Thread> threads = new ArrayList<>();
    try (HttpClient client = HttpClient.newHttpClient()) {
      for (String id : IDS) {
        ChargePoint chargePoint = chargePoint(client, id);
        chargePoints.add(chargePoint);
        threads.add(Thread.ofVirtual().name(id).start(() -> run(chargePoint)));
      }
      try {
        await().atMost(WAIT).until(() -> IDS.stream().allMatch(this::hasFaulted));
        // One boot per charge point is the first connection; any more follow a drop.
        verify(boots, timeout(WAIT.toMillis()).atLeast(IDS.size() + 1))
            .handle(anyString(), any(ObjectNode.class));

        chargePoints.forEach(chargePoint -> chargePoint.changeFaults(FaultPolicy.NONE));
        await().atMost(WAIT).until(() -> chargePoints.stream().allMatch(this::hasSettled));

        for (ChargePoint chargePoint : chargePoints) {
          assertThatCentralSystemMatches(chargePoint);
        }
      } finally {
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
          thread.join(WAIT);
        }
      }
    }
  }

  private ChargePoint chargePoint(HttpClient client, String id) {
    return new ChargePoint(
        client,
        URI.create("ws://localhost:" + port + "/ocpp"),
        id,
        CALL_TIMEOUT,
        RECONNECT,
        CHAOS,
        CONNECTORS);
  }

  private static void run(ChargePoint chargePoint) {
    try {
      chargePoint.run();
    } catch (InterruptedException _) {
      // Interrupted by the test: the charge point is done.
      Thread.currentThread().interrupt();
    }
  }

  private boolean hasFaulted(String id) {
    return changes.of(id).stream()
        .map(ConnectorStatusChanged::current)
        .anyMatch(status -> status == ConnectorStatus.FAULTED);
  }

  // Without faults a connector ends up Available, and then nothing changes any more.
  private boolean hasSettled(ChargePoint chargePoint) {
    Map<Integer, ChargePointStatus> reported = chargePoint.reportedLast();
    return reported.size() == CONNECTORS
        && reported.values().stream().allMatch(status -> status == ChargePointStatus.AVAILABLE);
  }

  private void assertThatCentralSystemMatches(ChargePoint chargePoint) {
    chargePoint
        .reportedLast()
        .forEach((connectorId, status) ->
            assertThat(
                    stations
                        .findById(chargePoint.id())
                        .orElseThrow()
                        .connector(connectorId)
                        .map(Connector::status)
                        .map(Enum::name))
                .as("connector %d of %s", connectorId, chargePoint.id())
                .contains(status.name()));
  }
}
