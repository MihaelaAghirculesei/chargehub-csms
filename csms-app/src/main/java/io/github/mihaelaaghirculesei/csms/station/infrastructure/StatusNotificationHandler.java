package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Applied;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Takes the status a charge point reports for a connector. The answer is always an empty
 * confirmation: OCPP 1.6 has no way to refuse a report, see docs/adr/0013.
 */
@Component
class StatusNotificationHandler implements ActionHandler {

  private final Clock clock;
  private final ChargingStationRepository stations;
  private final ApplicationEventPublisher events;

  StatusNotificationHandler(
      Clock clock, ChargingStationRepository stations, ApplicationEventPublisher events) {
    this.clock = clock;
    this.stations = stations;
    this.events = events;
  }

  @Override
  public Action action() {
    return Action.STATUS_NOTIFICATION;
  }

  // Events are published inside the transaction; listeners that act on them should run after
  // commit, so that they never see a change that was rolled back.
  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    StatusNotificationRequest report = OcppMessages.read(request, StatusNotificationRequest.class);
    Optional<ChargingStation> found = stations.findById(chargePointId);
    if (found.isPresent()) {
      ChargingStation station = found.get();
      ReportOutcome outcome =
          station.report(report.connectorId(), toDomain(report.status()), reportedAt(report));
      stations.save(station);
      if (outcome instanceof Applied applied) {
        events.publishEvent(applied.change());
      }
    }
    return JsonNodeFactory.instance.objectNode();
  }

  /** The timestamp is optional in OCPP 1.6; without it, the time of arrival is the best guess. */
  private Instant reportedAt(StatusNotificationRequest report) {
    Instant timestamp = report.timestamp();
    return timestamp != null ? timestamp : clock.instant();
  }

  private static ConnectorStatus toDomain(ChargePointStatus status) {
    return switch (status) {
      case AVAILABLE -> ConnectorStatus.AVAILABLE;
      case PREPARING -> ConnectorStatus.PREPARING;
      case CHARGING -> ConnectorStatus.CHARGING;
      case SUSPENDED_EVSE -> ConnectorStatus.SUSPENDED_EVSE;
      case SUSPENDED_EV -> ConnectorStatus.SUSPENDED_EV;
      case FINISHING -> ConnectorStatus.FINISHING;
      case RESERVED -> ConnectorStatus.RESERVED;
      case UNAVAILABLE -> ConnectorStatus.UNAVAILABLE;
      case FAULTED -> ConnectorStatus.FAULTED;
    };
  }
}
