package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadType;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadViolation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Turns every CALL from a charge point into its CALLRESULT or CALLERROR. See docs/adr/0008. */
@Component
class CallDispatcher {

  private static final Logger log = LoggerFactory.getLogger(CallDispatcher.class);

  private final PayloadValidator validator;
  private final Map<Action, ActionHandler> handlers = new EnumMap<>(Action.class);

  CallDispatcher(PayloadValidator validator, List<ActionHandler> handlers) {
    this.validator = validator;
    for (ActionHandler handler : handlers) {
      // Two owners for one action is a wiring mistake; failing at startup beats picking one.
      ActionHandler previous = this.handlers.putIfAbsent(handler.action(), handler);
      if (previous != null) {
        throw new IllegalStateException("Two handlers for " + handler.action().wireName());
      }
    }
  }

  OcppFrame dispatch(String chargePointId, Call call) {
    // Also answers an action unknown to OCPP 1.6 with NotImplemented.
    Optional<PayloadViolation> violation =
        validator.validate(call.action(), PayloadType.REQUEST, call.payload());
    if (violation.isPresent()) {
      return error(call, violation.get().errorCode(), violation.get().description());
    }
    Action action = Action.fromWireName(call.action()).orElseThrow();
    ActionHandler handler = handlers.get(action);
    if (handler == null) {
      // NotSupported, not NotImplemented: OCPP-J keeps the second for actions it does not define.
      return error(call, ErrorCode.NOT_SUPPORTED, action.wireName() + " is not supported");
    }
    ObjectNode response;
    try {
      response = handler.handle(chargePointId, call.payload());
    } catch (RuntimeException e) {
      log.error("Handler for {} failed", action.wireName(), e);
      return internalError(call);
    }
    Optional<PayloadViolation> invalidResponse =
        validator.validate(action.wireName(), PayloadType.RESPONSE, response);
    if (invalidResponse.isPresent()) {
      log.error(
          "Handler for {} returned an invalid response: {}",
          action.wireName(),
          invalidResponse.get().description());
      return internalError(call);
    }
    return new CallResult(call.messageId(), response);
  }

  // Our own failures are not explained to the charge point: the details are in our log.
  private static CallError internalError(Call call) {
    return error(call, ErrorCode.INTERNAL_ERROR, "Internal error");
  }

  private static CallError error(Call call, ErrorCode code, String description) {
    return new CallError(
        call.messageId(), code, description, JsonNodeFactory.instance.objectNode());
  }
}
