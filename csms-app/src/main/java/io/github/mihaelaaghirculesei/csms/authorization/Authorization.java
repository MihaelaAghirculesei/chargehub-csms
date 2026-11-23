package io.github.mihaelaaghirculesei.csms.authorization;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The answer for one id tag at one moment.
 *
 * @param expiryDate when the tag stops being valid, if it ever does
 * @param parentIdTag the group of the tag: tags of one group may stop each other's charging
 */
public record Authorization(
    Status status, @Nullable Instant expiryDate, @Nullable String parentIdTag) {

  public static final Authorization UNKNOWN_TAG = new Authorization(Status.INVALID, null, null);

  public enum Status {
    ACCEPTED,
    BLOCKED,
    EXPIRED,
    INVALID
  }
}
