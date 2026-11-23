package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.IdTagAuthorization;
import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeRequest;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeResponse;
import io.github.mihaelaaghirculesei.ocpp.message.IdTagInfo;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Answers whether a tag presented at a charge point may charge. A refused tag is a normal response
 * with its status, not a CALLERROR: the charge point must show the driver the reason.
 */
@Component
class AuthorizeHandler implements ActionHandler {

  private final IdTagAuthorization authorization;

  AuthorizeHandler(IdTagAuthorization authorization) {
    this.authorization = authorization;
  }

  @Override
  public Action action() {
    return Action.AUTHORIZE;
  }

  @Override
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    AuthorizeRequest authorize = OcppMessages.read(request, AuthorizeRequest.class);
    Authorization answer = authorization.authorize(authorize.idTag());
    return OcppMessages.write(new AuthorizeResponse(idTagInfo(answer)));
  }

  static IdTagInfo idTagInfo(Authorization answer) {
    AuthorizationStatus status =
        switch (answer.status()) {
          case ACCEPTED -> AuthorizationStatus.ACCEPTED;
          case BLOCKED -> AuthorizationStatus.BLOCKED;
          case EXPIRED -> AuthorizationStatus.EXPIRED;
          case INVALID -> AuthorizationStatus.INVALID;
        };
    return new IdTagInfo(status, answer.expiryDate(), answer.parentIdTag());
  }
}
