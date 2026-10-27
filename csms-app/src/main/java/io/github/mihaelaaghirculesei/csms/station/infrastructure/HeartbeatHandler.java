package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.ocpp.message.HeartbeatResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

/**
 * Marks the station as seen and answers with the central system time, which charge points use to
 * keep their clock in sync.
 */
@Component
class HeartbeatHandler implements ActionHandler {

  private final Clock clock;
  private final ChargingStationRepository stations;
  private final ApplicationEventPublisher events;

  HeartbeatHandler(
      Clock clock, ChargingStationRepository stations, ApplicationEventPublisher events) {
    this.clock = clock;
    this.stations = stations;
    this.events = events;
  }

  @Override
  public Action action() {
    return Action.HEARTBEAT;
  }

  // A station that has not booted only gets the time: it has nothing stored to be seen against.
  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    Instant now = clock.instant();
    stations
        .findById(chargePointId)
        .ifPresent(
            station -> {
              station.seen(now).ifPresent(events::publishEvent);
              stations.save(station);
            });
    return OcppMessages.write(new HeartbeatResponse(now));
  }
}
