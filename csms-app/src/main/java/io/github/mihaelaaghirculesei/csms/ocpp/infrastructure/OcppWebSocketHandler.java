package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
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
      String chargePointId = loggable(chargePointId(session));
      List<String> offered =
          session
              .getHandshakeHeaders()
              .getOrEmpty(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL)
              .stream()
              .map(OcppWebSocketHandler::loggable)
              .toList();
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

  // The values come from the client, and the path is already percent-decoded: a line break or a
  // terminal escape would let it forge lines in a plain-text log. Charge point ids and subprotocol
  // names need nothing outside this allow list.
  private static String loggable(String value) {
    return value.replaceAll("[^A-Za-z0-9._:-]", "_");
  }

  // Always present: ChargePointIdInterceptor refuses the handshake otherwise.
  static String chargePointId(WebSocketSession session) {
    return (String)
        Objects.requireNonNull(session.getAttributes().get(ChargePointIdInterceptor.ATTRIBUTE));
  }
}
