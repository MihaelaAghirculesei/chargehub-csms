package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

  private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);

  @Test
  void logsOneEcsJsonObjectPerLine(CapturedOutput output) {
    log.info("structured logging probe");

    JsonNode json =
        JsonMapper.shared().readTree(lineContaining(output, "structured logging probe"));
    assertThat(json.path("message").asString()).isEqualTo("structured logging probe");
    assertThat(json.path("log").path("level").asString()).isEqualTo("INFO");
    assertThat(json.path("service").path("name").asString()).isEqualTo("csms");
    assertThat(json.path("@timestamp").asString()).endsWith("Z");
  }

  static String lineContaining(CapturedOutput output, String text) {
    return output.getOut().lines().filter(line -> line.contains(text)).findFirst().orElseThrow();
  }
}
