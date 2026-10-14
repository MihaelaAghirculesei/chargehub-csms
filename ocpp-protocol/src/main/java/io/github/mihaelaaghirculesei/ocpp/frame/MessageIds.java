package io.github.mihaelaaghirculesei.ocpp.frame;

final class MessageIds {

  // The limit set by OCPP-J 1.6 for the unique message id.
  static final int MAX_LENGTH = 36;

  private MessageIds() {}

  static void requireValid(String messageId) {
    // Counted in code points, like maxLength in the OCPP JSON schemas.
    int length = messageId.codePointCount(0, messageId.length());
    if (length == 0 || length > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Message id must have 1 to " + MAX_LENGTH + " characters, got " + length);
    }
  }
}
