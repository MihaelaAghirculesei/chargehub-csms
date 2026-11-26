package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineTest {

  @Test
  void aDeadlineInTheFutureHasTimeLeft() {
    Deadline deadline = Deadline.after(Duration.ofHours(1));

    assertThat(deadline.passed()).isFalse();
    assertThat(deadline.left())
        .isGreaterThan(Duration.ofMinutes(59))
        .isLessThan(Duration.ofHours(1).plusNanos(1));
  }

  @Test
  void aDeadlineThatPassedHasNoTimeLeft() {
    Deadline deadline = Deadline.after(Duration.ofHours(-1));

    assertThat(deadline.passed()).isTrue();
    assertThat(deadline.left()).isZero();
  }

  @Test
  void passesOnceItsTimeHasGone() throws InterruptedException {
    Deadline deadline = Deadline.after(Duration.ofMillis(20));

    Thread.sleep(deadline.left());

    assertThat(deadline.passed()).isTrue();
  }

  @Test
  void comparesAcrossTheWrapAroundOfNanoTime() {
    Deadline deadline = new Deadline(System.nanoTime() + Long.MAX_VALUE);

    assertThat(deadline.passed()).isFalse();
  }
}
