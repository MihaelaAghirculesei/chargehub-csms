package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import io.github.mihaelaaghirculesei.ocpp.call.PendingCalls;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** Plain WebSocket, no STOMP: OCPP-J defines its own framing. See docs/adr/0007. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class OcppWebSocketConfiguration implements WebSocketConfigurer {

  private final ChargePointConnections connections;
  private final PendingCalls pendingCalls;
  private final FrameCodec codec;
  private final CallDispatcher dispatcher;

  OcppWebSocketConfiguration(
      ChargePointConnections connections,
      PendingCalls pendingCalls,
      FrameCodec codec,
      CallDispatcher dispatcher) {
    this.connections = connections;
    this.pendingCalls = pendingCalls;
    this.codec = codec;
    this.dispatcher = dispatcher;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Without this Spring answers without Sec-WebSocket-Protocol, and many charge points then
    // drop the connection.
    DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
    handshakeHandler.setSupportedProtocols(OcppWebSocketHandler.SUBPROTOCOL);
    registry
        .addHandler(
            new OcppWebSocketHandler(connections, pendingCalls, codec, dispatcher), "/ocpp/*")
        .setHandshakeHandler(handshakeHandler)
        .addInterceptors(new ChargePointIdInterceptor());
  }
}
