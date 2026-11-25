package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.session.domain.ClockGap;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReading;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.RegisterDrop;
import io.github.mihaelaaghirculesei.csms.session.domain.Session;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionStart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcMeterReadingRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-11-24T10:00:00.123456Z");

  @Autowired private MeterReadingRepository readings;
  @Autowired private SessionRepository sessions;
  @Autowired private JdbcClient jdbc;

  @Test
  void storesEveryFieldOfEveryReading() {
    int transactionId = session("METER-FIELDS");

    readings.addAll(
        transactionId,
        List.of(
            MeterReading.of(T0, T0, "1500", null, null, null, null, null, null),
            MeterReading.of(
                T0.plusSeconds(60),
                T0.plusSeconds(60),
                "230.4",
                "Voltage",
                "V",
                "L1-N",
                "Sample.Clock",
                "Inlet",
                "Raw")));

    assertThat(rowsOf(transactionId))
        .containsExactly(
            Map.of(
                "sampled_at", T0.toString(),
                "value", "1500",
                "measurand", "Energy.Active.Import.Register",
                "unit", "Wh",
                "phase", "",
                "context", "Sample.Periodic",
                "location", "Outlet",
                "format", "Raw"),
            Map.of(
                "sampled_at", T0.plusSeconds(60).toString(),
                "value", "230.4",
                "measurand", "Voltage",
                "unit", "V",
                "phase", "L1-N",
                "context", "Sample.Clock",
                "location", "Inlet",
                "format", "Raw"));
  }

  /**
   * A statement trigger records each insert statement on the table, so the test sees round trips
   * and not only rows. It records the session too, which keeps other tests out of the count.
   */
  @Test
  void writesAllReadingsOfAMessageInOneStatement() {
    int transactionId = session("METER-BATCH");
    jdbc.sql(
            """
            create table meter_value_statement (transaction_id integer);
            create function record_meter_value_statement() returns trigger language plpgsql as $$
            begin
              insert into meter_value_statement select distinct transaction_id from inserted;
              return null;
            end $$;
            create trigger record_meter_value_statement after insert on meter_value
                referencing new table as inserted
                for each statement execute function record_meter_value_statement();
            """)
        .update();
    try {
      readings.addAll(
          transactionId,
          List.of(
              MeterReading.of(T0, T0, "1500", null, null, null, null, null, null),
              MeterReading.of(T0, T0, "16.2", "Current.Import", "A", "L1", null, null, null),
              MeterReading.of(T0, T0, "16.1", "Current.Import", "A", "L2", null, null, null)));

      assertThat(
              jdbc.sql("select count(*) from meter_value_statement where transaction_id = ?")
                  .param(transactionId)
                  .query(Integer.class)
                  .single())
          .isEqualTo(1);
      assertThat(rowsOf(transactionId)).hasSize(3);
    } finally {
      jdbc.sql(
              """
              drop trigger record_meter_value_statement on meter_value;
              drop function record_meter_value_statement();
              drop table meter_value_statement;
              """)
          .update();
    }
  }

  @Test
  void storesEachReadingOnce() {
    int transactionId = session("METER-ONCE");
    List<MeterReading> message =
        List.of(
            MeterReading.of(T0, T0, "1500", null, null, null, null, null, null),
            MeterReading.of(T0, T0, "16.2", "Current.Import", "A", "L1", null, null, null));

    readings.addAll(transactionId, message);
    readings.addAll(
        transactionId,
        List.of(
            MeterReading.of(T0, T0, "1500", null, null, null, null, null, null),
            MeterReading.of(
                T0.plusSeconds(60),
                T0.plusSeconds(60),
                "1600",
                null,
                null,
                null,
                null,
                null,
                null)));
    readings.addAll(transactionId, message);

    assertThat(rowsOf(transactionId))
        .extracting(row -> row.get("value"))
        .containsExactly("1500", "16.2", "1600");
  }

  @Test
  void returnsTheReadingsThatGoBackBecauseOfTheMessage() {
    int transactionId = session("METER-DROP");
    readings.addAll(transactionId, List.of(energy(1, "1500"), energy(3, "1600")));

    List<RegisterDrop> drops = readings.addAll(transactionId, List.of(energy(2, "1700")));

    assertThat(drops)
        .containsExactly(
            new RegisterDrop(
                T0.plusSeconds(180), null, new BigDecimal("1600"), new BigDecimal("1700")));
  }

  @Test
  void returnsNoDropForAMessageAlreadyStored() {
    int transactionId = session("METER-DROP-AGAIN");
    List<MeterReading> message = List.of(energy(1, "1500"), energy(2, "1400"));

    assertThat(readings.addAll(transactionId, message)).hasSize(1);
    assertThat(readings.addAll(transactionId, message)).isEmpty();
  }

  @Test
  void refusesReadingsForAnUnknownSession() {
    List<MeterReading> orphan =
        List.of(MeterReading.of(T0, T0, "1", null, null, null, null, null, null));

    assertThatThrownBy(() -> readings.addAll(-1, orphan))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static MeterReading energy(int minute, String value) {
    Instant sampledAt = T0.plusSeconds(60L * minute);
    return MeterReading.of(sampledAt, sampledAt, value, null, null, null, null, null, null);
  }

  private int session(String idTag) {
    Session session =
        Session.open(
            sessions.nextTransactionId(),
            new SessionStart("CP-METER", 1, idTag, 0, T0, T0),
            Status.INVALID,
            false,
            ClockGap.ON_TIME);
    sessions.save(session);
    return session.transactionId();
  }

  /** Times as UTC text to the microsecond, and a missing phase as an empty string. */
  private List<Map<String, Object>> rowsOf(int transactionId) {
    return jdbc.sql(
            """
            select to_char(sampled_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
                       as sampled_at,
                   value, measurand, unit, coalesce(phase, '') as phase, context, location, format
            from meter_value where transaction_id = ? order by id
            """)
        .param(transactionId)
        .query()
        .listOfRows();
  }
}
