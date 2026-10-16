package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class OcppWebSocketEndpointTest {

  @LocalServerPort private int port;

  @Test
  void acceptsOcpp16AndKeepsTheConnectionOpen() throws Exception {
    Client client = new Client();
    try (WebSocketSession session = client.connect("CP-1", "ocpp1.6")) {
      assertThat(session.getAcceptedProtocol()).isEqualTo("ocpp1.6");
      assertThat(session.isOpen()).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ocpp2.0.1", "none"})
  void closesConnectionWithoutOcpp16AsProtocolError(String offered) throws Exception {
    Client client = new Client();
    String[] subprotocols = offered.equals("none") ? new String[0] : new String[] {offered};

    client.connect("CP-2", subprotocols);

    assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(CloseStatus.PROTOCOL_ERROR);
  }

  @Test
  void logsChargePointAndOfferedSubprotocols(CapturedOutput output) throws Exception {
    Client client = new Client();

    client.connect("CP-LOG", "ocpp2.0.1");
    client.closed.get(5, TimeUnit.SECONDS);

    assertThat(output.getOut()).contains("Closing connection of CP-LOG", "ocpp2.0.1");
  }

  /**
   * The id becomes a map key and a log field, so anything but a plain identifier is refused. Tomcat
   * already refuses an encoded line break on its own; the case stays to cover the whole path.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "CP%0D%0AFORGED",
        "CP%20ONE",
        "CP%3CX%3E",
        "A123456789B123456789C123456789D123456789E12345678"
      })
  void refusesMalformedChargePointIdBeforeTheUpgrade(String chargePointId) {
    assertThatThrownBy(() -> new Client().connect(chargePointId, "ocpp1.6"))
        .rootCause()
        .hasMessageContaining("400");
  }

  @Test
  void acceptsAnIdOfFortyEightCharacters() throws Exception {
    try (WebSocketSession session = new Client().connect("A".repeat(48), "ocpp1.6")) {
      assertThat(session.isOpen()).isTrue();
    }
  }

  private final class Client extends TextWebSocketHandler {

    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

    WebSocketSession connect(String chargePointId, String... subprotocols) throws Exception {
      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
      if (subprotocols.length > 0) {
        headers.setSecWebSocketProtocol(List.of(subprotocols));
      }
      URI uri = URI.create("ws://localhost:" + port + "/ocpp/" + chargePointId);
      return new StandardWebSocketClient().execute(this, headers, uri).get(5, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
      closed.complete(status);
    }
  }
}
