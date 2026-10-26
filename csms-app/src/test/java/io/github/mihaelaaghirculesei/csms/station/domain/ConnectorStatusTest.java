package io.github.mihaelaaghirculesei.csms.station.domain;

import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.AVAILABLE;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.FAULTED;
import static io.github.mihaelaaghirculesei.csms.station.domain.ConnectorStatus.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectorStatusTest {

  /**
   * The table of OCPP 1.6 section 4.9, copied row by row so it can be read against the
   * specification: rows are the current status, columns the next one, in declaration order.
   */
  private static final String SPECIFICATION_TABLE =
      """
      AVAILABLE       . x x x x . x x x
      PREPARING       x . x x x x . . x
      CHARGING        x . . x x x . x x
      SUSPENDED_EV    x . x . x x . x x
      SUSPENDED_EVSE  x . x x . x . x x
      FINISHING       x x . . . . . x x
      RESERVED        x x . . . . . x x
      UNAVAILABLE     x x x x x . . . x
      FAULTED         x x x x x x x x .
      """;

  private static final Pattern CELL_SEPARATOR = Pattern.compile("\\s+");

  private static List<List<String>> rows() {
    return SPECIFICATION_TABLE
        .lines()
        .map(line -> CELL_SEPARATOR.splitAsStream(line.strip()).toList())
        .toList();
  }

  static Stream<Arguments> everyPairOfStatuses() {
    ConnectorStatus[] statuses = ConnectorStatus.values();
    return rows().stream()
        .flatMap(
            row ->
                IntStream.range(0, statuses.length)
                    .mapToObj(
                        column ->
                            Arguments.of(
                                ConnectorStatus.valueOf(row.getFirst()),
                                statuses[column],
                                row.get(column + 1).equals("x"))));
  }

  @ParameterizedTest(name = "{0} to {1}: {2}")
  @MethodSource("everyPairOfStatuses")
  void allowsExactlyTheTransitionsOfTheSpecification(
      ConnectorStatus current, ConnectorStatus next, boolean allowed) {
    assertThat(current.allowsChangeTo(next)).isEqualTo(allowed);
  }

  @Test
  void theTableHasOneRowAndOneColumnPerStatus() {
    List<String> names = Arrays.stream(ConnectorStatus.values()).map(Enum::name).toList();

    assertThat(rows()).allSatisfy(row -> assertThat(row).hasSize(names.size() + 1));
    assertThat(rows().stream().map(List::getFirst)).containsExactlyElementsOf(names);
  }

  @Test
  void theWholeStationIsOnlyAvailableUnavailableOrFaulted() {
    List<ConnectorStatus> stationStatuses =
        Arrays.stream(ConnectorStatus.values())
            .filter(ConnectorStatus::appliesToTheWholeStation)
            .toList();

    assertThat(stationStatuses).containsExactly(AVAILABLE, UNAVAILABLE, FAULTED);
  }
}
