package io.github.mihaelaaghirculesei.csms.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MeterReadingTest {

  private static final Instant SAMPLED_AT = Instant.parse("2026-11-24T10:00:00Z");

  @Test
  void aBareValueIsAnEnergyRegisterInWh() {
    assertThat(MeterReading.of(SAMPLED_AT, "1500", null, null, null, null, null, null))
        .isEqualTo(
            new MeterReading(
                SAMPLED_AT,
                "1500",
                "Energy.Active.Import.Register",
                "Wh",
                null,
                "Sample.Periodic",
                "Outlet",
                "Raw"));
  }

  @Test
  void keepsEveryFieldTheChargePointSent() {
    assertThat(
            MeterReading.of(
                SAMPLED_AT, "230.4", "Voltage", "V", "L1-N", "Sample.Clock", "Inlet", "Raw"))
        .isEqualTo(
            new MeterReading(
                SAMPLED_AT, "230.4", "Voltage", "V", "L1-N", "Sample.Clock", "Inlet", "Raw"));
  }
}
