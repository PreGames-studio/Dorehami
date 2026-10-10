// :core:gamesdk — the game contract (docs/GAME_CONTRACT.md): GameModule, GameSession, GameEngine,
// deterministic Rng/Clock, MatchRecord + replay, GameRegistry.
// Depends only on :core:protocol (AGENTS.md section 2).
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
    // Views, options and snapshots are plain JSON objects (contract rule 6/7).
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
