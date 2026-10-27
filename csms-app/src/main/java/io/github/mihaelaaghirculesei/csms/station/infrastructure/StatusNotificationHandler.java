package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Applied;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Rejected;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Stale;
import io.github.mihaelaaghirculesei.csms.station.domain.ReportOutcome.Unchanged;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Takes the status a charge point reports for a connector. The answer is always an empty
 * confirmation: OCPP 1.6 has no way to refuse a report, see docs/adr/0013. Reports that change
 * nothing are logged and every outcome is counted, so a charge point that keeps sending reports out
 * of order shows up on a dashboard.
 */
@Component
class StatusNotificationHandler implements ActionHandler {

  static final String REPORTS_METRIC = "csms.connector.status.reports";

  private static final Logger log = LoggerFactory.getLogger(StatusNotificationHandler.class);

  private final Clock clock;
  private final ChargingStationRepository stations;
  private final ApplicationEventPublisher events;
  private final MeterRegistry meters;

  StatusNotificationHandler(
      Clock clock,
      ChargingStationRepository stations,
      ApplicationEventPublisher events,
      MeterRegistry meters) {
    this.clock = clock;
    this.stations = stations;
    this.events = events;
    this.meters = meters;
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
    if (found.isEmpty()) {
      // A charge point must boot first; a CALLERROR would only make it send the report again.
      log.atWarn()
          .addKeyValue("chargePointId", chargePointId)
          .addKeyValue("connectorId", report.connectorId())
          .log("Ignoring a status report from {}, which has not booted", chargePointId);
      count("unknown_station");
    } else {
      ChargingStation station = found.get();
      station.seen(clock.instant()).ifPresent(events::publishEvent);
      ConnectorStatus reported = toDomain(report.status());
      ReportOutcome outcome = station.report(report.connectorId(), reported, reportedAt(report));
      stations.save(station);
      count(take(chargePointId, reported, outcome));
    }
    return JsonNodeFactory.instance.objectNode();
  }

  /** Publishes or logs what the report did, and names the outcome for the metric. */
  private String take(String chargePointId, ConnectorStatus reported, ReportOutcome outcome) {
    return switch (outcome) {
      case Applied(var change) -> {
        events.publishEvent(change);
        yield "applied";
      }
      case Unchanged _ -> "unchanged";
      case Stale(var current, var reportedAt) -> {
        log.atInfo()
            .addKeyValue("chargePointId", chargePointId)
            .addKeyValue("connectorId", current.id())
            .log(
                "Ignoring a late status report from {} for connector {}: {} at {} is older than {} at {}",
                chargePointId,
                current.id(),
                reported,
                reportedAt,
                current.status(),
                current.reportedAt());
        yield "stale";
      }
      case Rejected(var connectorId, var current, var _) -> {
        log.atWarn()
            .addKeyValue("chargePointId", chargePointId)
            .addKeyValue("connectorId", connectorId)
            .log(
                "Ignoring a status change OCPP 1.6 does not allow from {} for connector {}: {} to {}",
                chargePointId,
                connectorId,
                current,
                reported);
        yield "rejected";
      }
    };
  }

  private void count(String outcome) {
    meters.counter(REPORTS_METRIC, "outcome", outcome).increment();
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
