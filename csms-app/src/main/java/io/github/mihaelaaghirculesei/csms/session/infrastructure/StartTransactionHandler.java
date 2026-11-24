package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.IdTagAuthorization;
import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.IdTagInfo;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opens a session when a charge point starts charging and gives it a transaction id. A refused tag
 * is answered with its status in a normal response, as for Authorize.
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
    return OcppMessages.write(
        new StartTransactionResponse(idTagInfo(session.tagStatus(), tag), session.transactionId()));
  }

  private static IdTagInfo idTagInfo(TagStatus status, Authorization tag) {
    AuthorizationStatus answer =
        switch (status) {
          case ACCEPTED -> AuthorizationStatus.ACCEPTED;
          case BLOCKED -> AuthorizationStatus.BLOCKED;
          case EXPIRED -> AuthorizationStatus.EXPIRED;
          case INVALID -> AuthorizationStatus.INVALID;
          case CONCURRENT_TX -> AuthorizationStatus.CONCURRENT_TX;
        };
    return new IdTagInfo(answer, tag.expiryDate(), tag.parentIdTag());
  }
}
