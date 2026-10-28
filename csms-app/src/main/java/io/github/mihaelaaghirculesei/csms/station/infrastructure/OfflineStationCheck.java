package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Takes as offline the stations that missed too many heartbeats. Every instance of the central
 * system runs it, and an advisory lock lets only one of them act at a time (ADR 0015).
 */
@Component
class OfflineStationCheck {

  // Any number works as long as no other advisory lock of the application uses it.
  static final long LOCK_KEY = 1_001L;

  // Small enough to keep each transaction short, large enough to catch up quickly after an outage.
  static final int BATCH_SIZE = 100;

  private static final Logger log = LoggerFactory.getLogger(OfflineStationCheck.class);

  private final Clock clock;
  private final ChargingStationRepository stations;
  private final ApplicationEventPublisher events;
  private final AdvisoryLocks locks;
  private final TransactionTemplate transactions;
  private final Duration silenceLimit;

  OfflineStationCheck(
      Clock clock,
      ChargingStationRepository stations,
      ApplicationEventPublisher events,
      AdvisoryLocks locks,
      PlatformTransactionManager transactionManager,
      StationProperties properties) {
    this.clock = clock;
    this.stations = stations;
    this.events = events;
    this.locks = locks;
    this.transactions = new TransactionTemplate(transactionManager);
    this.silenceLimit = properties.silenceLimit();
  }

  /** Checks batch after batch, each in its own transaction, until a batch is not full. */
  void run() {
    try {
      int found;
      do {
        found = Objects.requireNonNull(transactions.execute(status -> checkOneBatch()));
      } while (found == BATCH_SIZE);
    } catch (OptimisticLockingFailureException e) {
      // A station was seen while its batch was checked. The batch rolled back; the next run
      // takes the other stations again.
      log.info("Offline check stopped by a concurrent change: {}", e.getMessage());
    }
  }

  private int checkOneBatch() {
    // Another instance is running the check right now and will handle these stations.
    if (!locks.tryLockForTransaction(LOCK_KEY)) {
      return 0;
    }
    Instant now = clock.instant();
    Instant cutoff = now.minus(silenceLimit);
    List<ChargingStation> silent = stations.findOnlineNotSeenSince(cutoff, BATCH_SIZE);
    for (ChargingStation station : silent) {
      station.markOfflineIfNotSeenSince(cutoff, now).ifPresent(events::publishEvent);
      stations.save(station);
    }
    if (!silent.isEmpty()) {
      log.info("Charging stations taken as offline: {}", silent.size());
    }
    return silent.size();
  }
}
