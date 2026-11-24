package io.github.mihaelaaghirculesei.ocpp.message;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * MeterValues.req: readings of a connector, during a transaction or outside one.
 *
 * @param transactionId missing when the readings belong to no transaction
 */
public record MeterValuesRequest(
    int connectorId, @Nullable Integer transactionId, List<MeterValue> meterValue) {}
