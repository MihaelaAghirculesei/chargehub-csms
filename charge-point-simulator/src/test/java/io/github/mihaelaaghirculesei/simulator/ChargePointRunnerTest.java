package io.github.mihaelaaghirculesei.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargePointRunnerTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  private final HttpClient client = HttpClient.newHttpClient();
  private final FakeCentralSystem centralSystem = FakeCentralSystem.start();
  private @Nullable ChargePointRunner runner;

  @BeforeEach
  void acceptBoots() {
    centralSystem.answerBootsWith(300);
  }

  @AfterEach
  void close() {
    if (runner != null) {
      runner.stop();
    }
    centralSystem.close();
    client.close();
  }

  @Test
  void runsEveryChargePointUntilStopped() {
    ChargePointRunner started = runner(3);
    assertThat(started.isRunning()).isFalse();

    started.start();
    await()
        .atMost(WAIT)
        .until(
            centralSystem::connectedChargePointIds,
            Set.of("CP-RUN-1", "CP-RUN-2", "CP-RUN-3")::equals);
    assertThat(started.isRunning()).isTrue();

    started.stop();
    assertThat(started.isRunning()).isFalse();
    await().atMost(WAIT).until(() -> centralSystem.connectedChargePointIds().isEmpty());
  }

  @Test
  void keepsRunningWhenTheSessionFails() throws Exception {
    ChargePointRunner started = runner(1);
    started.start();
    centralSystem.nextFrame(WAIT);

    centralSystem.disconnect();

    assertThat(((Call) centralSystem.nextFrame(WAIT)).action()).isEqualTo("BootNotification");
    assertThat(started.isRunning()).isTrue();
  }

  private ChargePointRunner runner(int count) {
    List<ChargePoint> chargePoints =
        IntStream.rangeClosed(1, count)
            .mapToObj(
                n ->
                    new ChargePoint(
                        client,
                        centralSystem.url(),
                        "CP-RUN-" + n,
                        WAIT,
                        new ReconnectPolicy(Duration.ofMillis(50), Duration.ZERO, 0)))
            .toList();
    runner = new ChargePointRunner(chargePoints, Duration.ofMillis(200));
    return runner;
  }
}
