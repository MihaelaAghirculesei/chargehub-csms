package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RegistrationStatus {
  @JsonProperty("Accepted")
  ACCEPTED,
  @JsonProperty("Pending")
  PENDING,
  @JsonProperty("Rejected")
  REJECTED
}
