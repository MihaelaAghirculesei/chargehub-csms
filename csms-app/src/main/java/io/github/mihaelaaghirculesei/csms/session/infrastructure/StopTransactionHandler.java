package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.IdTagAuthorization;
import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.session.StopReason;
import io.github.mihaelaaghirculesei.csms.session.domain.ClockGap;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStop;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import io.github.mihaelaaghirculesei.ocpp.message.IdTagInfo;
import io.github.mihaelaaghirculesei.ocpp.message.MeterValue;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.Reason;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

/**
 * Closes a session when a charge point stops charging. A stop that matches no open session is
 * answered normally and logged: a CALLERROR would make the charge point send it again and again,
 * and the stop cannot become valid by being repeated.
 */
@Component
class StopTransactionHandler implements ActionHandler {

  private static final String CHARGE_POINT_ID = "chargePointId";
  private static final String TRANSACTION_ID = "transactionId";

  private static final Logger log = LoggerFactory.getLogger(StopTransactionHandler.class);

  private final Clock clock;
  private final SessionProperties properties;
  private final IdTagAuthorization authorization;
  private final SessionRepository sessions;
  private final MeterReadingRepository readings;
  private final ApplicationEventPublisher events;

  StopTransactionHandler(
      Clock clock,
      SessionProperties properties,
      IdTagAuthorization authorization,
      SessionRepository sessions,
      MeterReadingRepository readings,
      ApplicationEventPublisher events) {
    this.clock = clock;
    this.properties = properties;
    this.authorization = authorization;
    this.sessions = sessions;
    this.readings = readings;
    this.events = events;
  }

  @Override
  public Action action() {
    return Action.STOP_TRANSACTION;
  }

  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    StopTransactionRequest stopped = OcppMessages.read(request, StopTransactionRequest.class);
    Instant receivedAt = clock.instant();
    int transactionId = stopped.transactionId();
    // A session of another charge point is treated as unknown, so that no charge point can end
    // the sessions of another one.
    Optional<Session> found =
        sessions.findById(transactionId).filter(session -> session.startedBy(chargePointId));
    if (found.isEmpty()) {
      log.atWarn()
          .addKeyValue(CHARGE_POINT_ID, chargePointId)
          .addKeyValue(TRANSACTION_ID, transactionId)
          .log("Ignoring a stop from {} for unknown transaction {}", chargePointId, transactionId);
    } else if (!found.get().isOpen()) {
      log.atInfo()
          .addKeyValue(CHARGE_POINT_ID, chargePointId)
          .addKeyValue(TRANSACTION_ID, transactionId)
          .log("Ignoring a second stop from {} for transaction {}", chargePointId, transactionId);
    } else {
      SessionStop stop = toDomain(stopped, receivedAt);
      ClockGap gap = ClockGap.between(stop.stoppedAt(), receivedAt, properties.clockGapThreshold());
      Session session = found.get().close(stop, gap);
      sessions.save(session);
      // Only with the first stop: a repeated stop carries the same readings again.
      List<MeterValue> transactionData = stopped.transactionData();
      if (transactionData != null) {
        readings.addAll(transactionId, MeterReadings.from(transactionData, receivedAt));
      }
      events.publishEvent(session.completed());
      ClockGaps.report(gap, chargePointId, transactionId, "stop", stop.stoppedAt(), receivedAt);
      log.atInfo()
          .addKeyValue(CHARGE_POINT_ID, chargePointId)
          .addKeyValue(TRANSACTION_ID, transactionId)
          .log("Stopped transaction {} on {}", transactionId, chargePointId);
    }
    return OcppMessages.write(new StopTransactionResponse(idTagInfo(stopped.idTag())));
  }

  /** The tag is checked whatever happened to the session: the charge point caches the answer. */
  private @Nullable IdTagInfo idTagInfo(@Nullable String idTag) {
    if (idTag == null) {
      return null;
    }
    Authorization tag = authorization.authorize(idTag);
    return IdTagInfos.of(TagStatus.of(tag.status()), tag);
  }

  private static SessionStop toDomain(StopTransactionRequest stopped, Instant receivedAt) {
    Reason reason = stopped.reason();
    return new SessionStop(
        stopped.idTag(),
        stopped.meterStop(),
        stopped.timestamp(),
        reason == null ? StopReason.LOCAL : toDomain(reason),
        receivedAt);
  }

  private static StopReason toDomain(Reason reason) {
    return switch (reason) {
      case EMERGENCY_STOP -> StopReason.EMERGENCY_STOP;
      case EV_DISCONNECTED -> StopReason.EV_DISCONNECTED;
      case HARD_RESET -> StopReason.HARD_RESET;
      case LOCAL -> StopReason.LOCAL;
      case OTHER -> StopReason.OTHER;
      case POWER_LOSS -> StopReason.POWER_LOSS;
      case REBOOT -> StopReason.REBOOT;
      case REMOTE -> StopReason.REMOTE;
      case SOFT_RESET -> StopReason.SOFT_RESET;
      case UNLOCK_COMMAND -> StopReason.UNLOCK_COMMAND;
      case DE_AUTHORIZED -> StopReason.DE_AUTHORIZED;
    };
  }
}
