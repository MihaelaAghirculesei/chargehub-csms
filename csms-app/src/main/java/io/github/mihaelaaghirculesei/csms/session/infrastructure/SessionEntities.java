package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionEntities extends JpaRepository<SessionEntity, Integer> {

  Optional<SessionEntity> findByStationIdAndConnectorIdAndIdTagAndMeterStartAndStartedAt(
      String stationId, int connectorId, String idTag, long meterStart, Instant startedAt);

  boolean existsByIdTagAndTagStatusAndStopIsNull(String idTag, TagStatus tagStatus);
}
