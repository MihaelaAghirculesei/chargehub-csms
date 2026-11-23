package io.github.mihaelaaghirculesei.csms.authorization.domain;

/** The status an operator gives a tag. A tag past its expiry date is expired whatever this says. */
public enum IdTagStatus {
  ACCEPTED,
  BLOCKED,
  EXPIRED
}
