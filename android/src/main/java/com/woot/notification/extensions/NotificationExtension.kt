package com.woot.notification.extensions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.getcapacitor.JSObject
import com.getcapacitor.NativePlugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.plugin.PushNotifications
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.RemoteMessage

@NativePlugin
class NotificationExtension : PushNotifications() {
    private lateinit var sqLiteHandler: SQLiteHandler
    override fun load() {
        super.load()
        sqLiteHandler = SQLiteHandler(context)
        sqLiteHandler.openDB()
        sqLiteHandler.createFilterTable()
    }

    override fun fireNotification(remoteMessage: RemoteMessage) {
        val remoteMessageData = JSObject()
        for (key in remoteMessage.data.keys) {
            val value: Any? = remoteMessage.data[key]
            remoteMessageData.put(key, value)
        }
        notifyListeners("pushNotificationReceived", remoteMessageData, true)
    }

    @PluginMethod
    fun getToken(call: PluginCall) {
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener {
            val ret = JSObject()
            ret.put("value", it.token)
            call.success(ret)
        }
    }

    @PluginMethod
    fun getFilters(call: PluginCall) {
        sqLiteHandler.openDB()
        val result = JSObject()
        result.put("value", sqLiteHandler.getAllFilters())
        call.success(result)
    }

    @PluginMethod
    fun addTimeFilter(call: PluginCall) {
        sqLiteHandler.openDB()
        val startFrom = call.getString("startFrom")
        val endAt = call.getString("endAt")
        val result = sqLiteHandler.insertTimeFilter(startFrom, endAt)
        if (result.getValue("success") as Boolean) {
            call.success()
        } else {
            call.reject(result.getValue("reason") as String)
        }
    }

    @PluginMethod
    fun removeTimeFilter(call: PluginCall) {
        sqLiteHandler.openDB()
        val result = sqLiteHandler.removeTimeFilter()
        if (result.getValue("success") as Boolean) {
            call.success()
        } else {
            call.reject("Got an unexpected error while remove the time filter.")
        }
    }

    @PluginMethod
    fun addFilters(call: PluginCall) {
        sqLiteHandler.openDB()
        val filters = call.getArray("filters").toList<String>()
        for (filter in filters) {
            val result = sqLiteHandler.insertFilter(filter)
            if (!(result.getValue("success") as Boolean)) {
                call.reject(result.getValue("reason") as String)
            }
        }
        call.success()
    }

    @PluginMethod
    fun removeFilters(call: PluginCall) {
        sqLiteHandler.openDB()
        val filters = call.getArray("filters").toList<String>()
        for (filter in filters) {
            val result = sqLiteHandler.removeFilter(filter)
            if (!(result.getValue("success") as Boolean)) {
                call.reject(result.getValue("reason") as String)
            }
        }
        call.success()
    }

    companion object {
        private fun getPushNotificationsInstance(): NotificationExtension? {
            if (staticBridge != null && staticBridge.webView != null) {
                val handle = staticBridge.getPlugin("NotificationExtension") ?: return null
                return handle.instance as NotificationExtension
            }
            return null
        }

        private fun isApplicationActive(): Boolean {
            return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }

        private fun sendRemoteMessage(remoteMessage: RemoteMessage) {
            val pushPlugin = getPushNotificationsInstance()
            if (pushPlugin != null) {
                pushPlugin.fireNotification(remoteMessage)
            } else {
                lastMessage = remoteMessage
            }
        }

        fun handleNotification(remoteMessage: RemoteMessage) {
            sendRemoteMessage(remoteMessage)
            if (!isApplicationActive()) {
                val channelId = "default"
                val notificationId = (remoteMessage.data["objectId"]
                        ?: "0").toInt() + remoteMessage.data["code"]!!.toInt() + (remoteMessage.data["senderId"]
                        ?: "0").toInt()
                val groupId = remoteMessage.data["objectId"]!!.toInt() + remoteMessage.data["code"]!!.toInt()

                val intent = Intent(staticBridge.context, staticBridge.activity::class.java)
                intent.putExtras(remoteMessage.toIntent())
                val pendingIntent = PendingIntent.getActivity(staticBridge.context, 0, intent, PendingIntent.FLAG_ONE_SHOT)

                val notificationBuilder = NotificationCompat.Builder(staticBridge.context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(remoteMessage.data["title"])
                        .setContentText(remoteMessage.data["body"])
                        .setAutoCancel(true)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setContentIntent(pendingIntent)
                        .setGroup(groupId.toString())
                        .setStyle(NotificationCompat.BigTextStyle()
                                .bigText(remoteMessage.data["body"]))
                val notificationManager = staticBridge.activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                            channelId,
                            "default",
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                }
                val groupBuilder = NotificationCompat.Builder(staticBridge.context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setGroup(groupId.toString())
                        .setGroupSummary(true)
                        .setStyle(NotificationCompat.InboxStyle())

                NotificationManagerCompat.from(staticBridge.context).apply {
                    notify(notificationId, notificationBuilder.build())
                    notify(groupId, groupBuilder.build())
                }
            }
        }
    }
}