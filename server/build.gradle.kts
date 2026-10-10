plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Plain Gradle application plugin (provides `run`) instead of io.ktor.plugin:
    // the Ktor plugin drags its own Kotlin/AGP interop onto the build classpath and
    // clashes with AGP 9 built-in Kotlin. Ktor itself is just a dependency below.
    id("application")
}

group = "ir.dorehami"
version = "0.1.0"

application {
    mainClass.set("ir.dorehami.server.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Shared kernel: the server is an *authority*, it reuses exactly the same rules and protocol.
    implementation(project(":core:gamesdk"))
    implementation(project(":core:session"))
    implementation(project(":transport:api"))

    // Games that the server can arbitrate.
    implementation(project(":games:hokm:core"))
    implementation(project(":games:pantohub:core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Persistence (optional at runtime: db.enabled=false runs fully in memory for development).
    // Deliberately plain Hikari + Flyway + JDBC: our schema is three queries deep, and skipping an ORM
    // removes a whole class of version-mismatch build failures for the agents working on this repo.
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikari)

    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
