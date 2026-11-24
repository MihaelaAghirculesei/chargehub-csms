package io.github.mihaelaaghirculesei.csms.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionTest {

  private static final Instant STARTED_AT = Instant.parse("2026-11-24T10:00:00Z");
  private static final SessionStart START =
      new SessionStart("CP-1", 1, "B4F62CEF", 1200, STARTED_AT);

  @Test
  void anAcceptedTagOpensAnAuthorizedSession() {
    Session session = Session.open(7, START, Status.ACCEPTED, false);

    assertThat(session.tagStatus()).isEqualTo(TagStatus.ACCEPTED);
    assertThat(session.authorized()).isTrue();
  }

  @Test
  void anAcceptedTagThatIsAlreadyChargingIsAConcurrentTransaction() {
    Session session = Session.open(7, START, Status.ACCEPTED, true);

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
    Session session = Session.open(7, START, tagCheck, tagAlreadyCharging);

    assertThat(session.tagStatus()).isEqualTo(expected);
    assertThat(session.authorized()).isFalse();
  }

  @Test
  void keepsTheTagInUpperCase() {
    assertThat(new SessionStart("CP-1", 1, "b4f62cef", 0, STARTED_AT).idTag())
        .isEqualTo("B4F62CEF");
  }

  @Test
  void describesTheStartInItsEvent() {
    assertThat(Session.open(7, START, Status.BLOCKED, false).started())
        .isEqualTo(new SessionStarted(7, "CP-1", 1, "B4F62CEF", false, STARTED_AT));
  }
}
