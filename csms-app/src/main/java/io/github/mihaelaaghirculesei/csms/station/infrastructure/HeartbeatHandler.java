package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.ocpp.message.HeartbeatResponse;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/** Answers with the central system time, which charge points use to keep their clock in sync. */
@Component
class HeartbeatHandler implements ActionHandler {

  private final Clock clock;

  HeartbeatHandler(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Action action() {
    return Action.HEARTBEAT;
  }

  @Override
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    return OcppMessages.write(new HeartbeatResponse(clock.instant()));
  }
}
