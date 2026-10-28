package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStation;
import io.github.mihaelaaghirculesei.csms.station.domain.ChargingStationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Keeps charging stations in PostgreSQL, mapping them to and from their entity. */
@Repository
class JpaChargingStationRepository implements ChargingStationRepository {

  private final ChargingStationEntities entities;

  JpaChargingStationRepository(ChargingStationEntities entities) {
    this.entities = entities;
  }

  @Override
  public Optional<ChargingStation> findById(String id) {
    return entities.findById(id).map(ChargingStationEntity::toDomain);
  }

  @Override
  public List<ChargingStation> findOnlineNotSeenSince(Instant cutoff, int limit) {
    return entities
        .findByOnlineTrueAndLastSeenAtBeforeOrderByLastSeenAt(cutoff, Limit.of(limit))
        .stream()
        .map(ChargingStationEntity::toDomain)
        .toList();
  }

  @Override
  public void save(ChargingStation station) {
    // Inside the transaction that loaded the station this is the same entity instance, so the
    // version checked on update is the one read at load time.
    ChargingStationEntity entity =
        entities
            .findById(station.id())
            .map(
                found -> {
                  found.copy(station);
                  return found;
                })
            .orElseGet(() -> new ChargingStationEntity(station));
    entities.save(entity);
  }
}
