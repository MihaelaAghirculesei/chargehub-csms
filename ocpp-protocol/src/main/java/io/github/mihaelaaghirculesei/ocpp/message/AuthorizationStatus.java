package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AuthorizationStatus {
  @JsonProperty("Accepted")
  ACCEPTED,
  @JsonProperty("Blocked")
  BLOCKED,
  @JsonProperty("Expired")
  EXPIRED,
  @JsonProperty("Invalid")
  INVALID,
  @JsonProperty("ConcurrentTx")
  CONCURRENT_TX
}
