package io.github.mihaelaaghirculesei.csms.session;

import java.time.Instant;

/**
 * A charge point started a session and the central system stored it.
 *
 * @param authorized false when the tag was refused; the session still exists, because the charge
 *     point may keep charging until it stops the transaction
 */
public record SessionStarted(
    int transactionId,
    String stationId,
    int connectorId,
    String idTag,
    boolean authorized,
    Instant startedAt) {}
