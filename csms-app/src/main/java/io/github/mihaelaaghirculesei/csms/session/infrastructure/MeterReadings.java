package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.MeterReading;
import io.github.mihaelaaghirculesei.ocpp.message.MeterValue;
import java.util.List;

/** Turns the meter values of MeterValues and of StopTransaction into readings. */
final class MeterReadings {

  private MeterReadings() {}

  /** One reading per sampled value, each with the time of the meter value it belongs to. */
  static List<MeterReading> from(List<MeterValue> meterValues) {
    return meterValues.stream()
        .flatMap(meterValue ->
            meterValue.sampledValue().stream()
                .map(sample ->
                    MeterReading.of(
                        meterValue.timestamp(),
                        sample.value(),
                        sample.measurand(),
                        sample.unit(),
                        sample.phase(),
                        sample.context(),
                        sample.location(),
                        sample.format())))
        .toList();
  }
}
