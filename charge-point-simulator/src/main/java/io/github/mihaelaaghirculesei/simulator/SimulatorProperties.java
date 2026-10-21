package io.github.mihaelaaghirculesei.simulator;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param centralSystem the OCPP endpoint without the charge point id, which is appended to it
 * @param callTimeout how long the central system may take to answer
 */
@ConfigurationProperties("chargehub.simulator")
record SimulatorProperties(
    @DefaultValue("ws://localhost:8080/ocpp") URI centralSystem,
    @DefaultValue("SIM-0001") String chargePointId,
    @DefaultValue("30s") Duration callTimeout) {}
