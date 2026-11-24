package io.github.mihaelaaghirculesei.csms.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClockGapTest {

  private static final Instant RECEIVED_AT = Instant.parse("2026-11-24T12:00:00Z");
  private static final Duration THRESHOLD = Duration.ofMinutes(5);

  @ParameterizedTest
  @CsvSource({
    "2026-11-24T12:00:00Z, ON_TIME",
    "2026-11-24T11:55:00Z, ON_TIME",
    "2026-11-24T12:05:00Z, ON_TIME",
    "2026-11-24T11:54:59Z, LATE",
    "2026-11-24T08:00:00Z, LATE",
    "2026-11-24T12:05:01Z, AHEAD",
    "2026-11-25T12:00:00Z, AHEAD"
  })
  void comparesTheStationTimeWithTheReceiveTime(Instant stationTime, ClockGap expected) {
    assertThat(ClockGap.between(stationTime, RECEIVED_AT, THRESHOLD)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"ON_TIME, false", "LATE, true", "AHEAD, true"})
  void flagsEveryGapAboveTheThreshold(ClockGap gap, boolean flagged) {
    assertThat(gap.flagged()).isEqualTo(flagged);
  }
}
