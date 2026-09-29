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

// Every module must resolve Jackson 3 to the catalog version, which equals the Spring Boot one.
// Without this, a library asking for a newer Jackson line silently moves the whole application.
// See docs/adr/0006.
val expectedJackson = libs.findVersion("jackson").get().requiredVersion
val resolvedJackson =
    configurations.named("runtimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
        artifacts
            .mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
            .filter { it.group == "tools.jackson.core" && it.module == "jackson-databind" }
            .map { it.version }
            .toSortedSet()
    }

val verifyJacksonAlignment =
    tasks.register("verifyJacksonAlignment") {
        group = "verification"
        description = "Fails when jackson-databind does not resolve to the catalog version."
        val expected = expectedJackson
        val resolved = resolvedJackson
        val projectPath = project.path
        doLast {
            val versions = resolved.get()
            if (versions.isNotEmpty() && versions != sortedSetOf(expected)) {
                throw GradleException(
                    "$projectPath resolves jackson-databind $versions, expected $expected. " +
                        "Upgrade Jackson together with Spring Boot, see docs/adr/0006.",
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyJacksonAlignment)
}
