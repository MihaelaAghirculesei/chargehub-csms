package io.github.mihaelaaghirculesei.csms.session.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An energy register reading lower than the one before it by station time, or than meterStart. The
 * reading is kept: it points to a meter fault, a replaced meter or a wrong unit, which someone has
 * to check before billing.
 *
 * @param sampledAt by the clock of the charge point
 * @param phase null for the register of the whole connector
 * @param energyWh the reading, in Wh whatever unit it came in
 * @param previousWh the reading before it, or meterStart
 */
public record RegisterDrop(
    Instant sampledAt, @Nullable String phase, BigDecimal energyWh, BigDecimal previousWh) {}
