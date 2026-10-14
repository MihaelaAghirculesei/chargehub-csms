package io.github.mihaelaaghirculesei.ocpp.frame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class FrameRoundTripPropertyTest {

  private final FrameCodec codec = new FrameCodec();

  @Property
  void decodingAnEncodedFrameGivesTheSameFrame(@ForAll("frames") OcppFrame frame) {
    assertThat(codec.decode(codec.encode(frame))).isEqualTo(frame);
  }

  @Provide
  Arbitrary<OcppFrame> frames() {
    Arbitrary<Call> calls = Combinators.combine(messageIds(), actions(), objects()).as(Call::new);
    Arbitrary<CallResult> results =
        Combinators.combine(messageIds(), objects()).as(CallResult::new);
    Arbitrary<CallError> errors =
        Combinators.combine(messageIds(), Arbitraries.of(ErrorCode.class), texts(), objects())
            .as(CallError::new);
    return Arbitraries.oneOf(calls, results, errors);
  }

  private static Arbitrary<String> messageIds() {
    return Arbitraries.strings()
        .alpha()
        .numeric()
        .withChars('-')
        .ofMinLength(1)
        .ofMaxLength(MessageIds.MAX_LENGTH);
  }

  private static Arbitrary<String> actions() {
    return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
  }

  /** Quotes, backslashes, control characters and non-ASCII text all need escaping in JSON. */
  private static Arbitrary<String> texts() {
    return Arbitraries.strings()
        .ascii()
        .withCharRange(' ', '߿')
        .withChars('"', '\\', '\n', '\t')
        .ofMaxLength(50);
  }

  // Integers only: a number written as 5 is read back as an int node, so a long node would not
  // compare equal even though the JSON text is the same.
  private static Arbitrary<ObjectNode> objects() {
    Arbitrary<Object> values =
        Arbitraries.oneOf(
            texts().map(Object.class::cast),
            Arbitraries.integers().map(Object.class::cast),
            Arbitraries.of(true, false).map(Object.class::cast));
    return Arbitraries.maps(actions(), values)
        .ofMaxSize(5)
        .map(FrameRoundTripPropertyTest::toObject);
  }

  private static ObjectNode toObject(Map<String, Object> fields) {
    ObjectNode object = JsonNodeFactory.instance.objectNode();
    fields.forEach(
        (name, value) -> {
          switch (value) {
            case String text -> object.put(name, text);
            case Integer number -> object.put(name, number);
            case Boolean flag -> object.put(name, flag);
            default -> throw new IllegalArgumentException("Unexpected value " + value);
          }
        });
    return object;
  }
}
