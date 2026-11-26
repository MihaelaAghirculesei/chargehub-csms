package io.github.mihaelaaghirculesei.simulator.chargepoint;

import java.time.Duration;

/**
 * A moment on the monotonic clock of the JVM. The clock of a charge point may be skewed on purpose,
 * and the wall clock of the host may be changed, but neither may move what the charge point is
 * waiting for.
 *
 * @param nanoTime in the units of {@link System#nanoTime()}
 */
record Deadline(long nanoTime) {

  static Deadline after(Duration wait) {
    return new Deadline(System.nanoTime() + wait.toNanos());
  }

  static Deadline earliest(Deadline one, Deadline other) {
    return one.nanoTime - other.nanoTime <= 0 ? one : other;
  }

  /** How long until the deadline, never negative. */
  Duration left() {
    return Duration.ofNanos(Math.max(0, nanoTime - System.nanoTime()));
  }

  // nanoTime values may wrap around, so only their difference can be compared.
  boolean passed() {
    return nanoTime - System.nanoTime() <= 0;
  }
}
