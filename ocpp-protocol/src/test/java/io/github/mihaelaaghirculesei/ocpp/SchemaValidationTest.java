package io.github.mihaelaaghirculesei.ocpp;

import static io.github.mihaelaaghirculesei.ocpp.BootNotificationSchema.payload;
import static io.github.mihaelaaghirculesei.ocpp.BootNotificationSchema.validate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class SchemaValidationTest {

  @Test
  void acceptsValidPayload() {
    assertThat(validate(payload("ChargeHub", "Simulator X"))).isEmpty();
  }

  @Test
  void rejectsMissingRequiredField() {
    ObjectNode withoutModel = payload("ChargeHub", "Simulator X");
    withoutModel.remove("chargePointModel");

    assertThat(validate(withoutModel)).singleElement().asString().contains("chargePointModel");
  }

  @Test
  void rejectsValueLongerThanMaxLength() {
    assertThat(validate(payload("a vendor name that is too long", "Simulator X"))).hasSize(1);
  }

  @Test
  void rejectsUnknownProperty() {
    assertThat(validate(payload("ChargeHub", "Simulator X").put("firmware", "1.0"))).hasSize(1);
  }
}
