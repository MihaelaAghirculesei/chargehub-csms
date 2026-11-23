package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTag;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagRepository;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus;
import java.sql.Timestamp;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

// Tests share one database, so each one uses its own tags.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaIdTagRepositoryTest {

  private static final Instant EXPIRES_AT = Instant.parse("2027-01-01T00:00:00.123456Z");

  @Autowired private IdTagRepository tags;
  @Autowired private JdbcClient jdbc;

  @Test
  void loadsATagWithEveryField() {
    insert("REPO-FULL", "BLOCKED", EXPIRES_AT, "REPO-PARENT");

    assertThat(tags.find("REPO-FULL"))
        .contains(new IdTag("REPO-FULL", IdTagStatus.BLOCKED, EXPIRES_AT, "REPO-PARENT"));
  }

  @Test
  void loadsATagWithoutOptionalFields() {
    insert("REPO-PLAIN", "ACCEPTED", null, null);

    assertThat(tags.find("REPO-PLAIN"))
        .contains(new IdTag("REPO-PLAIN", IdTagStatus.ACCEPTED, null, null));
  }

  @Test
  void findsATagWhateverTheCaseItIsAskedIn() {
    insert("REPO-CASE", "ACCEPTED", null, null);

    assertThat(tags.find("repo-case")).isPresent();
  }

  @Test
  void doesNotFindAnUnknownOrInvalidTag() {
    assertThat(tags.find("REPO-UNKNOWN")).isEmpty();
    assertThat(tags.find("")).isEmpty();
    assertThat(tags.find("123456789012345678901")).isEmpty();
  }

  @Test
  void theTableRefusesATagInLowerCase() {
    assertThatThrownBy(() -> insert("repo-lower", "ACCEPTED", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insert(
      String idTag, String status, @Nullable Instant expiresAt, @Nullable String parentIdTag) {
    jdbc.sql("insert into id_tag (id_tag, status, expires_at, parent_id_tag) values (?, ?, ?, ?)")
        .params(idTag, status, expiresAt == null ? null : Timestamp.from(expiresAt), parentIdTag)
        .update();
  }
}
