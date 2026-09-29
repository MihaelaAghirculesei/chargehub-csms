package io.github.mihaelaaghirculesei.csms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL container for the whole test run. Each distinct Spring test context gets this
 * bean, but they all share the same static container instead of starting one each.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(byDigest(DockerCompose.image("postgres")));

  // No destroy method: closing one cached context must not stop the container for the others.
  // Testcontainers removes it when the JVM exits.
  @Bean(destroyMethod = "")
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return POSTGRES;
  }

  /**
   * Testcontainers cannot parse a reference with both tag and digest. Docker ignores the tag when a
   * digest is present, so pulling by digest alone fetches the same image.
   */
  static DockerImageName byDigest(String reference) {
    return DockerImageName.parse(reference.replaceFirst(":[^@/]+@", "@"));
  }
}
