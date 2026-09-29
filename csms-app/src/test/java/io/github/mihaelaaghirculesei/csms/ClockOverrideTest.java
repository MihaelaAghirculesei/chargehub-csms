package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClockOverrideTest {

  private static final Instant FIXED = Instant.parse("2026-03-29T00:30:00Z");

  @TestBean private Clock clock;

  @Autowired private ApplicationContext context;

  static Clock clock() {
    return Clock.fixed(FIXED, ZoneOffset.UTC);
  }

  @Test
  void testsCanReplaceTheClock() {
    assertThat(context.getBean(Clock.class)).isSameAs(clock);
    assertThat(clock.instant()).isEqualTo(FIXED);
  }
}
