package io.github.mihaelaaghirculesei.ocpp.frame;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class OcppFrameTest {

  private static final ObjectNode EMPTY = JsonNodeFactory.instance.objectNode();

  @Test
  void acceptsMessageIdOfThirtySixCharacters() {
    assertThatNoException().isThrownBy(() -> new CallResult("a".repeat(36), EMPTY));
  }

  @Test
  void rejectsMessageIdLongerThanThirtySixCharacters() {
    assertThatIllegalArgumentException().isThrownBy(() -> new CallResult("a".repeat(37), EMPTY));
  }

  @Test
  void rejectsEmptyMessageId() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CallError("", ErrorCode.GENERIC_ERROR, "", EMPTY));
  }

  /** Each emoji is two Java chars but one character for OCPP, so 36 of them still fit. */
  @Test
  void countsMessageIdLengthInCodePoints() {
    assertThatNoException().isThrownBy(() -> new CallResult("😀".repeat(36), EMPTY));
  }

  @Test
  void rejectsEmptyAction() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Call("19223201", "", EMPTY));
  }
}
