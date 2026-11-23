package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import static io.github.mihaelaaghirculesei.ocpp.payload.PayloadType.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.authorization.Authorization.Status;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTag;
import io.github.mihaelaaghirculesei.csms.authorization.domain.IdTagStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.PayloadValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class AuthorizeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private static final Map<String, IdTag> REGISTRY =
      Map.of(
          "B4F62CEF",
          new IdTag("B4F62CEF", IdTagStatus.ACCEPTED, NOW.plusSeconds(3600), "FLEET-01"),
          "OLD-CARD",
          new IdTag("OLD-CARD", IdTagStatus.ACCEPTED, NOW, null));

  private final PayloadValidator validator = PayloadValidator.ocpp16();
  private final AuthorizeHandler handler =
      new AuthorizeHandler(
          new RegistryIdTagAuthorization(
              CLOCK, idTag -> Optional.ofNullable(REGISTRY.get(IdTag.normalize(idTag)))));

  @Test
  void acceptsAKnownTagWithItsExpiryDateAndGroup() {
    ObjectNode response = handler.handle("CP-1", json("{\"idTag\":\"b4f62cef\"}"));

    assertThat(response)
        .isEqualTo(
            json(
                "{\"idTagInfo\":{\"status\":\"Accepted\","
                    + "\"expiryDate\":\"2026-11-23T11:00:00Z\",\"parentIdTag\":\"FLEET-01\"}}"));
    assertThat(validator.validate("Authorize", RESPONSE, response)).isEmpty();
  }

  @Test
  void answersExpiredForATagPastItsExpiryDate() {
    ObjectNode response = handler.handle("CP-1", json("{\"idTag\":\"OLD-CARD\"}"));

    assertThat(response.get("idTagInfo").get("status").stringValue()).isEqualTo("Expired");
  }

  @Test
  void answersInvalidForAnUnknownTag() {
    ObjectNode response = handler.handle("CP-1", json("{\"idTag\":\"NOBODY\"}"));

    assertThat(response).isEqualTo(json("{\"idTagInfo\":{\"status\":\"Invalid\"}}"));
  }

  @ParameterizedTest
  @EnumSource(Status.class)
  void everyStatusGivesAValidResponse(Status status) {
    ObjectNode response =
        OcppMessages.write(
            new AuthorizeResponse(
                AuthorizeHandler.idTagInfo(new Authorization(status, null, null))));

    assertThat(validator.validate("Authorize", RESPONSE, response)).isEmpty();
  }

  private static ObjectNode json(String text) {
    return (ObjectNode) JsonMapper.shared().readTree(text);
  }
}
