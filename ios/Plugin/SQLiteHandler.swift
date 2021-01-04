//
//  File.swift
//  NotificationExtensions
//
//  Created by mjLee on 2020/12/29.
//

import Foundation
import SQLite3

class SQLiteHandler {
    var db: OpaquePointer?
    var isDbOpened = false
    private let tableName = "notification_extensions_filter"
    init() {
        openDB()
    }
    
    // MARK: - Open Database
    func openDB() {
        let fileURL = try! FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false).appendingPathComponent("TestDatabase.sqlite")
            
        if sqlite3_open(fileURL.path, &db) != SQLITE_OK {
            print("open DB Failed")
            isDbOpened = false
        } else {
            print("open DB Succeed")
            isDbOpened = true
        }
    }
    
    // MARK: - Create Filter Table
    func createFilterTable() -> Void {
        if (!isDbOpened) {
            print("Local database not opened yet.")
            return
        }
        var sql = "CREATE TABLE IF NOT EXISTS "
            sql += tableName
            sql += "(id INTEGER PRIMARY KEY NOT NULL, key TEXT NOT NULL UNIQUE, value TEXT);"
        if sqlite3_exec(db, sql, nil, nil, nil) != SQLITE_OK {
            let errmsg = String(cString: sqlite3_errmsg(db)!)
            print("error creating table: \(errmsg)")
        }
    }
    
    // MARK: - Get Time Filters
    func getTimeFilter() -> Array<[String: Any]> {
        if (!isDbOpened) {
            print("Local database not opened yet.")
            return []
        }
        
        var sql = "SELECT * FROM "
            sql += tableName
            sql += " WHERE key IN ('filter_start_from', 'filter_end_at', 'is_time_filter_on');"

        return runSelectQuery(sql: sql)
    }
    
    // MARK: - Get Non-Time Filters
    func getFilters() -> Array<[String: Any]> {
        if (!isDbOpened) {
            print("Local database not opened yet.")
            return []
        }
        var sql = "SELECT * FROM "
            sql += tableName
            sql += " WHERE key NOT IN ('filter_start_from', 'filter_end_at', 'is_time_filter_on');"
        
        return runSelectQuery(sql: sql)
    }
    
    // MARK: - Add Time Filter
    func insertTimeFilter(startFrom: String, endAt: String) -> [String: Any] {
        if (!isDbOpened) {
            return ["success": false, "reason": "Local database not opened yet."]
        }
        
        if (startFrom.components(separatedBy: ":").count != 2 || endAt.components(separatedBy: ":").count != 2) {
            return ["success": false, "reason": "Invalid time format"]
        }

        var sql = "INSERT OR REPLACE INTO "
            sql += tableName
            sql += " (key, value) VALUES "
//            sql += "('filter_start_from', '09:10'), "
//            sql += "('filter_end_at', '23:20'), "
            sql += "('filter_start_from', '\(startFrom)'), "
            sql += "('filter_end_at', '\(endAt)'), "
            sql += "('is_time_filter_on', 'true');"

        return runDataChangingQuery(sql: sql)
    }
    
    // MARK: - Remove Time Filter
    func removeTimeFilter() -> [String: Any]  {
        if (!isDbOpened) {
            return ["success": false, "reason": "Local database not opened yet."]
        }

        var sql = "INSERT OR REPLACE INTO "
            sql += tableName
            sql += " (key, value) VALUES "
            sql += "('is_time_filter_on', 'false');"
        
        return runDataChangingQuery(sql: sql)
    }
    
    // MARK: - Add Non-Time Filter
    func insertFilter(key: String) -> [String: Any] {
        if (!isDbOpened) {
            return ["success": false, "reason": "Local database not opened yet."]
        }
        
        var sql = "INSERT OR REPLACE INTO "
            sql += tableName
            sql += " (key, value) VALUES "
            sql += "('\(key)', 'false');"
        
        return runDataChangingQuery(sql: sql)
    }
    
    // MARK: - Remove Non-Time Filter
    func removeFilter(key: String) -> [String: Any] {
        if (!isDbOpened) {
            return ["success": false, "reason": "Local database not opened yet."]
        }
        
        var sql = "INSERT OR REPLACE INTO "
            sql += tableName
            sql += " (key, value) VALUES "
            sql += "('\(key)', 'true');"
        
        return runDataChangingQuery(sql: sql)
    }
    
    // run insert, update, delete query
    func runDataChangingQuery(sql: String) -> [String: Any] {
        var statement: OpaquePointer? = nil;
        var errmsg: String
        if sqlite3_prepare(db, sql, -1, &statement, nil) == SQLITE_OK {
            if sqlite3_step(statement) == SQLITE_DONE {
                return ["success": true]
            } else {
                errmsg = String(cString: sqlite3_errmsg(db)!)
            }
        } else {
            errmsg = String(cString: sqlite3_errmsg(db)!)
            print("get timeFilter: \(errmsg)")
        }
        
        return ["success": false, "reason": errmsg]
    }
    
    // run select query
    func runSelectQuery(sql: String) -> Array<[String: Any]> {
        var statement: OpaquePointer? = nil;
        if sqlite3_prepare(db, sql, -1, &statement, nil) == SQLITE_OK {
            var resultArray = Array<[String: Any]>()
            while sqlite3_step(statement) == SQLITE_ROW {
                let id = sqlite3_column_int(statement, 0) as Int32
                let key: String = String(cString: sqlite3_column_text(statement, 1))
                let value: String = String(cString: sqlite3_column_text(statement, 2))
                resultArray.append(["id": id, "key": key, "value": value])
            }
            return resultArray
        } else {
            let errmsg = String(cString: sqlite3_errmsg(db)!)
            print("get timeFilter: \(errmsg)")
            return []
        }
    }
}
