package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.ClockGap;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs the clock gap of a start or stop, at a level that tells the two cases apart. */
final class ClockGaps {

  private static final Logger log = LoggerFactory.getLogger(ClockGaps.class);

  private ClockGaps() {}

  // A late message is normal after a lost connection; a time in the future is a wrong clock.
  static void report(
      ClockGap gap,
      String chargePointId,
      int transactionId,
      String message,
      Instant stationTime,
      Instant receivedAt) {
    switch (gap) {
      case ON_TIME -> {
        // Nothing to report.
      }
      case LATE ->
          log.atInfo()
              .addKeyValue("chargePointId", chargePointId)
              .addKeyValue("transactionId", transactionId)
              .log(
                  "Received the {} of transaction {} from {} late: station time {}, received {}",
                  message,
                  transactionId,
                  chargePointId,
                  stationTime,
                  receivedAt);
      case AHEAD ->
          log.atWarn()
              .addKeyValue("chargePointId", chargePointId)
              .addKeyValue("transactionId", transactionId)
              .log(
                  "The clock of {} runs ahead: the {} of transaction {} has station time {},"
                      + " received {}",
                  chargePointId,
                  message,
                  transactionId,
                  stationTime,
                  receivedAt);
    }
  }
}
