package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.call.PendingCalls;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameDecodingException;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadType;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadViolation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** The WebSocket of one charge point to the central system. Thread-safe. */
public final class CsmsConnection implements AutoCloseable {

  static final String SUBPROTOCOL = "ocpp1.6";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final FrameCodec CODEC = new FrameCodec();
  private static final PayloadValidator VALIDATOR = PayloadValidator.ocpp16();
  private static final Logger log = LoggerFactory.getLogger(CsmsConnection.class);

  private final String chargePointId;
  private final PendingCalls pendingCalls;
  private final CountDownLatch closed = new CountDownLatch(1);
  private final Object sendLock = new Object();
  // The JDK client refuses a send while the previous one is still running, so sends are chained.
  private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);
  private final AtomicReference<CompletableFuture<?>> drop =
      new AtomicReference<>(CompletableFuture.completedFuture(null));
  private @Nullable WebSocket webSocket;

  private CsmsConnection(String chargePointId, Duration callTimeout) {
    this.chargePointId = chargePointId;
    this.pendingCalls = new PendingCalls(callTimeout);
  }

  /**
   * Opens the connection to {@code centralSystem/chargePointId}, as OCPP-J 1.6 asks.
   *
   * @throws SessionFailedException if the connection fails, or the central system does not accept
   *     the ocpp1.6 subprotocol
   */
  public static CsmsConnection open(
      HttpClient client, URI centralSystem, String chargePointId, Duration callTimeout) {
    CsmsConnection connection = new CsmsConnection(chargePointId, callTimeout);
    String base = centralSystem.toString();
    URI uri = URI.create(base + (base.endsWith("/") ? "" : "/") + chargePointId);
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
   * Sends a request and waits for the answer. Only one call at a time, as OCPP-J requires.
   *
   * @throws SessionFailedException if the call is refused, times out, or the connection closes
   */
  public <T> T call(Action action, Object request, Class<T> responseType)
      throws InterruptedException {
    ObjectNode payload = OcppMessages.write(request);
    requireValid(action, PayloadType.REQUEST, payload);
    Call call = new Call(UUID.randomUUID().toString(), action.wireName(), payload);
    CompletableFuture<CallResult> answer = pendingCalls.begin(chargePointId, call);
    send(Objects.requireNonNull(webSocket), CODEC.encode(call));
    CallResult result;
    try {
      result = answer.get();
    } catch (ExecutionException e) {
      throw new SessionFailedException(action + " failed", e.getCause());
    }
    requireValid(action, PayloadType.RESPONSE, result.payload());
    return OcppMessages.read(result.payload(), responseType);
  }

  /**
   * Waits until the connection closes, at most for the given time.
   *
   * @return true if the connection is closed
   */
  public boolean awaitClosed(Duration timeout) throws InterruptedException {
    return closed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Drops the connection after the given time without a close frame, as a failing network would.
   * Calls in progress fail at once, as they do when the central system closes the connection.
   */
  public void dropAfter(Duration delay) {
    drop.set(
        CompletableFuture.runAsync(
            this::drop,
            CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)));
  }

  private void drop() {
    log.info("Dropping the connection of {} on purpose", chargePointId);
    Objects.requireNonNull(webSocket).abort();
    // An aborted WebSocket tells its listener nothing.
    closed();
  }

  @Override
  public void close() {
    drop.get().cancel(false);
    // Set by open before anyone can call close. When the output is already closed the future just
    // fails: nothing to do, the connection is going away anyway.
    var _ = Objects.requireNonNull(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    closed();
  }

  // A broken message is caught on the side that produced it, see docs/adr/0010.
  private static void requireValid(Action action, PayloadType type, ObjectNode payload) {
    Optional<PayloadViolation> violation = VALIDATOR.validate(action.wireName(), type, payload);
    if (violation.isPresent()) {
      throw new SessionFailedException(
          action + " " + type + " is invalid: " + violation.get().description(), null);
    }
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

  private void closed() {
    pendingCalls.connectionClosed(chargePointId);
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

    private void received(WebSocket webSocket, String message) {
      OcppFrame frame;
      try {
        frame = CODEC.decode(message);
      } catch (FrameDecodingException e) {
        log.warn("Dropping a malformed frame sent to {}: {}", chargePointId, e.errorCode());
        return;
      }
      boolean matched =
          switch (frame) {
            case CallResult result -> pendingCalls.answer(chargePointId, result);
            case CallError error -> pendingCalls.answer(chargePointId, error);
            case Call call -> {
              // Answering at once spares the central system its call timeout.
              send(
                  webSocket,
                  CODEC.encode(
                      new CallError(
                          call.messageId(),
                          ErrorCode.NOT_IMPLEMENTED,
                          "Not supported by the simulator",
                          JsonNodeFactory.instance.objectNode())));
              yield true;
            }
          };
      if (!matched) {
        log.warn("Dropping an answer to {} that matches no outstanding call", chargePointId);
      }
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
