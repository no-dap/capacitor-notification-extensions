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
    private let tableName = "notification_extensions_filter"
    init() {
    }
    func openDB() {
        let fileURL = try! FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false).appendingPathComponent("TestDatabase.sqlite")
            
        if sqlite3_open(fileURL.path, &db) != SQLITE_OK {
            print("DB 열기 실패222")
        } else {
            print("DB 열기 성공")
        }
    }
    
    func createFilterTable() -> Void {
        var sql = "CREATE TABLE IF NOT EXISTS "
            sql.append(tableName)
            sql.append("(id INTEGER PRIMARY KEY NOT NULL, key TEXT NOT NULL UNIQUE, value TEXT)")
        if sqlite3_exec(db, sql, nil, nil, nil) != SQLITE_OK {
            let errmsg = String(cString: sqlite3_errmsg(db)!)
            print("error creating table: \(errmsg)")
        } else {
            print("Table Created")
        }
    }
    
    func getTimeFilter() {
        
    }
    
    func getFilters() {
        
    }
    
    func insertTimeFilter(startFrom: String, endAt: String) {
        
    }
    
    func removeTimeFilter() {
        
    }
    
    func insertFilter(key: String) {
        
    }
    
    func removeFilter(key: String) {
        
    }

}
