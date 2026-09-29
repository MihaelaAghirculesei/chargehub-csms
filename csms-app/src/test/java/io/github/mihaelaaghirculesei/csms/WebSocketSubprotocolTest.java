package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Proves the servlet WebSocket stack negotiates the OCPP subprotocol. The endpoint exists only in
 * this test; the production endpoint belongs to Phase 1.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, WebSocketSubprotocolTest.OcppProbeEndpoint.class})
class WebSocketSubprotocolTest {

  @LocalServerPort private int port;

  @Test
  void negotiatesOcpp16Subprotocol() throws Exception {
    try (WebSocketSession session = connect("ocpp1.6")) {
      assertThat(session.getAcceptedProtocol()).isEqualTo("ocpp1.6");
    }
  }

  /**
   * Spring completes the handshake without a subprotocol and keeps the connection open. OCPP-J
   * expects the server to close it right away; the Phase 1 endpoint has to do that itself.
   */
  @ParameterizedTest
  @ValueSource(strings = {"ocpp2.0.1", "none"})
  void keepsConnectionOpenWithoutSubprotocolWhenNoneMatches(String offered) throws Exception {
    try (WebSocketSession session = offered.equals("none") ? connect() : connect(offered)) {
      assertThat(session.getAcceptedProtocol()).isEmpty();
      assertThat(session.isOpen()).isTrue();
    }
  }

  private WebSocketSession connect(String... subprotocols) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    if (subprotocols.length > 0) {
      headers.setSecWebSocketProtocol(List.of(subprotocols));
    }
    return new StandardWebSocketClient()
        .execute(
            new TextWebSocketHandler(),
            headers,
            URI.create("ws://localhost:" + port + "/test/ocpp/CP-1"))
        .get(5, TimeUnit.SECONDS);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSocket
  static class OcppProbeEndpoint implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
      handshakeHandler.setSupportedProtocols("ocpp1.6");
      registry
          .addHandler(new TextWebSocketHandler(), "/test/ocpp/*")
          .setHandshakeHandler(handshakeHandler);
    }
  }
}
