package com.baklavapp.reachlauncher.core.system.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A cold flow of the broadcasts matching [actions], registered while collected.
 *
 * `RECEIVER_NOT_EXPORTED`: we only listen to protected *system* broadcasts (profile
 * available/unavailable…), which the system delivers to non-exported receivers too.
 * Not exporting the receiver means no other app can fake one. (Android 14+ requires an
 * explicit exported/not-exported flag; `ContextCompat` handles older versions.)
 *
 * Flutter analogy: a `StreamController` whose `onListen` registers a platform callback
 * and whose `onCancel` unregisters it — `callbackFlow` + `awaitClose` is exactly that.
 */
internal fun Context.broadcasts(actions: Collection<String>): Flow<Intent> =
    callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    trySend(intent)
                }
            }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(this@broadcasts, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { unregisterReceiver(receiver) }
    }
