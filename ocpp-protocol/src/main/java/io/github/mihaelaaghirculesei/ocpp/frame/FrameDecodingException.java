package io.github.mihaelaaghirculesei.ocpp.frame;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A text that is not a valid OCPP-J frame. Carries what is needed to answer with a CALLERROR: the
 * error code and, when it could be read, the message id.
 */
public final class FrameDecodingException extends RuntimeException {

  private final ErrorCode errorCode;
  private final @Nullable String messageId;
  private final boolean call;

  FrameDecodingException(
      ErrorCode errorCode, @Nullable String messageId, String message, @Nullable Throwable cause) {
    this(errorCode, messageId, false, message, cause);
  }

  private FrameDecodingException(
      ErrorCode errorCode,
      @Nullable String messageId,
      boolean call,
      @Nullable String message,
      @Nullable Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.messageId = messageId;
    this.call = call;
  }

  /** The same failure, for a frame whose message type id says it is a CALL. */
  FrameDecodingException ofCall() {
    return new FrameDecodingException(errorCode, messageId, true, getMessage(), getCause());
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public Optional<String> messageId() {
    return Optional.ofNullable(messageId);
  }

  /**
   * Whether the sender expects a CALLERROR: the frame was a CALL and its message id could be read.
   * A malformed CALLRESULT or CALLERROR is never answered, since nothing waits for an answer to it.
   */
  public boolean answerable() {
    return call && messageId != null;
  }
}
