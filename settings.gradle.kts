pluginManagement {
    repositories {
        google()
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

rootProject.name = "dorehami"

include(
    ":app",
    ":server",
    ":core:protocol",
    ":core:gamesdk",
    ":core:session",
    ":core:testkit",
    ":core:uikit",
    ":transport:api",
    ":transport:nearby",
    ":transport:hotspot",
    ":transport:online",
    ":games:hokm:core",
    ":games:hokm:ui",
    ":games:pantohub:core",
    ":games:pantohub:ui",
    ":games:chess:core",
    ":games:chess:ui",
    ":games:dejban:core",
    ":games:dejban:ui",
)
