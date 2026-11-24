package io.github.mihaelaaghirculesei.csms.session.domain;

import java.util.Optional;

/** Where charging sessions are kept. */
public interface SessionRepository {

  /** Reserves a transaction id that no other session has or will get. */
  int nextTransactionId();

  Optional<Session> findById(int transactionId);

  /** Finds the session a start already opened, when a charge point sends the start again. */
  Optional<Session> findByStart(SessionStart start);

  /** Whether the tag, in upper case, has an open session that was accepted. */
  boolean isCharging(String idTag);

  void save(Session session);
}
