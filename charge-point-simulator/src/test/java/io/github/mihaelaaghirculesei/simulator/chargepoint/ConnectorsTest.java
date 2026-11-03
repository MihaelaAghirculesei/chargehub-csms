package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;

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
        .allSatisfy(
            report -> {
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
}
