package com.loosewiredev.notifications.data

import com.thelightphone.sdk.apps.LightNotification

/**
 * The notification list after filtering, plus enough context for the screen to
 * choose between "nothing posted" and "not connected yet".
 */
data class NotificationListState(
    val accessGranted: Boolean = false,
    val connected: Boolean = false,
    val loading: Boolean = true,
    val rows: List<LightNotification> = emptyList(),
    val message: String? = null,
) {
    /**
     * True only once the listener has connected and reported at least one row,
     * so a cold start never claims the device has no notifications.
     */
    val hasRows: Boolean get() = rows.isNotEmpty()
}

/**
 * What the screen offers as an action on a row. Muting has no programmatic
 * equivalent — per-channel state only exists behind the system dialog — so it
 * is the same destination as notification settings, labelled for intent.
 */
enum class NotificationAction { Open, AppNotificationSettings, AppInfo, CopyText, MuteApp }

object NotificationList {
    /**
     * Rows sorted the way the list shows them: alphabetically by title, since
     * the SDK already dropped the noise (ongoing, media, keep-alive, redundant
     * group summaries, and this app's own posts).
     */
    fun ordered(items: List<LightNotification>): List<LightNotification> =
        items.sortedWith { left, right -> left.title.compareTo(right.title, ignoreCase = true) }

    /** The body line to show, or null when there is nothing to show. */
    fun body(notification: LightNotification): String? =
        notification.text?.trim()?.takeIf { it.isNotEmpty() }
}
