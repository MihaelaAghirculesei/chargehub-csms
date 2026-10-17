package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Heartbeat.conf. The request has no fields, so it has no record. */
public record HeartbeatResponse(Instant currentTime) {

  // The examples of the specification, and many charge points, stop at milliseconds.
  public HeartbeatResponse {
    currentTime = currentTime.truncatedTo(ChronoUnit.MILLIS);
  }
}
