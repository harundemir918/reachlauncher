// Baseline Profile generator (M5, docs/DECISIONS.md D68).
//
// A `com.android.test` module: its code runs on a device/emulator and drives the *installed*
// :app with UiAutomator (≈ an integration_test that exercises a release build). The
// androidx.baselineprofile plugin wires it to :app, so `./gradlew :app:generateBaselineProfile`
// builds a non-minified release, runs BaselineProfileGenerator on a connected device and writes
// the result to app/src/release/generated/baselineProfiles/. Never part of `build`: it needs a
// device (API 33+, or a rooted/userdebug API 28+).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.baklavapp.reachlauncher.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
}

// Run on whatever device `adb devices` shows (no Gradle-managed emulator: it would download
// a system image during the build).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
