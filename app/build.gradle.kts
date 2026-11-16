import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.androidx.baselineprofile)
}

/*
 * Release signing (M5, docs/DECISIONS.md D69). Keys are never committed: they come from
 * `keystore.properties` at the repo root (git-ignored; see keystore.properties.example) or,
 * on CI, from the REACH_KEYSTORE_* environment variables. Without either, the release build
 * is signed with the local debug key so it still installs for testing (and for Baseline
 * Profile generation) — Google Play refuses debug-signed uploads, so it can't ship by mistake.
 */
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }

fun signingValue(
    property: String,
    environment: String,
): String? = keystoreProperties.getProperty(property) ?: providers.environmentVariable(environment).orNull

val releaseStoreFile = signingValue("storeFile", "REACH_KEYSTORE_FILE")

android {
    namespace = "com.baklavapp.reachlauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.baklavapp.reachlauncher"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "REACH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "REACH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "REACH_KEY_PASSWORD")
            }
        }
    }

    // Reach switches language in-app (Appearance › Language). A Play bundle split by language
    // would only install the phone's language, so switching would show missing strings; the
    // strings are a few kilobytes, so every APK carries all of them.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            // R8 over the whole app (D69, replacing D16's library-only packageScope): shrinks,
            // optimizes and obfuscates Reach's own code too. Hilt, Room, DataStore and Compose
            // ship their own consumer keep rules; manifest components (activity, services) are
            // kept by AAPT's generated rules; our extra rules are in src/main/keepRules/.
            optimization {
                enable = true
            }
            // Drops resources nothing references (after R8 removed the code that used them).
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric needs the merged resources and manifest (MainActivity, its theme).
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // New AndroidX/library releases appear weekly; version bumps are a deliberate
        // task (see docs/DECISIONS.md), not a lint failure. ObsoleteLintCustomCheck: some
        // AndroidX libraries ship lint checks built for a different lint API; lint skips
        // them and warns, which warningsAsErrors would otherwise turn into a failure.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:system"))
    implementation(project(":feature:home"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Installs the Baseline Profile (src/release/generated/baselineProfiles) at install time
    // on devices whose app store doesn't, e.g. sideloads (D68).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // Robolectric runs MainActivity with the real Hilt graph on the JVM (no Hilt testing
    // artifact needed: the real ReachApplication creates the component).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
