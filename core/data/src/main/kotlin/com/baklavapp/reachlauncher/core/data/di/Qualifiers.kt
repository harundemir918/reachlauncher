package com.baklavapp.reachlauncher.core.data.di

import javax.inject.Qualifier

/*
 * Hilt qualifiers: labels that tell apart several bindings of the same type
 * (e.g. two `CoroutineDispatcher`s).
 *
 * Flutter analogy: injectable's `@Named('io')` on a registration, used again on the
 * constructor parameter that needs it.
 */

/**
 * A `CoroutineScope` that lives as long as the process. Used for work that must outlive
 * any screen — e.g. keeping the app cache in sync with the system — where a
 * `viewModelScope` would be cancelled when the ViewModel is cleared.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** `Dispatchers.IO`: blocking I/O (system services, disk). Injected so tests can swap it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** `Dispatchers.Default`: CPU work (sorting, scoring). Injected so tests can swap it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
