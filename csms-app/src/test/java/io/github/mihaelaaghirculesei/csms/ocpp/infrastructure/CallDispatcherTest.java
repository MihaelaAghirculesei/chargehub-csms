package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import io.github.mihaelaaghirculesei.ocpp.frame.OcppFrame;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Runs on the official schemas, with handlers for actions the project will not implement. */
class CallDispatcherTest {

  private static final String RESERVE_NOW =
      """
      {"connectorId":1,"expiryDate":"2026-10-17T12:00:00Z","idTag":"TAG-1","reservationId":7}""";

  private final AtomicInteger reserveNowCalls = new AtomicInteger();

  private final CallDispatcher dispatcher =
      new CallDispatcher(
          PayloadValidator.ocpp16(),
          List.of(
              handler(
                  Action.RESERVE_NOW,
                  request -> {
                    reserveNowCalls.incrementAndGet();
                    return json("{\"status\":\"Accepted\"}");
                  }),
              handler(
                  Action.CANCEL_RESERVATION,
                  request -> {
                    throw new IllegalStateException("broken handler");
                  }),
              handler(Action.UPDATE_FIRMWARE, request -> json("{\"unexpected\":true}"))));

  @Test
  void answersWithTheResponseOfTheHandler() {
    OcppFrame answer = dispatcher.dispatch("CP-1", call("ReserveNow", RESERVE_NOW));

    assertThat(answer).isInstanceOf(CallResult.class);
    assertThat(((CallResult) answer).payload()).isEqualTo(json("{\"status\":\"Accepted\"}"));
    assertThat(answer.messageId()).isEqualTo("m1");
  }

  @Test
  void answersAnActionUnknownToOcppWithNotImplemented() {
    assertThat(errorCode(dispatcher.dispatch("CP-1", call("Unknown", "{}"))))
        .isEqualTo(ErrorCode.NOT_IMPLEMENTED);
  }

  @Test
  void answersAKnownActionWithoutHandlerWithNotSupported() {
    assertThat(errorCode(dispatcher.dispatch("CP-1", call("Heartbeat", "{}"))))
        .isEqualTo(ErrorCode.NOT_SUPPORTED);
  }

  @Test
  void neverPassesAnInvalidRequestToTheHandler() {
    OcppFrame answer = dispatcher.dispatch("CP-1", call("ReserveNow", "{\"connectorId\":1}"));

    assertThat(errorCode(answer)).isEqualTo(ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION);
    assertThat(reserveNowCalls).hasValue(0);
  }

  @Test
  void answersAFailingHandlerWithInternalError() {
    OcppFrame answer =
        dispatcher.dispatch("CP-1", call("CancelReservation", "{\"reservationId\":7}"));

    assertThat(errorCode(answer)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(((CallError) answer).errorDescription()).doesNotContain("broken handler");
  }

  @Test
  void answersAnInvalidResponseOfTheHandlerWithInternalError() {
    String request =
        "{\"location\":\"https://example.com/fw\",\"retrieveDate\":\"2026-10-17T12:00:00Z\"}";

    assertThat(errorCode(dispatcher.dispatch("CP-1", call("UpdateFirmware", request))))
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void refusesTwoHandlersForTheSameAction() {
    List<ActionHandler> twice =
        List.of(handler(Action.RESET, r -> r), handler(Action.RESET, r -> r));
    PayloadValidator validator = PayloadValidator.ocpp16();

    assertThatIllegalStateException()
        .isThrownBy(() -> new CallDispatcher(validator, twice))
        .withMessageContaining("Reset");
  }

  private static ErrorCode errorCode(OcppFrame answer) {
    assertThat(answer).isInstanceOf(CallError.class);
    return ((CallError) answer).errorCode();
  }

  private static Call call(String action, String payload) {
    return new Call("m1", action, json(payload));
  }

  private static ActionHandler handler(Action action, Function<ObjectNode, ObjectNode> body) {
    return new ActionHandler() {
      @Override
      public Action action() {
        return action;
      }

      @Override
      public ObjectNode handle(String chargePointId, ObjectNode request) {
        return body.apply(request);
      }
    };
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
