package io.github.mihaelaaghirculesei.csms.station.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where charging stations are kept. Load and save a station in the same transaction: a save that
 * would overwrite a change made by another transaction in between fails instead.
 */
public interface ChargingStationRepository {

  Optional<ChargingStation> findById(String id);

  /** At most {@code limit} online stations not seen since the cutoff, the longest silent first. */
  List<ChargingStation> findOnlineNotSeenSince(Instant cutoff, int limit);

  void save(ChargingStation station);
}
