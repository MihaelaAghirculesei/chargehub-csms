package io.github.mihaelaaghirculesei.ocpp.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class MaxLengthPropertyTest {

  private final PayloadValidator validator = new PayloadValidator("/schemas/");

  /**
   * JSON Schema counts maxLength in Unicode code points, Java String.length() in UTF-16 units. An
   * emoji is one code point but two chars, so the two counts disagree exactly at the limit.
   */
  @Property
  void acceptsExactlyVendorNamesOfAtMostTwentyCodePoints(@ForAll("vendorNames") String vendor) {
    boolean withinLimit = vendor.codePointCount(0, vendor.length()) <= 20;

    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("chargePointVendor", vendor).put("chargePointModel", "Simulator X");

    assertThat(validator.validate("BootNotification", PayloadType.REQUEST, payload).isEmpty())
        .isEqualTo(withinLimit);
  }

  /**
   * The default string generator almost never produces surrogate pairs, so it would not reach the
   * case above. Names mix printable ASCII with code points outside the Basic Multilingual Plane.
   */
  @Provide
  Arbitrary<String> vendorNames() {
    Arbitrary<Integer> codePoint =
        Arbitraries.frequencyOf(
            Tuple.of(3, Arbitraries.integers().between(0x20, 0x7E)),
            Tuple.of(1, Arbitraries.integers().between(0x1F300, 0x1FAFF)));
    return codePoint.list().ofMaxSize(30).map(MaxLengthPropertyTest::fromCodePoints);
  }

  private static String fromCodePoints(List<Integer> codePoints) {
    StringBuilder name = new StringBuilder();
    codePoints.forEach(name::appendCodePoint);
    return name.toString();
  }
}
