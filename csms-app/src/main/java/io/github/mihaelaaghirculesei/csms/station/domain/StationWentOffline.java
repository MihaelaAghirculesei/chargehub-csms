package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;

/** A station was not heard from for too long and is now taken as offline. */
public record StationWentOffline(String stationId, Instant lastSeenAt, Instant wentOfflineAt) {}
