package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class StationHandlersTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T10:15:30.123Z"), ZoneOffset.UTC);

  private final PayloadValidator validator = PayloadValidator.ocpp16();

  @Test
  void acceptsTheChargePointWithTheConfiguredHeartbeatInterval() {
    BootNotificationHandler handler =
        new BootNotificationHandler(CLOCK, new StationProperties(Duration.ofMinutes(2)));

    ObjectNode response = handler.handle("CP-1", bootNotification());

    assertThat(response)
        .isEqualTo(
            json(
                "{\"status\":\"Accepted\",\"currentTime\":\"2026-10-17T10:15:30.123Z\","
                    + "\"interval\":120}"));
    assertThat(validator.validate("BootNotification", RESPONSE, response)).isEmpty();
  }

  @Test
  void answersAHeartbeatWithTheCurrentTime() {
    ObjectNode response =
        new HeartbeatHandler(CLOCK).handle("CP-1", JsonNodeFactory.instance.objectNode());

    assertThat(response).isEqualTo(json("{\"currentTime\":\"2026-10-17T10:15:30.123Z\"}"));
    assertThat(validator.validate("Heartbeat", RESPONSE, response)).isEmpty();
  }

  private static ObjectNode bootNotification() {
    return json("{\"chargePointVendor\":\"VendorX\",\"chargePointModel\":\"SingleSocketCharger\"}");
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
