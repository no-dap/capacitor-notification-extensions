package com.woot.notification.extensions

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {
    private val sqLiteHandler = SQLiteHandler(this)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val remoteMessageData = remoteMessage.data
        Log.d("remote message", remoteMessageData.toString())
        val opened = sqLiteHandler.openDB()
        if (opened) {
            sqLiteHandler.createFilterTable()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("token", token)
    }
}