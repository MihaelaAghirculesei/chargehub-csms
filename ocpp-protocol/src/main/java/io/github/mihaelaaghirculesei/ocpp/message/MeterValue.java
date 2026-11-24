package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import java.util.List;

/**
 * The readings a charge point took at one moment.
 *
 * @param timestamp by the clock of the charge point
 */
public record MeterValue(Instant timestamp, List<SampledValue> sampledValue) {}
