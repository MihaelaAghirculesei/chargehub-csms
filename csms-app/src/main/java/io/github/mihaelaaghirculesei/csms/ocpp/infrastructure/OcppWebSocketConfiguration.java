package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** Plain WebSocket, no STOMP: OCPP-J defines its own framing. See docs/adr/0007. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class OcppWebSocketConfiguration implements WebSocketConfigurer {

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Without this Spring answers without Sec-WebSocket-Protocol, and many charge points then
    // drop the connection.
    DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
    handshakeHandler.setSupportedProtocols(OcppWebSocketHandler.SUBPROTOCOL);
    registry
        .addHandler(new OcppWebSocketHandler(), "/ocpp/*")
        .setHandshakeHandler(handshakeHandler)
        .addInterceptors(new ChargePointIdInterceptor());
  }
}
