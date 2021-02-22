package com.woot.notification.extensions

import android.content.Context
import android.content.pm.PackageManager
import com.getcapacitor.JSArray
import com.woot.notification.extensions.sqliteHelper.SQLiteDatabaseHelper
import java.lang.Exception


class SQLiteHandler(private var context: Context) {
    private val encrypted = false
    private val mode = "no-encryption"
    private val dbVersion = 1
    private val tableName = "notification_extensions_filter"
    private lateinit var mdb: SQLiteDatabaseHelper

    fun openDB(): Boolean {
        context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .apply {
                    val dbName = metaData.getString("com.woot.notification.extensions.local_database_name")
                    mdb = SQLiteDatabaseHelper(
                            context,
                            dbName!! + "SQLite.db",
                            encrypted,
                            mode,
                            dbVersion = dbVersion
                    )
                    return mdb.isOpen
                }
    }

    @Throws(Exception::class)
    fun createFilterTable() {
        if (::mdb.isInitialized) {
            val statement = StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(tableName)
                    .append(" (id INTEGER PRIMARY KEY NOT NULL, key TEXT NOT NULL UNIQUE, value TEXT);")
                    .toString()
            val createTableSQL: Array<String> = arrayOf(statement)
            mdb.execSQL(createTableSQL)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun getAllFilters(): JSArray {
        if (::mdb.isInitialized) {
            val statement = StringBuilder("SELECT * FROM")
                    .append(tableName)
                    .toString()
            return mdb.querySQL(statement, null)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun getTimeFilter(): JSArray {
        if (::mdb.isInitialized) {
        val statement = StringBuilder("SELECT * FROM ")
                .append(tableName)
                .append(" WHERE key IN ('filter_start_from', 'filter_end_at', 'is_time_filter_on');")
                .toString()
        return mdb.querySQL(statement, null)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun getFilters(): JSArray {
        if (::mdb.isInitialized) {
            val statement = StringBuilder("SELECT * FROM ")
                    .append(tableName)
                    .append(" WHERE key NOT IN ('filter_start_from', 'filter_end_at', 'is_time_filter_on');")
                    .toString()
            return mdb.querySQL(statement, null)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun insertTimeFilter(startFrom: String, endAt: String): Map<String, Any> {
        if (startFrom.split(':').size != 2 || endAt.split(':').size != 2) {
            return mapOf("success" to false, "reason" to "Invalid time format")
        }
        if (::mdb.isInitialized) {
            val values: Array<String> = arrayOf(
                    "('filter_start_from', '$startFrom')",
                    "('filter_end_at', '$endAt')",
                    "('is_time_filter_on', 'true')"
            )
            val statement = StringBuilder("INSERT OR REPLACE INTO ")
                    .append(tableName)
                    .append(" (key, value) VALUES ")
                    .append(values.joinToString(", "))
                    .append(';')
                    .toString()
            mdb.execSQL(arrayOf(statement))
            return mapOf("success" to true)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun removeTimeFilter(): Map<String, Any> {
        if (::mdb.isInitialized) {
            val statement = StringBuilder("INSERT OR REPLACE INTO ")
                    .append(tableName)
                    .append(" (key, value) VALUES ")
                    .append("('is_time_filter_on', 'false');")
                    .toString()
            mdb.execSQL(arrayOf(statement))
            return mapOf("success" to true)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun insertFilter(key: String): Map<String, Any> {
        return if (::mdb.isInitialized) {
            try {
                val statement = StringBuilder("INSERT OR REPLACE INTO ")
                        .append(tableName)
                        .append(" (key, value) VALUES ")
                        .append("('$key', 'false');")
                        .toString()
                mdb.execSQL(arrayOf(statement))
                mapOf("success" to true)
            } catch (exception: Throwable) {
                mapOf("success" to false, "reason" to "Unexpected error occurred while insert filter '$key'")
            }
        } else {
            throw Exception("Local database not opened yet.")
        }
    }

    @Throws(Exception::class)
    fun removeFilter(key: String): Map<String, Any> {
        return if (::mdb.isInitialized) {
            try {
                val statement = StringBuilder("INSERT OR REPLACE INTO")
                        .append(tableName)
                        .append(" (key, value) VALUES ")
                        .append("('$key', 'true');")
                        .toString()
                mdb.execSQL(arrayOf(statement))
                mapOf("success" to true)
            } catch (exception: Throwable) {
                mapOf("success" to false, "reason" to "Unexpected error occurred while insert filter '$key'")
            }
        } else {
            throw Exception("Local database not opened yet.")
        }
    }
}