// :core:session — room lifecycle on top of any GameTransport: lobby, host authority, view fan-out,
// bots for empty/disconnected seats, resync. Pure Kotlin/JVM, no Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "ir.dorehami"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:protocol"))
    api(project(":core:gamesdk"))
    api(project(":transport:api"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testkit"))
    testImplementation(project(":games:hokm:core"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
