package com.woot.notification.extensions.local

import android.content.Intent
import android.util.Log
import com.getcapacitor.*
import com.getcapacitor.plugin.LocalNotifications
import com.getcapacitor.plugin.notification.LocalNotification
import com.getcapacitor.plugin.notification.NotificationAction
import com.getcapacitor.plugin.notification.NotificationChannelManager
import com.getcapacitor.plugin.notification.NotificationStorage
import java.util.*


@NativePlugin(requestCodes = [PluginRequestCodes.NOTIFICATION_OPEN])
class LocalNotificationExtension: LocalNotifications() {
    private lateinit var notificationStorage: NotificationStorage
    private lateinit var manager: LocalNotificationManager
    private lateinit var notificationChannelManager: NotificationChannelManager

    override fun load() {
        notificationStorage = NotificationStorage(context)
        manager = LocalNotificationManager(notificationStorage, activity, context, bridge.config)
        manager.createNotificationChannel()
        notificationChannelManager = NotificationChannelManager(activity)
        staticBridge = this.bridge
    }

    override fun handleOnNewIntent(data: Intent) {
        if (Intent.ACTION_MAIN != data.action) {
            return
        }
        val dataJson = manager.handleNotificationActionPerformed(data, notificationStorage)
        if (dataJson != null) {
            notifyListeners("localNotificationActionPerformed", dataJson, true)
        }
    }

    override fun handleOnActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.handleOnActivityResult(requestCode, resultCode, data)
        handleOnNewIntent(data!!)
    }

    /**
     * Schedule a notification call from JavaScript
     * Creates local notification in system.
     */
    @PluginMethod
    override fun schedule(call: PluginCall) {
        val localNotifications = LocalNotification.buildNotificationList(call) ?: return
        val ids = manager.schedule(call, localNotifications)
        if (ids != null) {
            notificationStorage.appendNotifications(localNotifications)
            val result = JSObject()
            val jsArray = JSArray()
            for (i in 0 until ids.length()) {
                try {
                    val notification = JSObject().put("id", ids.getString(i))
                    jsArray.put(notification)
                } catch (ex: Exception) {
                }
            }
            result.put("notifications", jsArray)
            call.success(result)
        }
    }

    @PluginMethod
    override fun cancel(call: PluginCall) {
        manager.cancel(call)
    }

    @PluginMethod
    override fun getPending(call: PluginCall) {
        val ids = notificationStorage.savedNotificationIds
        val result = LocalNotification.buildLocalNotificationPendingList(ids)
        call.success(result)
    }

    @PluginMethod
    override fun registerActionTypes(call: PluginCall) {
        val types = call.getArray("types")
        val typesArray = NotificationAction.buildTypes(types)
        notificationStorage.writeActionGroup(typesArray)
        call.success()
    }

    @PluginMethod
    override fun areEnabled(call: PluginCall) {
        val data = JSObject()
        data.put("value", manager.areNotificationsEnabled())
        call.success(data)
    }

    companion object {
        lateinit var staticBridge: Bridge

        fun fireReceived(notification: JSObject) {
            if (::staticBridge.isInitialized) {
                val handle = staticBridge.getPlugin("LocalNotificationExtension")
                val pluginInstance = handle.instance as LocalNotificationExtension
                pluginInstance.notifyListeners("localNotificationReceived", notification, true)
            } else {
                Log.e("LN: ", "Event listener named 'localNotificationReceived' can only fired after the plugin loaded.")
            }
        }
    }
}
