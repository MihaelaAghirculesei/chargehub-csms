package io.github.mihaelaaghirculesei.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.simulator.SimulatorProperties.ChargePoints;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SimulatorPropertiesTest {

  @Test
  void numbersTheChargePointsAfterThePrefix() {
    ChargePoints chargePoints = new ChargePoints(3, "SIM-", Duration.ZERO);

    assertThat(chargePoints.ids()).containsExactly("SIM-0001", "SIM-0002", "SIM-0003");
  }

  @Test
  void refusesASimulatorWithoutChargePoints() {
    Duration none = Duration.ZERO;

    assertThatThrownBy(() -> new ChargePoints(0, "SIM-", none))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesANegativeStartSpread() {
    Duration negative = Duration.ofSeconds(-1);

    assertThatThrownBy(() -> new ChargePoints(1, "SIM-", negative))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
