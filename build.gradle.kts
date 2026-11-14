// Root build file: only declares plugin versions (apply false) so every module resolves
// the same versions from gradle/libs.versions.toml. Modules apply what they need.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
}

// Pin the ktlint engine itself: the Gradle plugin otherwise uses its own (older) default,
// which may not parse the Kotlin version we compile with.
subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(libs.versions.ktlintEngine.get())
        }
    }
}
