package io.github.mihaelaaghirculesei.csms.authorization;

/** Tells whether an id tag may charge right now. */
public interface IdTagAuthorization {

  /** Answers {@link Authorization#UNKNOWN_TAG} for a tag the central system does not know. */
  Authorization authorize(String idTag);
}
