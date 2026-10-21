package io.github.mihaelaaghirculesei.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargePointRunnerTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  private final HttpClient client = HttpClient.newHttpClient();
  private final FakeCentralSystem centralSystem = FakeCentralSystem.start();
  private final ChargePointRunner runner =
      new ChargePointRunner(new ChargePoint(client, centralSystem.url(), "CP-RUN", WAIT));

  @BeforeEach
  void acceptBoots() {
    centralSystem.answerBootsWith(300);
  }

  @AfterEach
  void close() {
    runner.stop();
    centralSystem.close();
    client.close();
  }

  @Test
  void runsTheChargePointUntilStopped() throws Exception {
    assertThat(runner.isRunning()).isFalse();

    runner.start();
    centralSystem.nextFrame(WAIT);
    assertThat(runner.isRunning()).isTrue();

    runner.stop();
    assertThat(runner.isRunning()).isFalse();
  }

  @Test
  void endsWhenTheSessionFails() throws Exception {
    runner.start();
    centralSystem.nextFrame(WAIT);

    centralSystem.disconnect();

    await().atMost(WAIT).until(() -> !runner.isRunning());
  }
}
