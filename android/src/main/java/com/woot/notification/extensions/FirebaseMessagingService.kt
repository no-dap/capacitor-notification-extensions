package com.woot.notification.extensions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.getcapacitor.JSObject
import com.getcapacitor.plugin.util.AssetUtil
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class FirebaseMessagingService : FirebaseMessagingService() {
    private val sqLiteHandler = SQLiteHandler(this)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val remoteMessageData = remoteMessage.data
        Log.d("remote message", remoteMessageData.toString())
        val opened = sqLiteHandler.openDB()
        if (opened) {
            if (isValidTime() && isValidCondition(remoteMessageData["filter"])) {
                handleNotification(remoteMessageData["title"]!!, remoteMessageData["body"]!!)
            } else {
                Log.d("NotificationExtension: ", "Push notification suppressed by filter")
            }
        }
    }

    private fun handleNotification(messageTitle: String, messageBody: String) {
        val channelId = "default"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("token", token)
    }

    private fun getCurrentTimeString(): String {
        val date = Calendar.getInstance().time
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    private fun compareTimeString(input: String, comparison: String): Boolean {
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return try {
            timeFormatter.parse(input) as Date >= timeFormatter.parse(comparison) as Date
        } catch (exception: ParseException) {
            Log.e("NotificationExtension: ", "Time string input is not parsable.", exception)
            true
        }
    }

    private fun isValidTime(): Boolean {
        sqLiteHandler.createFilterTable()
        val currentTime = getCurrentTimeString()
        val timeFilters = sqLiteHandler.getTimeFilter().toList<JSObject>()
        val startTimeFilter: JSObject? = timeFilters.find { timeFilter ->
            timeFilter["key"] == "filter_start_from"
        }
        val endTimeFilter: JSObject? = timeFilters.find { timeFilter ->
            timeFilter["key"] == "filter_end_at"
        }
        return if (startTimeFilter != null && endTimeFilter != null) {
            val startFrom: String = startTimeFilter.getString("value")
            val endAt: String = endTimeFilter.getString("value")
            compareTimeString(currentTime, startFrom) && compareTimeString(endAt, currentTime)
        } else {
            true
        }
    }

    private fun isValidCondition(filters: String?): Boolean {
        var filterString: String = filters ?: return true
        if (filterString.endsWith(',')) {
            filterString = filterString.substring(0, filterString.length - 1)
        }
        val filterList = filterString.split(',').map { it.trim() }
        sqLiteHandler.createFilterTable()
        val savedFilters = sqLiteHandler.getFilters().toList<JSObject>()
        val matchedFilters = savedFilters.filter { filter ->
            filter["key"] in filterList
        }
        for (matchedFilter in matchedFilters) {
            if (matchedFilter["value"] == "false") {
                return false
            }
        }
        return true
    }
}