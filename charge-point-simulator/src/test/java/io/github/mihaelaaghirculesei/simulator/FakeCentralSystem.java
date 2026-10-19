package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** A central system for tests, on a random port: it records what the charge point sends. */
public final class FakeCentralSystem extends TextWebSocketHandler implements AutoCloseable {

  private static final FrameCodec CODEC = new FrameCodec();

  private final BlockingQueue<OcppFrame> received = new LinkedBlockingQueue<>();
  private final boolean ocpp16;
  private final ConfigurableApplicationContext context;
  private volatile @Nullable WebSocketSession session;

  private FakeCentralSystem(boolean ocpp16) {
    this.ocpp16 = ocpp16;
    this.context =
        new SpringApplicationBuilder(Server.class)
            .properties(Map.of("server.port", "0", "spring.main.banner-mode", "off"))
            .initializers(c -> c.getBeanFactory().registerSingleton("centralSystem", this))
            .run();
  }

  public static FakeCentralSystem start() {
    return new FakeCentralSystem(true);
  }

  /** Completes the handshake without a subprotocol, like a server that does not speak OCPP. */
  public static FakeCentralSystem withoutOcpp16() {
    return new FakeCentralSystem(false);
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

  /** Sends text as it is, valid frame or not. */
  public synchronized void send(String text) throws IOException {
    Objects.requireNonNull(session).sendMessage(new TextMessage(text));
  }

  public void disconnect() throws IOException {
    Objects.requireNonNull(session).close();
  }

  /** The id the charge point put in the path of its connection. */
  public String connectedChargePointId() {
    String path = Objects.requireNonNull(Objects.requireNonNull(session).getUri()).getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    this.session = session;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    received.add(CODEC.decode(message.getPayload()));
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
