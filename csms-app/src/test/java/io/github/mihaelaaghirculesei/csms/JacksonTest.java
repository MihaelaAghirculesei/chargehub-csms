package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JacksonTest {

  record Sample(String id, Instant at) {}

  private static final Sample SAMPLE = new Sample("cp-1", Instant.parse("2026-03-29T00:30:00Z"));
  private static final String JSON = "{\"id\":\"cp-1\",\"at\":\"2026-03-29T00:30:00Z\"}";

  @Autowired private JsonMapper mapper;

  @Test
  void writesRecordWithInstantAsIso8601() {
    assertThat(mapper.writeValueAsString(SAMPLE)).isEqualTo(JSON);
  }

  @Test
  void readsRecordWithIso8601Instant() {
    assertThat(mapper.readValue(JSON, Sample.class)).isEqualTo(SAMPLE);
  }
}
