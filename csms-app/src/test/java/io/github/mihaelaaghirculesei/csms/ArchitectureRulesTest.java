package io.github.mihaelaaghirculesei.csms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.NullMarked;
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
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fasterxml.jackson.databind..")
        .because("Jackson 2 is on the classpath only for springdoc; see docs/adr/0005")
        .check(ALL_CLASSES);
  }

  @Test
  void everyProductionPackageIsNullMarked() {
    classes()
        .should(resideInNullMarkedPackage())
        .because(
            "@NullMarked does not apply to subpackages, and NullAway skips packages without it")
        .check(PRODUCTION_CLASSES);
  }

  private static ArchCondition<JavaClass> resideInNullMarkedPackage() {
    return new ArchCondition<>("reside in a package annotated with @NullMarked") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (!javaClass.getPackage().isAnnotatedWith(NullMarked.class)) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " is in package "
                      + javaClass.getPackageName()
                      + ", which has no @NullMarked package-info.java"));
        }
      }
    };
  }
}
