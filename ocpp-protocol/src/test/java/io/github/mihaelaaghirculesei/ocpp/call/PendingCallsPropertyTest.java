package io.github.mihaelaaghirculesei.ocpp.call;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.databind.node.JsonNodeFactory;

class PendingCallsPropertyTest {

  /** Answers are matched by message id only, so any other id must leave the call waiting. */
  @Property
  void onlyTheSameMessageIdCompletesTheCall(
      @ForAll("messageIds") String sent, @ForAll("messageIds") String answered) {
    Assume.that(!sent.equals(answered));
    PendingCalls calls = new PendingCalls(Duration.ofMinutes(1));
    CompletableFuture<CallResult> future = calls.begin("CP-1", PendingCallsTest.call(sent));

    boolean matched =
        calls.answer("CP-1", new CallResult(answered, JsonNodeFactory.instance.objectNode()));

    assertThat(matched).isFalse();
    assertThat(future).isNotDone();
  }

  @Provide
  Arbitrary<String> messageIds() {
    return Arbitraries.strings().alpha().numeric().withChars('-').ofMinLength(1).ofMaxLength(36);
  }
}
