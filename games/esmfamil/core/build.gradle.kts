// :games:esmfamil:core — the اسم فامیل sheet: letters, categories, normalisation, scoring and the
// deterministic session. Depends only on :core:gamesdk (contract rule: a game talks to the SDK).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Unique coordinates and jar name — two modules called `core` would otherwise share
// `ir.dorehami:core:0.1.0` and one set of rules would be dropped when the APK is packaged.
group = "ir.dorehami.games.esmfamil"
version = "0.1.0"

base {
    archivesName = "dorehami-games-esmfamil-core"
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
