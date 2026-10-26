package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.mihaelaaghirculesei.ocpp.call.CallErrorException;
import io.github.mihaelaaghirculesei.ocpp.call.ConnectionClosedException;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationResponse;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.github.mihaelaaghirculesei.simulator.FakeCentralSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.node.JsonNodeFactory;

class CsmsConnectionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  // Longer than any wait of a test: a call that fails before it failed at once.
  private static final Duration LONG_TIMEOUT = Duration.ofSeconds(30);
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final BootNotificationRequest BOOT =
      new BootNotificationRequest("VendorX", "ModelY", null, null, null, null, null, null, null);

  private final FakeCentralSystem centralSystem = FakeCentralSystem.start();

  @AfterEach
  void stopCentralSystem() {
    centralSystem.close();
  }

  @AfterAll
  static void closeClient() {
    CLIENT.close();
  }

  @Test
  void connectsWithTheChargePointIdInThePath() throws Exception {
    URI withTrailingSlash = URI.create(centralSystem.url() + "/");

    try (CsmsConnection connection = CsmsConnection.open(CLIENT, withTrailingSlash, "CP-1", WAIT)) {
      assertThat(connection.awaitClosed(Duration.ofMillis(200))).isFalse();
      assertThat(centralSystem.connectedChargePointId()).isEqualTo("CP-1", WAIT);
    }
  }

  @Test
  void refusesACentralSystemThatDoesNotSpeakOcpp16() {
    try (FakeCentralSystem other = FakeCentralSystem.withoutOcpp16()) {
      URI url = other.url();
      assertThatThrownBy(() -> CsmsConnection.open(CLIENT, url, "CP-1", WAIT))
          .isInstanceOf(SessionFailedException.class)
          .hasMessageContaining("ocpp1.6");
    }
  }

  @Test
  void failsWhenTheCentralSystemCannotBeReached() {
    URI url;
    try (FakeCentralSystem stopped = FakeCentralSystem.start()) {
      url = stopped.url();
    }

    assertThatThrownBy(() -> CsmsConnection.open(CLIENT, url, "CP-1", WAIT))
        .isInstanceOf(SessionFailedException.class)
        .hasMessageContaining("Cannot connect");
  }

  @Test
  void dropsWhatItCannotUseAndAnswersUnsupportedCallsWithNotImplemented() throws Exception {
    try (var _ = CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1", WAIT)) {
      centralSystem.send("not a frame");
      centralSystem.send("[3,\"unknown-call\",{}]");
      centralSystem.send("[4,\"unknown-call\",\"InternalError\",\"\",{}]");
      centralSystem.send(
          new Call("reset-1", "Reset", JsonNodeFactory.instance.objectNode().put("type", "Soft")));

      OcppFrame answer = centralSystem.nextFrame(WAIT);
      assertThat(answer).isInstanceOf(CallError.class);
      assertThat(answer.messageId()).isEqualTo("reset-1");
      assertThat(((CallError) answer).errorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED);
    }
  }

  @Test
  void readsAFrameThatArrivesInSeveralParts() throws Exception {
    try (var _ = CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1", WAIT)) {
      String data = "d".repeat(100_000);
      centralSystem.send(
          new Call(
              "data-1",
              "DataTransfer",
              JsonNodeFactory.instance.objectNode().put("vendorId", "X").put("data", data)));

      assertThat(centralSystem.nextFrame(WAIT).messageId()).isEqualTo("data-1");
    }
  }

  @Test
  void returnsTheTypedAnswerToItsCall() throws Exception {
    centralSystem.answerWith(
        call ->
            "[3,\"%s\",{\"status\":\"Accepted\",\"currentTime\":\"2026-10-19T08:00:00Z\",\"interval\":300}]"
                .formatted(call.messageId()));

    try (CsmsConnection connection = open(WAIT)) {
      BootNotificationResponse response =
          connection.call(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class);

      assertThat(response)
          .isEqualTo(
              new BootNotificationResponse(
                  RegistrationStatus.ACCEPTED, Instant.parse("2026-10-19T08:00:00Z"), 300));
      Call sent = (Call) centralSystem.nextFrame(WAIT);
      assertThat(sent.action()).isEqualTo("BootNotification");
      assertThat(sent.payload().path("chargePointVendor").stringValue()).isEqualTo("VendorX");
    }
  }

  @Test
  void doesNotSendARequestThatBreaksItsSchema() {
    BootNotificationRequest tooLong =
        new BootNotificationRequest(
            "V".repeat(21), "ModelY", null, null, null, null, null, null, null);

    try (CsmsConnection connection = open(WAIT)) {
      assertThatThrownBy(
              () ->
                  connection.call(
                      Action.BOOT_NOTIFICATION, tooLong, BootNotificationResponse.class))
          .isInstanceOf(SessionFailedException.class)
          .hasMessageContaining("REQUEST is invalid");
      Duration quietPeriod = Duration.ofMillis(500);
      assertThatThrownBy(() -> centralSystem.nextFrame(quietPeriod))
          .isInstanceOf(AssertionError.class);
    }
  }

  @Test
  void failsOnAnAnswerThatBreaksItsSchema() {
    centralSystem.answerWith(
        call -> "[3,\"%s\",{\"status\":\"Accepted\"}]".formatted(call.messageId()));

    assertThat(failureOfABootNotification(WAIT)).hasMessageContaining("RESPONSE is invalid");
  }

  @Test
  void failsWithTheErrorOfTheCentralSystem() {
    centralSystem.answerWith(
        call -> "[4,\"%s\",\"InternalError\",\"\",{}]".formatted(call.messageId()));

    assertThat(failureOfABootNotification(WAIT)).hasCauseInstanceOf(CallErrorException.class);
  }

  @Test
  void dropsAnAnswerToAnotherCallAndTimesOut() {
    centralSystem.answerWith(call -> "[3,\"another-call\",{}]");

    assertThat(failureOfABootNotification(Duration.ofMillis(500)))
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void failsAtOnceWhenTheConnectionClosesDuringACall() {
    centralSystem.answerWith(
        call -> {
          try {
            centralSystem.disconnect();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          return null;
        });

    assertThat(failureOfABootNotification(LONG_TIMEOUT))
        .hasCauseInstanceOf(ConnectionClosedException.class);
  }

  @Test
  void failsAtOnceWhenCallingOnAClosedConnection() throws Exception {
    try (CsmsConnection connection = open(LONG_TIMEOUT)) {
      centralSystem.disconnect();
      assertThat(connection.awaitClosed(WAIT)).isTrue();

      assertThatThrownBy(
              () -> connection.call(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class))
          .hasCauseInstanceOf(ConnectionClosedException.class);
    }
  }

  @Test
  void learnsAtOnceThatTheCentralSystemClosedTheConnection() throws Exception {
    try (CsmsConnection connection =
        CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1", WAIT)) {
      centralSystem.disconnect();

      assertThat(connection.awaitClosed(WAIT)).isTrue();
    }
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void sendsACallTwiceAndExpectsTheSecondAnswer(CapturedOutput output) throws Exception {
    centralSystem.answerBootsWith(300);

    try (CsmsConnection connection = open(WAIT)) {
      BootNotificationResponse response =
          connection.callTwice(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class);
      // Answers arrive in order: once this one is back, so is the second answer to the first call.
      connection.call(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class);

      assertThat(response.status()).isEqualTo(RegistrationStatus.ACCEPTED);
      OcppFrame first = centralSystem.nextFrame(WAIT);
      assertThat(centralSystem.nextFrame(WAIT)).isEqualTo(first);
      assertThat(output).doesNotContain("matches no outstanding call");
    }
  }

  @Test
  void dropsTheConnectionWithoutTellingTheCentralSystem() throws Exception {
    try (CsmsConnection connection = open(WAIT)) {
      await().atMost(WAIT).until(() -> centralSystem.connectedChargePointIds().contains("CP-1"));

      connection.dropAfter(Duration.ofMillis(100));

      assertThat(connection.awaitClosed(WAIT)).isTrue();
      await().atMost(WAIT).until(() -> centralSystem.connectedChargePointIds().isEmpty());
    }
  }

  @Test
  void failsACallInProgressAtOnceWhenTheConnectionDrops() {
    centralSystem.answerWith(call -> null);

    try (CsmsConnection connection = open(LONG_TIMEOUT)) {
      connection.dropAfter(Duration.ofMillis(200));

      assertThatThrownBy(
              () -> connection.call(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class))
          .hasCauseInstanceOf(ConnectionClosedException.class);
    }
  }

  private CsmsConnection open(Duration callTimeout) {
    return CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1", callTimeout);
  }

  private SessionFailedException failureOfABootNotification(Duration callTimeout) {
    try (CsmsConnection connection = open(callTimeout)) {
      connection.call(Action.BOOT_NOTIFICATION, BOOT, BootNotificationResponse.class);
    } catch (SessionFailedException e) {
      return e;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
    throw new AssertionError("The BootNotification did not fail");
  }
}
