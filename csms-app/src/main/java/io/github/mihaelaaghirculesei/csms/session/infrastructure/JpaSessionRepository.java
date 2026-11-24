package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import jakarta.persistence.EntityManager;
import java.util.Optional;
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

  @Override
  public Optional<Session> findById(int transactionId) {
    return entities.findById(transactionId).map(SessionEntity::toDomain);
  }

  @Override
  public Optional<Session> findByStart(SessionStart start) {
    return entities
        .findByStationIdAndConnectorIdAndIdTagAndMeterStartAndStartedAt(
            start.stationId(),
            start.connectorId(),
            start.idTag(),
            start.meterStart(),
            start.startedAt())
        .map(SessionEntity::toDomain);
  }

  @Override
  public boolean isCharging(String idTag) {
    return entities.existsByIdTagAndTagStatusAndStopIsNull(idTag, TagStatus.ACCEPTED);
  }

  @Override
  public void save(Session session) {
    // Inside the transaction that loaded the session this is the same entity instance, so the
    // version checked on update is the one read at load time.
    SessionEntity entity =
        entities
            .findById(session.transactionId())
            .map(found -> {
              found.copy(session);
              return found;
            })
            .orElseGet(() -> new SessionEntity(session));
    entities.save(entity);
  }
}
