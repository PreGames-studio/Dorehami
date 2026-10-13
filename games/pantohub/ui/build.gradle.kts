plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ir.dorehami.games.pantohub.ui"
    compileSdk = providers.gradleProperty("dorehami.compileSdk").get().toInt()

    defaultConfig {
        minSdk = providers.gradleProperty("dorehami.minSdk").get().toInt()
        // Word-bank management is for the developer only. Enabled with -Pdorehami.pantohubDeveloperTools=true.
        val developerTools = providers.gradleProperty("dorehami.pantohubDeveloperTools").orNull
            ?.trim()
            ?.equals("true", ignoreCase = true) ?: false
        buildConfigField("boolean", "PANTOHUB_DEVELOPER_TOOLS", developerTools.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":games:pantohub:core"))
    implementation(project(":core:uikit"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
