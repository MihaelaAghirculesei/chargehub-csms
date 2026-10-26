package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.simulator.FakeCentralSystem;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChargePointTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  // Short and without a random part, so the tests see the reconnection quickly.
  private static final ReconnectPolicy QUICK_RECONNECT =
      new ReconnectPolicy(Duration.ofMillis(50), Duration.ZERO, 0);

  private final FakeCentralSystem centralSystem = FakeCentralSystem.start();
  private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
  private @Nullable Thread running;

  @AfterEach
  void stop() throws InterruptedException {
    if (running != null) {
      running.interrupt();
      running.join(WAIT);
    }
    centralSystem.close();
  }

  @AfterAll
  static void closeClient() {
    CLIENT.close();
  }

  @Test
  void bootsThenSendsHeartbeatsAtTheIntervalFromTheResponse() throws Exception {
    centralSystem.answerBootsWith(1);
    run();

    Call boot = nextCall();
    assertThat(boot.action()).isEqualTo("BootNotification");
    assertThat(boot.payload().path("chargePointVendor").stringValue()).isEqualTo("ChargeHub");
    assertThat(nextCall().action()).isEqualTo("Heartbeat");
    long first = System.nanoTime();
    assertThat(nextCall().action()).isEqualTo("Heartbeat");
    assertThat(Duration.ofNanos(System.nanoTime() - first)).isGreaterThan(Duration.ofMillis(900));
  }

  @Test
  void bootsAgainUntilAccepted() throws Exception {
    centralSystem.answerBootsWith(1, RegistrationStatus.PENDING, RegistrationStatus.REJECTED);
    run();

    assertThat(nextCall().action()).isEqualTo("BootNotification");
    assertThat(nextCall().action()).isEqualTo("BootNotification");
    assertThat(nextCall().action()).isEqualTo("BootNotification");
    assertThat(nextCall().action()).isEqualTo("Heartbeat");
  }

  @Test
  void waitsItsOwnIntervalWhenTheCentralSystemSendsZero() throws Exception {
    centralSystem.answerBootsWith(0);
    run();
    nextCall();

    Duration quietPeriod = Duration.ofSeconds(2);
    assertThatThrownBy(() -> centralSystem.nextFrame(quietPeriod))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void connectsAgainAtOnceWhenTheConnectionIsLost() throws Exception {
    centralSystem.answerBootsWith(300);
    run();
    nextCall();

    centralSystem.disconnect();

    // The heartbeat interval is 300s: a boot within the wait shows the loss was noticed at once.
    assertThat(nextCall().action()).isEqualTo("BootNotification");
    assertThat(failure.get()).isNull();
  }

  @Test
  void keepsTryingUntilTheCentralSystemIsBackThenStartsTheBackOffOver() throws Exception {
    URI url = centralSystem.url();
    centralSystem.close();
    ChargePoint chargePoint =
        new ChargePoint(
            CLIENT,
            url,
            "CP-TEST",
            WAIT,
            new ReconnectPolicy(Duration.ofMillis(10), Duration.ZERO, 3),
            FaultPolicy.NONE);
    run(chargePoint);
    await().atMost(WAIT).until(() -> chargePoint.failedAttempts() >= 3);

    try (FakeCentralSystem restarted = FakeCentralSystem.start(url.getPort())) {
      restarted.answerBootsWith(300);

      assertThat(((Call) restarted.nextFrame(WAIT)).action()).isEqualTo("BootNotification");
      await().atMost(WAIT).until(() -> chargePoint.failedAttempts() == 0);
      assertThat(failure.get()).isNull();
    }
  }

  @Test
  void dropsItsConnectionAtRandomTimesAndConnectsAgain() throws Exception {
    centralSystem.answerBootsWith(300);
    run(new FaultPolicy(Duration.ofMillis(200), 0));

    // The heartbeat interval is 300s: every boot after the first one follows a drop.
    for (int boot = 0; boot < 3; boot++) {
      assertThat(nextCall().action()).isEqualTo("BootNotification");
    }
    assertThat(failure.get()).isNull();
  }

  @Test
  void sendsEveryCallTwiceWhenAlwaysDuplicating() throws Exception {
    centralSystem.answerBootsWith(1);
    run(new FaultPolicy(null, 1));

    for (String action : List.of("BootNotification", "Heartbeat")) {
      Call first = nextCall();
      Call second = nextCall();
      assertThat(first.action()).isEqualTo(action);
      assertThat(second).isEqualTo(first);
    }
    assertThat(failure.get()).isNull();
  }

  @Test
  void stopsWhenItsThreadIsInterrupted() throws Exception {
    centralSystem.answerBootsWith(300);
    run();
    nextCall();

    Thread chargePoint = Objects.requireNonNull(running);
    chargePoint.interrupt();
    chargePoint.join(WAIT);

    assertThat(chargePoint.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
  }

  private Call nextCall() throws InterruptedException {
    return (Call) centralSystem.nextFrame(WAIT);
  }

  // Never on the test thread: a charge point that fails to stop would hang the build.
  private void run() {
    run(FaultPolicy.NONE);
  }

  private void run(FaultPolicy faults) {
    run(new ChargePoint(CLIENT, centralSystem.url(), "CP-TEST", WAIT, QUICK_RECONNECT, faults));
  }

  private void run(ChargePoint chargePoint) {
    running =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    chargePoint.run();
                  } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                  } catch (RuntimeException e) {
                    failure.set(e);
                  }
                });
  }
}
