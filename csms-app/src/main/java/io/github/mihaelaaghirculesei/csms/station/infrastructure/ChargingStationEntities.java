package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChargingStationEntities extends JpaRepository<ChargingStationEntity, String> {}
