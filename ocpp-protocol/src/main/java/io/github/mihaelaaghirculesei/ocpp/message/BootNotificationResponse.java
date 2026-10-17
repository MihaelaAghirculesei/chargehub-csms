package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * BootNotification.conf.
 *
 * @param interval seconds between heartbeats once accepted, or before the next BootNotification
 *     when pending or rejected
 */
public record BootNotificationResponse(
    RegistrationStatus status, Instant currentTime, int interval) {

  // The examples of the specification, and many charge points, stop at milliseconds.
  public BootNotificationResponse {
    currentTime = currentTime.truncatedTo(ChronoUnit.MILLIS);
  }
}
