import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9 applies Kotlin itself; adding org.jetbrains.kotlin.android on top is
    // an error. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The flat's household id and the Yandex OAuth token are apartment-identifying, so they live in
// local.properties (gitignored) and reach the code only as BuildConfig constants. Absent values
// stay empty rather than failing the build — a checkout with no local.properties must still
// compile and run the tests; the panel surfaces the missing token as a visible error instead.
//
// YANDEX_OAUTH_TOKEN is a *seed* only: on first launch it is written into
// EncryptedSharedPreferences, and from then on the panel reads the store, never this constant.
// It is still in the APK, so it is the one thing here that a build without a token is better off
// without — see docs/yandex.md, "How the token gets in".
// Read through a UTF-8 Reader, not an InputStream: `Properties.load(InputStream)` decodes
// ISO-8859-1, and the room names in `tuya.rooms` are Cyrillic. Loaded the other way, "Спальня"
// reaches BuildConfig as "Ð¡Ð¿Ð°Ð»ÑŒÐ½Ñ" — which is not an error anywhere, it just puts every
// recuperator in a section named after the mojibake. Verified by reading the generated
// BuildConfig.java; nothing in `src/test/` can see this file.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.reader(Charsets.UTF_8).use(::load)
    }

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty()

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

        buildConfigField("String", "YANDEX_HOUSEHOLD_ID", "\"${localProperty("yandex.household.id")}\"")
        buildConfigField("String", "YANDEX_OAUTH_TOKEN", "\"${localProperty("yandex.oauth.token")}\"")

        // Tuya's cloud project credentials and the uid of the Smart Life account linked to it,
        // seeded into the same encrypted store on first launch and read from there afterwards.
        // `tuya.region.host` is deliberately not here: the account is in Central Europe and that
        // is the client's own default, so a second place to get it wrong buys nothing.
        buildConfigField("String", "TUYA_CLIENT_ID", "\"${localProperty("tuya.client.id")}\"")
        buildConfigField("String", "TUYA_CLIENT_SECRET", "\"${localProperty("tuya.client.secret")}\"")
        buildConfigField("String", "TUYA_UID", "\"${localProperty("tuya.uid")}\"")

        // Which room each recuperator is in — `xfj-01=Спальня;xfj-05=Зал`. Not a credential, but
        // apartment-identifying all the same, since it is a list of device ids; and it is here
        // rather than in the code because Tuya's API answers nothing about grouping and the flat
        // is the only thing that knows. Unset means the recuperators show up in the panel's
        // unplaced section, which is a working panel. See ru.domovoy.panel.recuperatorRooms.
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
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

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
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
