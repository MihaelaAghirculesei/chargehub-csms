package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;

/**
 * The last status a charge point reported for one of its connectors.
 *
 * @param id 0 for the whole station, from 1 for each connector
 * @param reportedAt when the charge point says the status was reached, not when the report arrived
 */
public record Connector(int id, ConnectorStatus status, Instant reportedAt) {

  public static final int WHOLE_STATION = 0;

  public Connector {
    if (id < WHOLE_STATION) {
      throw new IllegalArgumentException("Connector id must not be negative, got " + id);
    }
  }
}
