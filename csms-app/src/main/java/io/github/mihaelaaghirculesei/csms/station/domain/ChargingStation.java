package io.github.mihaelaaghirculesei.csms.station.domain;

import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Applied;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Rejected;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Stale;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Unchanged;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A charging station, what it said about itself at its last boot, whether it is online and the last
 * known status of each of its connectors.
 *
 * <p>A station becomes known through its first boot: OCPP 1.6 lets a charge point send nothing else
 * before it is accepted. Connectors are learned from the reports, since a boot announces no
 * connector count. Not thread-safe: a station is loaded, changed and saved within one transaction.
 */
public final class ChargingStation {

  private final String id;
  private StationDetails details;
  private Instant lastBootAt;
  private Instant lastSeenAt;
  private boolean online;
  private final Map<Integer, Connector> connectors = new TreeMap<>();

  private ChargingStation(
      String id, StationDetails details, Instant lastBootAt, Instant lastSeenAt, boolean online) {
    if (id.isBlank()) {
      throw new IllegalArgumentException("Charging station id must not be blank");
    }
    this.id = id;
    this.details = details;
    this.lastBootAt = lastBootAt;
    this.lastSeenAt = lastSeenAt;
    this.online = online;
  }

  /** A station that boots for the first time, and so is online. */
  public static ChargingStation register(String id, StationDetails details, Instant bootedAt) {
    return new ChargingStation(id, details, bootedAt, bootedAt, true);
  }

  /**
   * A station as it was saved. The connectors are taken as they are: they passed the transition
   * rules when they were reported.
   */
  public static ChargingStation restore(
      String id,
      StationDetails details,
      Instant lastBootAt,
      Instant lastSeenAt,
      boolean online,
      List<Connector> connectors) {
    ChargingStation station = new ChargingStation(id, details, lastBootAt, lastSeenAt, online);
    connectors.forEach(connector -> station.connectors.put(connector.id(), connector));
    return station;
  }

  public String id() {
    return id;
  }

  public StationDetails details() {
    return details;
  }

  public Instant lastBootAt() {
    return lastBootAt;
  }

  /** When the central system last heard from the station, by its own clock. */
  public Instant lastSeenAt() {
    return lastSeenAt;
  }

  public boolean online() {
    return online;
  }

  /**
   * Takes any message from the station as a sign that it is alive.
   *
   * @return the event to publish when the station was offline until now
   */
  public Optional<StationCameOnline> seen(Instant at) {
    // Instances of the central system can disagree by a few milliseconds; never move back.
    if (at.isAfter(lastSeenAt)) {
      lastSeenAt = at;
    }
    if (online) {
      return Optional.empty();
    }
    online = true;
    return Optional.of(new StationCameOnline(id, at));
  }

  /**
   * Takes a new boot. The details may have changed, for example after a firmware update. The
   * connector statuses stay: the station reports them again after the boot.
   */
  public void boot(StationDetails details, Instant bootedAt) {
    this.details = details;
    this.lastBootAt = bootedAt;
  }

  public Optional<Connector> connector(int connectorId) {
    return Optional.ofNullable(connectors.get(connectorId));
  }

  /** Every known connector, by id. */
  public List<Connector> connectors() {
    return List.copyOf(connectors.values());
  }

  /**
   * Takes the status the charge point reports for one connector. Reports can arrive twice or out of
   * order, so a report older than the last one taken is ignored, and the same status again changes
   * only the time of the last report.
   *
   * @param reportedAt when the charge point says the status was reached
   */
  public ReportOutcome report(int connectorId, ConnectorStatus status, Instant reportedAt) {
    Connector reported = new Connector(connectorId, status, reportedAt);
    Connector current = connectors.get(connectorId);
    if (current != null && reportedAt.isBefore(current.reportedAt())) {
      return new Stale(current, reportedAt);
    }
    ConnectorStatus previous = current == null ? null : current.status();
    if (previous == status) {
      connectors.put(connectorId, reported);
      return new Unchanged(reported);
    }
    if (!isAllowed(connectorId, previous, status)) {
      return new Rejected(connectorId, previous, status);
    }
    connectors.put(connectorId, reported);
    return new Applied(new ConnectorStatusChanged(id, connectorId, previous, status, reportedAt));
  }

  private static boolean isAllowed(
      int connectorId, @Nullable ConnectorStatus previous, ConnectorStatus next) {
    if (connectorId == Connector.WHOLE_STATION && !next.appliesToTheWholeStation()) {
      return false;
    }
    // A connector seen for the first time may be in any status, for example after a reboot.
    return previous == null || previous.allowsChangeTo(next);
  }
}
