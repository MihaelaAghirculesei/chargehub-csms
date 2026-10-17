package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** The open OCPP session of each connected charge point. Thread-safe. */
@Component
class ChargePointConnections {

  // A charge point that stops reading must not hold a server thread or fill the memory: past these
  // limits its session is closed.
  private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
  private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

  static final CloseStatus REPLACED = CloseStatus.NORMAL.withReason("Replaced by a new connection");

  private static final Logger log = LoggerFactory.getLogger(ChargePointConnections.class);

  private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  /**
   * A charge point that lost its network often reconnects before the server notices the old
   * connection is dead, so the new connection wins and the old one is closed.
   *
   * @return whether a previous connection of the same charge point was replaced
   */
  boolean register(String chargePointId, WebSocketSession session) {
    WebSocketSession sendSafe =
        new ConcurrentWebSocketSessionDecorator(
            session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
    WebSocketSession previous = sessions.put(chargePointId, sendSafe);
    if (previous == null) {
      return false;
    }
    close(previous);
    return true;
  }

  /**
   * Removes the session only if it is still the current one, so a replaced session leaves the new
   * one in place.
   *
   * @return whether the session was the current one and is now removed
   */
  boolean unregister(String chargePointId, WebSocketSession session) {
    WebSocketSession current = sessions.get(chargePointId);
    return current != null
        && current.getId().equals(session.getId())
        && sessions.remove(chargePointId, current);
  }

  Optional<WebSocketSession> find(String chargePointId) {
    return Optional.ofNullable(sessions.get(chargePointId));
  }

  private static void close(WebSocketSession session) {
    try {
      session.close(REPLACED);
    } catch (IOException e) {
      // The old connection is usually already broken; it is gone from the registry either way.
      log.debug("Previous connection was already closed", e);
    }
  }
}
