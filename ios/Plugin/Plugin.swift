import Foundation
import Capacitor
import FirebaseMessaging

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
        sqlHandler.createFilterTable()
        handleNotification(notification: notification)
    }
    
    @objc func handleNotification(notification: Notification) -> Void {
        // extract push data. if there is no data, return.
        guard let notificationData: [String: Any] = notification.userInfo?["aps"] as? [String: Any] else {
            return
        }

        // notify notification data to listner
        notifyToListeners(identifier: "pushNotificationReceived", data: notificationData)
        
        if checkMessageCondition(remoteMessageData: notificationData) {
            UNUserNotificationCenter.current().getDeliveredNotifications { deliveredNotifications in
                // if there is no identifier, finish this process
                guard let identifier: String = self.getIdentifier(notificationData) else {
                    return
                }
                let content: UNMutableNotificationContent = self.getPushNotificationContent(notificationData, deliveredNotifications, identifier)
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
                let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
                
                self.showPushNotification(request: request)
            }
        }
    }
    
    @objc func getIdentifier(_ data: [String: Any]) -> String? {
        if let identifier: String = data["filter"] as? String {
            return identifier
        } else {
            return nil
        }
    }
    
    // get push notification content
    @objc func getPushNotificationContent(_ data: [String: Any], _ deliveredData: [UNNotification], _ identifier: String) -> UNMutableNotificationContent {
        let title: String = (data["title"] ?? "") as! String // title
        let body: String = (data["body"] ?? "") as! String // body
        // default badge value == 1
        var badge: Int = (data["badge"] as? Int) ?? 1
        // if there is duplicated notification, badge count is same as before
        if let _: UNNotification = getDuplicatedNotification(data: deliveredData, identifier: identifier) {
            badge = getDeliveredBadgeCount(data: deliveredData)
        } else {
            badge += getDeliveredBadgeCount(data: deliveredData)
        }
        let content = UNMutableNotificationContent()
        content.title = title
//        content.subtitle = subtitle
        content.body = body
        content.badge = badge as NSNumber
        content.sound = UNNotificationSound.default
        return content
    }
    
    // get duplicated notification data from notifications which have been sent and not read(== delivered notifications).
    @objc func getDuplicatedNotification(data: [UNNotification], identifier: String) -> UNNotification? {
        let duplicatedNotifications: [UNNotification?] = data.filter { (noti: UNNotification) -> Bool in
            let notificationRequest = noti.value(forKey: "request") as! UNNotificationRequest
            if let notificationIdentifier: String = notificationRequest.value(forKey: "identifier") as? String {
                return notificationIdentifier == identifier
            } else {
                return false
            }
        }
        if duplicatedNotifications.count > 0 {
            if let result: UNNotification = duplicatedNotifications[0] {
                return result
            }
        }
        return nil
    }
    
    // get count of delivered notifications
    @objc func getDeliveredBadgeCount(data: [UNNotification]) -> Int {
        return data.count
    }
    
    // show push notification to user
    @objc func showPushNotification(request: UNNotificationRequest) -> Void {
        // send push only inactive state
        let isActive = self.isApplicationActive()
        if !isActive {
            UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
        }
    }
    
    @objc func notifyToListeners(identifier: String, data: [String: Any]) -> Void {
        notifyListeners(identifier, data: data)
    }
    
    /**
     * get is application active ( return true when application is in foreground )
     */
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
        Messaging.messaging().token { token, error in
          if let error = error {
            call.error("Failed to get instance FirebaseID", error)
          } else if let token = token {
            call.success([
                "token": token
            ]);
          }
        }
    }

    @objc func addTimeFilter(_ call: CAPPluginCall) -> Void {
        // add timeFilter only when PluginCall has "startFrom" and "endAt"
        if let startFrom = call.getString("startFrom"), let endAt = call.getString("endAt") {
            handleResult(result: sqlHandler.insertTimeFilter(startFrom: startFrom, endAt: endAt), call)
        }
    }

    @objc func removeTimeFilter(_ call: CAPPluginCall) -> Void {
        handleResult(result: sqlHandler.removeTimeFilter(), call)
    }

    @objc func addFilters(_ call: CAPPluginCall) -> Void {
        guard let filters = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }

        for filter in filters {
            let result = isSucceedResult(result: sqlHandler.insertFilter(key: filter), call)
            if !result {
                return;
            }
        }
        call.success()
    }

    @objc func removeFilters(_ call: CAPPluginCall) -> Void {
        guard let filters = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }
        
        for filter in filters {
            let result = isSucceedResult(result:  sqlHandler.removeFilter(key: filter), call)
            if !result {
                return;
            }
        }
        call.success()
    }

    @objc func handleResult(result: [String: Any], _ call: CAPPluginCall) {
        if result["success"] as! Bool {
            call.success()
        } else {
            if let reason: String = result["reason"] as? String {
                call.reject(reason)
            } else {
                call.reject("Something when wrong")
            }
            
        }
    }
    
    @objc func isSucceedResult(result: [String: Any], _ call: CAPPluginCall) -> Bool {
        let boolResult = result["success"] as! Bool
        if !boolResult {
            if let reason: String = result["reason"] as? String {
                call.reject(reason)
            } else {
                call.reject("Something when wrong")
            }
        }
        return boolResult
    }
    
    /**
     * return current time only hours and minutes ( ex. HH:mm )
     */
    @objc func getCurrentTimeString() -> String {
        let date = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "ko_KR")
        dateFormatter.dateFormat = "HH:mm"
        return dateFormatter.string(from: date)
    }

    /**
     * return input time >= comparison time
     */
    @objc func compareTimeString(_ input: String, _ comparison: String) -> Bool {
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "ko_KR")
        dateFormatter.dateFormat = "HH:mm"
        return dateFormatter.date(from: input)! >= dateFormatter.date(from: comparison)!
    }

    @objc func isValidTime() -> Bool {
        sqlHandler.createFilterTable()
        let currentTime = getCurrentTimeString()
        let timeFilters = sqlHandler.getTimeFilter()

        func getFilteredData(filterString: String) -> Array<[String: Any]> {
            return timeFilters.filter { (filter: [String: Any]) -> Bool in
                if let key: String = filter["key"] as? String {
                    return key == filterString
                }
                return false
            }
        }
        let startTimeFilter: Array<[String: Any]> = getFilteredData(filterString: "filter_start_from")
        let endTimeFilter: Array<[String: Any]> = getFilteredData(filterString: "filter_end_at")
        let isTimeFilterOnObject: Array<[String: Any]> = getFilteredData(filterString: "is_time_filter_on")

        // if there is no time filter or filterOn == false -> return true (can receive push-noti anytime)
        if isTimeFilterOnObject.count >= 0 {
            if let timeFilterOn: String = isTimeFilterOnObject[0]["value"] as? String {
                if timeFilterOn == "false" {
                    return true
                }
            }
        }
        if startTimeFilter.count != 0 && endTimeFilter.count != 0 {
            guard let startFrom: String = startTimeFilter[0]["value"] as? String,
                  let endAt: String = endTimeFilter[0]["value"] as? String else {
                return false
            }
            return compareTimeString(currentTime, startFrom) && compareTimeString(endAt, currentTime)
        } else {
            return true
        }
    }

    @objc func isValidCondition(_ remoteMessageData: [String: Any]) -> Bool {
        guard let filterString: String = remoteMessageData["filter"] as? String else {
            // return true if there is no filter
            return true
        }
        
        let filterList: Array<String> = processFilterString(filterString: filterString)
        sqlHandler.createFilterTable()
        let savedFilters: Array<[String: Any]> = sqlHandler.getFilters()
        let matchedFilters = savedFilters.filter { (filter: [String: Any]) -> Bool in
            if let key = filter["key"] as? String {
                return filterList.contains(key)
            } else {
                return false
            }
            
        }
        for matchedFilter in matchedFilters {
            if let value = matchedFilter["value"] as? String {
                return value == "true"
            }
        }
        return true
    }

    @objc func checkMessageCondition(remoteMessageData: [String: Any]) -> Bool {
        return shouldMessageShown(remoteMessageData) && isValidTime() && isValidCondition(remoteMessageData)
    }

    @objc func processFilterString(filterString: String) -> Array<String> {
        var mutableFilterString: String = filterString
        if (mutableFilterString.hasSuffix(",")) {
            let endIndex: String.Index = mutableFilterString.index(mutableFilterString.startIndex, offsetBy: mutableFilterString.count - 1)
            mutableFilterString = String(mutableFilterString[...endIndex])
        }
        return mutableFilterString.split(separator: ",").map({ (value: Substring) -> String in
            return String(value.trimmingCharacters(in: .whitespacesAndNewlines))
        })
    }
    
    @objc func shouldMessageShown(_ remoteMessageData: [String: Any]) -> Bool {
        if let messageShown: String = remoteMessageData["is_shown"] as? String {
            return messageShown == "true"
        } else {
            return true
        }
    }
}
