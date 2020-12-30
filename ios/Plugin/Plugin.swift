import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NotificationExtension)
public class NotificationExtension: CAPPlugin {
    let sqlHandler = SQLiteHandler()
    public override func load() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(onNotification(_:)),
            name: Notification.Name("notification"),
            object: nil
        )
    }

    @objc func onNotification(_ notification: Notification) {
        sqlHandler.openDB()
        sqlHandler.createFilterTable()
        addNewNotification(notification: notification)
    }
    
    @objc func addNewNotification(notification: Notification) {
        print(notification)
        UNUserNotificationCenter.current().getDeliveredNotifications { notifications in
            let identifier = String(Int.random(in: 0..<5))
            var badge = notifications.count
            // get delivered notification duplicated width identifier
            let duplitedNotificationIdentifier = notifications.filter { (noti: UNNotification) -> Bool in
                let notificationRequest = noti.value(forKey: "request") as! UNNotificationRequest
                let notificationIdentifier = notificationRequest.value(forKey: "identifier")
                return notificationIdentifier as! String == identifier
            }
            // if there is no duplicated delivered notifications, plus badge count
            if duplitedNotificationIdentifier.count == 0 {
                badge += 1
            }
            let notificationData = notification.userInfo?["aps"] as! [String: Any]
            let title = (notificationData["hello"] ?? "") as! String
            let subtitle = (notificationData["foo"] ?? "") as! String
            let body = (notificationData["bar"] ?? "") as! String
//            let url = (notificationData["url"] ?? "") as! String
            let content = UNMutableNotificationContent()
            print("sync test")
            content.title = title
            content.subtitle = subtitle
            content.body = body
            content.badge = badge as NSNumber
            content.sound = UNNotificationSound.default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
            let isActive = self.isApplicationActive()
            if (isActive) {
                content.badge = 0
                content.sound = nil
            }
            let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
        }
    }
    
    @objc func afterPushCompleted() {
        print("completed")
    }
    
    @objc func isApplicationActive() -> Bool {
        DispatchQueue.main.sync {
            switch UIApplication.shared.applicationState {
            case .active:
                return true
            default:
                return false
            }
        }
    }
    
    @objc func getToken(_ call: CAPPluginCall) {
        
    }

    @objc func addTimeFilter(_ call: CAPPluginCall) {
        sqlHandler.openDB()
        let startFrom = call.getString("startFrom")
        let endAt = call.getString("endAt")
//        let result = sqLiteHandler.insertTimeFilter(startFrom, endAt)
//        if (result.getValue("success") as Boolean) {
//            call.success()
//        } else {
//            call.reject(result.getValue("reason") as String)
//        }
    }

    @objc func removeTimeFilter(_ call: CAPPluginCall) {
        sqlHandler.openDB()
//        let result = sqLiteHandler.removeTimeFilter()
//        if (result.getValue("success") as Boolean) {
//            call.success()
//        } else {
//            call.reject("Got an unexpected error while remove the time filter.")
//        }
    }

    @objc func addFilters(_ call: CAPPluginCall) {
        sqlHandler.openDB()
        guard let filters = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }
//        for (filter in filters) {
//            let result = sqLiteHandler.insertFilter(filter)
//            if (!(result.getValue("success") as Boolean)) {
//                call.reject(result.getValue("reason") as String)
//            }
//        }
//        call.success()
    }

    @objc func removeFilters(_ call: CAPPluginCall) {
        sqlHandler.openDB()
        guard let filters = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }
//        for (filter in filters) {
//            let result = sqLiteHandler.removeFilter(filter)
//            if (!(result.getValue("success") as Boolean)) {
//                call.reject(result.getValue("reason") as String)
//            }
//        }
//        call.success()
    }
}
