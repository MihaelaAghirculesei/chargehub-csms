package io.github.mihaelaaghirculesei.ocpp.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActionTest {

  @Test
  void coversTheTwentyEightActionsOfOcpp16() {
    assertThat(Action.values()).hasSize(28);
  }

  @Test
  void findsActionByItsCaseSensitiveWireName() {
    assertThat(Action.fromWireName("StartTransaction")).contains(Action.START_TRANSACTION);
    assertThat(Action.fromWireName("startTransaction")).isEmpty();
  }
}
