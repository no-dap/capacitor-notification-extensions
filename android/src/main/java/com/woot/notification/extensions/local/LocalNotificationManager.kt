package com.woot.notification.extensions.local

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.getcapacitor.CapConfig
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PluginCall
import com.getcapacitor.android.R
import com.getcapacitor.plugin.notification.LocalNotification
import com.getcapacitor.plugin.notification.LocalNotificationManager
import com.getcapacitor.plugin.notification.NotificationDismissReceiver
import com.getcapacitor.plugin.notification.NotificationStorage
import com.getcapacitor.plugin.util.AssetUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LocalNotificationManager(
        private val storage: NotificationStorage, private val activity: Activity, private val context: Context, private val config: CapConfig
) : LocalNotificationManager(storage, activity, context, config) {
    /**
     * Method executed when notification is launched by user from the notification bar.
     */
    override fun handleNotificationActionPerformed(data: Intent, notificationStorage: NotificationStorage): JSObject? {
        Logger.debug(Logger.tags("LN"), "LocalNotification received: " + data.dataString)
        val notificationId = data.getIntExtra(NOTIFICATION_INTENT_KEY, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) {
            Logger.debug(Logger.tags("LN"), "Activity started without notification attached")
            return null
        }
        val isRemovable = data.getBooleanExtra(NOTIFICATION_IS_REMOVABLE_KEY, true)
        if (isRemovable) {
            notificationStorage.deleteNotification(notificationId.toString())
        }
        val dataJson = JSObject()
        val results = RemoteInput.getResultsFromIntent(data)
        if (results != null) {
            val input = results.getCharSequence(REMOTE_INPUT_KEY)
            dataJson.put("inputValue", input.toString())
        }
        val menuAction = data.getStringExtra(ACTION_INTENT_KEY)
        dismissVisibleNotification(notificationId)
        dataJson.put("actionId", menuAction)
        var request: JSONObject? = null
        try {
            val notificationJsonString = data.getStringExtra(NOTIFICATION_OBJ_INTENT_KEY)
            if (notificationJsonString != null) {
                request = JSObject(notificationJsonString)
            }
        } catch (e: JSONException) {
        }
        dataJson.put("notification", request)
        return dataJson
    }

    override fun schedule(call: PluginCall?, localNotifications: List<LocalNotification>): JSONArray? {
        val ids = JSONArray()
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationsEnabled = notificationManager.areNotificationsEnabled()
        if (!notificationsEnabled) {
            call?.error("Notifications not enabled on this device")
            return null
        }
        for (localNotification in localNotifications) {
            val id = localNotification.id
            if (localNotification.id == null) {
                call?.error("LocalNotification missing identifier")
                return null
            }
            dismissVisibleNotification(id)
            cancelTimerForNotification(id)
            buildNotification(notificationManager, localNotification, call!!)
            ids.put(id)
        }
        return ids
    }

    private fun dismissVisibleNotification(notificationId: Int) {
        val notificationManager = NotificationManagerCompat.from(this.context)
        notificationManager.cancel(notificationId)
    }

    override fun areNotificationsEnabled(): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        return notificationManager.areNotificationsEnabled()
    }

    private fun cancelTimerForNotification(notificationId: Int) {
        val intent = Intent(context, TimedNotificationPublisher::class.java)
        val pi = PendingIntent.getBroadcast(
                context, notificationId, intent, 0)
        if (pi != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pi)
        }
    }

    private fun buildIntent(localNotification: LocalNotification, action: String): Intent {
        val intent = Intent(context, activity.javaClass)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(NOTIFICATION_INTENT_KEY, localNotification.id)
        intent.putExtra(ACTION_INTENT_KEY, action)
        intent.putExtra(NOTIFICATION_OBJ_INTENT_KEY, localNotification.source)
        val schedule = localNotification.schedule
        intent.putExtra(NOTIFICATION_IS_REMOVABLE_KEY, schedule == null || schedule.isRemovable)
        return intent
    }

    private fun createActionIntents(localNotification: LocalNotification, mBuilder: NotificationCompat.Builder) {
        // Open intent

        // Open intent
        val intent = buildIntent(localNotification, DEFAULT_PRESS_ACTION)

        val pendingIntent = PendingIntent.getActivity(context, localNotification.id, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        mBuilder.setContentIntent(pendingIntent)

        // Build action types

        // Build action types
        val actionTypeId = localNotification.actionTypeId
        if (actionTypeId != null) {
            val actionGroup = storage.getActionGroup(actionTypeId)
            for (i in actionGroup.indices) {
                val notificationAction = actionGroup[i]
                // TODO Add custom icons to actions
                val actionIntent = buildIntent(localNotification, notificationAction.id)
                val actionPendingIntent = PendingIntent.getActivity(context, localNotification.id + notificationAction.id.hashCode(), actionIntent, PendingIntent.FLAG_CANCEL_CURRENT)
                val actionBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(R.drawable.ic_transparent, notificationAction.title, actionPendingIntent)
                if (notificationAction.isInput) {
                    val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
                            .setLabel(notificationAction.title)
                            .build()
                    actionBuilder.addRemoteInput(remoteInput)
                }
                mBuilder.addAction(actionBuilder.build())
            }
        }

        // Dismiss intent

        // Dismiss intent
        val dissmissIntent = Intent(context, NotificationDismissReceiver::class.java)
        dissmissIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        dissmissIntent.putExtra(NOTIFICATION_INTENT_KEY, localNotification.id)
        dissmissIntent.putExtra(ACTION_INTENT_KEY, "dismiss")
        val schedule = localNotification.schedule
        dissmissIntent.putExtra(NOTIFICATION_IS_REMOVABLE_KEY, schedule == null || schedule.isRemovable)
        val deleteIntent = PendingIntent.getBroadcast(
                context, localNotification.id, dissmissIntent, 0)
        mBuilder.setDeleteIntent(deleteIntent)
    }

    private fun getDefaultSmallIcon(context: Context): Int {
        if (defaultSmallIconID != AssetUtil.RESOURCE_ID_ZERO_VALUE) return defaultSmallIconID

        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        var smallIconConfigResourceName = config.getString(CONFIG_KEY_PREFIX + "smallIcon")
        smallIconConfigResourceName = AssetUtil.getResourceBaseName(smallIconConfigResourceName)

        if (smallIconConfigResourceName != null) {
            resId = AssetUtil.getResourceID(context, smallIconConfigResourceName, "drawable")
        }

        if (resId == AssetUtil.RESOURCE_ID_ZERO_VALUE) {
            resId = android.R.drawable.ic_dialog_info
        }

        defaultSmallIconID = resId
        return resId
    }

    private fun getDefaultSound(context: Context): Int {
        if (defaultSoundID != AssetUtil.RESOURCE_ID_ZERO_VALUE) return defaultSoundID

        var resId = AssetUtil.RESOURCE_ID_ZERO_VALUE
        var soundConfigResourceName = config.getString(CONFIG_KEY_PREFIX + "sound")
        soundConfigResourceName = AssetUtil.getResourceBaseName(soundConfigResourceName)

        if (soundConfigResourceName != null) {
            resId = AssetUtil.getResourceID(context, soundConfigResourceName, "raw")
        }

        defaultSoundID = resId
        return resId
    }

    private fun buildNotification(notificationManager: NotificationManagerCompat, localNotification: LocalNotification, call: PluginCall) {
        var channelId: String? = DEFAULT_NOTIFICATION_CHANNEL_ID
        if (localNotification.channelId != null) {
            channelId = localNotification.channelId
        }
        val mBuilder = NotificationCompat.Builder(this.context, channelId!!)
                .setContentTitle(localNotification.title)
                .setContentText(localNotification.body)
                .setAutoCancel(localNotification.isAutoCancel)
                .setOngoing(localNotification.isOngoing)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroupSummary(localNotification.isGroupSummary)


        // support multiline text


        // support multiline text
        mBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(localNotification.body))

        val sound = localNotification.getSound(context, getDefaultSound(context))
        if (sound != null) {
            val soundUri = Uri.parse(sound)
            // Grant permission to use sound
            context.grantUriPermission(
                    "com.android.systemui", soundUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mBuilder.setSound(soundUri)
            mBuilder.setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
        } else {
            mBuilder.setDefaults(Notification.DEFAULT_ALL)
        }


        val group = localNotification.group
        if (group != null) {
            mBuilder.setGroup(group)
        }

        // make sure scheduled time is shown instead of display time

        // make sure scheduled time is shown instead of display time
        if (localNotification.isScheduled && localNotification.schedule.at != null) {
            mBuilder.setWhen(localNotification.schedule.at.time)
                    .setShowWhen(true)
        }

        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        mBuilder.setOnlyAlertOnce(true)

        mBuilder.setSmallIcon(localNotification.getSmallIcon(context, getDefaultSmallIcon(context)))

        val iconColor = localNotification.getIconColor(config.getString(CONFIG_KEY_PREFIX + "iconColor"))
        if (iconColor != null) {
            try {
                mBuilder.color = Color.parseColor(iconColor)
            } catch (ex: IllegalArgumentException) {
                call.error("Invalid color provided. Must be a hex string (ex: #ff0000")
                return
            }
        }

        createActionIntents(localNotification, mBuilder)
        // notificationId is a unique int for each localNotification that you must define
        // notificationId is a unique int for each localNotification that you must define
        val buildNotification = mBuilder.build()
        if (localNotification.isScheduled) {
            triggerScheduledNotification(buildNotification, localNotification)
        } else {
            notificationManager.notify(localNotification.id, buildNotification)
        }
    }

    private fun triggerScheduledNotification(notification: Notification, request: LocalNotification) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val schedule = request.schedule
        val notificationIntent = Intent(context, TimedNotificationPublisher::class.java)
        notificationIntent.putExtra(NOTIFICATION_INTENT_KEY, request.id)
        notificationIntent.putExtra(TimedNotificationPublisher.NOTIFICATION_KEY, notification)
        var pendingIntent = PendingIntent.getBroadcast(context, request.id, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        // Schedule at specific time (with repeating support)

        // Schedule at specific time (with repeating support)
        val at = schedule.at
        if (at != null) {
            if (at.time < Date().time) {
                Logger.error(Logger.tags("LN"), "Scheduled time must be *after* current time", null)
                return
            }
            if (schedule.isRepeating) {
                val interval = at.time - Date().time
                alarmManager.setRepeating(AlarmManager.RTC, at.time, interval, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC, at.time, pendingIntent)
            }
            return
        }

        // Schedule at specific intervals

        // Schedule at specific intervals
        val every = schedule.every
        if (every != null) {
            val everyInterval = schedule.everyInterval
            if (everyInterval != null) {
                val startTime = Date().time + everyInterval
                alarmManager.setRepeating(AlarmManager.RTC, startTime, everyInterval, pendingIntent)
            }
            return
        }

        // Cron like scheduler

        // Cron like scheduler
        val on = schedule.on
        if (on != null) {
            val trigger = on.nextTrigger(Date())
            notificationIntent.putExtra(TimedNotificationPublisher.CRON_KEY, on.toMatchString())
            pendingIntent = PendingIntent.getBroadcast(context, request.id, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            alarmManager.setExact(AlarmManager.RTC, trigger, pendingIntent)
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            Logger.debug(Logger.tags("LN"), "notification " + request.id + " will next fire at " + sdf.format(Date(trigger)))
        }
    }

    override fun cancel(call: PluginCall) {
        val notificationsToCancel = LocalNotification.getLocalNotificationPendingList(call)
        if (notificationsToCancel != null) {
            for (id in notificationsToCancel) {
                dismissVisibleNotification(id!!)
                cancelTimerForNotification(id)
                storage.deleteNotification(id.toString())
            }
        }
        call.success()
    }

    companion object {
        var defaultSoundID = AssetUtil.RESOURCE_ID_ZERO_VALUE
        var defaultSmallIconID = AssetUtil.RESOURCE_ID_ZERO_VALUE
        const val ACTION_INTENT_KEY = "LocalNotificationUserAction"
        const val CONFIG_KEY_PREFIX = "plugins.LocalNotifications."
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "default"
        const val DEFAULT_PRESS_ACTION = "tap"
        const val NOTIFICATION_INTENT_KEY = "LocalNotificationId"
        const val NOTIFICATION_IS_REMOVABLE_KEY = "LocalNotificationRepeating"
        const val REMOTE_INPUT_KEY = "LocalNotificationRemoteInput"
    }
}
