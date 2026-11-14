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
}
