package com.baklavapp.reachlauncher.core.system.notifications

import com.baklavapp.reachlauncher.core.model.PackageUser

/**
 * One posted notification, reduced to what counting needs. Built from a
 * `StatusBarNotification` by the listener service; plain Kotlin so the counting rules are
 * JVM-testable.
 *
 * @property key `StatusBarNotification.key`, unique per notification across users.
 * @property isOngoing `FLAG_ONGOING_EVENT` (music, navigation, downloads), a foreground
 *   service's notification or a media player's (even paused) — "something running", not
 *   "something unread".
 * @property isGroupSummary `FLAG_GROUP_SUMMARY`: the bundle header of a group ("3 new
 *   messages") that sits on top of the real per-message notifications; counting it too
 *   would add one per bundle.
 */
data class NotificationEntry(
    val key: String,
    val app: PackageUser,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
)

/**
 * The counting rules for "N NEW" badges (spec §7.1, D12/D31), kept apart from the service:
 * active notifications that are neither ongoing nor a group summary nor Reach's own, counted
 * per package *and profile*.
 *
 * Holds the countable notifications by key, so an update that turns a notification ongoing
 * (or back) replaces its earlier state instead of double-counting. Not thread-safe on its
 * own; [NotificationListenerHub] guards it.
 *
 * Flutter analogy: a plain Dart class with a `Map<String, Entry>` that a plugin's event
 * handler feeds — no platform types, easy to unit test.
 */
class NotificationCounter(
    private val ownPackage: String,
) {
    private val countable = LinkedHashMap<String, PackageUser>()

    /** Whether [entry] contributes to a badge. */
    fun counts(entry: NotificationEntry): Boolean = !entry.isOngoing && !entry.isGroupSummary && entry.app.packageName != ownPackage

    /** Replaces everything with a full snapshot (listener connected / rebound). */
    fun replaceAll(entries: List<NotificationEntry>) {
        countable.clear()
        entries.forEach(::posted)
    }

    /** A notification was posted or updated. */
    fun posted(entry: NotificationEntry) {
        if (counts(entry)) countable[entry.key] = entry.app else countable.remove(entry.key)
    }

    /** A notification was removed (dismissed, opened, cancelled by its app). */
    fun removed(key: String) {
        countable.remove(key)
    }

    /** Forgets everything (listener disconnected or access revoked). */
    fun clear() = countable.clear()

    /** Current counts; packages-in-profile without countable notifications are absent. */
    fun snapshot(): Map<PackageUser, Int> = countable.values.groupingBy { it }.eachCount()
}
