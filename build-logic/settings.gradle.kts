// Same rule as the root settings: each group goes to the one repository that publishes it. An
// included build has its own settings, so the root rules do not reach the kotlin-dsl plugin.
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeGroupAndSubgroups("org.gradle.kotlin") }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeGroup("net.ltgt.gradle") }
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
