import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("chargehub.java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(project(":ocpp-protocol"))

    // No web starter: the simulator is a WebSocket client, and the JDK client needs no server.
    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
    // Only the test central system runs a WebSocket server.
    testImplementation(libs.spring.boot.starter.websocket)
    testImplementation(testFixtures(project(":ocpp-protocol")))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Only the code that plays a charge point counts for the gate. The Spring wiring and the entry
// point are covered by the application test instead.
tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(sourceSets.main.map { it.output.asFileTree.matching { include("**/chargepoint/**") } })
}
