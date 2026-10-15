package io.github.mihaelaaghirculesei.csms;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.mihaelaaghirculesei.architecture.ArchitectureRules;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private static final String ROOT = "io.github.mihaelaaghirculesei.csms";

  private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages(ROOT);

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  void doesNotUseJackson2Databind() {
    ArchitectureRules.noJackson2Databind()
        .because("Jackson 2 is on the classpath only for springdoc; see docs/adr/0005")
        .check(ALL_CLASSES);
  }

  @Test
  void everyProductionPackageIsNullMarked() {
    ArchitectureRules.everyPackageNullMarked().check(PRODUCTION_CLASSES);
  }
}
