package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** The table side of {@link Session}. */
// Only written so far: the fields are read back once a stop has to find its session.
@SuppressWarnings("UnusedVariable")
@Entity
@Table(name = "charging_session")
class SessionEntity {

  @Id private int transactionId;

  private String stationId;

  private int connectorId;

  private String idTag;

  private long meterStart;

  private Instant startedAt;

  @Enumerated(EnumType.STRING)
  private TagStatus tagStatus;

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
    this.tagStatus = session.tagStatus();
  }
}
