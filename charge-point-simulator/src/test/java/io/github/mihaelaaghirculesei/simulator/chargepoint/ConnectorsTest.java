package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.ocpp.message.ChargePointErrorCode;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ConnectorsTest {

  private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
  private static final FaultPolicy ALWAYS_FAULTS = new FaultPolicy(null, 0, 1);

  private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

  @Test
  void startsWithEveryConnectorAvailableAndNumberedFromOne() {
    List<StatusNotificationRequest> reports = new Connectors(2).reports(NOW);

    assertThat(reports).extracting(StatusNotificationRequest::connectorId).containsExactly(1, 2);
    assertThat(reports)
        .allSatisfy(report -> {
          assertThat(report.status()).isEqualTo(ChargePointStatus.AVAILABLE);
          assertThat(report.errorCode()).isEqualTo(ChargePointErrorCode.NO_ERROR);
          assertThat(report.timestamp()).isEqualTo(NOW);
        });
  }

  @Test
  void changesNothingWithoutFaults() {
    assertThat(new Connectors(2).advance(FaultPolicy.NONE, random, NOW)).isEmpty();
  }

  @Test
  void faultsWithAnErrorCodeThenGoesBackToAvailable() {
    Connectors connectors = new Connectors(1);

    StatusNotificationRequest faulted = connectors.advance(ALWAYS_FAULTS, random, NOW).getFirst();
    assertThat(faulted.status()).isEqualTo(ChargePointStatus.FAULTED);
    assertThat(faulted.errorCode()).isNotEqualTo(ChargePointErrorCode.NO_ERROR);

    StatusNotificationRequest recovered = connectors.advance(ALWAYS_FAULTS, random, NOW).getFirst();
    assertThat(recovered.status()).isEqualTo(ChargePointStatus.AVAILABLE);
    assertThat(recovered.errorCode()).isEqualTo(ChargePointErrorCode.NO_ERROR);
  }

  @Test
  void reportsTheCurrentStatusAgainAfterAFault() {
    Connectors connectors = new Connectors(1);
    StatusNotificationRequest faulted = connectors.advance(ALWAYS_FAULTS, random, NOW).getFirst();

    assertThat(connectors.reports(NOW)).containsExactly(faulted);
  }

  @Test
  void givesTheFirstConnectorThatWorksAndHasNoSession() {
    Connectors connectors = new Connectors(3);
    assertThat(connectors.free()).hasValue(1);

    connectors.use(1, ChargePointStatus.CHARGING, NOW);
    assertThat(connectors.free()).hasValue(2);

    connectors.advance(ALWAYS_FAULTS, random, NOW);
    assertThat(connectors.free()).isEmpty();

    connectors.release(1, NOW);
    assertThat(connectors.free()).hasValue(1);
  }

  @Test
  void reportsTheStatusOfTheSessionUntilItIsReleased() {
    Connectors connectors = new Connectors(2);

    assertThat(connectors.use(2, ChargePointStatus.CHARGING, NOW).status())
        .isEqualTo(ChargePointStatus.CHARGING);
    assertThat(connectors.reports(NOW))
        .extracting(StatusNotificationRequest::status)
        .containsExactly(ChargePointStatus.AVAILABLE, ChargePointStatus.CHARGING);
    assertThat(connectors.release(2, NOW).status()).isEqualTo(ChargePointStatus.AVAILABLE);
  }

  @Test
  void neverFaultsAConnectorInASession() {
    Connectors connectors = new Connectors(1);
    connectors.use(1, ChargePointStatus.CHARGING, NOW);

    assertThat(connectors.advance(ALWAYS_FAULTS, random, NOW)).isEmpty();
    assertThat(connectors.reports(NOW).getFirst().status()).isEqualTo(ChargePointStatus.CHARGING);
  }

  @Test
  void keepsTheEnergyOfEachConnectorAndNeverLetsItGoBack() {
    Connectors connectors = new Connectors(2);

    connectors.deliver(1, 1500);
    connectors.deliver(1, 250);

    assertThat(connectors.register(1)).isEqualTo(1750);
    assertThat(connectors.register(2)).isZero();
    assertThatThrownBy(() -> connectors.deliver(1, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
