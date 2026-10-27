package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.Connector;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The table side of {@link ChargingStation}. Connectors are values of the station, not entities:
 * they are always loaded and saved with it, and the station version guards them too.
 */
@Entity
@Table(name = "charging_station")
class ChargingStationEntity {

  @Id private String id;

  private String vendor;

  private String model;

  private @Nullable String serialNumber;

  private @Nullable String firmwareVersion;

  private Instant lastBootAt;

  private Instant lastSeenAt;

  private boolean online;

  // Eager because the aggregate is always used whole, and mapping happens after the query.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "connector", joinColumns = @JoinColumn(name = "charging_station_id"))
  @MapKeyColumn(name = "connector_id")
  private Map<Integer, ConnectorColumns> connectors = new HashMap<>();

  // Null until the first save, which is how Spring Data tells a new station from a stored one.
  @Version private @Nullable Long version;

  @SuppressWarnings("NullAway.Init")
  protected ChargingStationEntity() {
    // JPA fills the fields through reflection.
  }

  ChargingStationEntity(ChargingStation station) {
    // Delegates so that NullAway, which cannot follow copy(), checks no field here.
    this();
    this.id = station.id();
    copy(station);
  }

  /** Takes the state of the station, keeping the version this entity was loaded with. */
  void copy(ChargingStation station) {
    StationDetails details = station.details();
    vendor = details.vendor();
    model = details.model();
    serialNumber = details.serialNumber();
    firmwareVersion = details.firmwareVersion();
    lastBootAt = station.lastBootAt();
    lastSeenAt = station.lastSeenAt();
    online = station.online();
    Map<Integer, ConnectorColumns> reported = new HashMap<>();
    station
        .connectors()
        .forEach(c -> reported.put(c.id(), new ConnectorColumns(c.status(), c.reportedAt())));
    // Changed in place so that Hibernate writes only the rows that differ.
    connectors.keySet().retainAll(reported.keySet());
    connectors.putAll(reported);
  }

  ChargingStation toDomain() {
    List<Connector> restored =
        connectors.entrySet().stream()
            .map(e -> new Connector(e.getKey(), e.getValue().status(), e.getValue().reportedAt()))
            .toList();
    return ChargingStation.restore(
        id,
        new StationDetails(vendor, model, serialNumber, firmwareVersion),
        lastBootAt,
        lastSeenAt,
        online,
        restored);
  }

  @Embeddable
  record ConnectorColumns(
      @Enumerated(EnumType.STRING) ConnectorStatus status, Instant reportedAt) {}
}
