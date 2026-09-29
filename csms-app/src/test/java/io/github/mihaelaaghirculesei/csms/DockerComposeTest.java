package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class DockerComposeTest {

  @Test
  void readsPinnedPostgresImage() {
    assertThat(DockerCompose.image("postgres"))
        .matches("postgres:18\\.\\d+-trixie@sha256:[0-9a-f]{64}");
  }

  @Test
  void failsClearlyForUnknownService() {
    assertThatIllegalStateException()
        .isThrownBy(() -> DockerCompose.image("mysql"))
        .withMessageContaining("services.mysql");
  }
}
