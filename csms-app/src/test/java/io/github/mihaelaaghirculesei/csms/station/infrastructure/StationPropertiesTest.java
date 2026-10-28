package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StationPropertiesTest {

  private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
  private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);

  @Test
  void aStationMayMissTheConfiguredNumberOfHeartbeats() {
    StationProperties properties = new StationProperties(FIVE_MINUTES, THIRTY_SECONDS, 3);

    assertThat(properties.silenceLimit()).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void refusesToTakeAStationOfflineBeforeItMissedAnyHeartbeat() {
    assertThatThrownBy(() -> new StationProperties(FIVE_MINUTES, THIRTY_SECONDS, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
