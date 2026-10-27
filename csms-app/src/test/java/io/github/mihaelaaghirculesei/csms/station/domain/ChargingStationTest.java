package io.github.mihaelaaghirculesei.csms.station.domain;

import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.AVAILABLE;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.CHARGING;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.FAULTED;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.FINISHING;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.PREPARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Applied;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Rejected;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Stale;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Unchanged;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChargingStationTest {

  private static final Instant T0 = Instant.parse("2026-10-26T10:00:00Z");
  private static final Instant T1 = T0.plusSeconds(1);
  private static final Instant T2 = T0.plusSeconds(2);

  private static final StationDetails DETAILS =
      new StationDetails("VendorX", "SingleSocketCharger", "SN-1", "1.0.0");

  private final ChargingStation station = ChargingStation.register("CP-1", DETAILS, T0);

  @Test
  void keepsWhatTheStationSaidAtItsFirstBoot() {
    assertThat(station.id()).isEqualTo("CP-1");
    assertThat(station.details()).isEqualTo(DETAILS);
    assertThat(station.lastBootAt()).isEqualTo(T0);
    assertThat(station.lastSeenAt()).isEqualTo(T0);
    assertThat(station.online()).isTrue();
    assertThat(station.connectors()).isEmpty();
  }

  @Test
  void anOnlineStationSeenAgainOnlyMovesTheTime() {
    assertThat(station.seen(T1)).isEmpty();

    assertThat(station.lastSeenAt()).isEqualTo(T1);
    assertThat(station.online()).isTrue();
  }

  @Test
  void anOfflineStationSeenAgainComesOnline() {
    ChargingStation offline = ChargingStation.restore("CP-1", DETAILS, T0, T0, false, List.of());

    assertThat(offline.seen(T2)).contains(new StationCameOnline("CP-1", T2));
    assertThat(offline.online()).isTrue();
    assertThat(offline.lastSeenAt()).isEqualTo(T2);
  }

  @Test
  void neverMovesTheLastSeenTimeBack() {
    station.seen(T2);

    station.seen(T1);

    assertThat(station.lastSeenAt()).isEqualTo(T2);
  }

  @Test
  void aNewBootReplacesTheDetailsAndKeepsTheConnectors() {
    station.report(1, CHARGING, T0);
    StationDetails updated = new StationDetails("VendorX", "SingleSocketCharger", "SN-1", "1.1.0");

    station.boot(updated, T2);

    assertThat(station.details()).isEqualTo(updated);
    assertThat(station.lastBootAt()).isEqualTo(T2);
    assertThat(station.connector(1)).contains(new Connector(1, CHARGING, T0));
  }

  @Test
  void restoresTheConnectorsAsTheyWereSavedInIdOrder() {
    List<Connector> saved = List.of(new Connector(2, FINISHING, T1), new Connector(0, FAULTED, T0));

    ChargingStation restored = ChargingStation.restore("CP-1", DETAILS, T0, T1, false, saved);

    assertThat(restored.details()).isEqualTo(DETAILS);
    assertThat(restored.lastBootAt()).isEqualTo(T0);
    assertThat(restored.lastSeenAt()).isEqualTo(T1);
    assertThat(restored.online()).isFalse();
    assertThat(restored.connectors()).containsExactly(saved.get(1), saved.get(0));
  }

  @Test
  void takesAnyStatusForAConnectorSeenForTheFirstTime() {
    ReportOutcome outcome = station.report(1, CHARGING, T0);

    assertThat(outcome)
        .isEqualTo(new Applied(new ConnectorStatusChanged("CP-1", 1, null, CHARGING, T0)));
    assertThat(station.connector(1)).contains(new Connector(1, CHARGING, T0));
  }

  @Test
  void appliesAChangeTheSpecificationAllows() {
    station.report(1, AVAILABLE, T0);

    ReportOutcome outcome = station.report(1, PREPARING, T1);

    assertThat(outcome)
        .isEqualTo(new Applied(new ConnectorStatusChanged("CP-1", 1, AVAILABLE, PREPARING, T1)));
    assertThat(station.connector(1)).contains(new Connector(1, PREPARING, T1));
  }

  @Test
  void keepsTheStatusWhenTheSpecificationDoesNotAllowTheChange() {
    station.report(1, AVAILABLE, T0);

    ReportOutcome outcome = station.report(1, FINISHING, T1);

    assertThat(outcome).isEqualTo(new Rejected(1, AVAILABLE, FINISHING));
    assertThat(station.connector(1)).contains(new Connector(1, AVAILABLE, T0));
  }

  @Test
  void onlyMovesTheTimeWhenTheStatusIsTheSame() {
    station.report(1, AVAILABLE, T0);

    ReportOutcome outcome = station.report(1, AVAILABLE, T1);

    assertThat(outcome).isEqualTo(new Unchanged(new Connector(1, AVAILABLE, T1)));
    assertThat(station.connector(1)).contains(new Connector(1, AVAILABLE, T1));
  }

  @Test
  void takesTheSameReportTwiceAsUnchanged() {
    station.report(1, PREPARING, T0);

    ReportOutcome outcome = station.report(1, PREPARING, T0);

    assertThat(outcome).isEqualTo(new Unchanged(new Connector(1, PREPARING, T0)));
  }

  @Test
  void ignoresAReportOlderThanTheLastOneTaken() {
    station.report(1, AVAILABLE, T0);
    station.report(1, PREPARING, T2);

    ReportOutcome outcome = station.report(1, AVAILABLE, T1);

    assertThat(outcome).isEqualTo(new Stale(new Connector(1, PREPARING, T2), T1));
    assertThat(station.connector(1)).contains(new Connector(1, PREPARING, T2));
  }

  @Test
  void aRejectedReportDoesNotMoveTheTimeOfTheLastReport() {
    station.report(1, AVAILABLE, T1);
    station.report(1, FINISHING, T2);

    ReportOutcome outcome = station.report(1, PREPARING, T1);

    assertThat(outcome).isInstanceOf(Applied.class);
  }

  @Test
  void theWholeStationTakesOnlyItsOwnStatuses() {
    assertThat(station.report(Connector.WHOLE_STATION, CHARGING, T0))
        .isEqualTo(new Rejected(Connector.WHOLE_STATION, null, CHARGING));
    assertThat(station.report(Connector.WHOLE_STATION, FAULTED, T0)).isInstanceOf(Applied.class);
  }

  @Test
  void listsTheKnownConnectorsById() {
    station.report(2, AVAILABLE, T0);
    station.report(Connector.WHOLE_STATION, AVAILABLE, T0);
    station.report(1, CHARGING, T0);

    assertThat(station.connectors()).extracting(Connector::id).containsExactly(0, 1, 2);
    assertThat(station.connector(3)).isEmpty();
  }

  @Test
  void refusesValuesThatCannotComeFromAChargePoint() {
    assertThatThrownBy(() -> ChargingStation.register(" ", DETAILS, T0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StationDetails(" ", "Model", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StationDetails("Vendor", "", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> station.report(-1, AVAILABLE, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
