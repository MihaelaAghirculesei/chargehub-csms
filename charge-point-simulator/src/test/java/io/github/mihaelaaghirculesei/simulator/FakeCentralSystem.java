package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationResponse;
import io.github.mihaelaaghirculesei.ocpp.message.HeartbeatResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import tools.jackson.databind.node.ObjectNode;

/** A central system for tests, on a random port: it records what the charge point sends. */
public final class FakeCentralSystem extends TextWebSocketHandler implements AutoCloseable {

  private static final FrameCodec CODEC = new FrameCodec();
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  private final BlockingQueue<OcppFrame> received = new LinkedBlockingQueue<>();
  private final boolean ocpp16;
  private final ConfigurableApplicationContext context;
  // Completed on a server thread, possibly after the client already saw the handshake succeed.
  // Replaced when the connection closes, so a charge point that reconnects gets a fresh one.
  private final AtomicReference<CompletableFuture<WebSocketSession>> connected =
      new AtomicReference<>(new CompletableFuture<>());
  private volatile Function<Call, @Nullable String> answers = call -> null;
  // Charge point id to the id of its open session, for tests that run several charge points.
  private final Map<String, String> open = new ConcurrentHashMap<>();
  // Session id to a wrapper that serializes sends: a test and an answer may send at the same time.
  private final Map<String, WebSocketSession> sendable = new ConcurrentHashMap<>();

  private FakeCentralSystem(boolean ocpp16, int port) {
    this.ocpp16 = ocpp16;
    this.context =
        new SpringApplicationBuilder(Server.class)
            .properties(Map.of("server.port", port, "spring.main.banner-mode", "off"))
            .initializers(c -> c.getBeanFactory().registerSingleton("centralSystem", this))
            .run();
  }

  public static FakeCentralSystem start() {
    return new FakeCentralSystem(true, 0);
  }

  /** Starts on a given port, for example the one of a central system that was stopped. */
  public static FakeCentralSystem start(int port) {
    return new FakeCentralSystem(true, port);
  }

  /** Completes the handshake without a subprotocol, like a server that does not speak OCPP. */
  public static FakeCentralSystem withoutOcpp16() {
    return new FakeCentralSystem(false, 0);
  }

  public URI url() {
    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
    return URI.create("ws://localhost:" + port + "/ocpp");
  }

  /** The next frame from the charge point; fails the test if none arrives in time. */
  public OcppFrame nextFrame(Duration wait) throws InterruptedException {
    OcppFrame frame = received.poll(wait.toMillis(), TimeUnit.MILLISECONDS);
    if (frame == null) {
      throw new AssertionError("No frame from the charge point within " + wait);
    }
    return frame;
  }

  public void send(OcppFrame frame) throws IOException {
    send(CODEC.encode(frame));
  }

  /** Sends text as it is, valid frame or not, on the latest connection. */
  public void send(String text) throws IOException {
    send(session(), text);
  }

  private void send(WebSocketSession session, String text) throws IOException {
    Objects.requireNonNull(sendable.get(session.getId())).sendMessage(new TextMessage(text));
  }

  /** From now on every call is answered with the text the function returns; null sends nothing. */
  public void answerWith(Function<Call, @Nullable String> answers) {
    this.answers = answers;
  }

  /**
   * Answers BootNotification with the given statuses in turn, then with Accepted, and Heartbeat
   * with a fixed time.
   */
  public void answerBootsWith(int interval, RegistrationStatus... statuses) {
    Queue<RegistrationStatus> pending = new ConcurrentLinkedQueue<>(List.of(statuses));
    answerWith(
        call -> {
          ObjectNode payload =
              switch (call.action()) {
                case "BootNotification" ->
                    OcppMessages.write(
                        new BootNotificationResponse(
                            Objects.requireNonNullElse(pending.poll(), RegistrationStatus.ACCEPTED),
                            NOW,
                            interval));
                case "Heartbeat" -> OcppMessages.write(new HeartbeatResponse(NOW));
                default -> throw new IllegalStateException("Unexpected action " + call.action());
              };
          return CODEC.encode(new CallResult(call.messageId(), payload));
        });
  }

  public void disconnect() throws IOException {
    session().close();
  }

  /** The id the charge point put in the path of its connection. */
  public String connectedChargePointId() {
    return chargePointId(session());
  }

  /** The ids of all charge points connected right now. */
  public Set<String> connectedChargePointIds() {
    return Set.copyOf(open.keySet());
  }

  private static String chargePointId(WebSocketSession session) {
    String path = Objects.requireNonNull(session.getUri()).getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  @Override
  public synchronized void afterConnectionEstablished(WebSocketSession session) {
    sendable.put(
        session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 1024 * 1024));
    // The new connection can arrive before the server has seen the old one close.
    if (!connected.get().complete(session)) {
      connected.set(CompletableFuture.completedFuture(session));
    }
    open.put(chargePointId(session), session.getId());
  }

  @Override
  public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    open.remove(chargePointId(session), session.getId());
    sendable.remove(session.getId());
    CompletableFuture<WebSocketSession> current = connected.get();
    if (current.isDone() && current.join().getId().equals(session.getId())) {
      connected.set(new CompletableFuture<>());
    }
  }

  private WebSocketSession session() {
    try {
      return connected.get().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for the charge point", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new AssertionError("No charge point connected within 5 seconds", e);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {
    OcppFrame frame = CODEC.decode(message.getPayload());
    String answer = frame instanceof Call call ? answers.apply(call) : null;
    if (answer != null) {
      send(session, answer);
    }
    // Only after the answer, so a test reacting to the frame never sends at the same time.
    received.add(frame);
  }

  @Override
  public void close() {
    context.close();
  }

  // A test configuration, so the component scan of the simulator application never picks it up.
  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EnableWebSocket
  static class Server implements WebSocketConfigurer {

    private final FakeCentralSystem centralSystem;

    Server(FakeCentralSystem centralSystem) {
      this.centralSystem = centralSystem;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
      if (centralSystem.ocpp16) {
        handshakeHandler.setSupportedProtocols("ocpp1.6");
      }
      registry.addHandler(centralSystem, "/ocpp/*").setHandshakeHandler(handshakeHandler);
    }
  }
}
