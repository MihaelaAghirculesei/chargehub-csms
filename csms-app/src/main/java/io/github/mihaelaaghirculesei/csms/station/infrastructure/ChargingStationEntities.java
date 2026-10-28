package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChargingStationEntities extends JpaRepository<ChargingStationEntity, String> {

  // Served by the partial index on last_seen_at where online.
  List<ChargingStationEntity> findByOnlineTrueAndLastSeenAtBeforeOrderByLastSeenAt(
      Instant cutoff, Limit limit);
}
