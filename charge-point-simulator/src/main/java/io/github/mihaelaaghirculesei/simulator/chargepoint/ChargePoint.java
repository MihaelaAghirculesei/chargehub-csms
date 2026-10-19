package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationResponse;
import io.github.mihaelaaghirculesei.ocpp.message.HeartbeatResponse;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.JsonNodeFactory;

/** A simulated charge point: it boots, then sends heartbeats until stopped or disconnected. */
public final class ChargePoint {

  // OCPP 1.6 lets the charge point choose when the central system sends an interval of 0.
  static final Duration OWN_INTERVAL = Duration.ofMinutes(5);

  private static final BootNotificationRequest BOOT_NOTIFICATION =
      new BootNotificationRequest(
          "ChargeHub", "Simulator", null, null, null, null, null, null, null);
  private static final Logger log = LoggerFactory.getLogger(ChargePoint.class);

  private final HttpClient client;
  private final URI centralSystem;
  private final String id;
  private final Duration callTimeout;

  public ChargePoint(HttpClient client, URI centralSystem, String id, Duration callTimeout) {
    this.client = client;
    this.centralSystem = centralSystem;
    this.id = id;
    this.callTimeout = callTimeout;
  }

  public String id() {
    return id;
  }

  /**
   * Runs until the thread is interrupted.
   *
   * @throws SessionFailedException if a call fails or the connection closes
   */
  public void run() throws InterruptedException {
    try (CsmsConnection connection = CsmsConnection.open(client, centralSystem, id, callTimeout)) {
      Duration heartbeatInterval = boot(connection);
      log.info("Charge point {} accepted, heartbeat every {}", id, heartbeatInterval);
      while (!Thread.currentThread().isInterrupted()) {
        waitOrFail(connection, heartbeatInterval);
        connection.call(
            Action.HEARTBEAT, JsonNodeFactory.instance.objectNode(), HeartbeatResponse.class);
      }
    }
  }

  /** Repeats the BootNotification until accepted, as OCPP 1.6 asks while pending or rejected. */
  private Duration boot(CsmsConnection connection) throws InterruptedException {
    while (true) {
      BootNotificationResponse response =
          connection.call(
              Action.BOOT_NOTIFICATION, BOOT_NOTIFICATION, BootNotificationResponse.class);
      Duration interval =
          response.interval() > 0 ? Duration.ofSeconds(response.interval()) : OWN_INTERVAL;
      if (response.status() == RegistrationStatus.ACCEPTED) {
        return interval;
      }
      log.info("Charge point {} is {}, booting again in {}", id, response.status(), interval);
      waitOrFail(connection, interval);
    }
  }

  // Waiting on the connection instead of sleeping notices a lost connection at once.
  private static void waitOrFail(CsmsConnection connection, Duration interval)
      throws InterruptedException {
    if (connection.awaitClosed(interval)) {
      throw new SessionFailedException("Connection closed by the central system", null);
    }
  }
}
