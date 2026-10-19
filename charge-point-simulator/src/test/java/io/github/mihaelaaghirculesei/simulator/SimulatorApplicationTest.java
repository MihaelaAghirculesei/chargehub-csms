package io.github.mihaelaaghirculesei.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

// No web environment: the simulator is a WebSocket client and never serves HTTP.
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class SimulatorApplicationTest {

  @Test
  void contextLoads() {}
}
