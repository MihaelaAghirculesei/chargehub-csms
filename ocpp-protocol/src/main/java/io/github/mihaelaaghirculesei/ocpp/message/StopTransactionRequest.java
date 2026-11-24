package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * StopTransaction.req: a charge point reports that a transaction ended.
 *
 * @param idTag the tag that stopped the transaction, when a driver presented one
 * @param meterStop the energy register of the connector in Wh
 * @param timestamp when the transaction stopped, by the clock of the charge point
 * @param reason missing means {@link Reason#LOCAL}, as the specification says
 * @param transactionData readings of the transaction the charge point did not send before
 */
public record StopTransactionRequest(
    @Nullable String idTag,
    long meterStop,
    Instant timestamp,
    int transactionId,
    @Nullable Reason reason,
    @Nullable List<MeterValue> transactionData) {}
