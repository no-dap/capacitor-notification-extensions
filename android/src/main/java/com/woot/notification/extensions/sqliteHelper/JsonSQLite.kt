package com.woot.notification.extensions.sqliteHelper

import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONException

class JsonSQLite {
    // Setter
    // Getter
    var database = ""
    var encrypted: Boolean? = null
    var mode = ""
    var tables: ArrayList<JsonTable> = ArrayList()

    val keys: ArrayList<String>
        get() {
            val retArray = ArrayList<String>()
            if (database.isNotEmpty()) retArray.add("database")
            if (encrypted != null) retArray.add("encrypted")
            if (mode.isNotEmpty()) retArray.add("mode")
            if (tables.size > 0) retArray.add("tables")
            return retArray
        }

    fun isJsonSQLite(jsObj: JSObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyFirstLevel.contains(key)) return false
            try {
                val value = jsObj[key]
                if (key == "database") {
                    database = if (value !is String) {
                        return false
                    } else {
                        value
                    }
                }
                if (key == "encrypted") {
                    encrypted = if (value !is Boolean) {
                        return false
                    } else {
                        jsObj.getBool(key)
                    }
                }
                if (key == "mode") {
                    mode = if (value !is String) {
                        return false
                    } else {
                        jsObj.getString(key)
                    }
                }
                if (key == "tables") {
                    if (value !is JSONArray) {
                        Log.d(TAG, "value: not instance of JSONArray 1")
                        return false
                    } else {
                        val arrJS = jsObj.getJSONArray(key)
                        tables = ArrayList()
                        for (i in 0 until arrJS.length()) {
                            val table = JsonTable()
                            val retTable: Boolean = table.isTable(arrJS.getJSONObject(i))
                            if (!retTable) return false
                            tables.add(table)
                        }
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
        Log.d(TAG, "database: $database")
        Log.d(TAG, "encrypted: $encrypted")
        Log.d(TAG, "mode: $mode")
        Log.d(TAG, "number of Tables: " + tables.size)
        for (table in tables) {
            table.print()
        }
    }

    val tablesAsJSObject: JSArray
        get() {
            val jsTables = JSArray()
            for (table in tables) {
                jsTables.put(table.tableAsJSObject)
            }
            return jsTables
        }

    companion object {
        private const val TAG = "JsonSQLite"
        private val keyFirstLevel: List<String> = ArrayList(listOf("database", "encrypted", "mode", "tables"))
    }
}