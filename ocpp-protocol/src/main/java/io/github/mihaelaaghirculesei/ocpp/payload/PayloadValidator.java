package io.github.mihaelaaghirculesei.ocpp.payload;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks OCPP payloads against their JSON schemas and maps a failure to the OCPP error code to
 * answer with. See docs/adr/0008. Thread-safe.
 */
public final class PayloadValidator {

  // The OCPP 1.6 schemas are written in draft-04.
  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4);

  private final String schemaDirectory;
  // Keyed by enum values only, so the cache is bounded no matter what charge points send.
  private final Map<SchemaKey, Schema> schemas = new ConcurrentHashMap<>();

  PayloadValidator(String schemaDirectory) {
    this.schemaDirectory = schemaDirectory;
  }

  /**
   * Returns the reason the payload is refused, or empty if it is valid.
   *
   * @param action the action name exactly as it appears in the frame
   */
  public Optional<PayloadViolation> validate(String action, PayloadType type, ObjectNode payload) {
    Optional<Action> known = Action.fromWireName(action);
    if (known.isEmpty()) {
      return Optional.of(
          new PayloadViolation(ErrorCode.NOT_IMPLEMENTED, "Unknown action " + action));
    }
    List<Error> errors = schema(new SchemaKey(known.get(), type)).validate(payload);
    if (errors.isEmpty()) {
      return Optional.empty();
    }
    // A CALLERROR carries one code: the first error decides it, the description lists them all.
    String description = errors.stream().map(Error::getMessage).collect(Collectors.joining("; "));
    return Optional.of(new PayloadViolation(errorCode(errors.getFirst()), description));
  }

  private static ErrorCode errorCode(Error error) {
    return switch (error.getKeyword()) {
      case "required" -> ErrorCode.OCCURRENCE_CONSTRAINT_VIOLATION;
      case "type" -> ErrorCode.TYPE_CONSTRAINT_VIOLATION;
      case null, default -> ErrorCode.PROPERTY_CONSTRAINT_VIOLATION;
    };
  }

  private Schema schema(SchemaKey key) {
    return schemas.computeIfAbsent(key, this::load);
  }

  private Schema load(SchemaKey key) {
    String resource = schemaDirectory + key.fileName();
    try (InputStream in = PayloadValidator.class.getResourceAsStream(resource)) {
      // Every action of the enum has both schemas, so a missing one is a packaging bug.
      if (in == null) {
        throw new IllegalStateException("Missing OCPP schema " + resource);
      }
      return REGISTRY.getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record SchemaKey(Action action, PayloadType type) {

    // The naming used by the Open Charge Alliance in the published schema files.
    String fileName() {
      return switch (type) {
        case REQUEST -> action.wireName() + ".json";
        case RESPONSE -> action.wireName() + "Response.json";
      };
    }
  }
}
