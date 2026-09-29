package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaConfigurationTest {

  @Autowired private ApplicationContext context;

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void doesNotKeepEntityManagerOpenInView() {
    assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
  }

  @Test
  void validatesSchemaInsteadOfGeneratingIt() {
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");
  }

  @Test
  void writesTimestampsInUtc() {
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.jdbc.time_zone", "UTC");
  }
}
