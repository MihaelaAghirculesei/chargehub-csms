package io.github.mihaelaaghirculesei.csms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Module boundaries from docs/adr/0002, checked on every build. */
class ApplicationModulesTest {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(CsmsApplication.class).verify();
  }
}
