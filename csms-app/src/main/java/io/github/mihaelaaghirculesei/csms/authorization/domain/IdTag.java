package io.github.mihaelaaghirculesei.csms.authorization.domain;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * An id tag known to the central system, usually the number of an RFID card.
 *
 * @param value the tag in upper case
 * @param expiresAt from this moment on the tag is expired
 * @param parentIdTag the group of the tag, in upper case
 */
public record IdTag(
    String value, IdTagStatus status, @Nullable Instant expiresAt, @Nullable String parentIdTag) {

  /** The longest tag OCPP 1.6 carries. */
  public static final int MAX_LENGTH = 20;

  public IdTag {
    value = normalize(value);
    if (parentIdTag != null) {
      parentIdTag = normalize(parentIdTag);
    }
  }

  /**
   * OCPP 1.6 compares id tags without regard to case, and card readers differ in the case they
   * write hexadecimal numbers in. One stored form keeps lookups to a primary key match.
   */
  public static String normalize(String idTag) {
    if (idTag.isBlank() || idTag.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "An id tag has 1 to " + MAX_LENGTH + " characters: '" + idTag + "'");
    }
    return idTag.toUpperCase(Locale.ROOT);
  }

  public Authorization check(Instant now) {
    Status answer =
        switch (status) {
          case BLOCKED -> Status.BLOCKED;
          case EXPIRED -> Status.EXPIRED;
          case ACCEPTED -> expiredAt(now) ? Status.EXPIRED : Status.ACCEPTED;
        };
    return new Authorization(answer, expiresAt, parentIdTag);
  }

  private boolean expiredAt(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }
}
