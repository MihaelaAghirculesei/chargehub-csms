package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameDecodingException;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.JsonNodeFactory;

/** The WebSocket of one charge point to the central system. Thread-safe. */
public final class CsmsConnection implements AutoCloseable {

  static final String SUBPROTOCOL = "ocpp1.6";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final FrameCodec CODEC = new FrameCodec();
  private static final Logger log = LoggerFactory.getLogger(CsmsConnection.class);

  private final String chargePointId;
  private final CountDownLatch closed = new CountDownLatch(1);
  private final Object sendLock = new Object();
  // The JDK client refuses a send while the previous one is still running, so sends are chained.
  private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);
  private @Nullable WebSocket webSocket;

  private CsmsConnection(String chargePointId) {
    this.chargePointId = chargePointId;
  }

  /**
   * Opens the connection to {@code centralSystem/chargePointId}, as OCPP-J 1.6 asks.
   *
   * @throws SessionFailedException if the connection fails, or the central system does not accept
   *     the ocpp1.6 subprotocol
   */
  public static CsmsConnection open(HttpClient client, URI centralSystem, String chargePointId) {
    CsmsConnection connection = new CsmsConnection(chargePointId);
    URI uri = URI.create(centralSystem.toString().replaceAll("/+$", "") + "/" + chargePointId);
    WebSocket webSocket;
    try {
      webSocket =
          client
              .newWebSocketBuilder()
              .subprotocols(SUBPROTOCOL)
              .connectTimeout(CONNECT_TIMEOUT)
              .buildAsync(uri, connection.new Listener())
              .join();
    } catch (CompletionException e) {
      throw new SessionFailedException("Cannot connect to " + uri, e.getCause());
    }
    connection.webSocket = webSocket;
    // A server that knows none of the offered subprotocols still completes the handshake.
    if (!SUBPROTOCOL.equals(webSocket.getSubprotocol())) {
      connection.close();
      throw new SessionFailedException("Central system did not accept " + SUBPROTOCOL, null);
    }
    return connection;
  }

  /**
   * Waits until the connection closes, at most for the given time.
   *
   * @return true if the connection is closed
   */
  public boolean awaitClosed(Duration timeout) throws InterruptedException {
    return closed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    // Set by open before anyone can call close. When the output is already closed the future just
    // fails: nothing to do, the connection is going away anyway.
    var _ = Objects.requireNonNull(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    closed();
  }

  private void send(WebSocket webSocket, String text) {
    synchronized (sendLock) {
      lastSend =
          lastSend
              .exceptionally(e -> null)
              .thenCompose(previous -> webSocket.sendText(text, true))
              .whenComplete(
                  (sent, failure) -> {
                    if (failure != null) {
                      closed();
                    }
                  });
    }
  }

  private void received(WebSocket webSocket, String text) {
    OcppFrame frame;
    try {
      frame = CODEC.decode(text);
    } catch (FrameDecodingException e) {
      log.warn("Dropping a malformed frame sent to {}: {}", chargePointId, e.errorCode());
      return;
    }
    switch (frame) {
      case CallResult _, CallError _ ->
          log.warn("Dropping an answer to {} that matches no outstanding call", chargePointId);
      case Call call ->
          // Answering at once spares the central system its call timeout.
          send(
              webSocket,
              CODEC.encode(
                  new CallError(
                      call.messageId(),
                      ErrorCode.NOT_IMPLEMENTED,
                      "Not supported by the simulator",
                      JsonNodeFactory.instance.objectNode())));
    }
  }

  private void closed() {
    closed.countDown();
  }

  private final class Listener implements WebSocket.Listener {

    private final StringBuilder text = new StringBuilder();

    @Override
    public @Nullable CompletionStage<?> onText(
        WebSocket webSocket, CharSequence data, boolean last) {
      // A frame can arrive in several parts; only the last one completes it.
      text.append(data);
      if (last) {
        String frame = text.toString();
        text.setLength(0);
        received(webSocket, frame);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public @Nullable CompletionStage<?> onClose(
        WebSocket webSocket, int statusCode, String reason) {
      closed();
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closed();
    }
  }
}
