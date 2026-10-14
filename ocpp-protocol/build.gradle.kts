plugins {
    id("chargehub.java-conventions")
    `java-library`
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
}
