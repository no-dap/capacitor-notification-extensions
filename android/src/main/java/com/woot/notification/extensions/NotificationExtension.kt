package com.woot.notification.extensions

import com.getcapacitor.JSObject
import com.getcapacitor.NativePlugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.plugin.PushNotifications
import com.google.firebase.iid.FirebaseInstanceId
import com.woot.notification.extensions.sqliteHelper.SQLiteDatabaseHelper

@NativePlugin
class NotificationExtension (
        var localDatabaseHelper: SQLiteDatabaseHelper
        ) : PushNotifications() {
    override fun load() {
        super.load()
    }
    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")
        val ret = JSObject()
        ret.put("value", value)
        call.success(ret)
    }

    @PluginMethod
    fun getToken(call: PluginCall) {
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener {
            task ->
            val ret = JSObject()
            ret.put("value", task.token)
            call.success(ret)
        }
    }

    @PluginMethod
    fun addTimeFilter(call: PluginCall) {

    }

}