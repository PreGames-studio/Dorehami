// :transport:online — WebSocket GameTransport + thin HTTP client for the Dorehami server
// (guest login, rooms, random-opponent queue). Pure JVM; the app supplies the OkHttp engine.
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
    api(project(":transport:api"))
    api(project(":core:session"))
    api(libs.ktor.client.core)
    api(libs.ktor.client.websockets)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.client.cio)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
