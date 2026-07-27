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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Smart-Reminder-App"

include(":app")

include(":core:model")
include(":core:data")
include(":core:nlp")
include(":core:audio")
include(":core:stt")
include(":core:alarm")
include(":core:ui")

include(":feature:capture")
include(":feature:list")
include(":feature:call")
