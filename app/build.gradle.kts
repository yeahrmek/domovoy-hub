import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 applies Kotlin itself; adding org.jetbrains.kotlin.android on top is
    // an error. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.domovoy"

    // Compiled against 37.1 because current AndroidX requires it; targetSdk stays
    // at 36, so runtime behaviour is unchanged. The two are deliberately apart.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "ru.domovoy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// JUnit5 needs the JUnit Platform runner. Done here rather than with the
// android-junit5 plugin — one dependency fewer for the same result.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
