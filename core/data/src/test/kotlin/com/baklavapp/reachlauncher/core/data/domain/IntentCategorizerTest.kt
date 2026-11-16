package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Spec §6.1's category → intent table, plus the dialer/SMS role override. */
class IntentCategorizerTest {
    private fun categorize(
        category: AppCategory,
        roles: Set<String> = emptySet(),
    ) = IntentCategorizer.categorize(category, "com.example.app", roles)

    @Test
    fun `every category maps to its spec intent`() {
        val expected =
            mapOf(
                AppCategory.SOCIAL to ReachIntent.TALK,
                AppCategory.AUDIO to ReachIntent.LISTEN,
                AppCategory.MAPS to ReachIntent.GO,
                AppCategory.IMAGE to ReachIntent.MAKE,
                AppCategory.PRODUCTIVITY to ReachIntent.MAKE,
                AppCategory.GAME to ReachIntent.PLAY,
                AppCategory.VIDEO to ReachIntent.PLAY,
                AppCategory.NEWS to ReachIntent.PLAY,
                AppCategory.UNDEFINED to ReachIntent.TOOLS,
                AppCategory.ACCESSIBILITY to ReachIntent.TOOLS,
            )
        // Guards against a new AppCategory constant slipping through untested.
        assertThat(expected.keys).containsExactlyElementsIn(AppCategory.entries)
        expected.forEach { (category, intent) -> assertThat(categorize(category)).isEqualTo(intent) }
    }

    @Test
    fun `default dialer or SMS app goes to Talk whatever its category`() {
        AppCategory.entries.forEach { category ->
            assertThat(categorize(category, roles = setOf("com.example.app"))).isEqualTo(ReachIntent.TALK)
        }
    }

    @Test
    fun `role holders only affect their own package`() {
        assertThat(categorize(AppCategory.GAME, roles = setOf("com.android.dialer"))).isEqualTo(ReachIntent.PLAY)
    }

    @Test
    fun `well-known apps override their declared category (D73)`() {
        // What real devices actually declare: Chrome, Clock and Gmail say PRODUCTIVITY.
        assertThat(IntentCategorizer.categorize(AppCategory.PRODUCTIVITY, "com.android.chrome", emptySet())).isEqualTo(ReachIntent.TOOLS)
        assertThat(
            IntentCategorizer.categorize(AppCategory.PRODUCTIVITY, "com.google.android.deskclock", emptySet()),
        ).isEqualTo(ReachIntent.TOOLS)
        assertThat(IntentCategorizer.categorize(AppCategory.PRODUCTIVITY, "com.google.android.gm", emptySet())).isEqualTo(ReachIntent.TALK)
        assertThat(IntentCategorizer.categorize(AppCategory.UNDEFINED, "com.sec.android.gallery3d", emptySet())).isEqualTo(ReachIntent.MAKE)
    }

    @Test
    fun `the default dialer or SMS role still beats the table`() {
        assertThat(
            IntentCategorizer.categorize(AppCategory.UNDEFINED, "com.android.chrome", setOf("com.android.chrome")),
        ).isEqualTo(ReachIntent.TALK)
    }
}
