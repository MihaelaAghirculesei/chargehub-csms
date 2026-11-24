package io.github.mihaelaaghirculesei.csms.session.domain;

import java.util.List;

/** Where the readings of sessions are kept. Readings are only ever added. */
public interface MeterReadingRepository {

  /**
   * Stores all readings of one message together: either all of them are kept or none. A reading
   * with the same time, measurand, phase, location and context as a stored one is skipped.
   */
  void addAll(int transactionId, List<MeterReading> readings);
}
