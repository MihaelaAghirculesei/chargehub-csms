package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL advisory locks that last until the end of the current transaction. PostgreSQL releases
 * them at commit or rollback, and also when the connection of an instance that died is closed, so a
 * crash never leaves a lock behind.
 */
@Component
class AdvisoryLocks {

  private final EntityManager entityManager;

  AdvisoryLocks(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * Takes the lock without waiting.
   *
   * @return false when another transaction holds it
   */
  // Outside a transaction the lock would be released as soon as the query returns.
  @Transactional(propagation = Propagation.MANDATORY)
  boolean tryLockForTransaction(long key) {
    // A native query is untyped in JPA; PostgreSQL returns a boolean for this function.
    return (Boolean)
        entityManager
            .createNativeQuery("select pg_try_advisory_xact_lock(?1)", Boolean.class)
            .setParameter(1, key)
            .getSingleResult();
  }
}
