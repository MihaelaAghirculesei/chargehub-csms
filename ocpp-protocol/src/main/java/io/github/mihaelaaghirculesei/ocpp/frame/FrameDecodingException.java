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

  FrameDecodingException(
      ErrorCode errorCode, @Nullable String messageId, String message, @Nullable Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.messageId = messageId;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public Optional<String> messageId() {
    return Optional.ofNullable(messageId);
  }
}
