package io.github.mihaelaaghirculesei.csms.session.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * How the time a charge point gave a message compares with the time it was received, see
 * docs/adr/0017.
 */
public enum ClockGap {
  ON_TIME,
  /** Sent late after a lost connection, or taken by a clock that runs behind: they look alike. */
  LATE,
  /** Taken by a clock that runs ahead: no message arrives before it was sent. */
  AHEAD;

  public static ClockGap between(Instant stationTime, Instant receivedAt, Duration threshold) {
    if (stationTime.isAfter(receivedAt.plus(threshold))) {
      return AHEAD;
    }
    if (stationTime.isBefore(receivedAt.minus(threshold))) {
      return LATE;
    }
    return ON_TIME;
  }

  public boolean flagged() {
    return this != ON_TIME;
  }
}
