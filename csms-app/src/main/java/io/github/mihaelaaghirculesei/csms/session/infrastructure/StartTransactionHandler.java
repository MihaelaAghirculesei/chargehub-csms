package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.IdTagAuthorization;
import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opens a session when a charge point starts charging and gives it a transaction id. A refused tag
 * is answered with its status in a normal response, as for Authorize. A start the charge point
 * sends again gets the transaction id of the first one, see docs/adr/0016.
 */
@Component
class StartTransactionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(StartTransactionHandler.class);

  private final IdTagAuthorization authorization;
  private final SessionRepository sessions;
  private final ApplicationEventPublisher events;

  StartTransactionHandler(
      IdTagAuthorization authorization,
      SessionRepository sessions,
      ApplicationEventPublisher events) {
    this.authorization = authorization;
    this.sessions = sessions;
    this.events = events;
  }

  @Override
  public Action action() {
    return Action.START_TRANSACTION;
  }

  // The session is committed when this method returns, before the dispatcher sends the answer. If
  // the central system crashes in between, the charge point gets no answer and sends the start
  // again; it never holds a transaction id the central system does not know.
  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    StartTransactionRequest started = OcppMessages.read(request, StartTransactionRequest.class);
    SessionStart start =
        new SessionStart(
            chargePointId,
            started.connectorId(),
            started.idTag(),
            started.meterStart(),
            started.timestamp());
    Authorization tag = authorization.authorize(start.idTag());
    // Before the check for a tag that is already charging: a repeated start would find its own
    // session there and get ConcurrentTx.
    Optional<Session> repeated = sessions.findByStart(start);
    if (repeated.isPresent()) {
      Session session = repeated.get();
      log.atInfo()
          .addKeyValue("chargePointId", chargePointId)
          .addKeyValue("transactionId", session.transactionId())
          .log(
              "Answering a repeated start from {} with transaction {}",
              chargePointId,
              session.transactionId());
      return answer(session, tag);
    }
    Session session =
        Session.open(
            sessions.nextTransactionId(), start, tag.status(), sessions.isCharging(start.idTag()));
    sessions.save(session);
    events.publishEvent(session.started());
    log.atInfo()
        .addKeyValue("chargePointId", chargePointId)
        .addKeyValue("transactionId", session.transactionId())
        .log(
            "Started transaction {} on {} connector {} with tag status {}",
            session.transactionId(),
            chargePointId,
            start.connectorId(),
            session.tagStatus());
    return answer(session, tag);
  }

  private static ObjectNode answer(Session session, Authorization tag) {
    return OcppMessages.write(
        new StartTransactionResponse(
            IdTagInfos.of(session.tagStatus(), tag), session.transactionId()));
  }
}
