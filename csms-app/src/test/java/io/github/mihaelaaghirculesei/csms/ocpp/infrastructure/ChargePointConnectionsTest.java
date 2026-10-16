package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class ChargePointConnectionsTest {

  private final ChargePointConnections connections = new ChargePointConnections();

  @Test
  void findsARegisteredChargePoint() {
    WebSocketSession session = session("s1");

    assertThat(connections.register("CP-1", session)).isFalse();

    assertThat(connections.find("CP-1")).get().extracting(WebSocketSession::getId).isEqualTo("s1");
  }

  @Test
  void findsNothingForAnUnknownChargePoint() {
    assertThat(connections.find("CP-1")).isEmpty();
  }

  @Test
  void replacesAndClosesThePreviousConnection() throws Exception {
    WebSocketSession first = session("s1");
    WebSocketSession second = session("s2");
    connections.register("CP-1", first);

    assertThat(connections.register("CP-1", second)).isTrue();

    verify(first).close(ChargePointConnections.REPLACED);
    verify(second, never()).close(ChargePointConnections.REPLACED);
    assertThat(connections.find("CP-1")).get().extracting(WebSocketSession::getId).isEqualTo("s2");
  }

  /** The replaced session reports its own close later; that must not remove the new one. */
  @Test
  void keepsTheNewConnectionWhenTheReplacedOneCloses() {
    WebSocketSession first = session("s1");
    connections.register("CP-1", first);
    connections.register("CP-1", session("s2"));

    connections.unregister("CP-1", first);

    assertThat(connections.find("CP-1")).get().extracting(WebSocketSession::getId).isEqualTo("s2");
  }

  @Test
  void removesTheCurrentConnectionWhenItCloses() {
    WebSocketSession session = session("s1");
    connections.register("CP-1", session);

    connections.unregister("CP-1", session);

    assertThat(connections.find("CP-1")).isEmpty();
  }

  private static WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }
}
