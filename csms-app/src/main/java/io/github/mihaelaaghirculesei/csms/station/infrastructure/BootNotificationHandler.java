package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Accepts every charge point. The station registry of Phase 3 will refuse unknown ones; until then
 * a charge point that speaks OCPP 1.6 is welcome.
 */
@Component
@EnableConfigurationProperties(StationProperties.class)
class BootNotificationHandler implements ActionHandler {

  private final Clock clock;
  private final int heartbeatIntervalSeconds;

  BootNotificationHandler(Clock clock, StationProperties properties) {
    this.clock = clock;
    this.heartbeatIntervalSeconds = Math.toIntExact(properties.heartbeatInterval().toSeconds());
  }

  @Override
  public Action action() {
    return Action.BOOT_NOTIFICATION;
  }

  @Override
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    return OcppMessages.write(
        new BootNotificationResponse(
            RegistrationStatus.ACCEPTED, clock.instant(), heartbeatIntervalSeconds));
  }
}
