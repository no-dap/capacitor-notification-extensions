package com.woot.notification.extensions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.getcapacitor.JSObject
import com.getcapacitor.NativePlugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.plugin.PushNotifications
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.RemoteMessage

@NativePlugin(
        permissions = [Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE],
        requestCodes = [NotificationExtension.REQUEST_SQLITE_PERMISSION],
        permissionRequestCode = NotificationExtension.REQUEST_SQLITE_PERMISSION
)
class NotificationExtension : PushNotifications() {
    private lateinit var sqLiteHandler: SQLiteHandler
    private var isPermissionGranted = false
    override fun load() {
        super.load()
        sqLiteHandler = SQLiteHandler(context)
        isPermissionGranted = hasRequiredPermissions()
    }

    override fun handleRequestPermissionsResult(requestCode: Int, permissions: Array<String?>?, grantResults: IntArray) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SQLITE_PERMISSION) {
            var permissionsGranted = true
            for (grantResult in grantResults) {
                if (grantResult != 0) {
                    permissionsGranted = false
                }
            }
            val savedCall = savedCall
            if (permissionsGranted) {
                isPermissionGranted = true
                savedCall.resolve()
            } else {
                isPermissionGranted = false
                savedCall.reject("permission failed")
            }
            freeSavedCall()
        }
    }

    @PluginMethod
    fun getToken(call: PluginCall) {
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { task ->
            val ret = JSObject()
            ret.put("value", task.token)
            call.success(ret)
        }
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
        val channelId = "default"
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
        val notificationManager = staticBridge.activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                    channelId,
                    "default",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    companion object {
        const val REQUEST_SQLITE_PERMISSION = 9538
    }
}