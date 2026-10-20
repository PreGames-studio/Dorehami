// :games:dejban:ui — Android host for the Godot/GDScript Dejban game.
//
// Dejban is deliberately not translated to Kotlin. This library owns only the
// thin Android shell: the Godot Android library, a dedicated host Activity,
// and the generated `dezhban.pck` packaged as an asset. Game logic stays in
// `games/dejban/godot`.
plugins {
    alias(libs.plugins.android.library)
}

val dejbanPack = File(rootDir, "games/dejban/godot/build/dezhban.pck")
val generatedDejbanAssets = layout.buildDirectory.dir("generated/dejbanAssets")

val prepareDejbanPack by tasks.registering(Copy::class) {
    description = "Copies the exported Dejban Godot pack into this library's assets."
    group = "build"

    doFirst {
        check(dejbanPack.isFile) {
            "Missing Dejban Godot pack: ${dejbanPack.absolutePath}. " +
                "Generate it with Godot 4.3 (`--export-pack Android build/dezhban.pck`) " +
                "or run the CI build, which performs that step automatically."
        }
    }

    from(dejbanPack)
    into(generatedDejbanAssets)
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
    implementation(libs.godot.android)
}
