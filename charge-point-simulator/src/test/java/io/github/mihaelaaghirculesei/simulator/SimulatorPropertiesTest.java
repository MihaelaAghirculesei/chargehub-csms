package io.github.mihaelaaghirculesei.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.simulator.SimulatorProperties.ChargePoints;
import io.github.mihaelaaghirculesei.simulator.SimulatorProperties.Faults;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SimulatorPropertiesTest {

  @Test
  void numbersTheChargePointsAfterThePrefix() {
    ChargePoints chargePoints = new ChargePoints(3, "SIM-", 2, Duration.ZERO);

    assertThat(chargePoints.ids()).containsExactly("SIM-0001", "SIM-0002", "SIM-0003");
  }

  @Test
  void refusesASimulatorWithoutChargePoints() {
    Duration none = Duration.ZERO;

    assertThatThrownBy(() -> new ChargePoints(0, "SIM-", 2, none))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAChargePointWithoutConnectors() {
    Duration none = Duration.ZERO;

    assertThatThrownBy(() -> new ChargePoints(1, "SIM-", 0, none))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsEachClockSkewWithinTheConfiguredRange() {
    Duration minimum = Duration.ofMinutes(-5);
    Duration maximum = Duration.ofMinutes(5);
    Faults faults = new Faults(null, 0, 0, minimum, maximum);
    RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

    List<Duration> skews = Stream.generate(() -> faults.clockSkew(random)).limit(1_000).toList();

    assertThat(skews)
        .allMatch(skew -> skew.compareTo(minimum) >= 0 && skew.compareTo(maximum) <= 0);
    assertThat(skews).anyMatch(Duration::isNegative);
    assertThat(skews).anyMatch(Duration::isPositive);
  }

  @Test
  void usesTheExactClockSkewWhenTheRangeIsOneValue() {
    Duration fixed = Duration.ofSeconds(90);
    Faults faults = new Faults(null, 0, 0, fixed, fixed);

    assertThat(faults.clockSkew(RandomGenerator.getDefault())).isEqualTo(fixed);
  }

  @Test
  void refusesAClockSkewRangeThatIsUpsideDown() {
    Duration minimum = Duration.ofMinutes(5);
    Duration maximum = Duration.ofMinutes(-5);

    assertThatThrownBy(() -> new Faults(null, 0, 0, minimum, maximum))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesANegativeStartSpread() {
    Duration negative = Duration.ofSeconds(-1);

    assertThatThrownBy(() -> new ChargePoints(1, "SIM-", 2, negative))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
