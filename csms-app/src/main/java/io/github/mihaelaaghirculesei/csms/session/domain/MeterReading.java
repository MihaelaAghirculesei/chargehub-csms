package io.github.mihaelaaghirculesei.csms.session.domain;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One reading a charge point took during a session. Measurand, unit, context, location and format
 * keep the names of OCPP 1.6: the schema has already checked them, and the central system stores
 * them without acting on most of them.
 *
 * @param sampledAt by the clock of the charge point
 * @param receivedAt by the clock of the central system
 * @param value as the charge point sent it: a number, or signed data when the format says so
 * @param phase null when the reading is not tied to a phase
 */
public record MeterReading(
    Instant sampledAt,
    Instant receivedAt,
    String value,
    String measurand,
    String unit,
    @Nullable String phase,
    String context,
    String location,
    String format) {

  public static final String DEFAULT_MEASURAND = "Energy.Active.Import.Register";
  public static final String DEFAULT_UNIT = "Wh";
  public static final String DEFAULT_CONTEXT = "Sample.Periodic";
  public static final String DEFAULT_LOCATION = "Outlet";
  public static final String DEFAULT_FORMAT = "Raw";

  /**
   * Fills a missing field with the default of the specification, so that a stored reading says what
   * it means without the reader knowing those defaults.
   */
  public static MeterReading of(
      Instant sampledAt,
      Instant receivedAt,
      String value,
      @Nullable String measurand,
      @Nullable String unit,
      @Nullable String phase,
      @Nullable String context,
      @Nullable String location,
      @Nullable String format) {
    return new MeterReading(
        sampledAt,
        receivedAt,
        value,
        Objects.requireNonNullElse(measurand, DEFAULT_MEASURAND),
        Objects.requireNonNullElse(unit, DEFAULT_UNIT),
        phase,
        Objects.requireNonNullElse(context, DEFAULT_CONTEXT),
        Objects.requireNonNullElse(location, DEFAULT_LOCATION),
        Objects.requireNonNullElse(format, DEFAULT_FORMAT));
  }
}
