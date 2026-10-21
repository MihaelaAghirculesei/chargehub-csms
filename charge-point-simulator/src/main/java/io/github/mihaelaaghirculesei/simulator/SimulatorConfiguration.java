package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimulatorProperties.class)
class SimulatorConfiguration {

  // One client for all charge points: each client owns a selector thread.
  @Bean
  HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }

  @Bean
  ChargePointRunner chargePointRunner(HttpClient httpClient, SimulatorProperties properties) {
    return new ChargePointRunner(
        new ChargePoint(
            httpClient,
            properties.centralSystem(),
            properties.chargePointId(),
            properties.callTimeout(),
            properties.reconnect().policy()));
  }
}
