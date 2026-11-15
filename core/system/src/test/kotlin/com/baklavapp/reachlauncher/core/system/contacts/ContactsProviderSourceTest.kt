package com.baklavapp.reachlauncher.core.system.contacts

import android.Manifest
import android.app.Application
import android.database.MatrixCursor
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [ContactsProviderSource]: denied permission means empty (never a crash), and the cursor
 * mapping (row skipping, limit, best phone number) on hand-made cursors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContactsProviderSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `denied permission reports not granted and returns nothing`() =
        runTest {
            shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
            val source = ContactsProviderSource(app, UnconfinedTestDispatcher(testScheduler))
            assertThat(source.isPermissionGranted()).isFalse()
            assertThat(source.search("ay", limit = 5)).isEmpty()
        }

    @Test
    fun `granted permission with no provider still returns nothing instead of throwing`() =
        runTest {
            shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
            val source = ContactsProviderSource(app, UnconfinedTestDispatcher(testScheduler))
            assertThat(source.isPermissionGranted()).isTrue()
            assertThat(source.search("ay", limit = 5)).isEmpty()
            assertThat(source.search("  ", limit = 5)).isEmpty()
        }

    @Test
    fun `contact rows skip nameless rows and respect the limit`() {
        val cursor =
            MatrixCursor(
                arrayOf(
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
            ).apply {
                addRow(arrayOf<Any?>(1, 1L, "k1", "Ayşe"))
                addRow(arrayOf<Any?>(0, 2L, "k2", null))
                addRow(arrayOf<Any?>(0, 3L, "k3", "Aydın"))
                addRow(arrayOf<Any?>(1, 4L, "k4", "Ayla"))
            }
        val rows = readContactRows(cursor, limit = 2)
        assertThat(rows).containsExactly(ContactRow(1, "k1", "Ayşe", true), ContactRow(3, "k3", "Aydın", false)).inOrder()
    }

    @Test
    fun `the first non-blank number per contact wins`() {
        val cursor =
            MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER)).apply {
                addRow(arrayOf<Any?>(1L, " "))
                addRow(arrayOf<Any?>(1L, "+90 555 111"))
                addRow(arrayOf<Any?>(1L, "+90 555 222"))
                addRow(arrayOf<Any?>(4L, "+90 555 444"))
            }
        assertThat(readPrimaryPhones(cursor)).containsExactly(1L, "+90 555 111", 4L, "+90 555 444")
    }
}
