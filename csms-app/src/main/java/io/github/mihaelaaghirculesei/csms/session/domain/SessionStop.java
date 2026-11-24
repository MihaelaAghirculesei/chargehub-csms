package io.github.mihaelaaghirculesei.csms.session.domain;

import io.github.mihaelaaghirculesei.csms.session.StopReason;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A stop as the charge point reported it.
 *
 * @param idTag the tag that stopped the session, in upper case, when a driver presented one; it can
 *     differ from the tag that started it, for example another card of the same group
 * @param meterStop the energy register of the connector in Wh
 * @param stoppedAt by the clock of the charge point
 * @param receivedAt by the clock of the central system
 */
public record SessionStop(
    @Nullable String idTag,
    long meterStop,
    Instant stoppedAt,
    StopReason reason,
    Instant receivedAt) {

  public SessionStop {
    if (idTag != null) {
      idTag = idTag.toUpperCase(Locale.ROOT);
    }
  }
}
