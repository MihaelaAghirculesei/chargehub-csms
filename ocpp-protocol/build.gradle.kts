plugins {
    id("chargehub.java-conventions")
    `java-library`
    // Shares the architecture rules with the other modules instead of copying them.
    `java-test-fixtures`
}

// No Spring Boot BOM here: this module stays framework-free and on JUnit Platform 1.x for jqwik.
dependencies {
    // Keeps Jackson on the Spring Boot version, see docs/adr/0006.
    // On api because frames expose Jackson nodes, so consumers need the same constraint.
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    // Nullness annotations are part of the public signatures.
    api(libs.jspecify)
    implementation(libs.json.schema.validator)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesApi(libs.archunit)
}

// The scanner indexes only the main and test source sets. The shared architecture rules in the
// test fixtures are test code as well, so they are analysed with the tests.
val testFixtures = sourceSets["testFixtures"]
configure<org.sonarqube.gradle.SonarExtension> {
    properties {
        property("sonar.tests", sourceSets.test.get().java.srcDirs + testFixtures.java.srcDirs)
        property(
            "sonar.java.test.binaries",
            sourceSets.test.get().output.classesDirs.files + testFixtures.output.classesDirs.files,
        )
    }
}
