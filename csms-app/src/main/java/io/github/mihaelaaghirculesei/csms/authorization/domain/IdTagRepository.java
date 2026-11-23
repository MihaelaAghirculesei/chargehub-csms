package io.github.mihaelaaghirculesei.csms.authorization.domain;

import java.util.Optional;

/** Where the id tags known to the central system are kept. */
public interface IdTagRepository {

  /** Finds a tag in any case; a value that is not a valid tag is simply not found. */
  Optional<IdTag> find(String idTag);
}
