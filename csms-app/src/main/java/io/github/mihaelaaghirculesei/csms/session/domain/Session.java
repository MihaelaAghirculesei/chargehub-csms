package io.github.mihaelaaghirculesei.csms.session.domain;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;

/**
 * A charging session.
 *
 * @param transactionId chosen by the central system before it answers the charge point
 */
public record Session(int transactionId, SessionStart start, TagStatus tagStatus) {

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
    return new Session(transactionId, start, tagStatus);
  }

  public boolean authorized() {
    return tagStatus == TagStatus.ACCEPTED;
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
}
