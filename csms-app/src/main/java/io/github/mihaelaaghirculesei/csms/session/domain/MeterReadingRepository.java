package io.github.mihaelaaghirculesei.csms.session.domain;

import java.util.List;

/** Where the readings of sessions are kept. Readings are only ever added. */
public interface MeterReadingRepository {

  /**
   * Stores all readings of one message together: either all of them are kept or none. A reading
   * with the same time, measurand, phase, location and context as a stored one is skipped.
   *
   * @return the energy register readings that go backwards because of the stored ones: a stored
   *     reading can go back itself, or make the reading after it by station time go back
   */
  List<RegisterDrop> addAll(int transactionId, List<MeterReading> readings);
}
