// :transport:nearby — offline rooms over Google Nearby Connections (Bluetooth / BLE / Wi-Fi Direct,
// chosen by the library). Android-only; the session layer never sees anything but GameTransport.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ir.dorehami.transport.nearby"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()

    defaultConfig {
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        // play-services-nearby ships .kotlin_module files built with a newer Kotlin metadata version
        // than our compiler; we only use its Java API, so the version check is safe to skip here.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    api(project(":transport:api"))
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
