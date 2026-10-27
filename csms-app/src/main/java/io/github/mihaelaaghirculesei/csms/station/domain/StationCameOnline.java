package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;

/** A station that was offline was heard from again. */
public record StationCameOnline(String stationId, Instant cameOnlineAt) {}
