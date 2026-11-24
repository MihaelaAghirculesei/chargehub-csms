package io.github.mihaelaaghirculesei.csms.session;

import java.time.Instant;

/**
 * A charge point stopped a session and the central system stored the stop.
 *
 * @param idTag the tag that started the session
 * @param authorized whether the tag was accepted at start
 * @param meterStart the energy register at start in Wh
 * @param meterStop the energy register at stop in Wh
 */
public record SessionCompleted(
    int transactionId,
    String stationId,
    int connectorId,
    String idTag,
    boolean authorized,
    long meterStart,
    long meterStop,
    Instant startedAt,
    Instant stoppedAt,
    StopReason reason) {}
