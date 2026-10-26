package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * What a status report did to a connector. Every outcome is a normal result, not an error: OCPP 1.6
 * gives the central system no way to refuse a StatusNotification, so the caller decides what to log
 * or count. See docs/adr/0013.
 */
public sealed interface ReportOutcome {

  /** The connector took the reported status. */
  record Applied(ConnectorStatusChanged change) implements ReportOutcome {}

  /** The connector already had the reported status; only the time of the last report moved on. */
  record Unchanged(Connector connector) implements ReportOutcome {}

  /** The report is older than the last one taken, so it says nothing about the connector now. */
  record Stale(Connector connector, Instant reportedAt) implements ReportOutcome {}

  /**
   * The specification does not allow this change, so the connector keeps its status.
   *
   * @param current null when the connector had no status yet
   */
  record Rejected(int connectorId, @Nullable ConnectorStatus current, ConnectorStatus reported)
      implements ReportOutcome {}
}
