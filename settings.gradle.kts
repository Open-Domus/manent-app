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

rootProject.name = "OmiCompanion"

include(":app")

// Omnesis modules copied by scripts/vendor.sh, kept under their own names so
// their build scripts' project(":…") references resolve unchanged.
listOf(
    "core-transport",
    "core-pairing",
    "core-designsystem",
    "feature-recording",
    "feature-omi",
    "feature-assist",
).forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("vendor/$name")
}
