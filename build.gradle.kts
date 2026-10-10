// Root build: verification aggregate only.
// Each module configures itself in its own build.gradle.kts (see docs/GITHUB_SETUP.md tree).
//
// `./gradlew checkAll` runs every JVM test in the repo — the exact command CI runs (AGENTS.md section 3).

plugins {
    // Version declarations shared by modules; each module applies what it needs.
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs the 'check' task of every module that has one (all JVM tests)."
    // Lazy task collections: modules not yet created (design phase) are simply skipped.
    dependsOn(subprojects.map { sp -> sp.tasks.matching { it.name == "check" } })
}
