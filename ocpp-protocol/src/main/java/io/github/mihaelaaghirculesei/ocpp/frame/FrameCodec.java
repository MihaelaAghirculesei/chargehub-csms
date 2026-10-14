package io.github.mihaelaaghirculesei.ocpp.frame;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Reads and writes OCPP-J frames. Thread-safe. */
public final class FrameCodec {

  private static final int CALL = 2;
  private static final int CALL_RESULT = 3;
  private static final int CALL_ERROR = 4;

  // Text after the closing bracket means the sender and this codec disagree on where a frame ends.
  private final JsonMapper mapper =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

  /**
   * Every structural problem is reported as FormationViolation, which OCPP-J 1.6 defines as a
   * message that does not conform to the frame structure.
   *
   * @throws FrameDecodingException if the text is not a valid frame
   */
  public OcppFrame decode(String text) {
    JsonNode frame;
    try {
      frame = mapper.readTree(text);
    } catch (JacksonException e) {
      throw invalid(null, "Frame is not valid JSON", e);
    }
    if (!frame.isArray()) {
      throw invalid(null, "Frame is not a JSON array", null);
    }
    // Read before anything else can fail, so the error can still be sent back to the caller.
    @Nullable String messageId = frame.path(1).isString() ? frame.path(1).stringValue() : null;
    JsonNode typeId = frame.path(0);
    if (!typeId.isInt()) {
      throw invalid(messageId, "Message type id is not an integer", null);
    }
    return switch (typeId.intValue()) {
      case CALL -> {
        requireSize(frame, 4, messageId);
        String id = requireId(messageId);
        String action = string(frame, 2, "Action", messageId);
        ObjectNode payload = object(frame, 3, "Payload", messageId);
        yield create(() -> new Call(id, action, payload), id);
      }
      case CALL_RESULT -> {
        requireSize(frame, 3, messageId);
        String id = requireId(messageId);
        ObjectNode payload = object(frame, 2, "Payload", messageId);
        yield create(() -> new CallResult(id, payload), id);
      }
      case CALL_ERROR -> {
        requireSize(frame, 5, messageId);
        String id = requireId(messageId);
        String name = string(frame, 2, "Error code", messageId);
        ErrorCode errorCode =
            ErrorCode.fromWireName(name)
                .orElseThrow(() -> invalid(messageId, "Unknown error code " + name, null));
        String description = string(frame, 3, "Error description", messageId);
        ObjectNode details = object(frame, 4, "Error details", messageId);
        yield create(() -> new CallError(id, errorCode, description, details), id);
      }
      default -> throw invalid(messageId, "Unknown message type id " + typeId.intValue(), null);
    };
  }

  public String encode(OcppFrame frame) {
    ArrayNode array = mapper.createArrayNode();
    switch (frame) {
      case Call call ->
          array.add(CALL).add(call.messageId()).add(call.action()).add(call.payload());
      case CallResult result ->
          array.add(CALL_RESULT).add(result.messageId()).add(result.payload());
      case CallError error ->
          array
              .add(CALL_ERROR)
              .add(error.messageId())
              .add(error.errorCode().wireName())
              .add(error.errorDescription())
              .add(error.errorDetails());
    }
    return mapper.writeValueAsString(array);
  }

  private static void requireSize(JsonNode frame, int size, @Nullable String messageId) {
    if (frame.size() != size) {
      throw invalid(messageId, "Expected " + size + " elements, got " + frame.size(), null);
    }
  }

  private static String requireId(@Nullable String messageId) {
    if (messageId == null) {
      throw invalid(null, "Message id is not a string", null);
    }
    return messageId;
  }

  private static String string(JsonNode frame, int index, String name, @Nullable String messageId) {
    JsonNode node = frame.path(index);
    if (!node.isString()) {
      throw invalid(messageId, name + " is not a string", null);
    }
    return node.stringValue();
  }

  private static ObjectNode object(
      JsonNode frame, int index, String name, @Nullable String messageId) {
    JsonNode node = frame.path(index);
    if (!(node instanceof ObjectNode object)) {
      throw invalid(messageId, name + " is not a JSON object", null);
    }
    return object;
  }

  /** The frame constructors check the OCPP limits; here a broken limit becomes a decoding error. */
  private static OcppFrame create(Supplier<OcppFrame> frame, @Nullable String messageId) {
    try {
      return frame.get();
    } catch (IllegalArgumentException e) {
      throw invalid(messageId, "Invalid frame: " + e.getMessage(), e);
    }
  }

  private static FrameDecodingException invalid(
      @Nullable String messageId, String message, @Nullable Throwable cause) {
    return new FrameDecodingException(ErrorCode.FORMATION_VIOLATION, messageId, message, cause);
  }
}
