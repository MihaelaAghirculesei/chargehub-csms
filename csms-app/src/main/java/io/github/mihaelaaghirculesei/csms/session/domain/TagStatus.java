package io.github.mihaelaaghirculesei.csms.session.domain;

/** What the central system answered for the tag when the session started. */
public enum TagStatus {
  ACCEPTED,
  BLOCKED,
  EXPIRED,
  INVALID,
  /** The tag was valid but already charging somewhere else. */
  CONCURRENT_TX
}
