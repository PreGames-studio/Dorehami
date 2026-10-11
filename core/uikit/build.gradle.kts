// :core:uikit — shared Compose pieces (Persian digits, sound player, page shells). No game logic, no state.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ir.dorehami.core.uikit"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()

    defaultConfig {
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 has built-in Kotlin — use the Compose compiler plugin without kotlin-android.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
