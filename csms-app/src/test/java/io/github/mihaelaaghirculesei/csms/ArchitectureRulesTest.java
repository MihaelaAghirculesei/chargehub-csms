package io.github.mihaelaaghirculesei.csms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private static final String ROOT = "io.github.mihaelaaghirculesei.csms";

  private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages(ROOT);

  @Test
  void doesNotUseJackson2Databind() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fasterxml.jackson.databind..")
        .because("Jackson 2 is on the classpath only for springdoc; see docs/adr/0005")
        .check(ALL_CLASSES);
  }
}
