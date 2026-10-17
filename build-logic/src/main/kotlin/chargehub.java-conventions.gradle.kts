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

// The gate covers every class of a module unless the module narrows classDirectories to where
// its logic lives. Applied to the module as a whole: per package, a small package would swing on
// a single branch.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
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
// Reads the resolved dependency graph, not the files: versions are all the check needs, and the
// graph is known without building the jars of the project modules a module depends on.
val resolvedJackson =
    configurations.named("runtimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }.map { root ->
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(listOf(root))
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (seen.add(component)) {
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending.add(it.selected) }
            }
        }
        seen
            .mapNotNull { it.id as? ModuleComponentIdentifier }
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
