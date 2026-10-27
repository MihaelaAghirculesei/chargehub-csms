package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.StationCameOnline;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.convention.TestBean;

/** Heartbeats over OCPP-J keep the station seen, and bring an offline one back. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, StationPresenceTest.RecordedArrivals.class})
class StationPresenceTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
  private static final Instant AN_HOUR_AGO = NOW.minusSeconds(3600);
  private static final StationDetails DETAILS =
      new StationDetails("VendorX", "SingleSocketCharger", null, null);

  @LocalServerPort private int port;
  @Autowired private ChargingStationRepository stations;
  @Autowired private RecordedArrivals arrivals;

  // Only declared to replace the Clock bean with clock() below; the test never reads it.
  @SuppressWarnings("UnusedVariable")
  @TestBean
  private Clock clock;

  static Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  @Test
  void aHeartbeatMovesTheTimeTheStationWasLastSeen() throws Exception {
    stations.save(ChargingStation.register("CP-ALIVE", DETAILS, AN_HOUR_AGO));

    heartbeat("CP-ALIVE");

    ChargingStation station = stations.findById("CP-ALIVE").orElseThrow();
    assertThat(station.lastSeenAt()).isEqualTo(NOW);
    assertThat(arrivals.of("CP-ALIVE")).isEmpty();
  }

  @Test
  void anOfflineStationThatSendsAHeartbeatComesOnline() throws Exception {
    stations.save(
        ChargingStation.restore("CP-BACK", DETAILS, AN_HOUR_AGO, AN_HOUR_AGO, false, List.of()));

    heartbeat("CP-BACK");

    assertThat(stations.findById("CP-BACK").orElseThrow().online()).isTrue();
    assertThat(arrivals.of("CP-BACK")).containsExactly(new StationCameOnline("CP-BACK", NOW));
  }

  private void heartbeat(String chargePointId) throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, chargePointId)) {
      chargePoint.send("[2,\"1\",\"Heartbeat\",{}]");
      assertThat(chargePoint.nextFrame(WAIT))
          .isEqualTo("[3,\"1\",{\"currentTime\":\"" + NOW + "\"}]");
    }
  }

  /** Handlers run on the WebSocket threads, so a listener collects the events. */
  @TestComponent
  static class RecordedArrivals {

    private final List<StationCameOnline> all = new CopyOnWriteArrayList<>();

    @EventListener
    void on(StationCameOnline arrival) {
      all.add(arrival);
    }

    List<StationCameOnline> of(String stationId) {
      return all.stream().filter(arrival -> arrival.stationId().equals(stationId)).toList();
    }
  }
}
