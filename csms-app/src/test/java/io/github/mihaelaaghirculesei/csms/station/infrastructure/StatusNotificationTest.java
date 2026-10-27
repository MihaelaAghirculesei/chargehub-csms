package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.AVAILABLE;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.CHARGING;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.Connector;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatusChanged;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** StatusNotification over OCPP-J, through to the database. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, StatusNotificationTest.RecordedChanges.class})
class StatusNotificationTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
  private static final Instant T1 = T0.plusSeconds(1);

  @LocalServerPort private int port;
  @Autowired private ChargingStationRepository stations;
  @Autowired private RecordedChanges changes;

  @Test
  void storesTheReportedStatusBeforeAnsweringAndPublishesTheChange() throws Exception {
    try (TestChargePoint chargePoint = booted("CP-STATUS")) {
      chargePoint.send(statusNotification("2", 1, "Available", T0));
      assertThat(chargePoint.nextFrame(WAIT)).isEqualTo("[3,\"2\",{}]");

      // Read without waiting: the report is stored before the answer is sent.
      assertThat(stations.findById("CP-STATUS").orElseThrow().connector(1))
          .contains(new Connector(1, AVAILABLE, T0));
      assertThat(changes.of("CP-STATUS"))
          .containsExactly(new ConnectorStatusChanged("CP-STATUS", 1, null, AVAILABLE, T0));
    }
  }

  @Test
  void keepsTheStatusWhenAReportIsOlderThanTheLastOne() throws Exception {
    try (TestChargePoint chargePoint = booted("CP-LATE")) {
      chargePoint.send(statusNotification("2", 1, "Charging", T1));
      chargePoint.nextFrame(WAIT);

      chargePoint.send(statusNotification("3", 1, "Available", T0));
      assertThat(chargePoint.nextFrame(WAIT)).isEqualTo("[3,\"3\",{}]");

      assertThat(stations.findById("CP-LATE").orElseThrow().connector(1))
          .contains(new Connector(1, CHARGING, T1));
      assertThat(changes.of("CP-LATE")).hasSize(1);
    }
  }

  private TestChargePoint booted(String chargePointId) throws Exception {
    TestChargePoint chargePoint = new TestChargePoint(port, chargePointId);
    chargePoint.send(
        "[2,\"1\",\"BootNotification\","
            + "{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}]");
    chargePoint.nextFrame(WAIT);
    return chargePoint;
  }

  private static String statusNotification(
      String messageId, int connectorId, String status, Instant timestamp) {
    return "[2,\""
        + messageId
        + "\",\"StatusNotification\",{\"connectorId\":"
        + connectorId
        + ",\"errorCode\":\"NoError\",\"status\":\""
        + status
        + "\",\"timestamp\":\""
        + timestamp
        + "\"}]";
  }

  /**
   * Handlers run on the WebSocket threads, which {@code @RecordApplicationEvents} does not see, so
   * a listener collects the events instead. The context is shared, so each test reads its own
   * station.
   */
  @TestComponent
  static class RecordedChanges {

    private final List<ConnectorStatusChanged> all = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ConnectorStatusChanged change) {
      all.add(change);
    }

    List<ConnectorStatusChanged> of(String stationId) {
      return all.stream().filter(change -> change.stationId().equals(stationId)).toList();
    }
  }
}
