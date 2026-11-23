package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTag;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** The table side of {@link IdTag}. Read only for now: tags are written by operators later. */
@Entity
@Table(name = "id_tag")
class IdTagEntity {

  @Id
  @Column(name = "id_tag")
  private String value;

  @Enumerated(EnumType.STRING)
  private IdTagStatus status;

  private @Nullable Instant expiresAt;

  private @Nullable String parentIdTag;

  @SuppressWarnings("NullAway.Init")
  protected IdTagEntity() {
    // JPA fills the fields through reflection.
  }

  IdTag toDomain() {
    return new IdTag(value, status, expiresAt, parentIdTag);
  }
}
