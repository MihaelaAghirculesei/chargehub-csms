import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("chargehub.java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    // Gradle's native platform support instead of the dependency-management plugin.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
