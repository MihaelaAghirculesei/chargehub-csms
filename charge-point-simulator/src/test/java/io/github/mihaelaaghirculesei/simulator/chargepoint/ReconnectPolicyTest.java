package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReconnectPolicyTest {

  // nextDouble is built from nextLong: 0 gives 0.0, all bits set gives the largest double below 1.
  private static final RandomGenerator LOWEST = () -> 0L;
  private static final RandomGenerator HIGHEST = () -> -1L;

  private final ReconnectPolicy policy =
      new ReconnectPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10), 3);

  @Test
  void waitsTheMinimumPlusUpToTheRandomRangeBeforeTheFirstAttempt() {
    assertThat(policy.waitBefore(0, LOWEST)).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.waitBefore(0, HIGHEST)).isEqualTo(Duration.ofSeconds(12));
  }

  @Test
  void doublesTheWaitAfterEachFailureUpToTheRepeatTimes() {
    assertThat(policy.waitBefore(1, LOWEST)).isEqualTo(Duration.ofSeconds(4));
    assertThat(policy.waitBefore(2, LOWEST)).isEqualTo(Duration.ofSeconds(8));
    assertThat(policy.waitBefore(3, LOWEST)).isEqualTo(Duration.ofSeconds(16));
    assertThat(policy.waitBefore(4, LOWEST)).isEqualTo(Duration.ofSeconds(16));
    assertThat(policy.waitBefore(Integer.MAX_VALUE, HIGHEST)).isEqualTo(Duration.ofSeconds(26));
  }

  @Test
  void spreadsChargePointsOverTheRandomRange() {
    RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

    long distinctWaits =
        IntStream.range(0, 100).mapToObj(i -> policy.waitBefore(0, random)).distinct().count();

    assertThat(distinctWaits).isGreaterThan(50);
  }

  @Test
  void refusesValuesThatMakeNoSense() {
    Duration second = Duration.ofSeconds(1);
    Duration negative = Duration.ofSeconds(-1);

    assertThatThrownBy(() -> new ReconnectPolicy(negative, second, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconnectPolicy(second, negative, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconnectPolicy(second, second, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconnectPolicy(second, second, 21))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
