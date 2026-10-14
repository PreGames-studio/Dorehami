// :games:hokm:core — Hokm rules, scoring, deterministic bot, contract tests.
// Depends only on :core:gamesdk (which re-exports :core:protocol). No UI, no transport, no server.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Unique coordinates and jar name on purpose — see the note in `games/chess/core`:
// two modules called `core` would otherwise share `ir.dorehami:core:0.1.0` and one set of rules
// would be dropped when the APK is packaged.
group = "ir.dorehami.games.hokm"
version = "0.1.0"

base {
    archivesName = "dorehami-games-hokm-core"
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
