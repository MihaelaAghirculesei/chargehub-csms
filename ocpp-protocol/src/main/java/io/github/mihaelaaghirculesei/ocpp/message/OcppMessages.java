package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Converts typed messages to and from the JSON payload of a frame. Thread-safe. */
public final class OcppMessages {

  // An optional field is left out, never written as null: the schemas type it as a string, so
  // null would break them. Times are ISO-8601 strings in UTC, as the date-time format requires.
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .changeDefaultPropertyInclusion(
              inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
          .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private OcppMessages() {}

  /** Reads a payload that already passed its schema. */
  public static <T> T read(ObjectNode payload, Class<T> type) {
    return MAPPER.treeToValue(payload, type);
  }

  public static ObjectNode write(Object message) {
    return MAPPER.valueToTree(message);
  }
}
