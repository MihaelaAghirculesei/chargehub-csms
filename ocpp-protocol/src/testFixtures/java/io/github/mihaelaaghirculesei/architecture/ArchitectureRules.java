package io.github.mihaelaaghirculesei.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.NullMarked;

/** Rules every module applies. Each module adds its own reason where the reason differs. */
public final class ArchitectureRules {

  private ArchitectureRules() {}

  public static ArchRule noJackson2Databind() {
    return noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fasterxml.jackson.databind..");
  }

  public static ArchRule everyPackageNullMarked() {
    return classes()
        .should(resideInNullMarkedPackage())
        .because(
            "@NullMarked does not apply to subpackages, and NullAway skips packages without it");
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
