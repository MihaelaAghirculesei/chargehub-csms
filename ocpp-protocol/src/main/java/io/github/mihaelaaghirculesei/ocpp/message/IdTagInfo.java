package io.github.mihaelaaghirculesei.ocpp.message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/**
 * What the central system knows about an id tag. Shared by Authorize, StartTransaction and
 * StopTransaction.
 *
 * @param expiryDate when the charge point should stop trusting a cached copy of this answer
 * @param parentIdTag groups tags that may stop each other's transactions
 */
public record IdTagInfo(
    AuthorizationStatus status, @Nullable Instant expiryDate, @Nullable String parentIdTag) {

  // The examples of the specification, and many charge points, stop at milliseconds.
  public IdTagInfo {
    if (expiryDate != null) {
      expiryDate = expiryDate.truncatedTo(ChronoUnit.MILLIS);
    }
  }
}
