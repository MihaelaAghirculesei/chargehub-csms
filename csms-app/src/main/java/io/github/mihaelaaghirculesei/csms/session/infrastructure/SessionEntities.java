package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionEntities extends JpaRepository<SessionEntity, Integer> {

  boolean existsByIdTagAndTagStatusAndStopIsNull(String idTag, TagStatus tagStatus);
}
