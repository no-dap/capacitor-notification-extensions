package com.woot.notification.extensions.sqliteHelper

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

class JsonIndex {
    // Setter
    // Getter
    var name: String? = null
    var column: String? = null

    val keys: ArrayList<String>
        get() {
            val retArray = ArrayList<String>()
            if (name!!.isNotEmpty()) retArray.add("name")
            if (column!!.isNotEmpty()) retArray.add("column")
            return retArray
        }

    fun isIndexes(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyIndexesLevel.contains(key)) return false
            try {
                val value = jsObj[key]
                if (key == "name") {
                    name = if (value !is String) {
                        return false
                    } else {
                        value
                    }
                }
                if (key == "column") {
                    column = if (value !is String) {
                        return false
                    } else {
                        value
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
        Log.d(TAG, "name: $name column: $column")
    }

    val indexAsJSObject: JSObject
        get() {
            val retObj = JSObject()
            retObj.put("name", name)
            retObj.put("column", column)
            return retObj
        }

    companion object {
        private const val TAG = "JsonIndex"
        private val keyIndexesLevel: List<String> = ArrayList(listOf("name", "column"))
    }
}