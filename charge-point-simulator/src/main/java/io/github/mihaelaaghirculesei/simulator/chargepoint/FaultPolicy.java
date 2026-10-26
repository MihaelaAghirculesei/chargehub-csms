package io.github.mihaelaaghirculesei.simulator.chargepoint;

import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * The faults a charge point produces on purpose, so the central system is tested against them.
 *
 * @param meanTimeBetweenDrops each connection drops after a random time with this mean; null never
 *     drops
 * @param duplicateCallProbability the share of calls sent twice with the same message id, from 0 to
 *     1
 */
public record FaultPolicy(
    @Nullable Duration meanTimeBetweenDrops, double duplicateCallProbability) {

  public static final FaultPolicy NONE = new FaultPolicy(null, 0);

  public FaultPolicy {
    if (meanTimeBetweenDrops != null && !meanTimeBetweenDrops.isPositive()) {
      throw new IllegalArgumentException("meanTimeBetweenDrops must be positive");
    }
    if (!(duplicateCallProbability >= 0 && duplicateCallProbability <= 1)) {
      throw new IllegalArgumentException(
          "duplicateCallProbability must be between 0 and 1, got " + duplicateCallProbability);
    }
  }

  /**
   * How long the next connection lasts before it drops, if drops are on.
   *
   * <p>The time is exponential, as for failures that strike at random: how long a connection has
   * lasted says nothing about how long it still has.
   */
  public Optional<Duration> nextDrop(RandomGenerator random) {
    if (meanTimeBetweenDrops == null) {
      return Optional.empty();
    }
    // The cast saturates, so even a very long mean cannot overflow.
    return Optional.of(
        Duration.ofMillis((long) (meanTimeBetweenDrops.toMillis() * random.nextExponential())));
  }

  /** Whether the next call is sent twice. */
  public boolean duplicatesNextCall(RandomGenerator random) {
    return random.nextDouble() < duplicateCallProbability;
  }
}
