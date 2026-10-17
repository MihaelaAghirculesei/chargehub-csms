package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import io.github.mihaelaaghirculesei.ocpp.call.PendingCalls;
import io.github.mihaelaaghirculesei.ocpp.frame.FrameCodec;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The framework-free pieces of ocpp-protocol, as single shared instances. All are thread-safe. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcppProperties.class)
class OcppProtocolConfiguration {

  @Bean
  FrameCodec frameCodec() {
    return new FrameCodec();
  }

  @Bean
  PayloadValidator payloadValidator() {
    return PayloadValidator.ocpp16();
  }

  @Bean
  PendingCalls pendingCalls(OcppProperties properties) {
    return new PendingCalls(properties.callTimeout());
  }
}
