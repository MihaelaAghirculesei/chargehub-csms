plugins {
    // Loads the convention plugin once in the root classloader. Without this, modules that apply
    // extra plugins (Spring Boot) get their own copy, and Spotless cannot share its build service
    // between sibling projects.
    id("chargehub.java-conventions") apply false
    alias(libs.plugins.sonarqube)
}

// The token is read from SONAR_TOKEN in the environment, never from the build files.
// Coverage comes from the JaCoCo XML report of each module.
sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "mihaelaaghirculesei")
        property("sonar.projectKey", "MihaelaAghirculesei_chargehub-csms")
        // A main method only hands over to Spring; the context tests cover what it starts.
        property("sonar.coverage.exclusions", "**/src/main/java/**/*Application.java")
    }
}
