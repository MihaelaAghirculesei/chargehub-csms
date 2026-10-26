package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FaultPolicyTest {

  // Seeded, so the averages below are the same on every run.
  private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

  @Test
  void neverDropsByDefault() {
    assertThat(FaultPolicy.NONE.nextDrop(random)).isEmpty();
  }

  @Test
  void dropsAfterTheMeanTimeOnAverage() {
    FaultPolicy faults = new FaultPolicy(Duration.ofSeconds(10));

    double averageMillis =
        IntStream.range(0, 10_000)
            .mapToLong(i -> faults.nextDrop(random).orElseThrow().toMillis())
            .average()
            .orElseThrow();

    assertThat(averageMillis).isCloseTo(10_000, within(500.0));
  }

  @Test
  void refusesAMeanTimeThatIsNotPositive() {
    Duration zero = Duration.ZERO;

    assertThatThrownBy(() -> new FaultPolicy(zero)).isInstanceOf(IllegalArgumentException.class);
  }
}
