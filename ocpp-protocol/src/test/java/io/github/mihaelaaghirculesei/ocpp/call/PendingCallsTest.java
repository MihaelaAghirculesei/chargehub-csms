package io.github.mihaelaaghirculesei.ocpp.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class PendingCallsTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final ObjectNode EMPTY = JsonNodeFactory.instance.objectNode();

  private final PendingCalls calls = new PendingCalls(Duration.ofMinutes(1));

  @Test
  void completesTheCallWithItsResult() {
    CompletableFuture<CallResult> future = calls.begin("CP-1", call("m1"));
    CallResult result = new CallResult("m1", EMPTY);

    assertThat(calls.answer("CP-1", result)).isTrue();

    assertThat(future).succeedsWithin(WAIT).isEqualTo(result);
  }

  @Test
  void failsTheCallWithTheErrorOfTheChargePoint() {
    CompletableFuture<CallResult> future = calls.begin("CP-1", call("m1"));
    CallError error = new CallError("m1", ErrorCode.NOT_SUPPORTED, "No reset", EMPTY);

    assertThat(calls.answer("CP-1", error)).isTrue();

    assertThat(future)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOfSatisfying(
            CallErrorException.class, e -> assertThat(e.error()).isEqualTo(error));
  }

  @Test
  void refusesASecondCallWhileOneIsOutstanding() {
    CompletableFuture<CallResult> outstanding = calls.begin("CP-1", call("m1"));

    assertThatThrownBy(() -> calls.begin("CP-1", call("m2")))
        .isInstanceOf(CallInProgressException.class);
    assertThat(outstanding).isNotDone();
    assertThatNoException().isThrownBy(() -> calls.begin("CP-2", call("m2")));
  }

  @Test
  void acceptsTheNextCallAsSoonAsTheOutcomeIsKnown() {
    CompletableFuture<CallResult> first = calls.begin("CP-1", call("m1"));
    calls.answer("CP-1", new CallResult("m1", EMPTY));

    first.join();

    assertThatNoException().isThrownBy(() -> calls.begin("CP-1", call("m2")));
  }

  @Test
  void ignoresAnAnswerWithAnotherMessageId() {
    CompletableFuture<CallResult> future = calls.begin("CP-1", call("m1"));

    assertThat(calls.answer("CP-1", new CallResult("other", EMPTY))).isFalse();
    assertThat(calls.answer("CP-2", new CallResult("m1", EMPTY))).isFalse();

    assertThat(future).isNotDone();
  }

  @Test
  void failsTheCallAfterTheTimeoutAndFreesTheChargePoint() {
    PendingCalls quick = new PendingCalls(Duration.ofMillis(50));
    CompletableFuture<CallResult> future = quick.begin("CP-1", call("m1"));

    assertThat(future)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(TimeoutException.class);
    assertThat(quick.answer("CP-1", new CallResult("m1", EMPTY))).isFalse();
    assertThatNoException().isThrownBy(() -> quick.begin("CP-1", call("m2")));
  }

  @Test
  void failsTheCallAtOnceWhenTheConnectionCloses() {
    CompletableFuture<CallResult> future = calls.begin("CP-1", call("m1"));

    calls.connectionClosed("CP-1");

    assertThat(future)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConnectionClosedException.class);
    assertThatNoException().isThrownBy(() -> calls.connectionClosed("CP-1"));
  }

  @Test
  void needsAPositiveTimeout() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PendingCalls(Duration.ZERO));
  }

  static Call call(String messageId) {
    return new Call(messageId, "Reset", EMPTY);
  }
}
