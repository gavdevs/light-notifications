package com.loosewiredev.notifications.data

import com.thelightphone.sdk.apps.LightNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationListTest {

    private fun notification(
        title: String,
        text: String? = null,
        appLabel: String = "App",
        key: String = title,
    ) = LightNotification(
        key = key,
        packageName = "com.example.${appLabel.lowercase()}",
        appLabel = appLabel,
        title = title,
        text = text,
        isOngoing = false,
        hasContentIntent = true,
    )

    @Test
    fun `rows are sorted by title ignoring case`() {
        val rows = NotificationList.ordered(
            listOf(notification("zebra"), notification("Apple"), notification("mango")),
        )

        assertEquals(listOf("Apple", "mango", "zebra"), rows.map { it.title })
    }

    @Test
    fun `sorting is stable for notifications sharing a title`() {
        val first = notification("Same", key = "a")
        val second = notification("Same", key = "b")

        val rows = NotificationList.ordered(listOf(first, second))

        assertEquals(listOf("a", "b"), rows.map { it.key })
    }

    @Test
    fun `an empty body is hidden`() {
        assertNull(NotificationList.body(notification("Title")))
        assertNull(NotificationList.body(notification("Title", text = "")))
        assertNull(NotificationList.body(notification("Title", text = "   ")))
    }

    @Test
    fun `a body is trimmed`() {
        assertEquals("hello", NotificationList.body(notification("Title", text = "  hello \n")))
    }

    @Test
    fun `a cold start without a connected listener is not reported as empty`() {
        val state = NotificationListState(accessGranted = true, connected = false, loading = false)

        assertTrue(!state.hasRows)
        assertTrue(!state.connected)
    }

    @Test
    fun `ordering an empty list is empty`() {
        assertEquals(emptyList(), NotificationList.ordered(emptyList()))
    }
}
