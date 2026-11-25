package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.MeterReading;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.RegisterDrop;
import java.time.OffsetDateTime;
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
  // A reading already stored is skipped, so a resent message changes nothing, see docs/adr/0016.
  // Times travel as ISO-8601 text, which PostgreSQL reads as UTC instants without driver support
  // for arrays of dates.
  private static final String INSERT =
      """
      insert into meter_value
          (transaction_id, sampled_at, received_at, value, measurand, unit, phase, context,
           location, format)
      select ?, r.*
      from unnest(?::timestamptz[], ?::timestamptz[], ?::text[], ?::text[], ?::text[],
                  ?::text[], ?::text[], ?::text[], ?::text[]) as r
      on conflict (transaction_id, sampled_at, measurand, phase, location, context) do nothing
      returning id
      """;

  // A separate statement: the rows an insert adds are not visible to the rest of its statement.
  private static final String DROPS =
      """
      select sampled_at, phase, energy_wh, previous_wh from energy_register_reading
      where transaction_id = ? and went_back and (id = any(?) or previous_id = any(?))
      order by sampled_at, id
      """;

  private final JdbcClient jdbc;

  JdbcMeterReadingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<RegisterDrop> addAll(int transactionId, List<MeterReading> readings) {
    if (readings.isEmpty()) {
      return List.of();
    }
    Long[] stored =
        jdbc.sql(INSERT)
            .params(
                transactionId,
                column(readings, reading -> reading.sampledAt().toString()),
                column(readings, reading -> reading.receivedAt().toString()),
                column(readings, MeterReading::value),
                column(readings, MeterReading::measurand),
                column(readings, MeterReading::unit),
                column(readings, MeterReading::phase),
                column(readings, MeterReading::context),
                column(readings, MeterReading::location),
                column(readings, MeterReading::format))
            .query(Long.class)
            .list()
            .toArray(Long[]::new);
    if (stored.length == 0) {
      return List.of();
    }
    return jdbc.sql(DROPS)
        .params(transactionId, stored, stored)
        .query((row, rowNumber) ->
            new RegisterDrop(
                row.getObject("sampled_at", OffsetDateTime.class).toInstant(),
                row.getString("phase"),
                row.getBigDecimal("energy_wh"),
                row.getBigDecimal("previous_wh")))
        .list();
  }

  private static String[] column(
      List<MeterReading> readings, Function<MeterReading, @Nullable String> field) {
    return readings.stream().map(field).toArray(String[]::new);
  }
}
