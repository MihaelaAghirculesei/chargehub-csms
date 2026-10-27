package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.AVAILABLE;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.PREPARING;
import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.Connector;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatusChanged;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class StationHandlersTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T10:15:30.123Z"), ZoneOffset.UTC);

  private final PayloadValidator validator = PayloadValidator.ocpp16();
  private final InMemoryStations stations = new InMemoryStations();
  private final BootNotificationHandler boots =
      new BootNotificationHandler(CLOCK, stations, new StationProperties(Duration.ofMinutes(2)));
  private final List<Object> published = new ArrayList<>();
  private final StatusNotificationHandler statuses =
      new StatusNotificationHandler(CLOCK, stations, published::add);

  @Test
  void acceptsTheChargePointWithTheConfiguredHeartbeatInterval() {
    ObjectNode response = boots.handle("CP-1", bootNotification());

    assertThat(response)
        .isEqualTo(
            json(
                "{\"status\":\"Accepted\",\"currentTime\":\"2026-10-17T10:15:30.123Z\","
                    + "\"interval\":120}"));
    assertThat(validator.validate("BootNotification", RESPONSE, response)).isEmpty();
  }

  @Test
  void registersAStationAtItsFirstBoot() {
    boots.handle(
        "CP-1",
        json(
            "{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\","
                + "\"chargePointSerialNumber\":\"SN-1\",\"firmwareVersion\":\"1.0.0\"}"));

    ChargingStation station = stations.findById("CP-1").orElseThrow();
    assertThat(station.details())
        .isEqualTo(new StationDetails("VendorX", "SingleSocketCharger", "SN-1", "1.0.0"));
    assertThat(station.lastBootAt()).isEqualTo(CLOCK.instant());
  }

  @Test
  void updatesAKnownStationWhenItBootsAgain() {
    StationDetails before = new StationDetails("VendorX", "SingleSocketCharger", null, "0.9.0");
    stations.save(ChargingStation.register("CP-1", before, CLOCK.instant().minusSeconds(3600)));

    boots.handle("CP-1", bootNotification());

    ChargingStation station = stations.findById("CP-1").orElseThrow();
    assertThat(station.details())
        .isEqualTo(new StationDetails("VendorX", "SingleSocketCharger", null, null));
    assertThat(station.lastBootAt()).isEqualTo(CLOCK.instant());
  }

  @Test
  void appliesAReportedStatusAndPublishesTheChange() {
    boots.handle("CP-1", bootNotification());

    ObjectNode response =
        statuses.handle("CP-1", statusNotification(1, "Available", "2026-10-17T10:15:00Z"));

    Instant reportedAt = Instant.parse("2026-10-17T10:15:00Z");
    assertThat(response).isEmpty();
    assertThat(validator.validate("StatusNotification", RESPONSE, response)).isEmpty();
    assertThat(stations.findById("CP-1").orElseThrow().connector(1))
        .contains(new Connector(1, AVAILABLE, reportedAt));
    assertThat(published)
        .containsExactly(new ConnectorStatusChanged("CP-1", 1, null, AVAILABLE, reportedAt));
  }

  @Test
  void takesTheTimeOfArrivalWhenTheReportHasNoTimestamp() {
    boots.handle("CP-1", bootNotification());

    statuses.handle(
        "CP-1", json("{\"connectorId\":1,\"errorCode\":\"NoError\",\"status\":\"Preparing\"}"));

    assertThat(stations.findById("CP-1").orElseThrow().connector(1))
        .contains(new Connector(1, PREPARING, CLOCK.instant()));
  }

  @Test
  void publishesNothingWhenTheReportIsNotApplied() {
    boots.handle("CP-1", bootNotification());
    statuses.handle("CP-1", statusNotification(1, "Available", "2026-10-17T10:15:00Z"));
    published.clear();

    statuses.handle("CP-1", statusNotification(1, "Finishing", "2026-10-17T10:15:10Z"));

    assertThat(published).isEmpty();
    assertThat(stations.findById("CP-1").orElseThrow().connector(1))
        .map(Connector::status)
        .contains(AVAILABLE);
  }

  @Test
  void answersAReportOfAnUnknownStationWithoutStoringIt() {
    ObjectNode response =
        statuses.handle("CP-UNKNOWN", statusNotification(1, "Available", "2026-10-17T10:15:00Z"));

    assertThat(response).isEmpty();
    assertThat(stations.findById("CP-UNKNOWN")).isEmpty();
    assertThat(published).isEmpty();
  }

  @Test
  void answersAHeartbeatWithTheCurrentTime() {
    ObjectNode response =
        new HeartbeatHandler(CLOCK).handle("CP-1", JsonNodeFactory.instance.objectNode());

    assertThat(response).isEqualTo(json("{\"currentTime\":\"2026-10-17T10:15:30.123Z\"}"));
    assertThat(validator.validate("Heartbeat", RESPONSE, response)).isEmpty();
  }

  private static ObjectNode bootNotification() {
    return json("{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}");
  }

  private static ObjectNode statusNotification(int connectorId, String status, String timestamp) {
    return json(
        "{\"connectorId\":"
            + connectorId
            + ",\"errorCode\":\"NoError\",\"status\":\""
            + status
            + "\",\"timestamp\":\""
            + timestamp
            + "\"}");
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }

  /** The port is small enough that a map stands in for it; the adapter has its own tests. */
  private static final class InMemoryStations implements ChargingStationRepository {

    private final Map<String, ChargingStation> byId = new HashMap<>();

    @Override
    public Optional<ChargingStation> findById(String id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(ChargingStation station) {
      byId.put(station.id(), station);
    }
  }
}
