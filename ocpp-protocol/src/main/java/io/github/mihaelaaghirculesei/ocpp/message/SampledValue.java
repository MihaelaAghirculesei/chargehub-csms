package io.github.mihaelaaghirculesei.ocpp.message;

import org.jspecify.annotations.Nullable;

/**
 * One reading of a meter. The optional fields keep the names of the specification; a missing one
 * has the default the specification gives it.
 *
 * @param value a number for raw readings, or signed data the central system stores as it came
 */
public record SampledValue(
    String value,
    @Nullable String context,
    @Nullable String format,
    @Nullable String measurand,
    @Nullable String phase,
    @Nullable String location,
    @Nullable String unit) {}
