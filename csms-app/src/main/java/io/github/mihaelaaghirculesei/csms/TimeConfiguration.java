package io.github.mihaelaaghirculesei.csms;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is a dependency: code asks this clock instead of calling {@code Instant.now()}, so tests can
 * fix or advance it.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
