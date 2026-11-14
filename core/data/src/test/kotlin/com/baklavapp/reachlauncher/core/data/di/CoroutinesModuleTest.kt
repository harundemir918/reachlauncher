package com.baklavapp.reachlauncher.core.data.di

import android.database.sqlite.SQLiteFullException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/** The application scope's backstop: a missed failure is logged, and the scope keeps working. */
@RunWith(RobolectricTestRunner::class)
class CoroutinesModuleTest {
    @Test
    fun `an uncaught failure in the application scope is logged, not fatal`() {
        val scope = CoroutinesModule.provideApplicationScope(Dispatchers.Unconfined)

        scope.launch { throw SQLiteFullException("database or disk is full") }

        assertThat(scope.isActive).isTrue()
        assertThat(scope.launch { }.isCompleted).isTrue() // Siblings still run (SupervisorJob).
        val logged = ShadowLog.getLogsForTag("ReachAppScope").single()
        assertThat(logged.throwable).isInstanceOf(SQLiteFullException::class.java)
    }
}
