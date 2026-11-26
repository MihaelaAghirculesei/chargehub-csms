package io.github.mihaelaaghirculesei.simulator.chargepoint;

import java.time.Duration;

/**
 * How a simulated charge point charges, one session after the other.
 *
 * @param idTag the simulated RFID card presented at the start and the stop of each session
 * @param duration how long each session charges
 * @param idleTime the pause before the first session and after each one, and before trying again
 *     when the tag was rejected or no connector was free
 * @param powerWatts the constant power of the charging
 */
public record SessionPlan(String idTag, Duration duration, Duration idleTime, long powerWatts) {

  // CiString20Type in OCPP 1.6.
  private static final int ID_TAG_MAX_LENGTH = 20;

  public SessionPlan {
    if (idTag.isBlank() || idTag.length() > ID_TAG_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "idTag must have 1 to " + ID_TAG_MAX_LENGTH + " characters, got \"" + idTag + "\"");
    }
    if (!duration.isPositive() || !idleTime.isPositive()) {
      throw new IllegalArgumentException("duration and idleTime must be positive");
    }
    if (powerWatts <= 0) {
      throw new IllegalArgumentException("powerWatts must be positive, got " + powerWatts);
    }
  }

  /** The energy one session delivers, in whole Wh. */
  long energyWattHours() {
    return powerWatts * duration.toMillis() / Duration.ofHours(1).toMillis();
  }
}
