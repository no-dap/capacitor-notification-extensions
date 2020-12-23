package com.woot.notification.extensions

import com.getcapacitor.*

@NativePlugin
class NotificationExtension : Plugin() {
    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")
        val ret = JSObject()
        ret.put("value", value)
        call.success(ret)
    }
}