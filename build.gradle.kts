// Top-level build file. Modules configure themselves; nothing global happens here on purpose, so a
// new game module can be added without touching this file (see docs/ADD_A_GAME.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktor) apply false
}

/**
 * Every JVM module ships a uniquely named jar.
 *
 * Without this, `:core:protocol` and `:core:gamesdk` both produce `core.jar` and one silently
 * overwrites the other inside a distribution (and on a VPS, inside `lib/`) — a bug that only shows up
 * as a mysterious `NoClassDefFoundError` at startup. The same names are used by `installDist`, so the
 * self-hosting script in `docs/DEPLOY.md` can rely on them.
 */
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.gradle.api.plugins.BasePluginExtension>("base") {
            archivesName.set("dorehami-" + project.path.removePrefix(":").replace(':', '-'))
        }
    }
}

/**
 * Convenience for contributors and CI: `./gradlew checkAll` runs everything that does not need an
 * emulator. Android instrumentation tests are intentionally excluded (see docs/CI.md).
 */
tasks.register("checkAll") {
    group = "verification"
    description = "Runs all JVM unit tests of every module."
    dependsOn(
        ":core:protocol:test",
        ":core:gamesdk:test",
        ":core:session:test",
        ":transport:api:test",
        ":transport:online:test",
        ":games:hokm:core:test",
        ":games:pantohub:core:test",
        ":server:test",
    )
}
