plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ir.dorehami.app"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()

    defaultConfig {
        applicationId = providers.gradleProperty("dorehami.applicationId").get()
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("dorehami.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("dorehami.versionCode").get().toInt()
        versionName = providers.gradleProperty("dorehami.versionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// AGP 9 has built-in Kotlin — do NOT apply org.jetbrains.kotlin.android here
// (see https://developer.android.com/build/migrate-to-built-in-kotlin).
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Games are independent modules; the app only wires them into navigation.
    implementation(project(":games:hokm:ui"))
    implementation(project(":core:uikit"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
