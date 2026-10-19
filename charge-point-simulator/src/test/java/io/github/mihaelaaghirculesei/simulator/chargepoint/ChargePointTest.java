package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.simulator.FakeCentralSystem;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChargePointTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

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
  void stopsAtOnceWhenTheConnectionCloses() throws Exception {
    centralSystem.answerBootsWith(300);
    run();
    nextCall();

    centralSystem.disconnect();

    Thread chargePoint = Objects.requireNonNull(running);
    chargePoint.join(WAIT);
    assertThat(chargePoint.isAlive()).isFalse();
    assertThat(failure.get()).isInstanceOf(SessionFailedException.class);
  }

  private Call nextCall() throws InterruptedException {
    return (Call) centralSystem.nextFrame(WAIT);
  }

  // Never on the test thread: a charge point that fails to stop would hang the build.
  private void run() {
    ChargePoint chargePoint = new ChargePoint(CLIENT, centralSystem.url(), "CP-TEST", WAIT);
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
