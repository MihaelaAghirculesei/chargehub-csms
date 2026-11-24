package io.github.mihaelaaghirculesei.csms.session.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * A start as the charge point reported it.
 *
 * @param idTag in upper case
 * @param meterStart the energy register of the connector in Wh
 * @param startedAt by the clock of the charge point
 */
public record SessionStart(
    String stationId, int connectorId, String idTag, long meterStart, Instant startedAt) {

  // OCPP 1.6 compares id tags without regard to case; one stored form keeps the check for a tag
  // that is already charging a plain equality.
  public SessionStart {
    idTag = idTag.toUpperCase(Locale.ROOT);
  }
}
