package io.github.mihaelaaghirculesei.csms.session.domain;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.session.SessionCompleted;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;
import org.jspecify.annotations.Nullable;

/**
 * A charging session.
 *
 * @param transactionId chosen by the central system before it answers the charge point
 * @param stop null while the session is open
 */
public record Session(
    int transactionId, SessionStart start, TagStatus tagStatus, @Nullable SessionStop stop) {

  /**
   * Opens a session whatever the tag status. OCPP 1.6 asks the central system to store a refused
   * start too: the charge point decides whether to stop charging, and its later messages must match
   * a known transaction.
   *
   * @param tagAlreadyCharging whether the tag has another open session that was accepted
   */
  public static Session open(
      int transactionId,
      SessionStart start,
      Authorization.Status tagCheck,
      boolean tagAlreadyCharging) {
    TagStatus tagStatus =
        switch (tagCheck) {
          case ACCEPTED -> tagAlreadyCharging ? TagStatus.CONCURRENT_TX : TagStatus.ACCEPTED;
          case BLOCKED -> TagStatus.BLOCKED;
          case EXPIRED -> TagStatus.EXPIRED;
          case INVALID -> TagStatus.INVALID;
        };
    return new Session(transactionId, start, tagStatus, null);
  }

  public boolean authorized() {
    return tagStatus == TagStatus.ACCEPTED;
  }

  public boolean isOpen() {
    return stop == null;
  }

  /** Only the charge point that started a session may stop it. */
  public boolean startedBy(String stationId) {
    return start.stationId().equals(stationId);
  }

  /**
   * Closes the session.
   *
   * @throws IllegalStateException when it is already stopped: the first stop stays the true one
   */
  public Session close(SessionStop stop) {
    if (!isOpen()) {
      throw new IllegalStateException("Transaction " + transactionId + " is already stopped");
    }
    return new Session(transactionId, start, tagStatus, stop);
  }

  public SessionStarted started() {
    return new SessionStarted(
        transactionId,
        start.stationId(),
        start.connectorId(),
        start.idTag(),
        authorized(),
        start.startedAt());
  }

  /**
   * The event for a stopped session.
   *
   * @throws IllegalStateException while the session is open
   */
  public SessionCompleted completed() {
    SessionStop stopped = stop;
    if (stopped == null) {
      throw new IllegalStateException("Transaction " + transactionId + " is still open");
    }
    return new SessionCompleted(
        transactionId,
        start.stationId(),
        start.connectorId(),
        start.idTag(),
        authorized(),
        start.meterStart(),
        stopped.meterStop(),
        start.startedAt(),
        stopped.stoppedAt(),
        stopped.reason());
  }
}
