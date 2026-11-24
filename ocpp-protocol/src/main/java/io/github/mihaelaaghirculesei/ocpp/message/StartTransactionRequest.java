package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * StartTransaction.req: a charge point reports that charging started on a connector.
 *
 * @param meterStart the energy register of the connector in Wh; a long, because the lifetime
 *     reading of a fast charger can pass the range of an int
 * @param timestamp when the transaction started, by the clock of the charge point
 */
public record StartTransactionRequest(
    int connectorId,
    String idTag,
    long meterStart,
    @Nullable Integer reservationId,
    Instant timestamp) {}
