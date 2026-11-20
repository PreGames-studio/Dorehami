plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ir.dorehami.hokmapp"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()
    defaultConfig {
        applicationId = "ir.dorehami.hokm"
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("dorehami.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("dorehami.versionCode").get().toInt()
        versionName = providers.gradleProperty("dorehami.versionName").get()
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    implementation(project(":games:hokm:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
