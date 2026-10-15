package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class OcppWebSocketHandler extends TextWebSocketHandler {

  static final String SUBPROTOCOL = "ocpp1.6";

  private static final Logger log = LoggerFactory.getLogger(OcppWebSocketHandler.class);

  /**
   * OCPP-J 1.6 asks the server to finish the handshake without a subprotocol and then close at once
   * when it supports none of the offered ones. Spring does the first part, not the second.
   */
  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    if (!SUBPROTOCOL.equals(session.getAcceptedProtocol())) {
      String chargePointId = chargePointId(session);
      List<String> offered =
          session.getHandshakeHeaders().getOrEmpty(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL);
      log.atWarn()
          .addKeyValue("chargePointId", chargePointId)
          .addKeyValue("offeredSubprotocols", offered)
          .log(
              "Closing connection of {}: it offered {} instead of {}",
              chargePointId,
              offered,
              SUBPROTOCOL);
      session.close(CloseStatus.PROTOCOL_ERROR);
    }
  }

  private static String chargePointId(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null) {
      return "unknown";
    }
    String path = uri.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
