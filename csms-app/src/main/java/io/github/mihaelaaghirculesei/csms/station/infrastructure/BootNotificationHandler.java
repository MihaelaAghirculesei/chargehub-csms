package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.StationDetails;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.message.BootNotificationResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.RegistrationStatus;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

/**
 * Registers the station, or updates what it says about itself, and accepts it. Every charge point
 * that speaks OCPP 1.6 is welcome: refusing unknown ones needs a registry managed by an operator.
 */
@Component
@EnableConfigurationProperties(StationProperties.class)
class BootNotificationHandler implements ActionHandler {

  private final Clock clock;
  private final ChargingStationRepository stations;
  private final int heartbeatIntervalSeconds;

  BootNotificationHandler(
      Clock clock, ChargingStationRepository stations, StationProperties properties) {
    this.clock = clock;
    this.stations = stations;
    this.heartbeatIntervalSeconds = Math.toIntExact(properties.heartbeatInterval().toSeconds());
  }

  @Override
  public Action action() {
    return Action.BOOT_NOTIFICATION;
  }

  // The transaction commits before the response is written: an accepted station is always stored.
  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    BootNotificationRequest boot = OcppMessages.read(request, BootNotificationRequest.class);
    StationDetails details =
        new StationDetails(
            boot.chargePointVendor(),
            boot.chargePointModel(),
            boot.chargePointSerialNumber(),
            boot.firmwareVersion());
    Instant now = clock.instant();
    ChargingStation station =
        stations
            .findById(chargePointId)
            .map(
                known -> {
                  known.boot(details, now);
                  return known;
                })
            .orElseGet(() -> ChargingStation.register(chargePointId, details, now));
    stations.save(station);
    return OcppMessages.write(
        new BootNotificationResponse(RegistrationStatus.ACCEPTED, now, heartbeatIntervalSeconds));
  }
}
