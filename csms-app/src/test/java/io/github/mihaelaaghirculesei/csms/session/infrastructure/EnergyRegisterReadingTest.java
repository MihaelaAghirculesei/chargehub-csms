package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.session.StopReason;
import io.github.mihaelaaghirculesei.csms.session.domain.ClockGap;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReading;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStop;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The view that compares each energy register reading with the one before it. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EnergyRegisterReadingTest {

  private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");
  private static final long METER_START = 1000;

  @Autowired private MeterReadingRepository readings;
  @Autowired private SessionRepository sessions;
  @Autowired private JdbcClient jdbc;

  @Test
  void flagsAReadingLowerThanTheOneBefore() {
    int transactionId = session("REGISTER-DOWN");

    add(transactionId, energy(1, "1200"), energy(2, "1100"), energy(3, "1300"));

    assertThat(wentBack(transactionId)).containsExactly(false, true, false);
  }

  @Test
  void comparesTheFirstReadingWithMeterStart() {
    int transactionId = session("REGISTER-START");

    add(transactionId, energy(1, "900"));

    assertThat(wentBack(transactionId)).containsExactly(true);
  }

  @Test
  void ordersReadingsByStationTimeWhateverTheirArrival() {
    int transactionId = session("REGISTER-LATE");

    add(transactionId, energy(1, "1100"), energy(3, "1300"));
    assertThat(wentBack(transactionId)).containsExactly(false, false);

    // The late reading is fine; the one after it by station time now goes back.
    add(transactionId, energy(2, "1400"));
    assertThat(wentBack(transactionId)).containsExactly(false, false, true);
  }

  @Test
  void comparesKilowattHoursAndWattHoursInWattHours() {
    int transactionId = session("REGISTER-UNIT");

    add(
        transactionId,
        reading(1, "1.5", "kWh", null, "Raw"),
        energy(2, "1400"),
        reading(3, "1.6", "kWh", null, "Raw"));

    assertThat(wentBack(transactionId)).containsExactly(false, true, false);
  }

  @Test
  void leavesOutValuesThatAreNotPlainNumbers() {
    int transactionId = session("REGISTER-PLAIN");

    add(
        transactionId,
        energy(1, "1500"),
        reading(2, "1e3", "Wh", null, "Raw"),
        reading(3, "-5", "Wh", null, "Raw"),
        reading(4, "0", "Wh", null, "SignedData"),
        reading(5, "1", "varh", null, "Raw"),
        energy(6, "1600"));

    assertThat(wentBack(transactionId)).containsExactly(false, false);
  }

  @Test
  void comparesTheRegisterOfAPhaseOnlyWithItself() {
    int transactionId = session("REGISTER-PHASE");

    add(
        transactionId,
        reading(1, "400", "Wh", "L1", "Raw"),
        energy(1, "1200"),
        reading(2, "500", "Wh", "L1", "Raw"),
        reading(2, "300", "Wh", "L2", "Raw"));

    assertThat(wentBack(transactionId)).containsExactly(false, false, false, false);
  }

  @Test
  void flagsAStopBelowTheStart() {
    int below = session("REGISTER-STOP-LOW");
    int above = session("REGISTER-STOP-OK");
    int open = session("REGISTER-OPEN");

    stop(below, METER_START - 1);
    stop(above, METER_START + 1);

    assertThat(stopBelowStart(below)).isTrue();
    assertThat(stopBelowStart(above)).isFalse();
    assertThat(stopBelowStart(open)).isNull();
  }

  private int session(String idTag) {
    Session session =
        Session.open(
            sessions.nextTransactionId(),
            new SessionStart("CP-REGISTER", 1, idTag, METER_START, T0, T0),
            Status.INVALID,
            false,
            ClockGap.ON_TIME);
    sessions.save(session);
    return session.transactionId();
  }

  private void stop(int transactionId, long meterStop) {
    Instant stoppedAt = T0.plusSeconds(600);
    sessions.save(
        sessions
            .findById(transactionId)
            .orElseThrow()
            .close(
                new SessionStop(null, meterStop, stoppedAt, StopReason.LOCAL, stoppedAt),
                ClockGap.ON_TIME));
  }

  private void add(int transactionId, MeterReading... added) {
    readings.addAll(transactionId, List.of(added));
  }

  private static MeterReading energy(int minute, String value) {
    return reading(minute, value, "Wh", null, "Raw");
  }

  private static MeterReading reading(
      int minute, String value, String unit, @Nullable String phase, String format) {
    Instant sampledAt = T0.plusSeconds(60L * minute);
    return MeterReading.of(sampledAt, sampledAt, value, null, unit, phase, null, null, format);
  }

  /** In the order of the view: by phase, then by station time. */
  private List<Boolean> wentBack(int transactionId) {
    return jdbc.sql(
            """
            select went_back from energy_register_reading where transaction_id = ?
            order by phase nulls first, sampled_at, id
            """)
        .param(transactionId)
        .query(Boolean.class)
        .list();
  }

  private @Nullable Boolean stopBelowStart(int transactionId) {
    return jdbc.sql("select meter_stop_below_start from charging_session where transaction_id = ?")
        .param(transactionId)
        .query(Boolean.class)
        .list()
        .getFirst();
  }
}
