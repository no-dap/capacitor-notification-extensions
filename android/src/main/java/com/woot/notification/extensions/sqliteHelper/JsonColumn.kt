package com.woot.notification.extensions.sqliteHelper

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

class JsonColumn {
    // Setter
    // Getter
    var column: String? = null
    var value: String? = null
    var foreignkey: String? = null

    val keys: ArrayList<String>
        get() {
            val retArray = ArrayList<String>()
            if (column != null && column!!.isNotEmpty()) retArray.add("column")
            if (value != null && value!!.isNotEmpty()) retArray.add("value")
            if (foreignkey != null && foreignkey!!.isNotEmpty()) retArray.add("foreignkey")
            return retArray
        }

    fun isSchema(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keySchemaLevel.contains(key)) return false
            try {
                val `val` = jsObj[key]
                if (key == "column") {
                    column = if (`val` !is String) {
                        return false
                    } else {
                        `val`
                    }
                }
                if (key == "value") {
                    value = if (`val` !is String) {
                        return false
                    } else {
                        `val`
                    }
                }
                if (key == "foreignkey") {
                    foreignkey = if (`val` !is String) {
                        return false
                    } else {
                        `val`
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    fun print() {
        var row = ""
        if (column != null) row = "column: $column"
        if (foreignkey != null) row = "foreignkey: $foreignkey"
        Log.d(TAG, "$row value: $value")
    }

    val columnAsJSObject: JSObject
        get() {
            val retObj = JSObject()
            if (column != null) retObj.put("column", column)
            retObj.put("value", value)
            if (foreignkey != null) retObj.put("foreignkey", foreignkey)
            return retObj
        }

    companion object {
        private const val TAG = "JsonColumn"
        private val keySchemaLevel: List<String> = ArrayList(listOf("column", "value", "foreignkey"))
    }
}