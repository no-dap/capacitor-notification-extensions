package com.woot.notification.extensions

import android.content.Context
import android.content.pm.PackageManager
import com.woot.notification.extensions.sqliteHelper.SQLiteDatabaseHelper
import java.lang.Exception

class SQLiteHandler(private var context: Context) {
    private val encrypted = false
    private val mode = "no-encryption"
    private val dbVersion = 1
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
        if (mdb.isOpen) {
            val statement = StringBuilder("CREATE TABLE IF NOT EXISTS notification_extensions_filter ")
                    .append("(id INTEGER PRIMARY KEY NOT NULL, key TEXT NOT NULL UNIQUE, value TEXT);")
                    .toString()
            val createTableSQL: Array<String> = arrayOf(statement)
            mdb.execSQL(createTableSQL)
        } else {
            throw Exception("Local database not opened yet.")
        }
    }
}