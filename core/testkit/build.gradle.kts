// :core:testkit — in-memory fakes for fast tests (no mock framework). Main source set on purpose:
// other modules add it as testImplementation.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "ir.dorehami"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":transport:api"))
    api(project(":core:gamesdk"))
    api(libs.kotlinx.coroutines.core)
}
