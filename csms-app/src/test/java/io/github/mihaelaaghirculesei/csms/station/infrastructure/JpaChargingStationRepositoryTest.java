package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.AVAILABLE;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.CHARGING;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.PREPARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.Connector;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Tests share one database, so each one uses its own station id.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaChargingStationRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-10-27T10:00:00.123456Z");
  private static final Instant T1 = T0.plusSeconds(1);
  private static final StationDetails DETAILS =
      new StationDetails("VendorX", "SingleSocketCharger", "SN-1", "1.0.0");

  @Autowired private ChargingStationRepository stations;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void savesAndLoadsAStationWithItsConnectors() {
    ChargingStation station = ChargingStation.register("CP-SAVE", DETAILS, T0);
    station.report(Connector.WHOLE_STATION, AVAILABLE, T0);
    station.report(1, CHARGING, T1);

    inTransaction(() -> stations.save(station));

    ChargingStation loaded = load("CP-SAVE");
    assertThat(loaded.details()).isEqualTo(DETAILS);
    assertThat(loaded.lastBootAt()).isEqualTo(T0);
    assertThat(loaded.connectors())
        .containsExactly(
            new Connector(Connector.WHOLE_STATION, AVAILABLE, T0), new Connector(1, CHARGING, T1));
  }

  @Test
  void savesTheChangesOfAStoredStation() {
    ChargingStation station = ChargingStation.register("CP-UPDATE", DETAILS, T0);
    station.report(1, AVAILABLE, T0);
    inTransaction(() -> stations.save(station));
    StationDetails updated = new StationDetails("VendorX", "SingleSocketCharger", "SN-1", "1.1.0");

    inTransaction(
        () -> {
          ChargingStation loaded = stations.findById("CP-UPDATE").orElseThrow();
          loaded.boot(updated, T1);
          loaded.report(1, PREPARING, T1);
          loaded.report(2, AVAILABLE, T1);
          stations.save(loaded);
        });

    ChargingStation loaded = load("CP-UPDATE");
    assertThat(loaded.details()).isEqualTo(updated);
    assertThat(loaded.lastBootAt()).isEqualTo(T1);
    assertThat(loaded.connectors())
        .containsExactly(new Connector(1, PREPARING, T1), new Connector(2, AVAILABLE, T1));
  }

  @Test
  void refusesToOverwriteAChangeMadeAfterTheStationWasLoaded() {
    inTransaction(() -> stations.save(ChargingStation.register("CP-RACE", DETAILS, T0)));

    assertThatThrownBy(
            () ->
                inTransaction(
                    () -> {
                      ChargingStation first = stations.findById("CP-RACE").orElseThrow();
                      inNewTransaction(
                          () -> {
                            ChargingStation second = stations.findById("CP-RACE").orElseThrow();
                            second.report(1, AVAILABLE, T1);
                            stations.save(second);
                          });
                      first.report(1, CHARGING, T1);
                      stations.save(first);
                    }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    ChargingStation loaded = load("CP-RACE");
    assertThat(loaded.connector(1)).contains(new Connector(1, AVAILABLE, T1));
  }

  private ChargingStation load(String id) {
    return new TransactionTemplate(transactionManager)
        .execute(status -> stations.findById(id).orElseThrow());
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  private void inNewTransaction(Runnable work) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> work.run());
  }
}
