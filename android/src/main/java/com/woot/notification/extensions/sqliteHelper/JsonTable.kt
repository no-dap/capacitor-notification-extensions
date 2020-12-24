package com.woot.notification.extensions.sqliteHelper

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList

class JsonTable {
    // Setter
    // Getter
    var name = ""
    var schema: ArrayList<JsonColumn> = ArrayList()
    var indexes: ArrayList<JsonIndex> = ArrayList()
    var values = ArrayList<ArrayList<Any>>()

    val keys: ArrayList<String>
        get() {
            val retArray = ArrayList<String>()
            if (name.isNotEmpty()) retArray.add("names")
            if (schema.size > 0) retArray.add("schema")
            if (indexes.size > 0) retArray.add("indexes")
            if (values.size > 0) retArray.add("values")
            return retArray
        }

    fun isTable(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        var nbColumn = 0
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyTableLevel.contains(key)) return false
            try {
                val value = jsObj[key]
                if (key == "name") {
                    name = if (value !is String) {
                        return false
                    } else {
                        value
                    }
                }
                if (key == "schema") {
                    if (value !is JSONArray && value !is ArrayList<*>) {
                        return false
                    } else {
                        schema = ArrayList()
                        val arr = jsObj.getJSONArray(key)
                        nbColumn = 0
                        for (i in 0 until arr.length()) {
                            val sch = JsonColumn()
                            val retSchema: Boolean = sch.isSchema(arr.getJSONObject(i))
                            if (sch.column != null) nbColumn++
                            if (!retSchema) return false
                            schema.add(sch)
                        }
                    }
                }
                if (key == "indexes") {
                    if (value !is JSONArray && value !is ArrayList<*>) {
                        return false
                    } else {
                        indexes = ArrayList()
                        val arr = jsObj.getJSONArray(key)
                        for (i in 0 until arr.length()) {
                            val idx = JsonIndex()
                            val retIndex: Boolean = idx.isIndexes(arr.getJSONObject(i))
                            if (!retIndex) return false
                            indexes.add(idx)
                        }
                    }
                }
                if (key == "values") {
                    if (value !is JSONArray && value !is ArrayList<*>) {
                        return false
                    } else {
                        values = ArrayList()
                        val arr = jsObj.getJSONArray(key)
                        for (i in 0 until arr.length()) {
                            val row = arr.getJSONArray(i)
                            val arrRow = ArrayList<Any>()
                            for (j in 0 until row.length()) {
                                if (nbColumn > 0 && row.length() != nbColumn) return false
                                arrRow.add(row[j])
                            }
                            values.add(arrRow)
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
        Log.d(TAG, "name: $name")
        Log.d(TAG, "number of Schema: " + schema.size)
        for (sch in schema) {
            sch.print()
        }
        Log.d(TAG, "number of Indexes: " + indexes.size)
        for (idx in indexes) {
            idx.print()
        }
        Log.d(TAG, "number of Values: " + values.size)
        for (row in values) {
            Log.d(TAG, "row: $row")
        }
    }

    val tableAsJSObject: JSObject
        get() {
            val retObj = JSObject()
            retObj.put("name", name)
            val jsSchema = JSONArray()
            if (schema.size > 0) {
                for (sch in schema) {
                    jsSchema.put(sch.columnAsJSObject)
                }
                retObj.put("schema", jsSchema)
            }
            val jsIndices = JSONArray()
            if (indexes.size > 0) {
                for (idx in indexes) {
                    jsIndices.put(idx.indexAsJSObject)
                }
                retObj.put("indexes", jsIndices)
            }
            val jsValues = JSONArray()
            if (values.size > 0) {
                for (row in values) {
                    val jsRow = JSONArray()
                    for (`val` in row) {
                        if (`val` is String) {
                            jsRow.put(`val`.toString())
                        } else {
                            jsRow.put(`val`)
                        }
                    }
                    jsValues.put(jsRow)
                }
                retObj.put("values", jsValues)
            }
            return retObj
        }

    companion object {
        private const val TAG = "JsonTable"
        private val keyTableLevel: List<String> = ArrayList(listOf("name", "schema", "indexes", "values"))
    }
}