// :games:chess:core — chess rules (moves, check, mate, stalemate, draws), SAN, deterministic bot,
// contract tests. Depends only on :core:gamesdk (which re-exports :core:protocol).
// No UI, no transport, no server.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Unique coordinates and jar name on purpose: every `games/<game>/core` module is called `core`,
// so without this they all build the same `core-0.1.0.jar`; Android packaging then keeps only one
// of them and the other game's rules silently vanish from the APK (`NoClassDefFoundError` on the
// device — the chess crash of 2026-10-13). The `APK contents` workflow guards this from now on.
group = "ir.dorehami.games.chess"
version = "0.1.0"

base {
    archivesName = "dorehami-games-chess-core"
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
