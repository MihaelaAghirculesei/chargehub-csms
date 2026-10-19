package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.simulator.FakeCentralSystem;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class CsmsConnectionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

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

    try (CsmsConnection connection = CsmsConnection.open(CLIENT, withTrailingSlash, "CP-1")) {
      assertThat(connection.awaitClosed(Duration.ofMillis(200))).isFalse();
      assertThat(centralSystem.connectedChargePointId()).isEqualTo("CP-1");
    }
  }

  @Test
  void refusesACentralSystemThatDoesNotSpeakOcpp16() {
    try (FakeCentralSystem other = FakeCentralSystem.withoutOcpp16()) {
      URI url = other.url();
      assertThatThrownBy(() -> CsmsConnection.open(CLIENT, url, "CP-1"))
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

    assertThatThrownBy(() -> CsmsConnection.open(CLIENT, url, "CP-1"))
        .isInstanceOf(SessionFailedException.class)
        .hasMessageContaining("Cannot connect");
  }

  @Test
  void dropsWhatItCannotUseAndAnswersUnsupportedCallsWithNotImplemented() throws Exception {
    try (var _ = CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1")) {
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
    try (var _ = CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1")) {
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
  void learnsAtOnceThatTheCentralSystemClosedTheConnection() throws Exception {
    try (CsmsConnection connection = CsmsConnection.open(CLIENT, centralSystem.url(), "CP-1")) {
      centralSystem.disconnect();

      assertThat(connection.awaitClosed(WAIT)).isTrue();
    }
  }
}
