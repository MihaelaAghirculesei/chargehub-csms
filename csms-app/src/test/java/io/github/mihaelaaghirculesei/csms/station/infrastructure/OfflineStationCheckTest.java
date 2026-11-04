package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import io.github.mihaelaaghirculesei.csms.station.domain.StationWentOffline;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The offline check against PostgreSQL. Tests call it directly, since the test configuration keeps
 * the scheduled run from firing. Tests share one database, so each one uses its own station ids.
 */
@SpringBootTest
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, OfflineStationCheckTest.HeartbeatDuringTheCheck.class})
class OfflineStationCheckTest {

  private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
  // With the defaults a station may stay silent for three heartbeats of 300 seconds.
  private static final Instant SILENT_SINCE = NOW.minus(Duration.ofMinutes(16));
  private static final Instant LATE_SINCE = NOW.minus(Duration.ofMinutes(14));
  private static final StationDetails DETAILS =
      new StationDetails("VendorX", "SingleSocketCharger", null, null);

  @Autowired private OfflineStationCheck check;
  @Autowired private ChargingStationRepository stations;
  @Autowired private AdvisoryLocks locks;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ApplicationEvents events;

  // Only declared to replace the Clock bean with clock() below; the test never reads it.
  @SuppressWarnings("UnusedVariable")
  @TestBean
  private Clock clock;

  static Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  @Test
  void takesAsOfflineAStationThatMissedTooManyHeartbeats() {
    inTransaction(() -> {
      stations.save(online("CP-SILENT", SILENT_SINCE));
      stations.save(online("CP-LATE", LATE_SINCE));
    });

    check.run();

    assertThat(load("CP-SILENT").online()).isFalse();
    assertThat(load("CP-LATE").online()).isTrue();
    assertThat(departures("CP-SILENT", "CP-LATE"))
        .containsExactly(new StationWentOffline("CP-SILENT", SILENT_SINCE, NOW));
  }

  @Test
  void checksEveryBatchInOneRun() {
    List<String> ids =
        IntStream.rangeClosed(0, OfflineStationCheck.BATCH_SIZE)
            .mapToObj(i -> "CP-MANY-" + i)
            .toList();
    inTransaction(() -> ids.forEach(id -> stations.save(online(id, SILENT_SINCE))));

    check.run();

    assertThat(ids).allSatisfy(id -> assertThat(load(id).online()).isFalse());
  }

  @Test
  void doesNothingWhileAnotherInstanceHoldsTheLock() {
    inTransaction(() -> stations.save(online("CP-LOCKED", SILENT_SINCE)));

    inTransaction(() -> {
      assertThat(locks.tryLockForTransaction(OfflineStationCheck.LOCK_KEY)).isTrue();
      // On another thread, so that the check runs in a transaction of its own.
      CompletableFuture.runAsync(check::run).orTimeout(10, TimeUnit.SECONDS).join();
    });

    assertThat(load("CP-LOCKED").online()).isTrue();
    assertThat(departures("CP-LOCKED")).isEmpty();
  }

  @Test
  void leavesOnlineAStationSeenWhileTheCheckRuns() {
    inTransaction(() -> stations.save(online(HeartbeatDuringTheCheck.STATION, SILENT_SINCE)));

    check.run();

    ChargingStation station = load(HeartbeatDuringTheCheck.STATION);
    assertThat(station.online()).isTrue();
    assertThat(station.lastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void runsOnTheConfiguredInterval(@Autowired ScheduledTaskHolder scheduler) {
    assertThat(scheduler.getScheduledTasks())
        .extracting(ScheduledTask::getTask)
        .filteredOn(FixedDelayTask.class::isInstance)
        .extracting(task -> ((FixedDelayTask) task).getIntervalDuration())
        .contains(Duration.ofDays(1));
  }

  private static ChargingStation online(String id, Instant lastSeenAt) {
    return ChargingStation.restore(id, DETAILS, lastSeenAt, lastSeenAt, true, List.of());
  }

  private List<StationWentOffline> departures(String... stationIds) {
    List<String> ids = List.of(stationIds);
    return events.stream(StationWentOffline.class)
        .filter(departure -> ids.contains(departure.stationId()))
        .toList();
  }

  private ChargingStation load(String id) {
    return new TransactionTemplate(transactionManager)
        .execute(status -> stations.findById(id).orElseThrow());
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  /**
   * Plays a heartbeat that commits after the check read the station and before it saved it: the
   * race that optimistic locking settles in favour of the heartbeat.
   */
  @TestComponent
  static class HeartbeatDuringTheCheck {

    static final String STATION = "CP-RACE";

    private final ChargingStationRepository stations;
    private final TransactionTemplate newTransaction;

    HeartbeatDuringTheCheck(
        ChargingStationRepository stations, PlatformTransactionManager transactionManager) {
      this.stations = stations;
      this.newTransaction = new TransactionTemplate(transactionManager);
      newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    void on(StationWentOffline departure) {
      if (departure.stationId().equals(STATION)) {
        newTransaction.executeWithoutResult(status -> {
          ChargingStation station = stations.findById(STATION).orElseThrow();
          station.seen(NOW);
          stations.save(station);
        });
      }
    }
  }
}
