package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.session.StopReason;
import io.github.mihaelaaghirculesei.csms.session.domain.ClockGap;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStop;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

// Tests share one database, so each one uses its own tags.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaSessionRepositoryTest {

  private static final Instant STARTED_AT = Instant.parse("2026-11-24T10:00:00.123456Z");
  private static final Instant STOPPED_AT = STARTED_AT.plusSeconds(3600);

  @Autowired private SessionRepository sessions;
  @Autowired private JdbcClient jdbc;

  @Test
  void givesEachSessionANewTransactionId() {
    int first = sessions.nextTransactionId();
    int second = sessions.nextTransactionId();

    assertThat(second).isGreaterThan(first);
  }

  @Test
  void storesEveryFieldOfTheStart() {
    Session session = open("REPO-STORED", Status.BLOCKED, 3_000_000_000L);

    sessions.save(session);

    assertThat(
            jdbc.sql(
                    "select station_id, connector_id, id_tag, meter_start, tag_status,"
                        + " started_at = ? as same_instant"
                        + " from charging_session where transaction_id = ?")
                .params(STARTED_AT.atOffset(ZoneOffset.UTC), session.transactionId())
                .query()
                .singleRow())
        .containsEntry("station_id", "CP-REPO")
        .containsEntry("connector_id", 2)
        .containsEntry("id_tag", "REPO-STORED")
        .containsEntry("meter_start", 3_000_000_000L)
        .containsEntry("tag_status", "BLOCKED")
        .containsEntry("same_instant", true);
  }

  @Test
  void aTagIsChargingOnlyWithAnAcceptedSession() {
    sessions.save(open("REPO-CHARGING", Status.ACCEPTED, 0));
    sessions.save(open("REPO-REFUSED", Status.BLOCKED, 0));

    assertThat(sessions.isCharging("REPO-CHARGING")).isTrue();
    assertThat(sessions.isCharging("REPO-REFUSED")).isFalse();
    assertThat(sessions.isCharging("REPO-NEVER")).isFalse();
  }

  @Test
  void refusesASecondAcceptedSessionForOneTag() {
    sessions.save(open("REPO-TWICE", Status.ACCEPTED, 0));
    Session second = open("REPO-TWICE", Status.ACCEPTED, 0);

    assertThatThrownBy(() -> sessions.save(second))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.sql("select count(*) from charging_session where id_tag = 'REPO-TWICE'")
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void findsTheSessionOfAStartReceivedBefore() {
    Session session = open("REPO-REPEATED", Status.ACCEPTED, 1200);
    sessions.save(session);

    assertThat(sessions.findByStart(session.start())).contains(session);
    assertThat(
            sessions.findByStart(
                new SessionStart("CP-REPO", 2, "REPO-REPEATED", 1201, STARTED_AT, STARTED_AT)))
        .isEmpty();
  }

  @Test
  void refusesToStoreOneStartTwice() {
    Session session = open("REPO-START-ONCE", Status.BLOCKED, 0);
    sessions.save(session);
    Session copy =
        Session.open(
            sessions.nextTransactionId(), session.start(), Status.BLOCKED, false, ClockGap.ON_TIME);

    assertThatThrownBy(() -> sessions.save(copy))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void readsBackAnOpenSession() {
    Session session = open("REPO-OPEN", Status.ACCEPTED, 1200);
    sessions.save(session);

    assertThat(sessions.findById(session.transactionId())).contains(session);
  }

  @Test
  void readsBackAStoppedSession() {
    Session session = open("REPO-STOPPED", Status.ACCEPTED, 1200);
    sessions.save(session);
    Session stopped =
        session.close(
            new SessionStop("REPO-FLEET", 9200, STOPPED_AT, StopReason.REMOTE, STOPPED_AT),
            ClockGap.ON_TIME);

    sessions.save(stopped);

    assertThat(sessions.findById(session.transactionId())).contains(stopped);
  }

  @Test
  void findsNoSessionForAnUnknownTransactionId() {
    assertThat(sessions.findById(-1)).isEmpty();
  }

  @Test
  void aStoppedTagMayChargeAgain() {
    Session first = open("REPO-AGAIN", Status.ACCEPTED, 0);
    sessions.save(first);
    sessions.save(
        first.close(
            new SessionStop(null, 10, STOPPED_AT, StopReason.LOCAL, STOPPED_AT), ClockGap.ON_TIME));

    assertThat(sessions.isCharging("REPO-AGAIN")).isFalse();
    sessions.save(open("REPO-AGAIN", Status.ACCEPTED, 10));
    assertThat(sessions.isCharging("REPO-AGAIN")).isTrue();
  }

  @Test
  void refusesAStopThatIsNotWhole() {
    Session session = open("REPO-HALF", Status.ACCEPTED, 0);
    sessions.save(session);

    assertThatThrownBy(() ->
            jdbc.sql("update charging_session set meter_stop = 10 where transaction_id = ?")
                .param(session.transactionId())
                .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Session open(String idTag, Status tagCheck, long meterStart) {
    return Session.open(
        sessions.nextTransactionId(),
        new SessionStart("CP-REPO", 2, idTag, meterStart, STARTED_AT, STARTED_AT),
        tagCheck,
        false,
        ClockGap.ON_TIME);
  }
}
