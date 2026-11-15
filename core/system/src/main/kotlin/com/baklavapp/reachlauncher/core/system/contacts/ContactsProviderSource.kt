package com.baklavapp.reachlauncher.core.system.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.ContactsSource
import com.baklavapp.reachlauncher.core.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contacts for search, read from the Contacts Provider (`ContactsContract`).
 *
 * Android background for Flutter developers: contacts live in a system *content
 * provider* — a database owned by another process that apps query through
 * `ContentResolver.query(uri, columns, …)` and read row by row with a `Cursor`. It is a
 * blocking IPC call, so it runs on the IO dispatcher.
 *
 * Two queries per search:
 * 1. `Contacts.CONTENT_FILTER_URI` + the typed text: the provider's own name lookup. It
 *    matches the start of any name word and ignores case and accents, which is what
 *    "type to launch" needs. A `limit` query parameter caps the rows it returns.
 * 2. `CommonDataKinds.Phone` for just those contacts, to pick one number per contact: the
 *    "super primary" (default) number first, then the primary one, then the first.
 *
 * Without `READ_CONTACTS` nothing is queried and the result is empty. Any provider failure
 * (`SecurityException` if the permission was revoked mid-flight, a provider crash, a
 * missing provider on an unusual device) also yields an empty list: search must keep
 * working without contacts.
 *
 * Not covered yet: work-profile contacts (`ENTERPRISE_CONTENT_FILTER_URI`) — they need the
 * admin's cross-profile contacts policy and are left for the M5 work-profile pass.
 *
 * Flutter analogy: the native half of a `flutter_contacts`-style plugin call — a
 * `ContentResolver.query` is what such a plugin runs behind its `MethodChannel`.
 */
@Singleton
class ContactsProviderSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ContactsSource {
        override fun isPermissionGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<Contact> {
            val text = query.trim()
            if (text.isEmpty() || limit <= 0 || !isPermissionGranted()) return emptyList()
            return withContext(ioDispatcher) {
                try {
                    queryContacts(context.contentResolver, text, limit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    emptyList()
                } catch (e: RuntimeException) {
                    // A provider that crashed or is missing surfaces as a RuntimeException
                    // (e.g. IllegalArgumentException for an unknown URI). Contacts are optional.
                    emptyList()
                }
            }
        }

        private fun queryContacts(
            resolver: ContentResolver,
            text: String,
            limit: Int,
        ): List<Contact> {
            val uri =
                Uri
                    .withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(text))
                    .buildUpon()
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
                    .build()
            val rows =
                resolver
                    .query(uri, CONTACT_COLUMNS, null, null, null)
                    ?.use { readContactRows(it, limit) }
                    .orEmpty()
            if (rows.isEmpty()) return emptyList()
            val withPhone = rows.filter { it.hasPhone }.map { it.id }
            val phones = if (withPhone.isEmpty()) emptyMap() else queryPhones(resolver, withPhone)
            return rows.map { Contact(it.id, it.lookupKey, it.displayName, phones[it.id]) }
        }

        private fun queryPhones(
            resolver: ContentResolver,
            contactIds: List<Long>,
        ): Map<Long, String> {
            val placeholders = contactIds.joinToString(",") { "?" }
            return resolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    PHONE_COLUMNS,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($placeholders)",
                    contactIds.map(Long::toString).toTypedArray(),
                    PHONE_ORDER,
                )?.use(::readPrimaryPhones)
                .orEmpty()
        }

        private companion object {
            val CONTACT_COLUMNS =
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                )

            val PHONE_COLUMNS =
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )

            /** Default ("super primary") number first, then the per-account primary one. */
            val PHONE_ORDER =
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
                    "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC, " +
                    ContactsContract.CommonDataKinds.Phone._ID
        }
    }

/** One row of the contacts filter query. */
internal data class ContactRow(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val hasPhone: Boolean,
)

/**
 * Reads up to [limit] contact rows from a cursor over the filter query's columns. Rows
 * without a name or lookup key (rare, half-synced accounts) are skipped: they couldn't be
 * shown or opened. Columns are looked up by name, so the projection order doesn't matter.
 */
internal fun readContactRows(
    cursor: Cursor,
    limit: Int,
): List<ContactRow> {
    val id = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
    val lookup = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
    val name = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
    val hasPhone = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
    val rows = mutableListOf<ContactRow>()
    while (rows.size < limit && cursor.moveToNext()) {
        val displayName = cursor.getString(name)?.takeIf { it.isNotBlank() } ?: continue
        val lookupKey = cursor.getString(lookup) ?: continue
        rows += ContactRow(cursor.getLong(id), lookupKey, displayName, cursor.getInt(hasPhone) != 0)
    }
    return rows
}

/**
 * The first number per contact from a cursor already sorted best-first (see the phone
 * query's sort order); blank numbers are skipped.
 */
internal fun readPrimaryPhones(cursor: Cursor): Map<Long, String> {
    val contactId = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
    val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
    val result = linkedMapOf<Long, String>()
    while (cursor.moveToNext()) {
        val phone = cursor.getString(number)?.takeIf { it.isNotBlank() } ?: continue
        result.putIfAbsent(cursor.getLong(contactId), phone)
    }
    return result
}
