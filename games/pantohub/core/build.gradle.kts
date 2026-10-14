plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// Unique coordinates and jar name on purpose — see the note in `games/chess/core`.
group = "ir.dorehami.games.pantohub"
version = "0.1.0"

base {
    archivesName = "dorehami-games-pantohub-core"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
