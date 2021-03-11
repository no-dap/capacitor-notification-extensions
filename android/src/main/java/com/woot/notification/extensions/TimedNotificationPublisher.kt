package com.woot.notification.extensions

import android.content.Context
import android.content.Intent
import com.getcapacitor.plugin.notification.LocalNotificationManager
import com.getcapacitor.plugin.notification.NotificationStorage
import com.getcapacitor.plugin.notification.TimedNotificationPublisher

class TimedNotificationPublisher: TimedNotificationPublisher() {
    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        val id = intent!!.getIntExtra(LocalNotificationManager.NOTIFICATION_INTENT_KEY, Int.MIN_VALUE)
        val storage = NotificationStorage(context)
        val notificationJson = storage.getSavedNotificationAsJSObject(id.toString())
        LocalNotificationExtension().fireReceived(notificationJson)
    }
}
