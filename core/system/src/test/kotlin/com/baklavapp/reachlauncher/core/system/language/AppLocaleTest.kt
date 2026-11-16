package com.baklavapp.reachlauncher.core.system.language

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android 9–12 path: the stored choice and the context wrapper that applies it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppLocaleTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a stored language is applied to the wrapped context`() {
        AppLocale.store(app, AppLanguage.SPANISH)
        assertThat(AppLocale.stored(app)).isEqualTo(AppLanguage.SPANISH)
        val wrapped = AppLocale.wrap(app)
        assertThat(
            wrapped.resources.configuration.locales[0]
                .language,
        ).isEqualTo("es")
    }

    @Test
    fun `following the system leaves the context untouched`() {
        AppLocale.store(app, AppLanguage.SYSTEM)
        assertThat(AppLocale.stored(app)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLocale.wrap(app)).isSameInstanceAs(app)
    }
}
