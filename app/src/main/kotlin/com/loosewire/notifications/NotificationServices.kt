package com.loosewire.notifications

import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.apps.LightNotifications

/**
 * Process-wide dependencies. The SDK hands out a `LightNotifications` scoped to
 * the tool, which already checks the `app-access` capability.
 */
class NotificationServices private constructor(val notifications: LightNotifications) {
    companion object {
        @Volatile
        private var instance: NotificationServices? = null

        fun from(context: SealedLightContext): NotificationServices =
            instance ?: synchronized(this) {
                instance ?: NotificationServices(context.appAccess.notifications)
                    .also { instance = it }
            }
    }
}
