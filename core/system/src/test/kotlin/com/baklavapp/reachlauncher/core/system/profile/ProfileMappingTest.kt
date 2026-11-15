package com.baklavapp.reachlauncher.core.system.profile

import android.content.Intent
import android.content.pm.ApplicationInfo
import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.system.apps.appCategoryOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ProfileMappingTest {
    @Test
    fun `own user is personal whatever the lookup says`() {
        PrivateLookup.entries.forEach { assertThat(classifyProfile(isOwnUser = true, privateLookup = it)).isEqualTo(ProfileType.PERSONAL) }
    }

    @Test
    fun `other profiles are work unless private`() {
        assertThat(classifyProfile(false, PrivateLookup.NOT_PRIVATE)).isEqualTo(ProfileType.WORK)
        assertThat(classifyProfile(false, PrivateLookup.PRIVATE)).isEqualTo(ProfileType.PRIVATE)
    }

    @Test
    fun `a refused lookup is treated as private (privacy-safe)`() {
        assertThat(classifyProfile(false, PrivateLookup.DENIED)).isEqualTo(ProfileType.PRIVATE)
    }

    @Test
    fun `launcher user info maps to a lookup`() {
        assertThat(privateLookupOf("android.os.usertype.profile.PRIVATE")).isEqualTo(PrivateLookup.PRIVATE)
        assertThat(privateLookupOf("android.os.usertype.profile.MANAGED")).isEqualTo(PrivateLookup.NOT_PRIVATE)
        // No info for a listed profile: a work profile in transition, not a private space (D67).
        assertThat(privateLookupOf(null)).isEqualTo(PrivateLookup.NOT_PRIVATE)
    }

    @Test
    fun `the private user type constant matches the SDK`() {
        assertThat(USER_TYPE_PROFILE_PRIVATE).isEqualTo(android.os.UserManager.USER_TYPE_PROFILE_PRIVATE)
    }

    @Test
    fun `application categories map to the mirror`() {
        assertThat(appCategoryOf(ApplicationInfo.CATEGORY_SOCIAL)).isEqualTo(AppCategory.SOCIAL)
        assertThat(appCategoryOf(ApplicationInfo.CATEGORY_MAPS)).isEqualTo(AppCategory.MAPS)
        assertThat(appCategoryOf(ApplicationInfo.CATEGORY_ACCESSIBILITY)).isEqualTo(AppCategory.ACCESSIBILITY)
        assertThat(appCategoryOf(ApplicationInfo.CATEGORY_UNDEFINED)).isEqualTo(AppCategory.UNDEFINED)
        assertThat(appCategoryOf(1234)).isEqualTo(AppCategory.UNDEFINED)
    }

    @Test
    fun `profile broadcasts on Android 15 include private-space events`() {
        assertThat(Profiles.profileChangeActions()).containsAtLeast(Intent.ACTION_PROFILE_AVAILABLE, Intent.ACTION_PROFILE_UNAVAILABLE)
    }

    @Test
    @Config(sdk = [28])
    fun `profile broadcasts on Android 9 are the managed-profile ones only`() {
        assertThat(Profiles.profileChangeActions()).containsExactly(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
        )
    }
}
