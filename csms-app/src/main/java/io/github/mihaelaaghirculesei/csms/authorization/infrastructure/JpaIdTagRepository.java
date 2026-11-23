package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTag;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Reads id tags from PostgreSQL. */
@Repository
class JpaIdTagRepository implements IdTagRepository {

  private final IdTagEntities entities;

  JpaIdTagRepository(IdTagEntities entities) {
    this.entities = entities;
  }

  // The schema already caps the length, but an empty tag passes it and cannot be in the table.
  @Override
  public Optional<IdTag> find(String idTag) {
    if (idTag.isBlank() || idTag.length() > IdTag.MAX_LENGTH) {
      return Optional.empty();
    }
    return entities.findById(IdTag.normalize(idTag)).map(IdTagEntity::toDomain);
  }
}
