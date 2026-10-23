// :games:dejban:ui — Android host for the Godot/GDScript Dejban game.
//
// Dejban is deliberately not translated to Kotlin. This library owns only the
// thin Android shell: the Godot Android library, a dedicated host Activity,
// and the generated `dezhban.pck` packaged as an asset. Game logic stays in
// `games/dejban/godot`.
plugins {
    alias(libs.plugins.android.library)
}

val generatedDejbanAssets =
    layout.buildDirectory.get().asFile.resolve("generated/dejbanAssets")

val prepareDejbanPack = tasks.register<Copy>("prepareDejbanPack") {
    description = "Copies the exported Dejban Godot pack into this library's assets."
    group = "build"

    // Keep the pack path local to this task: capturing script-level values in a
    // task action makes the task impossible to store in the configuration cache.
    val dejbanPack = project.rootProject.file("games/dejban/godot/build/dezhban.pck")
    inputs.file(dejbanPack)

    doFirst {
        check(dejbanPack.isFile) {
            "Missing Dejban Godot pack: ${dejbanPack.absolutePath}. " +
                "Generate it with Godot 4.3 (`--export-pack Android build/dezhban.pck`) " +
                "or run the CI build, which performs that step automatically."
        }
    }

    from(dejbanPack)
    into(generatedDejbanAssets)

    // The pack is produced outside Gradle (Godot export in CI / by hand locally),
    // so this copy intentionally opts out of configuration-cache serialization.
    notCompatibleWithConfigurationCache(
        "Copies the Godot pack generated outside Gradle into the library assets."
    )
}

android {
    namespace = "ir.dorehami.games.dejban.ui"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()

    defaultConfig {
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedDejbanAssets)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(prepareDejbanPack)
    }
}

dependencies {
    implementation(libs.androidx.fragment)
    implementation(libs.godot.android)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
