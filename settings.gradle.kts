pluginManagement {
    includeBuild("build-logic")
    // Maven Central first: the Plugin Portal only redirects library requests there, and a failed
    // redirect turns the Plugin Portal off for the whole build. The Plugin Portal stays for the
    // plugins published nowhere else.
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Repositories are declared once here so no module can pull from an unreviewed source.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "chargehub-csms"

include("ocpp-protocol", "csms-app", "charge-point-simulator")
