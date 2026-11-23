package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.IdTagAuthorization;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers from the id tag registry in PostgreSQL. */
@Service
class RegistryIdTagAuthorization implements IdTagAuthorization {

  private final Clock clock;
  private final IdTagRepository tags;

  RegistryIdTagAuthorization(Clock clock, IdTagRepository tags) {
    this.clock = clock;
    this.tags = tags;
  }

  @Override
  @Transactional(readOnly = true)
  public Authorization authorize(String idTag) {
    return tags.find(idTag)
        .map(tag -> tag.check(clock.instant()))
        .orElse(Authorization.UNKNOWN_TAG);
  }
}
