pluginManagement {
    includeBuild("build-logic")
    // Each group goes to the one repository that publishes it. Gradle moves to the next repository
    // only on a 404, so a lookup in the wrong place is one more request that a rate limit can fail.
    // A plugin from a new Plugin Portal group fails with "not found" until its group is added here.
    // This block also resolves what build-logic depends on, such as the Error Prone plugin.
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter {
                includeGroupAndSubgroups("org.gradle.toolchains")
                includeGroup("net.ltgt.gradle")
                includeGroup("org.sonarqube")
                includeGroup("org.sonarsource.scanner.gradle")
            }
        }
        mavenCentral()
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
