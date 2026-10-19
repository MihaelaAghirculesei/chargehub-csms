import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("chargehub.java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":ocpp-protocol")))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Only the code that plays a charge point counts for the gate. The Spring wiring and the entry
// point are covered by the application test instead.
tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(sourceSets.main.map { it.output.asFileTree.matching { include("**/chargepoint/**") } })
}
