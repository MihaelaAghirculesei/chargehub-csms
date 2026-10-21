package io.github.mihaelaaghirculesei.simulator.chargepoint;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * How long a charge point waits before it connects again. OCPP 1.6 leaves this open; the rule is
 * the one OCPP 2.0.1 later standardized with its RetryBackOff variables. The wait starts at {@code
 * waitMinimum} and doubles after each failed attempt, at most {@code repeatTimes} times. Every wait
 * gets a random part of up to {@code randomRange}, so charge points that lost the central system
 * together do not all come back at the same moment.
 */
public record ReconnectPolicy(Duration waitMinimum, Duration randomRange, int repeatTimes) {

  // Keeps the doubled wait far below the range of a long in milliseconds.
  static final int MAX_REPEAT_TIMES = 20;

  public ReconnectPolicy {
    if (waitMinimum.isNegative() || randomRange.isNegative()) {
      throw new IllegalArgumentException("Waits must not be negative");
    }
    if (repeatTimes < 0 || repeatTimes > MAX_REPEAT_TIMES) {
      throw new IllegalArgumentException(
          "repeatTimes must be between 0 and " + MAX_REPEAT_TIMES + ", got " + repeatTimes);
    }
  }

  /**
   * The wait before the next attempt to connect.
   *
   * @param failedAttempts how many attempts in a row have failed before this one
   */
  public Duration waitBefore(int failedAttempts, RandomGenerator random) {
    Duration base = waitMinimum.multipliedBy(1L << Math.min(failedAttempts, repeatTimes));
    long randomMillis = (long) (random.nextDouble() * (randomRange.toMillis() + 1));
    return base.plusMillis(randomMillis);
  }
}
