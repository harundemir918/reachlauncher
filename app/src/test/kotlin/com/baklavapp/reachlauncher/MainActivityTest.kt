package com.baklavapp.reachlauncher

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.home.HomeSurface
import com.baklavapp.reachlauncher.feature.home.HomeViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The Home-button wiring: `MainActivity` is `singleTask`, so pressing Home while Reach is
 * running delivers the HOME intent to `onNewIntent`, which must return to the Home surface.
 *
 * Runs the real Activity with the real Hilt graph under Robolectric: `ReachApplication`
 * (`@HiltAndroidApp`) is the manifest's application class, so Hilt builds its production
 * component — no `hilt-android-testing` needed for a wiring test like this one.
 *
 * Flutter analogy: an integration-style widget test that pumps the real `MyApp` and sends
 * it a platform message.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun `the Home intent returns to the Home surface`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        // The same instance MainActivity holds: `by viewModels()` uses this provider too.
        val viewModel = ViewModelProvider(controller.get())[HomeViewModel::class.java]
        viewModel.onIntentSelected(ReachIntent.TALK)
        assertThat(viewModel.surface.value).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertThat(viewModel.surface.value).isEqualTo(HomeSurface.Home)
    }
}
