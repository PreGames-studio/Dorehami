plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":core:gamesdk"))
    implementation(project(":core:protocol"))
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // روی CI خروجیِ کامل همیشه در دسترس نیست؛ نام و پیامِ تست‌های شکست‌خورده را چاپ کن
    // تا job بتواند آن‌ها را به annotation تبدیل کند.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        showCauses = true
        showExceptions = true
    }
}
