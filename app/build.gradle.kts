import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9 applies Kotlin itself.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Apartment-specific values stay in gitignored local.properties; credentials seed the encrypted
// store on first launch. Empty values keep unconfigured checkouts buildable. These seeds remain
// extractable from the APK; see docs/yandex.md. UTF-8 preserves Cyrillic room names.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.reader(Charsets.UTF_8).use(::load)
    }

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty()

fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (char.code < 32) {
                append('\\')
                append(char.code.toString(8).padStart(3, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}

android {
    namespace = "ru.domovoy"

    // AndroidX needs 37.1; targetSdk 36 controls runtime compatibility.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "ru.domovoy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "YANDEX_HOUSEHOLD_ID", "\"${localProperty("yandex.household.id")}\"")
        buildConfigField("String", "YANDEX_OAUTH_TOKEN", "\"${localProperty("yandex.oauth.token")}\"")

        // First-run seeds for Tuya; its region default belongs to the client.
        buildConfigField("String", "TUYA_CLIENT_ID", "\"${localProperty("tuya.client.id")}\"")
        buildConfigField("String", "TUYA_CLIENT_SECRET", "\"${localProperty("tuya.client.secret")}\"")
        buildConfigField("String", "TUYA_UID", "\"${localProperty("tuya.uid")}\"")

        // JSON preserves key characters that delimiter-separated configuration cannot represent.
        buildConfigField("String", "TUYA_LOCAL_DEVICES", javaStringLiteral(localProperty("tuya.local.devices")))

        // Tuya exposes no rooms. Map device ids locally; unset means unplaced tiles.
        buildConfigField("String", "TUYA_ROOMS", "\"${localProperty("tuya.rooms")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric needs real drawables and strings.
        unitTests.isIncludeAndroidResources = true

        // Plain JVM error-path tests call Log.w; android.jar must not throw there.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // All panel composables use Material 3 Expressive.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// JUnit5 runs directly on the JUnit Platform.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Robolectric and Compose UI tests use JUnit4 through the vintage engine.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
