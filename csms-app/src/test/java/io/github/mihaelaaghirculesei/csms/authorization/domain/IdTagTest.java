package io.github.mihaelaaghirculesei.csms.authorization.domain;

import static io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus.ACCEPTED;
import static io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus.BLOCKED;
import static io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdTagTest {

  private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

  @Test
  void anAcceptedTagWithoutExpiryIsAccepted() {
    assertThat(new IdTag("B4F62CEF", ACCEPTED, null, null).check(NOW))
        .isEqualTo(new Authorization(Status.ACCEPTED, null, null));
  }

  @Test
  void anAcceptedTagIsAcceptedUntilItsExpiryDate() {
    Instant expiresAt = NOW.plusSeconds(1);

    assertThat(new IdTag("B4F62CEF", ACCEPTED, expiresAt, "FLEET-01").check(NOW))
        .isEqualTo(new Authorization(Status.ACCEPTED, expiresAt, "FLEET-01"));
  }

  @Test
  void anAcceptedTagIsExpiredFromItsExpiryDateOn() {
    IdTag tag = new IdTag("B4F62CEF", ACCEPTED, NOW, null);

    assertThat(tag.check(NOW).status()).isEqualTo(Status.EXPIRED);
    assertThat(tag.check(NOW.plusSeconds(1)).status()).isEqualTo(Status.EXPIRED);
  }

  @Test
  void aBlockedTagStaysBlockedBeforeItsExpiryDate() {
    assertThat(new IdTag("B4F62CEF", BLOCKED, NOW.plusSeconds(60), null).check(NOW).status())
        .isEqualTo(Status.BLOCKED);
  }

  @Test
  void aTagMarkedExpiredIsExpiredWithoutAnExpiryDate() {
    assertThat(new IdTag("B4F62CEF", EXPIRED, null, null).check(NOW).status())
        .isEqualTo(Status.EXPIRED);
  }

  @Test
  void keepsTheTagAndItsParentInUpperCase() {
    IdTag tag = new IdTag("b4f62cef", ACCEPTED, null, "fleet-01");

    assertThat(tag.value()).isEqualTo("B4F62CEF");
    assertThat(tag.parentIdTag()).isEqualTo("FLEET-01");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "123456789012345678901"})
  void refusesWhatCannotBeAnIdTag(String value) {
    assertThatThrownBy(() -> new IdTag(value, ACCEPTED, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsATagOfTheLongestLength() {
    assertThat(new IdTag("12345678901234567890", ACCEPTED, null, null).value()).hasSize(20);
  }
}
