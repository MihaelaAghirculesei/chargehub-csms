package io.github.mihaelaaghirculesei.csms.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.session.SessionCompleted;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;
import io.github.mihaelaaghirculesei.csms.session.StopReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionTest {

  private static final Instant STARTED_AT = Instant.parse("2026-11-24T10:00:00Z");
  private static final SessionStart START =
      new SessionStart("CP-1", 1, "B4F62CEF", 1200, STARTED_AT, STARTED_AT);
  private static final Instant STOPPED_AT = STARTED_AT.plusSeconds(3600);
  private static final SessionStop STOP =
      new SessionStop(null, 9200, STOPPED_AT, StopReason.EV_DISCONNECTED, STOPPED_AT);

  @Test
  void tellsWhenTheMeterStoppedBelowItsStart() {
    Session open = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);
    SessionStop below =
        new SessionStop(null, 1199, STOPPED_AT, StopReason.EV_DISCONNECTED, STOPPED_AT);
    SessionStop same =
        new SessionStop(null, 1200, STOPPED_AT, StopReason.EV_DISCONNECTED, STOPPED_AT);

    assertThat(open.meterStopBelowStart()).isFalse();
    assertThat(open.close(below, ClockGap.ON_TIME).meterStopBelowStart()).isTrue();
    assertThat(open.close(same, ClockGap.ON_TIME).meterStopBelowStart()).isFalse();
  }

  @Test
  void anAcceptedTagOpensAnAuthorizedSession() {
    Session session = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);

    assertThat(session.tagStatus()).isEqualTo(TagStatus.ACCEPTED);
    assertThat(session.authorized()).isTrue();
  }

  @Test
  void anAcceptedTagThatIsAlreadyChargingIsAConcurrentTransaction() {
    Session session = Session.open(7, START, Status.ACCEPTED, true, ClockGap.ON_TIME);

    assertThat(session.tagStatus()).isEqualTo(TagStatus.CONCURRENT_TX);
    assertThat(session.authorized()).isFalse();
  }

  // A refused tag says nothing about other sessions: its own status is the more useful answer.
  @ParameterizedTest
  @CsvSource({
    "BLOCKED, BLOCKED, false",
    "BLOCKED, BLOCKED, true",
    "EXPIRED, EXPIRED, false",
    "EXPIRED, EXPIRED, true",
    "INVALID, INVALID, false",
    "INVALID, INVALID, true"
  })
  void aRefusedTagStillOpensASessionWithItsStatus(
      Status tagCheck, TagStatus expected, boolean tagAlreadyCharging) {
    Session session = Session.open(7, START, tagCheck, tagAlreadyCharging, ClockGap.ON_TIME);

    assertThat(session.tagStatus()).isEqualTo(expected);
    assertThat(session.authorized()).isFalse();
  }

  @Test
  void keepsTheStartTimesToTheMicrosecondAsTheDatabaseDoes() {
    Instant precise = Instant.parse("2026-11-24T10:00:00.123456789Z");
    Instant micros = Instant.parse("2026-11-24T10:00:00.123456Z");

    SessionStart start = new SessionStart("CP-1", 1, "B4F62CEF", 0, precise, precise);

    assertThat(start.startedAt()).isEqualTo(micros);
    assertThat(start.receivedAt()).isEqualTo(micros);
  }

  @Test
  void keepsTheTagInUpperCase() {
    assertThat(new SessionStart("CP-1", 1, "b4f62cef", 0, STARTED_AT, STARTED_AT).idTag())
        .isEqualTo("B4F62CEF");
  }

  @Test
  void describesTheStartInItsEvent() {
    assertThat(Session.open(7, START, Status.BLOCKED, false, ClockGap.ON_TIME).started())
        .isEqualTo(new SessionStarted(7, "CP-1", 1, "B4F62CEF", false, STARTED_AT));
  }

  @Test
  void aSessionIsOpenUntilItIsClosed() {
    Session open = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);

    Session closed = open.close(STOP, ClockGap.ON_TIME);

    assertThat(open.isOpen()).isTrue();
    assertThat(closed.isOpen()).isFalse();
    assertThat(closed.stop()).isEqualTo(STOP);
  }

  @Test
  void aSessionIsFlaggedByAGapAtStartOrAtStop() {
    Session onTime = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);
    Session lateStart = Session.open(7, START, Status.ACCEPTED, false, ClockGap.LATE);

    assertThat(onTime.clockGapFlagged()).isFalse();
    assertThat(onTime.close(STOP, ClockGap.ON_TIME).clockGapFlagged()).isFalse();
    assertThat(onTime.close(STOP, ClockGap.AHEAD).clockGapFlagged()).isTrue();
    assertThat(lateStart.clockGapFlagged()).isTrue();
    assertThat(lateStart.close(STOP, ClockGap.ON_TIME).clockGapFlagged()).isTrue();
  }

  @Test
  void aSessionIsClosedOnlyOnce() {
    Session closed =
        Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME)
            .close(STOP, ClockGap.ON_TIME);
    SessionStop late =
        new SessionStop(
            null, 9300, STOPPED_AT.plusSeconds(1), StopReason.OTHER, STOPPED_AT.plusSeconds(1));

    assertThatThrownBy(() -> closed.close(late, ClockGap.ON_TIME))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyTheStationThatStartedASessionStartedIt() {
    Session session = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);

    assertThat(session.startedBy("CP-1")).isTrue();
    assertThat(session.startedBy("CP-2")).isFalse();
  }

  @Test
  void keepsTheStopTagInUpperCase() {
    assertThat(new SessionStop("fleet-02", 0, STOPPED_AT, StopReason.LOCAL, STOPPED_AT).idTag())
        .isEqualTo("FLEET-02");
    assertThat(STOP.idTag()).isNull();
  }

  @Test
  void describesTheWholeSessionInItsCompletedEvent() {
    assertThat(
            Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME)
                .close(STOP, ClockGap.ON_TIME)
                .completed())
        .isEqualTo(
            new SessionCompleted(
                7,
                "CP-1",
                1,
                "B4F62CEF",
                true,
                1200,
                9200,
                STARTED_AT,
                STOPPED_AT,
                StopReason.EV_DISCONNECTED));
  }

  @Test
  void anOpenSessionHasNoCompletedEvent() {
    Session open = Session.open(7, START, Status.ACCEPTED, false, ClockGap.ON_TIME);

    assertThatThrownBy(open::completed).isInstanceOf(IllegalStateException.class);
  }
}
