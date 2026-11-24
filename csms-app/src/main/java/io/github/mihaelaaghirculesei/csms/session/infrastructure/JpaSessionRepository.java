package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Keeps charging sessions in PostgreSQL. */
@Repository
class JpaSessionRepository implements SessionRepository {

  private final SessionEntities entities;
  private final EntityManager entityManager;

  JpaSessionRepository(SessionEntities entities, EntityManager entityManager) {
    this.entities = entities;
    this.entityManager = entityManager;
  }

  // A sequence hands out each value once, even to transactions that roll back, so two instances
  // of the central system never give out the same id.
  @Override
  public int nextTransactionId() {
    Long next =
        (Long)
            entityManager
                .createNativeQuery("select nextval('transaction_id')", Long.class)
                .getSingleResult();
    return Math.toIntExact(next);
  }

  // Until sessions can be stopped, every accepted session is open.
  @Override
  public boolean isCharging(String idTag) {
    return entities.existsByIdTagAndTagStatus(idTag, TagStatus.ACCEPTED);
  }

  @Override
  public void save(Session session) {
    entities.save(new SessionEntity(session));
  }
}
