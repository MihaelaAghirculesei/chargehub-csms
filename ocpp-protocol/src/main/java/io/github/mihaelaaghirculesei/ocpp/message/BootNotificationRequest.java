package io.github.mihaelaaghirculesei.ocpp.message;

import org.jspecify.annotations.Nullable;

/** BootNotification.req: a charge point announces itself after it starts or reconnects. */
public record BootNotificationRequest(
    String chargePointVendor,
    String chargePointModel,
    @Nullable String chargePointSerialNumber,
    @Nullable String chargeBoxSerialNumber,
    @Nullable String firmwareVersion,
    @Nullable String iccid,
    @Nullable String imsi,
    @Nullable String meterType,
    @Nullable String meterSerialNumber) {}
