package com.woot.notification.extensions

import android.util.Log
import com.getcapacitor.JSObject
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class FirebaseMessagingService : FirebaseMessagingService() {
    private val sqLiteHandler = SQLiteHandler(this)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val remoteMessageData = remoteMessage.data
        Log.d(debugTag, remoteMessageData.toString())
        val opened = sqLiteHandler.openDB()
        if (opened) {
            if (checkMessageCondition(remoteMessageData)) {
                NotificationExtension().handleNotification(remoteMessage)
            } else {
                Log.d(debugTag, "Push notification suppressed by filter")
            }
        }
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
            Log.e(debugTag, "Time string input is not parsable.", exception)
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
        val isTimeFilterOnObject: JSObject? = timeFilters.find { timeFilter ->
            timeFilter["key"] == "is_time_filter_on"
        }
        isTimeFilterOnObject?: return true
        val isTimeFilterOn = isTimeFilterOnObject.getString("value")
        return if (isTimeFilterOn == "true" && startTimeFilter != null && endTimeFilter != null) {
            val startFrom: String = startTimeFilter.getString("value")
            val endAt: String = endTimeFilter.getString("value")
            compareTimeString(currentTime, startFrom) && compareTimeString(endAt, currentTime)
        } else {
            true
        }
    }

    private fun isValidCondition(remoteMessageData: Map<String, String>): Boolean {
        val filterString: String = remoteMessageData["filter"] ?: return true
        val filterList = processFilterString(filterString)
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

    private fun checkMessageCondition(remoteMessageData: Map<String, String>): Boolean {
        return isValidTime() && isValidCondition(remoteMessageData) && shouldMessageShown(remoteMessageData)
    }

    companion object {
        const val debugTag = "NotificationExtension: "
        private fun processFilterString(filterString: String): List<String> {
            var mutableFilterString = filterString
            if (mutableFilterString.endsWith(',')) {
                mutableFilterString = mutableFilterString.substring(0, mutableFilterString.length - 1)
            }
            return mutableFilterString.split(',').map { it.trim() }
        }

        private fun shouldMessageShown(remoteMessageData: Map<String, String>): Boolean {
            val messageShown = remoteMessageData["isShown"] ?: return true
            return messageShown == "true"
        }
    }
}