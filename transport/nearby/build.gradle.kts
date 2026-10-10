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
    }
}

dependencies {
    api(project(":transport:api"))
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
