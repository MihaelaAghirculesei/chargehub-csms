package io.github.mihaelaaghirculesei.csms.session.domain;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;

/** What the central system answered for a tag at the start or the stop of a session. */
public enum TagStatus {
  ACCEPTED,
  BLOCKED,
  EXPIRED,
  INVALID,
  /** The tag was valid but already charging somewhere else. */
  CONCURRENT_TX;

  /** The answer for a tag checked on its own, without looking at other sessions. */
  public static TagStatus of(Authorization.Status tagCheck) {
    return switch (tagCheck) {
      case ACCEPTED -> ACCEPTED;
      case BLOCKED -> BLOCKED;
      case EXPIRED -> EXPIRED;
      case INVALID -> INVALID;
    };
  }
}
