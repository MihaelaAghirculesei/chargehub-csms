package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Reads the charge point id from the last path segment and refuses the upgrade when it is not a
 * plain identifier. The id becomes a map key and a log field, so it is checked once, here.
 */
class ChargePointIdInterceptor implements HandshakeInterceptor {

  static final String ATTRIBUTE = "chargePointId";

  // 48 is the longest identity OCPP 2.0.1 allows; OCPP 1.6 sets no limit, but one is needed.
  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,48}");

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String path = request.getURI().getPath();
    String chargePointId = path.substring(path.lastIndexOf('/') + 1);
    if (!VALID_ID.matcher(chargePointId).matches()) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }
    attributes.put(ATTRIBUTE, chargePointId);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      @Nullable Exception exception) {
    // Nothing to do once the upgrade is done: the id is only checked before it.
  }
}
