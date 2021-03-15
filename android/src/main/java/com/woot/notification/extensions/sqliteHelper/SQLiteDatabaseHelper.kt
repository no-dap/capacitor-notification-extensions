//
//  SQLiteDatabaseHelper.kt
//  Plugin
//
//  Created by Quéau Jean Pierre on 01/21/2020.
//  Translated to kotlin by no dap on 12/23/2020
//  https://github.com/capacitor-community/sqlite
//
package com.woot.notification.extensions.sqliteHelper

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import net.sqlcipher.database.SQLiteStatement
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.sql.Blob
import java.text.SimpleDateFormat
import java.util.*

class SQLiteDatabaseHelper(
        private val context: Context,
        private val dbName: String,
        private var encrypted: Boolean,
        private val mode: String,
        private var secret: String = "",
        private val newsecret: String = "",
        dbVersion: Int
) : SQLiteOpenHelper(context, dbName, null, dbVersion) {
    var isOpen = false

    /**
     * Initialize SQLCipher
     */
    private fun initializeSQLCipher() {
        Log.d(TAG, " in InitializeSQLCipher: ")
        SQLiteDatabase.loadLibs(context)
        var database: SQLiteDatabase? = null
        val databaseFile: File
        if (!encrypted && mode == "no-encryption") {
            databaseFile = context.getDatabasePath(dbName)
            try {
                database = SQLiteDatabase.openOrCreateDatabase(databaseFile, "", null)
                isOpen = true
            } catch (e: Exception) {
                Log.d(TAG, "InitializeSQLCipher: no-encryption $e")
            } finally {
                database?.close()
            }
        } else if (encrypted && mode == "secret" && secret.isNotEmpty()) {
            databaseFile = context.getDatabasePath(dbName)
            try {
                database = SQLiteDatabase.openOrCreateDatabase(databaseFile, secret, null)
                isOpen = true
            } catch (e: Exception) {
                // test if you can open it with the new secret in case of multiple runs
                try {
                    database = SQLiteDatabase.openOrCreateDatabase(databaseFile, newsecret, null)
                    secret = newsecret
                    isOpen = true
                } catch (e1: Exception) {
                    Log.d(TAG, "InitializeSQLCipher: Wrong Secret ")
                }
            } finally {
                database?.close()
            }
        } else if (encrypted && mode == "newsecret" && secret.isNotEmpty() && newsecret.isNotEmpty()) {
            databaseFile = context.getDatabasePath(dbName)
            try {
                database = SQLiteDatabase.openOrCreateDatabase(databaseFile, secret, null)
                database.changePassword(newsecret)
                secret = newsecret
                isOpen = true
            } catch (e: Exception) {
                Log.d(TAG, "InitializeSQLCipher: $e")
            } finally {
                database?.close()
            }
        } else if (encrypted && mode == "encryption" && secret.isNotEmpty()) {
            try {
                encryptDataBase(secret)
                databaseFile = context.getDatabasePath(dbName)
                database = SQLiteDatabase.openOrCreateDatabase(databaseFile, secret, null)
                encrypted = true
                isOpen = true
            } catch (e: Exception) {
                Log.d(TAG, "InitializeSQLCipher: Error while encrypting the database")
            } finally {
                database?.close()
            }
        }
        Log.d(TAG, "InitializeSQLCipher isOpen: $isOpen")
    }

    /**
     * Encrypt the database
     * @param passphrase
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun encryptDataBase(passphrase: String) {
        val originalFile: File = context.getDatabasePath(dbName)
        val newFile = File.createTempFile("sqlcipherutils", "tmp", context.cacheDir)
        val existingDb = SQLiteDatabase.openOrCreateDatabase(originalFile, null, null)
        existingDb.rawExecSQL("ATTACH DATABASE '" + newFile.path + "' AS encrypted KEY '" + passphrase + "';")
        existingDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
        existingDb.rawExecSQL("DETACH DATABASE encrypted;")
        // close the database
        existingDb.close()
        // delete the original database
        originalFile.delete()
        // rename the encrypted database
        newFile.renameTo(originalFile)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "onCreate: name: database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion) {
            dropAllTables(db)
            onCreate(db)
        }
    }

    /**
     * get connection to the db
     */
    @Throws(Exception::class)
    private fun getConnection(readOnly: Boolean, secret: String): SQLiteDatabase? {
        val db: SQLiteDatabase? = if (readOnly) {
            getReadableDatabase(secret)
        } else {
            getWritableDatabase(secret)
        }
        try {
            val cmd = "PRAGMA foreign_keys = ON;"
            db?.execSQL(cmd)
            return db
        } catch (e: Exception) {
            Log.d(TAG, "Error: getConnection PRAGMA FOREIGN KEY failed: ", e)
            throw Exception("getConnection PRAGMA FOREIGN KEY failed")
        }
    }

    /**
     * execute sql raw statements after opening the db
     * @param statements
     * @return
     */
    fun execSQL(statements: Array<String>): JSObject {
        // Open the database for writing
        //        Log.d(TAG, "*** in execSQL: ");
        var retObj = JSObject()
        var db: SQLiteDatabase? = null
        return try {
            db = getConnection(false, secret)
            retObj = execute(db, statements)
            retObj
        } catch (e: Exception) {
            Log.d(TAG, "Error: execSQL failed: ", e)
            retObj.put("changes", Integer.valueOf(-1))
            retObj.put("message", "Error: execSQL failed: $e")
            retObj
        } finally {
            db?.close()
        }
    }

    /**
     * execute sql raw statements after opening the db
     * @param set
     * @return
     */
    @Throws(Exception::class)
    fun execSet(set: JSArray): JSObject {
        val retObj = JSObject()
        // Open the database for writing
        var db: SQLiteDatabase? = null
        var lastId = java.lang.Long.valueOf(-1)
        var changes = 0
        if (set.length() > 0) {
            try {
                db = getConnection(false, secret)
                db!!.beginTransaction()
                for (i in 0 until set.length()) {
                    val row = set.getJSONObject(i)
                    val statement = row.getString("statement")
                    val valuesJson = row.getJSONArray("values")
                    val values = JSArray()
                    for (j in 0 until valuesJson.length()) {
                        values.put(valuesJson[j])
                    }
                    lastId = prepareSQL(db, statement, values)
                    if (lastId == -1L) {
                        changes = Integer.valueOf(-1)
                        Log.v(TAG, "*** breaking lastId -1")
                        break
                    } else {
                        changes += 1
                    }
                }
                if (changes > 0) {
                    db.setTransactionSuccessful()
                    retObj.put("changes", dbChanges(db))
                    retObj.put("lastId", lastId)
                    return retObj
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error: ExecSet failed: ", e)
                retObj.put("changes", Integer.valueOf(-1))
                retObj.put("message", "Error: ExecSet failed: $e")
                return retObj
            } finally {
                db!!.endTransaction()
                db.close()
            }
            retObj.put("changes", Integer.valueOf(-1))
            retObj.put("message", "Error: ExecSet wrong statement")
            return retObj
        } else {
            retObj.put("changes", Integer.valueOf(-1))
            retObj.put("message", "Error: ExecSet no Set given")
            return retObj
        }
    }

    /**
     * execute sql raw statements
     * @param db
     * @param statements
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    fun execute(db: SQLiteDatabase?, statements: Array<String>): JSObject {
        val retObj = JSObject()
        try {
            for (cmd: String in statements) {
                var command: String = cmd
                if (!cmd.endsWith(";")) {
                    command += ";"
                }
                db!!.execSQL(command)
            }
            retObj.put("changes", dbChanges(db))
            return retObj
        } catch (e: Exception) {
            throw Exception("Execute failed")
        } finally {
            db?.close()
        }
    }

    /**
     * Run one statement with or without values after opening the db
     * @param statement
     * @param values
     * @return
     */
    fun runSQL(statement: String, values: JSArray?): JSObject {
        val retObj = JSObject()
        // Open the database for writing
        var db: SQLiteDatabase? = null
        val lastId: Long
        if (statement.length > 6) {
            try {
                db = getConnection(false, secret)
                db!!.beginTransaction()
                lastId = prepareSQL(db, statement, values)
                if (lastId != -1L) db.setTransactionSuccessful()
                retObj.put("changes", dbChanges(db))
                retObj.put("lastId", lastId)
                return retObj
            } catch (e: Exception) {
                Log.d(TAG, "Error: runSQL failed: ", e)
                retObj.put("changes", Integer.valueOf(-1))
                retObj.put("message", "Error: runSQL failed: $e")
                return retObj
            } finally {
                db!!.endTransaction()
                db.close()
            }
        } else {
            retObj.put("changes", Integer.valueOf(-1))
            retObj.put("message", "Error: runSQL statement not given")
            return retObj
        }
    }

    /**
     * Run one statement with or without values
     * @param db
     * @param statement
     * @param values
     * @return
     */
    private fun prepareSQL(db: SQLiteDatabase?, statement: String, values: JSArray?): Long {
        var success = true
        var lastId = java.lang.Long.valueOf(-1)
        val stmtType: String = statement.substring(0, 6).toUpperCase(Locale.getDefault())
        val stmt = db!!.compileStatement(statement)
        if (values != null && values.length() > 0) {
            // bind the values if any
            stmt.clearBindings()
            try {
                bindValues(stmt, values)
            } catch (e: JSONException) {
                Log.d(TAG, "Error: prepareSQL failed: " + e.message)
                success = false
            }
        }
        if (success) {
            lastId = if (stmtType == "INSERT") {
                stmt.executeInsert()
            } else {
                java.lang.Long.valueOf(stmt.executeUpdateDelete().toLong())
            }
        }
        stmt.close()
        return lastId
    }

    /**
     * Query a statement after opening the db
     * @param statement
     * @param values
     * @return
     */
    fun querySQL(statement: String, values: ArrayList<String>?): JSArray {
        val retArray: JSArray
        // Open the database for reading
        var db: SQLiteDatabase? = null
        return try {
            db = getConnection(true, secret)
            retArray = selectSQL(db, statement, values)
            if (retArray.length() > 0) {
                retArray
            } else {
                JSArray()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: querySQL failed: ", e)
            JSArray()
        } finally {
            db?.close()
        }
    }

    /**
     * Query a statement
     * @param db
     * @param statement
     * @param values
     * @return
     */
    private fun selectSQL(db: SQLiteDatabase?, statement: String, values: ArrayList<String>?): JSArray {
        val retArray = JSArray()
        val c: Cursor?
        if (values != null && values.isNotEmpty()) {
            // with values
            val bindings = arrayOfNulls<String>(values.size)
            for (i in values.indices) {
                bindings[i] = values[i]
            }
            c = db!!.rawQuery(statement, bindings)
        } else {
            // without values
            c = db!!.rawQuery(statement, null)
        }
        if (c.getCount() > 0) {
            if (c.moveToFirst()) {
                do {
                    val row = JSObject()
                    for (i in 0 until c.getColumnCount()) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_STRING -> row.put(c.getColumnName(i), c.getString(c.getColumnIndex(c.getColumnName(i))))
                            Cursor.FIELD_TYPE_INTEGER -> row.put(c.getColumnName(i), c.getLong(c.getColumnIndex(c.getColumnName(i))))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(c.getColumnName(i), c.getFloat(c.getColumnIndex(c.getColumnName(i))).toDouble())
                            Cursor.FIELD_TYPE_BLOB -> row.put(c.getColumnName(i), c.getBlob(c.getColumnIndex(c.getColumnName(i))))
                            Cursor.FIELD_TYPE_NULL -> {
                            }
                            else -> {
                            }
                        }
                    }
                    retArray.put(row)
                } while (c.moveToNext())
            }
        }
        if (c != null && !c.isClosed()) {
            c.close()
        }
        return retArray
    }

    /**
     * Close the database
     * @param databaseName
     * @return
     */
    fun closeDB(databaseName: String): Boolean {
        Log.d(TAG, "closeDB: databaseName $databaseName")
        val database: SQLiteDatabase?
        val databaseFile: File = context.getDatabasePath(databaseName)
        return try {
            database = SQLiteDatabase.openOrCreateDatabase(databaseFile, secret, null)
            database.close()
            isOpen = false
            true
        } catch (e: Exception) {
            Log.d(TAG, "Error: closeDB failed: ", e)
            false
        }
    }

    /**
     * Delete the database
     * @param databaseName
     * @return
     */
    fun deleteDB(databaseName: String): Boolean {
        Log.d(TAG, "deleteDB: databaseName $databaseName")
        context.deleteDatabase(databaseName)
        context.deleteFile(databaseName)
        val databaseFile: File = context.getDatabasePath(databaseName)
        return if (databaseFile.exists()) {
            false
        } else {
            isOpen = false
            true
        }
    }

    /**
     * Import from Json object
     * @param jsonSQL
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun importFromJson(jsonSQL: JsonSQLite): JSObject {
        Log.d(TAG, "importFromJson:  ")
        val retObj = JSObject()
        var changes: Int
        // create the database schema
        changes = createDatabaseSchema(jsonSQL)
        if (changes != -1) {
            changes = createTableData(jsonSQL)
        }
        retObj.put("changes", changes)
        return retObj
    }

    /**
     * Export to JSON Object
     * @param mode
     * @return
     */
    fun exportToJson(mode: String): JSObject {
        val inJson = JsonSQLite()
        val retObj = JSObject()
        inJson.database = dbName.substring(0, dbName.length - 9)
        inJson.encrypted = encrypted
        inJson.mode = mode
        val retJson: JsonSQLite = createJsonTables(inJson)
        val keys: ArrayList<String> = retJson.keys
        if (keys.contains("tables")) {
            retObj.put("database", retJson.database)
            retObj.put("encrypted", retJson.encrypted)
            retObj.put("mode", retJson.mode)
            retObj.put("tables", retJson.tablesAsJSObject)
        }
        return retObj
    }

    /**
     * Create the synchronization table
     * @return
     */
    fun createSyncTable(): JSObject {
        // Open the database for writing
        var retObj = JSObject()
        var db: SQLiteDatabase? = null
        try {
            db = getConnection(false, secret)
            // check if the table has already been created
            val isExists = isTableExists(db, "sync_table")
            if (!isExists) {
                val date = Date()
                val syncTime = date.time / 1000L
                val statements = arrayOf(
                        "BEGIN TRANSACTION;",
                        "CREATE TABLE IF NOT EXISTS sync_table (" + "id INTEGER PRIMARY KEY NOT NULL," + "sync_date INTEGER);",
                        "INSERT INTO sync_table (sync_date) VALUES ('$syncTime');",
                        "COMMIT TRANSACTION;"
                )
                retObj = execute(db, statements)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: createSyncTable failed: ", e)
        } finally {
            db?.close()
            return retObj
        }
    }

    /**
     * Set the synchronization date
     * @param syncDate
     * @return
     */
    fun setSyncDate(syncDate: String): Boolean {
        var ret = false
        var db: SQLiteDatabase? = null
        var retObj = JSObject()
        try {
            db = getConnection(false, secret)
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            val date = formatter.parse(syncDate.replace("Z$".toRegex(), "+0000"))
            val syncTime = date!!.time / 1000L
            val statements = arrayOf("UPDATE sync_table SET sync_date = $syncTime WHERE id = 1;")
            retObj = execute(db, statements)
        } catch (e: Exception) {
            Log.d(TAG, "Error: setSyncDate failed: ", e)
        } finally {
            db?.close()
            if (retObj.getInteger("changes") != Integer.valueOf(-1)) ret = true
            return ret
        }
    }

    /**
     * Create the database schema for import from Json
     * @param jsonSQL
     * @return
     */
    private fun createDatabaseSchema(jsonSQL: JsonSQLite): Int {
        var changes: Int
        var success = true
        // create the PRAGMAS
        val pragmas = ArrayList<String>()
        pragmas.add("PRAGMA user_version = 1;")
        pragmas.add("PRAGMA foreign_keys = ON;")
        val result1 = execSQL(pragmas.toTypedArray())
        changes = result1.getInteger("changes")
        if (changes == -1) return changes

        // create the database schema
        val statements = ArrayList<String>()
        statements.add("BEGIN TRANSACTION;")
        for (i in 0 until jsonSQL.tables.size) {
            if (jsonSQL.tables[i].schema.size > 0) {
                if (jsonSQL.mode == "full") {
                    val stmt: String = StringBuilder("DROP TABLE IF EXISTS ")
                            .append(jsonSQL.tables[i].name)
                            .append(";")
                            .toString()
                    statements.add(stmt)
                }
                var stmt: String = StringBuilder("CREATE TABLE IF NOT EXISTS ")
                        .append(jsonSQL.tables[i].name)
                        .append(" (")
                        .toString()
                for (j in 0 until jsonSQL.tables[i].schema.size) {
                    if (j == jsonSQL.tables[i].schema.size - 1) {
                        if (jsonSQL.tables[i].schema[j].column != null) {
                            stmt = StringBuilder(stmt)
                                    .append(jsonSQL.tables[i].schema[j].column)
                                    .append(" ")
                                    .append(jsonSQL.tables[i].schema[j].value)
                                    .toString()
                        } else if (jsonSQL.tables[i].schema[j].foreignkey != null) {
                            stmt = StringBuilder(stmt)
                                    .append("FOREIGN KEY (")
                                    .append(jsonSQL.tables[i].schema[j].foreignkey)
                                    .append(") ")
                                    .append(jsonSQL.tables[i].schema[j].value)
                                    .toString()
                        }
                    } else {
                        if (jsonSQL.tables[i].schema[j].column != null) {
                            stmt = StringBuilder(stmt)
                                    .append(jsonSQL.tables[i].schema[j].column)
                                    .append(" ")
                                    .append(jsonSQL.tables[i].schema[j].value)
                                    .append(",")
                                    .toString()
                        } else if (jsonSQL.tables[i].schema[j].foreignkey != null) {
                            stmt = StringBuilder(stmt)
                                    .append("FOREIGN KEY (")
                                    .append(jsonSQL.tables[i].schema[j].foreignkey)
                                    .append(") ")
                                    .append(jsonSQL.tables[i].schema[j].value)
                                    .append(",")
                                    .toString()
                        }
                    }
                }
                stmt = StringBuilder(stmt).append(");").toString()
                statements.add(stmt)
            }
            // create trigger last_modified associated with the table
            val stmtTrigger: String = StringBuilder("CREATE TRIGGER IF NOT EXISTS ")
                    .append(jsonSQL.tables[i].name)
                    .append("_trigger_last_modified")
                    .append(" AFTER UPDATE ON ")
                    .append(jsonSQL.tables[i].name)
                    .append(" FOR EACH ROW ")
                    .append("WHEN NEW.last_modified <= OLD.last_modified BEGIN ")
                    .append("UPDATE ")
                    .append(jsonSQL.tables[i].name)
                    .append(" SET last_modified = (strftime('%s','now')) ")
                    .append("WHERE id=OLD.id; ")
                    .append("END;")
                    .toString()
            statements.add(stmtTrigger)
            if (jsonSQL.tables[i].indexes.size > 0) {
                for (j in 0 until jsonSQL.tables[i].indexes.size) {
                    val stmt: String = StringBuilder("CREATE INDEX IF NOT EXISTS ")
                            .append(jsonSQL.tables[i].indexes[j].name)
                            .append(" ON ")
                            .append(jsonSQL.tables[i].name)
                            .append(" (")
                            .append(jsonSQL.tables[i].indexes[j].column)
                            .append(");")
                            .toString()
                    statements.add(stmt)
                }
            }
        }
        if (statements.size > 1) {
            statements.add("COMMIT TRANSACTION;")
            val result = execSQL(statements.toTypedArray())
            changes = result.getInteger("changes")
            if (changes == -1) {
                success = false
            }
        } else {
            changes = Integer.valueOf(0)
        }
        if (!success) {
            changes = Integer.valueOf(-1)
        }
        return changes
    }

    /**
     * Create the database table data for import from Json
     * @param jsonSQL
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    private fun createTableData(jsonSQL: JsonSQLite): Int {
        var success = true
        var changes = Integer.valueOf(-1)
        var db: SQLiteDatabase? = null
        var isValue = false

        // create the table's data
        val statements = ArrayList<String>()
        statements.add("BEGIN TRANSACTION;")
        try {
            db = getConnection(false, secret)
            db!!.beginTransaction()
            for (i in 0 until jsonSQL.tables.size) {
                if (jsonSQL.tables[i].values.size > 0) {
                    // Check if table exists
                    val isTable = isTableExists(db, jsonSQL.tables[i].name)
                    if (!isTable) {
                        Log.d(TAG, "importFromJson: Table " + jsonSQL.tables[i].name + "does not exist")
                        success = false
                        break
                    }
                    // Get the Column's Name and Type
                    try {
                        val tableNamesTypes = getTableColumnNamesTypes(db, jsonSQL.tables[i].name)
                        if (tableNamesTypes.length() == 0) {
                            success = false
                            break
                        }
                        val tableColumnNames = tableNamesTypes["names"] as ArrayList<String>
                        val tableColumnTypes = tableNamesTypes["types"] as ArrayList<String>
                        isValue = true
                        // Loop on Table's Values
                        for (j in 0 until jsonSQL.tables[i].values.size) {
                            // Check the row number of columns
                            val row: ArrayList<Any> = jsonSQL.tables[i].values[j]
                            if (tableColumnNames.size != row.size) {
                                Log.d(
                                        TAG,
                                        "importFromJson: Table " +
                                                jsonSQL.tables[i].name +
                                                " values row " +
                                                j.toString() +
                                                " not correct length"
                                )
                                success = false
                                break
                            }

                            // Check the column's type before proceeding
                            val retTypes = checkColumnTypes(tableColumnTypes, row)
                            if (!retTypes) {
                                Log.d(
                                        TAG,
                                        ("importFromJson: Table " +
                                                jsonSQL.tables[i].name +
                                                " values row " +
                                                j.toString() +
                                                " not correct types")
                                )
                                success = false
                                break
                            }
                            val retIdExists = isIdExists(db, jsonSQL.tables[i].name, tableColumnNames[0], row[0])
                            var stmt: String
                            // Create INSERT or UPDATE Statements
                            if (jsonSQL.mode == "full" || (jsonSQL.mode == "partial" && !retIdExists)) {
                                // Insert
                                val namesString = convertToString(tableColumnNames)
                                val questionMarkString = createQuestionMarkString(tableColumnNames.size)
                                stmt = StringBuilder("INSERT INTO ")
                                        .append(jsonSQL.tables[i].name)
                                        .append("(")
                                        .append(namesString)
                                        .append(")")
                                        .append(" VALUES (")
                                        .append(questionMarkString)
                                        .append(");")
                                        .toString()
                            } else {
                                // Update
                                val setString = setNameForUpdate(tableColumnNames)
                                if (setString.isEmpty()) {
                                    success = false
                                    break
                                }
                                stmt = StringBuilder("UPDATE ")
                                        .append(jsonSQL.tables[i].name)
                                        .append(" SET ")
                                        .append(setString)
                                        .append(" WHERE ")
                                        .append(tableColumnNames[0])
                                        .append(" = ")
                                        .append(row[0])
                                        .append(";")
                                        .toString()
                            }
                            val jsRow = convertToJSArray(row)
                            val lastId = prepareSQL(db, stmt, jsRow)
                            if (lastId == -1L) {
                                Log.d(TAG, "createTableData: Error in INSERT/UPDATE")
                                success = false
                                break
                            }
                        }
                    } catch (e: JSONException) {
                        Log.d(TAG, "get Table Values: Error ", e)
                        success = false
                        break
                    }
                }
            }
            if (success && isValue) db.setTransactionSuccessful()
        } catch (e: Exception) {
        } finally {
            if (db != null) {
                db.endTransaction()
                if (success && isValue) changes = dbChanges(db)
                if (!isValue) changes = Integer.valueOf(0)
                db.close()
            }
        }
        return changes
    }

    /**
     * Bind Values to Statement
     * @param stmt
     * @param values
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun bindValues(stmt: SQLiteStatement, values: JSArray) {
        for (i in 0 until values.length()) {
            if (values[i] is Float || values[i] is Double) {
                stmt.bindDouble(i + 1, values.getDouble(i))
            } else if (values[i] is Number) {
                stmt.bindLong(i + 1, values.getLong(i))
            } else if (values.isNull(i)) {
                stmt.bindNull(i + 1)
            } else {
                val str = values.getString(i)
                if ((str.toUpperCase(Locale.getDefault()) == "NULL")) {
                    stmt.bindNull(i + 1)
                } else {
                    stmt.bindString(i + 1, str)
                }
            }
        }
    }

    /**
     * Convert ArrayList to JSArray
     * @param row
     * @return
     */
    private fun convertToJSArray(row: ArrayList<Any>): JSArray {
        val jsArray = JSArray()
        for (i in row.indices) {
            jsArray.put(row[i])
        }
        return jsArray
    }

    /**
     * Check if a table exists
     * @param db
     * @param tableName
     * @return
     */
    private fun isTableExists(db: SQLiteDatabase?, tableName: String): Boolean {
        var ret = false
        val query = StringBuilder("SELECT name FROM sqlite_master WHERE type='table' AND name='")
                .append(tableName)
                .append("';")
                .toString()
        val resQuery = selectSQL(db, query, ArrayList())
        if (resQuery.length() > 0) ret = true
        return ret
    }

    /**
     * Get Field's type and name for a given table
     * @param db
     * @param tableName
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getTableColumnNamesTypes(db: SQLiteDatabase?, tableName: String): JSObject {
        val ret = JSObject()
        val names = ArrayList<String>()
        val types = ArrayList<String>()
        val query = StringBuilder("PRAGMA table_info(").append(tableName).append(");").toString()
        val resQuery = selectSQL(db, query, ArrayList())
        val lQuery = resQuery.toList<JSObject>()
        if (resQuery.length() > 0) {
            for (obj: JSObject in lQuery) {
                names.add(obj.getString("name"))
                types.add(obj.getString("type"))
            }
            ret.put("names", names)
            ret.put("types", types)
        }
        return ret
    }

    /**
     * Check the values type from fields type
     * @param types
     * @param values
     * @return
     */
    private fun checkColumnTypes(types: ArrayList<String>, values: ArrayList<Any>): Boolean {
        var isType = true
        for (i in values.indices) {
            isType = isType(types[i], values[i])
            if (!isType) break
        }
        return isType
    }

    /**
     * Check if the the value type is the same than the field type
     * @param type
     * @param value
     * @return
     */
    private fun isType(type: String, value: Any): Boolean {
        var ret = false
        val `val` = value.toString().toUpperCase(Locale.getDefault())
        if ((`val` == "NULL")) {
            ret = true
        } else if (`val`.contains("BASE64")) {
            ret = true
        } else {
            if ((type == "NULL") && value is JSONObject) ret = true
            if ((type == "TEXT") && value is String) ret = true
            if ((type == "INTEGER") && value is Int) ret = true
            if ((type == "INTEGER") && value is Long) ret = true
            if ((type == "REAL") && value is Float) ret = true
            if ((type == "BLOB") && value is Blob) ret = true
        }
        return ret
    }

    /**
     * Check if the Id already exsists
     * @param db
     * @param tableName
     * @param firstColumnName
     * @param key
     * @return
     */
    private fun isIdExists(db: SQLiteDatabase?, tableName: String, firstColumnName: String, key: Any): Boolean {
        var ret = false
        val query = StringBuilder("SELECT ")
                .append(firstColumnName)
                .append(" FROM ")
                .append(tableName)
                .append(" WHERE ")
                .append(firstColumnName)
                .append(" = ")
                .append(key)
                .append(";")
                .toString()
        val resQuery = selectSQL(db, query, ArrayList())
        if (resQuery.length() == 1) ret = true
        return ret
    }

    /**
     * Create the ? string for a given values length
     * @param length
     * @return
     */
    private fun createQuestionMarkString(length: Int): String {
        val retString: String
        val strB = StringBuilder()
        for (i in 0 until length) {
            strB.append("?,")
        }
        strB.deleteCharAt(strB.length - 1)
        retString = strB.toString()
        return retString
    }

    /**
     * Create the Name string from a given Names array
     * @param names
     * @return
     */
    private fun setNameForUpdate(names: ArrayList<String>): String {
        val retString: String
        val strB = StringBuilder()
        for (i in names.indices) {
            strB.append("(" + names[i] + ") = ? ,")
        }
        strB.deleteCharAt(strB.length - 1)
        retString = strB.toString()
        return retString
    }

    /**
     * Drop all Tables
     * @param db
     * @return
     */
    private fun dropAllTables(db: SQLiteDatabase): Boolean {
        var ret = true
        val tables: MutableList<String> = ArrayList()
        val cursor: Cursor?
        cursor = db.rawQuery("SELECT * FROM sqlite_master WHERE type='table';", null)
        cursor.moveToFirst()
        while (!cursor.isAfterLast()) {
            val tableName = cursor.getString(1)
            if (tableName != "android_metadata" && tableName != "sqlite_sequence") tables.add(tableName)
            cursor.moveToNext()
        }
        cursor.close()
        try {
            for (tableName: String in tables) {
                db.execSQL("DROP TABLE IF EXISTS $tableName")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: dropAllTables failed: ", e)
            ret = false
        } finally {
            return ret
        }
    }

    /**
     * Return the total number of changes in the DB from the last command
     * @param db
     * @return
     */
    private fun dbChanges(db: SQLiteDatabase?): Int {
        val sql = "SELECT total_changes()"
        var ret = Integer.valueOf(-1)
        try {
            val cursor: Cursor? = db!!.rawQuery(sql, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    ret = cursor.getString(0).toInt()
                }
            }
            cursor!!.close()
        } catch (e: Exception) {
            Log.d(TAG, "Error: dbChanges failed: ", e)
        } finally {
            return ret
        }
    }

    /**
     * Create a String from a given Array of Strings with a given separator
     * @param arr
     * @return
     */
    private fun convertToString(arr: ArrayList<String>): String {
        val builder = StringBuilder()
        // Append all Integers in StringBuilder to the StringBuilder.
        for (str: String in arr) {
            builder.append(str)
            builder.append(',')
        }
        // Remove last delimiter with setLength.
        builder.setLength(builder.length - 1)
        return builder.toString()
    }

    /**
     * Create Json Tables for the export to Json
     * @param sqlObj
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    private fun createJsonTables(sqlObj: JsonSQLite): JsonSQLite {
        var success = true
        val retObj = JsonSQLite()
        var db: SQLiteDatabase? = null
        val jsonTables: ArrayList<JsonTable> = ArrayList()
        var syncDate: Long = 0
        try {
            db = getConnection(true, secret)
            var stmt = "SELECT name,sql FROM sqlite_master WHERE type = 'table' "
            stmt += "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'sync_table';"
            val tables = selectSQL(db, stmt, ArrayList())
            var modTables = JSObject()
            var modTablesKeys = ArrayList<String?>()
            if (sqlObj.mode == "partial") {
                syncDate = getSyncDate(db)
                modTables = getTablesModified(db, tables, syncDate)
                modTablesKeys = getJSObjectKeys(modTables)
            }
            val lTables = tables.toList<JSObject>()
            for (i in lTables.indices) {
                val tableName = lTables[i].getString("name")
                var sqlStmt = lTables[i].getString("sql")
                if (sqlObj.mode == "partial" &&
                        ((modTablesKeys.size == 0) || (modTablesKeys.indexOf(tableName) == -1) || (modTables.getString(tableName) == "No"))) {
                    continue
                }
                val table = JsonTable()
                var isSchema = false
                var isIndexes = false
                var isValues = false
                table.name = tableName
                if (sqlObj.mode == "full" ||
                        (sqlObj.mode == "partial" && (modTables.getString(tableName) == "Create"))) {
                    // create the schema
                    val schema: ArrayList<JsonColumn> = ArrayList()
                    // get the sqlStmt between the parenthesis sqlStmt
                    sqlStmt = sqlStmt.substring(sqlStmt.indexOf("(") + 1, sqlStmt.lastIndexOf(")"))
                    val sch = sqlStmt.split(",".toRegex()).toTypedArray()
                    // for each element of the array split the first word as key
                    for (j in sch.indices) {
                        val row = sch[j].split(" ".toRegex(), 2).toTypedArray()
                        val jsonRow = JsonColumn()
                        if ((row[0].toUpperCase(Locale.getDefault()) == "FOREIGN")) {
                            val oPar = sch[j].indexOf("(")
                            val cPar = sch[j].indexOf(")")
                            row[0] = sch[j].substring(oPar + 1, cPar)
                            row[1] = sch[j].substring(cPar + 2, sch[j].length)
                            jsonRow.foreignkey = row[0]
                        } else {
                            jsonRow.column = row[0]
                        }
                        jsonRow.value = row[1]
                        schema.add(jsonRow)
                    }
                    table.schema = schema
                    isSchema = true

                    // create the indexes
                    stmt = "SELECT name,tbl_name,sql FROM sqlite_master WHERE "
                    stmt += "type = 'index' AND tbl_name = '$tableName' AND sql NOTNULL;"
                    val retIndexes = selectSQL(db, stmt, ArrayList())
                    val lIndexes = retIndexes.toList<JSObject>()
                    if (lIndexes.size > 0) {
                        val indexes: ArrayList<JsonIndex> = ArrayList()
                        for (j in lIndexes.indices) {
                            val jsonRow = JsonIndex()
                            if ((lIndexes[j].getString("tbl_name") == tableName)) {
                                jsonRow.name = lIndexes[j].getString("name")
                                val sql = lIndexes[j].getString("sql")
                                val oPar = sql.lastIndexOf("(")
                                val cPar = sql.lastIndexOf(")")
                                jsonRow.column = sql.substring(oPar + 1, cPar)
                                indexes.add(jsonRow)
                            } else {
                                success = false
                                throw Exception("createJsonTables: Error indexes table name doesn't match")
                            }
                        }
                        table.indexes = indexes
                        isIndexes = true
                    }
                }
                val tableNamesTypes = getTableColumnNamesTypes(db, tableName)
                val rowNames = tableNamesTypes["names"] as ArrayList<String>
                val rowTypes = tableNamesTypes["types"] as ArrayList<String>
                // create the data
                stmt = if (sqlObj.mode == "full" ||
                        (sqlObj.mode == "partial" && (modTables.getString(tableName) == "Create"))) {
                    "SELECT * FROM $tableName;"
                } else {
                    "SELECT * FROM $tableName WHERE last_modified > $syncDate;"
                }
                val retValues = selectSQL(db, stmt, ArrayList())
                val lValues = retValues.toList<JSObject>()
                if (lValues.size > 0) {
                    val values = ArrayList<ArrayList<Any>>()
                    for (j in lValues.indices) {
                        val row = ArrayList<Any>()
                        for (k in rowNames.indices) {
                            if ((rowTypes[k] == "INTEGER")) {
                                if (lValues[j].has(rowNames[k])) {
                                    row.add(lValues[j].getLong(rowNames[k]))
                                } else {
                                    row.add("NULL")
                                }
                            } else if ((rowTypes[k] == "REAL")) {
                                if (lValues[j].has(rowNames[k])) {
                                    row.add(lValues[j].getDouble(rowNames[k]))
                                } else {
                                    row.add("NULL")
                                }
                            } else {
                                if (lValues[j].has(rowNames[k])) {
                                    row.add(lValues[j].getString(rowNames[k]))
                                } else {
                                    row.add("NULL")
                                }
                            }
                        }
                        values.add(row)
                    }
                    table.values = values
                    isValues = true
                }
                if (table.keys.size < 1 || (!isSchema && !isIndexes && !isValues)) {
                    success = false
                    throw Exception("Error table is not a jsonTable")
                }
                jsonTables.add(table)
            }
        } catch (e: Exception) {
            success = false
            Log.d(TAG, "Error: createJsonTables failed: ", e)
        } finally {
            db?.close()
            if (success) {
                retObj.database = sqlObj.database
                retObj.mode = sqlObj.mode
                retObj.encrypted = sqlObj.encrypted
                retObj.tables = jsonTables
            }
            return retObj
        }
    }

    /**
     * Get JSObject keys
     * @param jsonObject
     * @return
     */
    private fun getJSObjectKeys(jsonObject: JSObject): ArrayList<String?> {
        // one level JSObject keys
        val retArray = ArrayList<String?>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            retArray.add(key)
        }
        return retArray
    }

    /**
     * Get the Tables which have been modified since the last synchronization
     * @param db
     * @param tables
     * @param syncDate
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getTablesModified(db: SQLiteDatabase?, tables: JSArray, syncDate: Long): JSObject {
        val retModified = JSObject()
        if (tables.length() > 0) {
            val lTables = tables.toList<JSObject>()
            for (i in lTables.indices) {
                var mode: String
                // get total count of the table
                val tableName = lTables[i].getString("name")
                var stmt = "SELECT count(*) FROM $tableName;"
                var retQuery = selectSQL(db, stmt, ArrayList())
                var lQuery = retQuery.toList<JSObject>()
                if (lQuery.size != 1) break
                val totalCount = lQuery[0].getLong("count(*)")
                // get total count of modified since last sync
                stmt = "SELECT count(*) FROM $tableName WHERE last_modified > $syncDate;"
                retQuery = selectSQL(db, stmt, ArrayList())
                lQuery = retQuery.toList()
                if (lQuery.size != 1) break
                val totalModifiedCount = lQuery[0].getLong("count(*)")
                mode = when {
                    totalModifiedCount == 0L -> {
                        "No"
                    }
                    totalCount == totalModifiedCount -> {
                        "Create"
                    }
                    else -> {
                        "Modified"
                    }
                }
                retModified.put(tableName, mode)
            }
        }
        return retModified
    }

    /**
     * Get the current synchronization date from the sync_table
     * @param db
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getSyncDate(db: SQLiteDatabase?): Long {
        var ret: Long = -1
        val stmt = "SELECT sync_date FROM sync_table;"
        val retQuery = selectSQL(db, stmt, ArrayList())
        val lQuery = retQuery.toList<JSObject>()
        if (lQuery.size == 1) {
            val syncDate = lQuery[0].getLong("sync_date")
            if (syncDate > 0) ret = syncDate
        }
        return ret
    }

    companion object {
        private const val TAG = "SQLiteDatabaseHelper"
    }

    init {
        initializeSQLCipher()
    }
}