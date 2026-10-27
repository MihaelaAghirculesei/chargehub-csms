package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * StatusNotification.req: the status of one connector, or of the whole station for connector 0.
 * StatusNotification.conf has no fields, so it has no record.
 *
 * @param timestamp when the status was reached; optional, so a charge point may leave it out
 */
public record StatusNotificationRequest(
    int connectorId,
    ChargePointErrorCode errorCode,
    @Nullable String info,
    ChargePointStatus status,
    @Nullable Instant timestamp,
    @Nullable String vendorId,
    @Nullable String vendorErrorCode) {}
