@file:Suppress("DEPRECATION")

package com.woot.notification.extensions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

    fun handleNotification(remoteMessage: RemoteMessage) {
        val (notificationId, groupId) = getIds(remoteMessage)
        val pendingIntent = buildNotificationPendingIntent(remoteMessage)
        val notificationBuilder = buildNotification(
                groupId,
                NotificationCompat.BigTextStyle().bigText(remoteMessage.data["body"]),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                pendingIntent)
                .setContentTitle(remoteMessage.data["title"])
                .setContentText(remoteMessage.data["body"])
        createDefaultChannel()

        val groupBuilder = buildNotification(groupId, NotificationCompat.InboxStyle())
                .setGroupSummary(true)

        NotificationManagerCompat.from(staticBridge.context).apply {
            notify(notificationId, notificationBuilder.build())
            notify(groupId, groupBuilder.build())
        }
    }

    companion object {
        private fun buildNotification(
                groupId: Int, style: NotificationCompat.Style, sound: Uri? = null, intent: PendingIntent? = null
        ): NotificationCompat.Builder {
            val builder = NotificationCompat.Builder(staticBridge.context, "default")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setGroup(groupId.toString())
                    .setStyle(style)
            if (sound != null) {
                builder.setSound(sound)
            }
            if (intent != null) {
                builder.setContentIntent(intent)
            }
            return builder
        }

        private fun buildNotificationPendingIntent(remoteMessage: RemoteMessage): PendingIntent {
            val intent = Intent(staticBridge.context, staticBridge.activity::class.java)
            intent.putExtras(remoteMessage.toIntent())
            return PendingIntent.getActivity(staticBridge.context, 0, intent, PendingIntent.FLAG_ONE_SHOT)
        }

        private fun createDefaultChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = staticBridge.activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                        "default",
                        "default",
                        NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun getIds(remoteMessage: RemoteMessage): Pair<Int, Int> {
            val objectId = (remoteMessage.data["objectId"] ?: "0").toInt()
            val senderId = (remoteMessage.data["senderId"] ?: "0").toInt()
            val code = (remoteMessage.data["code"] ?: "0").toInt()
            return Pair(objectId + code + senderId, objectId + code)
        }
    }
}