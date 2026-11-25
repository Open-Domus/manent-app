// SPDX-License-Identifier: MIT

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Manent"

include(":app")

include(
    ":core-transport",
    ":core-pairing",
    ":core-designsystem",
    ":feature-recording",
    ":feature-omi",
    ":feature-assist",
)
