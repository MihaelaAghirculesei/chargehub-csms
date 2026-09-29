import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("chargehub.java-conventions")
    alias(libs.plugins.spring.boot)
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    // Gradle's native platform support instead of the dependency-management plugin.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    // Without it Flyway fails with "Unsupported Database: PostgreSQL 18".
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    // Brings Jackson 2 through swagger-core; allowed only there, see docs/adr/0005.
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(platform(SpringBootPlugin.BOM_COORDINATES))
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.test {
    // Tests take container images from the compose file so local runs and tests cannot drift apart.
    // Declared as an input so changing an image reruns the tests.
    val composeFile = rootProject.file("deploy/docker-compose.yml")
    inputs.file(composeFile).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("chargehub.compose-file", composeFile.absolutePath)

    // Mockito otherwise attaches itself at runtime, which the JDK warns about and will disallow.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
}
