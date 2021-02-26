package com.woot.notification.extensions

import android.util.Log
import com.getcapacitor.JSObject
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.woot.notification.extensions.exceptions.TimeParseException
import java.util.*

class FirebaseMessagingService : FirebaseMessagingService() {
    private val sqLiteHandler = SQLiteHandler(this)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val remoteMessageData = remoteMessage.data
        Log.d(debugTag, remoteMessageData.toString())
        val opened = sqLiteHandler.openDB()
        if (opened) {
            if (checkMessageCondition(remoteMessageData) && sqLiteHandler.isLoggedIn()) {
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

    private fun getCurrentTime(): Calendar {
        return Calendar.getInstance()
    }

    @Throws(TimeParseException::class)
    private fun parseTime(input: String, checkNextDate: Boolean = false): Calendar {
        val ret = getTodayMidnight()
        val inputArray = input.split(':')
        if (inputArray.size != 2) {
            Log.e(debugTag, "Malformed datetime saved as time filter.")
            throw TimeParseException(TimeParseException.MalformedInputException)
        }
        ret.set(Calendar.HOUR_OF_DAY, inputArray[0].toInt())
        ret.set(Calendar.MINUTE, inputArray[1].toInt())
        if (checkNextDate && inputArray[0].toInt() < 13) {
            ret.add(Calendar.DATE, 1)
        }
        return ret
    }

    private fun isValidTime(): Boolean {
        sqLiteHandler.createFilterTable()
        val currentTime = getCurrentTime()
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
        isTimeFilterOnObject ?: return true
        val isTimeFilterOn = isTimeFilterOnObject.getString("value").toBoolean()
        return if (isTimeFilterOn && startTimeFilter != null && endTimeFilter != null) {
            try {
                val startFrom: Calendar = parseTime(startTimeFilter.getString("value"))
                val endAt: Calendar = parseTime(endTimeFilter.getString("value"))
                compareDate(startFrom, currentTime, endAt)
            } catch (error: TimeParseException) {
                true
            }
        } else {
            true
        }
    }

    private fun isValidCondition(remoteMessageData: Map<String, String>): Boolean {
        val filterString: String = remoteMessageData["filter"] ?: return true
        val filterList = processFilterString(filterString)
        sqLiteHandler.createFilterTable()
        val savedFilters = sqLiteHandler.getFilters().toList<JSObject>() as List<JSObject>
        val matchedFilters = savedFilters.filter { filter ->
            filter["key"] in filterList
        }
        for (matchedFilter in matchedFilters) {
            if ((matchedFilter.get("value") as String).toBoolean()) {
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
            return messageShown.toBoolean()
        }

        private fun getTodayMidnight(): Calendar {
            val now = Calendar.getInstance()
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            return now
        }

        private fun compareDate(startFrom: Calendar, currentTime: Calendar, endAt: Calendar): Boolean {
            if (startFrom.after(endAt)) {
                endAt.add(Calendar.DATE, 1)
                if (currentTime.get(Calendar.AM_PM) == Calendar.AM) {
                    currentTime.add(Calendar.DATE, 1)
                }
            }
            return !(startFrom.before(currentTime) && endAt.after(currentTime))
        }
    }
}