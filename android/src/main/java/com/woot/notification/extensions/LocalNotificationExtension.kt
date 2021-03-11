package com.woot.notification.extensions

import com.getcapacitor.JSObject
import com.getcapacitor.NativePlugin
import com.getcapacitor.plugin.LocalNotifications


@NativePlugin
class LocalNotificationExtension: LocalNotifications() {
    fun fireReceived(notification: JSObject) {
        notifyListeners("localNotificationReceived", notification, true)
    }
}
