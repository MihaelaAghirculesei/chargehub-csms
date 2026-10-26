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
 * A charging station and the last known status of each of its connectors.
 *
 * <p>Connectors are learned from the reports: OCPP 1.6 announces no connector count at boot. Not
 * thread-safe: a station is loaded, changed and saved within one transaction.
 */
public final class ChargingStation {

  private final String id;
  private final Map<Integer, Connector> connectors = new TreeMap<>();

  public ChargingStation(String id) {
    if (id.isBlank()) {
      throw new IllegalArgumentException("Charging station id must not be blank");
    }
    this.id = id;
  }

  public String id() {
    return id;
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
