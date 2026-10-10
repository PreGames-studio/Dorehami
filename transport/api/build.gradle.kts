// :transport:api — the pipe contract every transport (Nearby, WebSocket, in-memory) implements,
// plus the chunk framing used by packet-limited transports.
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
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
