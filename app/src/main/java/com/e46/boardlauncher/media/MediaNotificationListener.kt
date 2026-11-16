package com.e46.boardlauncher.media

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        fun isPermissionGranted(context: Context): Boolean {
            val cn = ComponentName(context, MediaNotificationListener::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == cn
            }
        }
    }
}
