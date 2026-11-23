package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdTagEntities extends JpaRepository<IdTagEntity, String> {}
