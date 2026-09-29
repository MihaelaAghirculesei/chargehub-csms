package io.github.mihaelaaghirculesei.ocpp;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** OCPP 1.6 schemas are JSON Schema draft-04; this one is a small test subset. */
final class BootNotificationSchema {

  private static final Schema SCHEMA =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)
          .getSchema(read("/schemas/BootNotification.json"));

  private BootNotificationSchema() {}

  static List<Error> validate(JsonNode payload) {
    return SCHEMA.validate(payload);
  }

  static ObjectNode payload(String vendor, String model) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("chargePointVendor", vendor);
    payload.put("chargePointModel", model);
    return payload;
  }

  private static String read(String resource) {
    try (InputStream in = BootNotificationSchema.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
