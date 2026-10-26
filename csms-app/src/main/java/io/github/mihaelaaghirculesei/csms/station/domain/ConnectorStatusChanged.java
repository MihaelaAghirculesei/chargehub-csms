package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A connector reached a new status.
 *
 * @param previous null for the first report of a connector the central system did not know yet
 */
public record ConnectorStatusChanged(
    String stationId,
    int connectorId,
    @Nullable ConnectorStatus previous,
    ConnectorStatus current,
    Instant changedAt) {}
