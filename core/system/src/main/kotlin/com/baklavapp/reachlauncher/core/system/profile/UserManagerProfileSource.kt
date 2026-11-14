package com.baklavapp.reachlauncher.core.system.profile

import android.content.Context
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.ProfileSource
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.system.util.broadcasts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profiles and their quiet-mode state, from `LauncherApps`/`UserManager`.
 *
 * Re-reads on every profile broadcast (see [Profiles.profileChangeActions]) instead of
 * trusting the broadcast's extras: the full re-read is cheap (a handful of profiles) and
 * can never drift out of sync.
 *
 * Flutter analogy: a plugin `EventChannel` stream that sends the full state on listen and
 * after every native callback.
 */
@Singleton
class UserManagerProfileSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val system: Profiles,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ProfileSource {
        override val profiles: Flow<List<ProfileState>> =
            context
                .broadcasts(Profiles.profileChangeActions())
                .map { }
                .onStart { emit(Unit) }
                .map { read() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        private fun read(): List<ProfileState> =
            system.visibleProfiles().map { user ->
                ProfileState(
                    userSerial = system.serialOf(user),
                    type = system.typeOf(user),
                    isQuietModeEnabled = system.isQuietMode(user),
                )
            }

        override fun setQuietMode(
            userSerial: Long,
            enabled: Boolean,
        ): Boolean {
            val user = system.userFor(userSerial) ?: return false
            return system.requestQuietMode(user, enabled)
        }
    }
