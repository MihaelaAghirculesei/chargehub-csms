package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** A WebSocket client playing the charge point: it records what the server sends and answers. */
final class TestChargePoint extends TextWebSocketHandler implements AutoCloseable {

  private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
  private final WebSocketSession session;

  private TestChargePoint(int port, String chargePointId) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setSecWebSocketProtocol(List.of("ocpp1.6"));
    URI uri = URI.create("ws://localhost:" + port + "/ocpp/" + chargePointId);
    session = new StandardWebSocketClient().execute(this, headers, uri).get(5, TimeUnit.SECONDS);
  }

  /** Connects and waits until the server has registered the connection. */
  static TestChargePoint connect(int port, String chargePointId, ChargePointConnections connections)
      throws Exception {
    TestChargePoint chargePoint = new TestChargePoint(port, chargePointId);
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (connections.find(chargePointId).isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException(chargePointId + " was not registered");
      }
      Thread.sleep(10);
    }
    return chargePoint;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    received.add(message.getPayload());
  }

  /** The next frame from the server, or null if none arrives in time. */
  @Nullable String nextFrame(Duration wait) throws InterruptedException {
    return received.poll(wait.toMillis(), TimeUnit.MILLISECONDS);
  }

  void send(String frame) throws IOException {
    session.sendMessage(new TextMessage(frame));
  }

  @Override
  public void close() throws IOException {
    session.close();
  }
}
