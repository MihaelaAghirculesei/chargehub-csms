package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

  @Autowired private JdbcClient jdbc;

  @Test
  void runsOnThePostgresVersionFromCompose() {
    String serverVersion = jdbc.sql("show server_version").query(String.class).single();
    String composeTag = DockerCompose.image("postgres").replaceAll("^postgres:([0-9.]+)-.*$", "$1");

    assertThat(serverVersion).startsWith(composeTag);
  }

  @Test
  void appliesBaselineMigration() {
    boolean applied =
        jdbc.sql("select success from flyway_schema_history where version = '1'")
            .query(Boolean.class)
            .single();

    assertThat(applied).isTrue();
  }
}
