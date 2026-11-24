package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.MeterReading;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adds readings with plain JDBC. They have no life cycle for JPA to manage, and Hibernate cannot
 * batch inserts into a table whose key the database generates.
 */
@Repository
class JdbcMeterReadingRepository implements MeterReadingRepository {

  // One statement for all readings of a message: unnest turns the arrays back into rows, so the
  // insert is one round trip whatever the number of readings, and it is atomic on its own.
  // Times travel as ISO-8601 text, which PostgreSQL reads as UTC instants without driver support
  // for arrays of dates.
  private static final String INSERT =
      """
      insert into meter_value
          (transaction_id, sampled_at, value, measurand, unit, phase, context, location, format)
      select ?, r.*
      from unnest(?::timestamptz[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[],
                  ?::text[], ?::text[]) as r
      """;

  private final JdbcClient jdbc;

  JdbcMeterReadingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void addAll(int transactionId, List<MeterReading> readings) {
    if (readings.isEmpty()) {
      return;
    }
    jdbc.sql(INSERT)
        .params(
            transactionId,
            column(readings, reading -> reading.sampledAt().toString()),
            column(readings, MeterReading::value),
            column(readings, MeterReading::measurand),
            column(readings, MeterReading::unit),
            column(readings, MeterReading::phase),
            column(readings, MeterReading::context),
            column(readings, MeterReading::location),
            column(readings, MeterReading::format))
        .update();
  }

  private static String[] column(
      List<MeterReading> readings, Function<MeterReading, @Nullable String> field) {
    return readings.stream().map(field).toArray(String[]::new);
  }
}
