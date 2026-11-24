package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionPropertiesTest {

  // Without a positive threshold every session would be flagged.
  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void refusesAThresholdThatIsNotPositive(long seconds) {
    Duration threshold = Duration.ofSeconds(seconds);

    assertThatThrownBy(() -> new SessionProperties(threshold))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
