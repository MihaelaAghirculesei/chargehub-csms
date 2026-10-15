package io.github.mihaelaaghirculesei.ocpp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.mihaelaaghirculesei.architecture.ArchitectureRules;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private static final String ROOT = "io.github.mihaelaaghirculesei.ocpp";

  private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages(ROOT);

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  void doesNotUseJackson2Databind() {
    ArchitectureRules.noJackson2Databind()
        .because("Jackson 3 only, aligned on the Spring Boot version; see docs/adr/0006")
        .check(ALL_CLASSES);
  }

  @Test
  void everyProductionPackageIsNullMarked() {
    ArchitectureRules.everyPackageNullMarked().check(PRODUCTION_CLASSES);
  }

  @Test
  void doesNotDependOnSpring() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework..")
        .because("the protocol module stays framework-free; see docs/adr/0003 and docs/adr/0004")
        .check(PRODUCTION_CLASSES);
  }
}
