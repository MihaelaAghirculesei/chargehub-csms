import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Keeps parameter names in bytecode for Spring binding and readable stack traces.
    options.compilerArgs.add("-parameters")
}

dependencies {
    "errorprone"(libs.findLibrary("errorprone-core").get())
    "errorprone"(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    // Warnings nobody is forced to read accumulate; the build fails on them instead.
    options.compilerArgs.add("-Werror")
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Only packages annotated with @NullMarked are checked, following the JSpecify contract.
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    // Tests pass null on purpose to exercise contracts.
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        // XML for CI and code quality services, HTML for people.
        xml.required = true
        html.required = true
    }
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
    }
}
