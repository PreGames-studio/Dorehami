// :transport:api — the pipe contract every transport (Nearby, WebSocket, in-memory) implements.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "ir.dorehami"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:protocol"))
    api(libs.kotlinx.coroutines.core)
}
