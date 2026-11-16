package com.baklavapp.reachlauncher.core.system.language

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.source.AppLanguageSource
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reach's own display language (Appearance › Language).
 *
 * **Android 13+.** The system keeps a language per app (`LocaleManager.applicationLocales`),
 * which is also what Settings › Apps › Reach › Language edits — the manifest's
 * `android:localeConfig` lists the languages Reach has. Setting it re-configures the running
 * Activity: `MainActivity` handles `locale` config changes itself, so Compose simply
 * recomposes with the new strings, without a restart. Because the user can change it in
 * Settings too, [language] is re-read on every resume ([AccessRechecks]).
 *
 * **Android 9–12** have no per-app language. The choice is stored in a small
 * SharedPreferences file (read synchronously when the Activity is created, so it can't be
 * DataStore), applied by [AppLocale.wrap] in `MainActivity.attachBaseContext`, and a change
 * recreates the Activity once so it is rebuilt in the new language.
 *
 * Flutter analogy: setting `MaterialApp(locale:)` from a settings provider — except that on
 * Android 13+ the OS stores the choice for you.
 */
@Singleton
class SystemAppLanguageSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tracker: ActivityTracker,
        rechecks: AccessRechecks,
    ) : AppLanguageSource {
        /** Bumped on every [setLanguage], so [language] re-reads even between resumes. */
        private val changes = MutableStateFlow(0)

        override val language: Flow<AppLanguage> =
            combine(rechecks.ticks, changes) { _, _ -> read() }.distinctUntilChanged()

        override fun setLanguage(language: AppLanguage) {
            if (language == read()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPerAppLocales(language)
            } else {
                AppLocale.store(context, language)
                // The Activity is rebuilt once, through attachBaseContext, in the new language.
                tracker.resumedActivity?.recreate()
            }
            changes.value++
        }

        private fun read(): AppLanguage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppLanguage.fromTags(perAppLocales()?.toLanguageTags())
            } else {
                AppLocale.stored(context)
            }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun perAppLocales(): LocaleList? = context.getSystemService<LocaleManager>()?.applicationLocales

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun setPerAppLocales(language: AppLanguage) {
            val locales = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            context.getSystemService<LocaleManager>()?.applicationLocales = locales
        }
    }

/**
 * The Android 9–12 half of [SystemAppLanguageSource]: the stored choice and the context
 * wrapper that applies it. On Android 13+ [wrap] returns the context unchanged — the system
 * applies the per-app language itself.
 */
object AppLocale {
    private const val PREFS = "reach_language"
    private const val KEY_TAG = "tag"

    /**
     * Call from `Activity.attachBaseContext`: the context with Reach's language applied.
     *
     * Lint can't see that :app disables language splits in its bundle config, so every
     * language is always installed (app/build.gradle.kts, `bundle.language.enableSplit`).
     */
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base).tag ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(configuration)
    }

    internal fun stored(context: Context): AppLanguage =
        AppLanguage.fromTags(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null))

    internal fun store(
        context: Context,
        language: AppLanguage,
    ) {
        // commit (synchronous), not apply: recreate() reads it back immediately.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY_TAG, language.tag) }
    }
}
