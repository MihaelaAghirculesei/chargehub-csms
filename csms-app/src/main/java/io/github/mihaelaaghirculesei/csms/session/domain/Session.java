package io.github.mihaelaaghirculesei.csms.session.domain;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.session.SessionCompleted;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;
import org.jspecify.annotations.Nullable;

/**
 * A charging session.
 *
 * @param transactionId chosen by the central system before it answers the charge point
 * @param clockGapFlagged whether the start or the stop came with a station time far from the
 *     receive time, see {@link ClockGap}
 * @param stop null while the session is open
 */
public record Session(
    int transactionId,
    SessionStart start,
    TagStatus tagStatus,
    boolean clockGapFlagged,
    @Nullable SessionStop stop) {

  /**
   * Opens a session whatever the tag status. OCPP 1.6 asks the central system to store a refused
   * start too: the charge point decides whether to stop charging, and its later messages must match
   * a known transaction.
   *
   * @param tagAlreadyCharging whether the tag has another open session that was accepted
   * @param startGap how the station time of the start compares with its receive time
   */
  public static Session open(
      int transactionId,
      SessionStart start,
      Authorization.Status tagCheck,
      boolean tagAlreadyCharging,
      ClockGap startGap) {
    TagStatus tagStatus = TagStatus.of(tagCheck);
    if (tagStatus == TagStatus.ACCEPTED && tagAlreadyCharging) {
      tagStatus = TagStatus.CONCURRENT_TX;
    }
    return new Session(transactionId, start, tagStatus, startGap.flagged(), null);
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
   * @param stopGap how the station time of the stop compares with its receive time
   * @throws IllegalStateException when it is already stopped: the first stop stays the true one
   */
  public Session close(SessionStop stop, ClockGap stopGap) {
    if (!isOpen()) {
      throw new IllegalStateException("Transaction " + transactionId + " is already stopped");
    }
    return new Session(transactionId, start, tagStatus, clockGapFlagged || stopGap.flagged(), stop);
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
