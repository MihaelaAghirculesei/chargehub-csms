package io.github.mihaelaaghirculesei.csms.station.domain;

import java.util.Optional;

/**
 * Where charging stations are kept. Load and save a station in the same transaction: a save that
 * would overwrite a change made by another transaction in between fails instead.
 */
public interface ChargingStationRepository {

  Optional<ChargingStation> findById(String id);

  void save(ChargingStation station);
}
