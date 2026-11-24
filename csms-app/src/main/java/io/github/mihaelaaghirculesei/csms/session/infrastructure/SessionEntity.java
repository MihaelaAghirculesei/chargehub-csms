package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.StopReason;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStop;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** The table side of {@link Session}. */
@Entity
@Table(name = "charging_session")
class SessionEntity {

  @Id private int transactionId;

  private String stationId;

  private int connectorId;

  private String idTag;

  private long meterStart;

  private Instant startedAt;

  private Instant startReceivedAt;

  @Enumerated(EnumType.STRING)
  private TagStatus tagStatus;

  private boolean clockGapFlagged;

  // Hibernate reads a stop whose columns are all null as no stop, which is an open session.
  @Embedded private @Nullable StopColumns stop;

  // The id is assigned before the insert, so a null version is how Spring Data tells a new
  // session from a stored one.
  @Version private @Nullable Long version;

  @SuppressWarnings("NullAway.Init")
  protected SessionEntity() {
    // JPA fills the fields through reflection.
  }

  SessionEntity(Session session) {
    SessionStart start = session.start();
    this.transactionId = session.transactionId();
    this.stationId = start.stationId();
    this.connectorId = start.connectorId();
    this.idTag = start.idTag();
    this.meterStart = start.meterStart();
    this.startedAt = start.startedAt();
    this.startReceivedAt = start.receivedAt();
    this.tagStatus = session.tagStatus();
    copy(session);
  }

  /** Takes what a stop changes; the start never changes once stored. */
  void copy(Session session) {
    clockGapFlagged = session.clockGapFlagged();
    SessionStop stopped = session.stop();
    stop = stopped == null ? null : StopColumns.of(stopped);
  }

  Session toDomain() {
    StopColumns stopped = stop;
    return new Session(
        transactionId,
        new SessionStart(stationId, connectorId, idTag, meterStart, startedAt, startReceivedAt),
        tagStatus,
        clockGapFlagged,
        stopped == null ? null : stopped.toDomain());
  }

  @Embeddable
  record StopColumns(
      @Nullable String stopIdTag,
      long meterStop,
      Instant stoppedAt,
      @Enumerated(EnumType.STRING) StopReason stopReason,
      Instant stopReceivedAt) {

    static StopColumns of(SessionStop stop) {
      return new StopColumns(
          stop.idTag(), stop.meterStop(), stop.stoppedAt(), stop.reason(), stop.receivedAt());
    }

    SessionStop toDomain() {
      return new SessionStop(stopIdTag, meterStop, stoppedAt, stopReason, stopReceivedAt);
    }
  }
}
