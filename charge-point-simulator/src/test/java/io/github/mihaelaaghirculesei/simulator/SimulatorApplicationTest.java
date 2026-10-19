package io.github.mihaelaaghirculesei.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The configured charge point starts with the application and connects with its own id. */
// No web environment: the simulator is a WebSocket client and never serves HTTP.
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class SimulatorApplicationTest {

  private static final FakeCentralSystem CENTRAL_SYSTEM = acceptingCentralSystem();

  @DynamicPropertySource
  static void centralSystem(DynamicPropertyRegistry registry) {
    registry.add("chargehub.simulator.central-system", CENTRAL_SYSTEM::url);
    registry.add("chargehub.simulator.charge-point-id", () -> "SIM-APP");
  }

  @AfterAll
  static void stopCentralSystem() {
    CENTRAL_SYSTEM.close();
  }

  @Test
  void connectsAndBootsTheConfiguredChargePoint() throws InterruptedException {
    Call boot = (Call) CENTRAL_SYSTEM.nextFrame(Duration.ofSeconds(5));

    assertThat(boot.action()).isEqualTo("BootNotification");
    assertThat(CENTRAL_SYSTEM.connectedChargePointId()).isEqualTo("SIM-APP");
  }

  private static FakeCentralSystem acceptingCentralSystem() {
    FakeCentralSystem centralSystem = FakeCentralSystem.start();
    centralSystem.answerBootsWith(300);
    return centralSystem;
  }
}
