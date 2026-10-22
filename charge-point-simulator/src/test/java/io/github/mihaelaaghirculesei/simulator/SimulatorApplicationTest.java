package io.github.mihaelaaghirculesei.simulator;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The configured charge points start with the application, each with its own id. */
// No web environment: the simulator is a WebSocket client and never serves HTTP.
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class SimulatorApplicationTest {

  private static final FakeCentralSystem CENTRAL_SYSTEM = acceptingCentralSystem();

  @DynamicPropertySource
  static void centralSystem(DynamicPropertyRegistry registry) {
    registry.add("chargehub.simulator.central-system", CENTRAL_SYSTEM::url);
    registry.add("chargehub.simulator.charge-points.count", () -> 2);
    registry.add("chargehub.simulator.charge-points.id-prefix", () -> "SIM-APP-");
    registry.add("chargehub.simulator.charge-points.start-spread", () -> "0s");
  }

  @AfterAll
  static void stopCentralSystem() {
    CENTRAL_SYSTEM.close();
  }

  @Test
  void connectsEveryConfiguredChargePoint() {
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            CENTRAL_SYSTEM::connectedChargePointIds,
            Set.of("SIM-APP-0001", "SIM-APP-0002")::equals);
  }

  private static FakeCentralSystem acceptingCentralSystem() {
    FakeCentralSystem centralSystem = FakeCentralSystem.start();
    centralSystem.answerBootsWith(300);
    return centralSystem;
  }
}
