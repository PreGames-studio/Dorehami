// :transport:hotspot — server-free rooms over a phone hotspot or any local Wi-Fi network.
// Pure Kotlin/JVM so framing, lifecycle and loopback gameplay can be tested without an emulator.
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
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
