package com.baklavapp.reachlauncher.core.data.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baklavapp.reachlauncher.core.data.repository.FavoritesRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.ReachIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favourites in the settings' Preferences DataStore: one string per intent
 * (`favorites_<intent storage key>`), one app per line as `package/activity#userSerial`.
 * Package and class names can contain neither `/`, `#` nor a line break, so the format needs
 * no escaping; a line that doesn't parse (written by a newer build) is skipped.
 *
 * Flutter analogy: a small repository over `shared_preferences` that stores a list as one
 * joined string — except DataStore streams every change back.
 */
@Singleton
class FavoritesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : FavoritesRepository {
        override val favorites: Flow<Map<ReachIntent, List<AppKey>>> =
            dataStore.data
                // As for settings: a read failure must not take Home down — no favourites.
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { prefs ->
                    ReachIntent.entries
                        .associateWith { intent -> decode(prefs[keyFor(intent)]) }
                        .filterValues { it.isNotEmpty() }
                }.distinctUntilChanged()

        override suspend fun update(
            intent: ReachIntent,
            transform: (List<AppKey>) -> List<AppKey>,
        ) {
            dataStore.edit { prefs ->
                val key = keyFor(intent)
                val next = transform(decode(prefs[key])).distinct().take(FavoritesRepository.MAX_PER_INTENT)
                if (next.isEmpty()) prefs.remove(key) else prefs[key] = encode(next)
            }
        }

        internal companion object {
            /** Never rename: it's what's on disk. */
            fun keyFor(intent: ReachIntent): Preferences.Key<String> = stringPreferencesKey("favorites_${intent.storageKey}")

            fun encode(keys: List<AppKey>): String = keys.joinToString("\n") { "${it.packageName}/${it.activityName}#${it.userSerial}" }

            fun decode(value: String?): List<AppKey> {
                if (value.isNullOrEmpty()) return emptyList()
                return value
                    .lineSequence()
                    .mapNotNull(::decodeOne)
                    .distinct()
                    .toList()
            }

            private fun decodeOne(line: String): AppKey? {
                val slash = line.indexOf('/')
                val hash = line.lastIndexOf('#')
                if (slash <= 0 || hash <= slash + 1) return null
                val serial = line.substring(hash + 1).toLongOrNull() ?: return null
                return AppKey(line.substring(0, slash), line.substring(slash + 1, hash), serial)
            }
        }
    }
