// :games:backgammon:core — backgammon rules (movement, bar, bearing off, gammon/backgammon
// scoring), deterministic bot and contract tests. Depends only on :core:gamesdk.
// No UI, no transport, no server.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Unique coordinates and jar name: every `games/<game>/core` module is called `core`, so without
// this they all build the same `core-0.1.0.jar` and Android packaging keeps only one of them.
group = "ir.dorehami.games.backgammon"
version = "0.1.0"

base {
    archivesName = "dorehami-games-backgammon-core"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:gamesdk"))

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
