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
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(platform(SpringBootPlugin.BOM_COORDINATES))
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.test {
    // Mockito otherwise attaches itself at runtime, which the JDK warns about and will disallow.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
}
