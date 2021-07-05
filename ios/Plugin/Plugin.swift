import Foundation
import Capacitor
import FirebaseMessaging

extension Date {
    var year: Int {
        return Calendar.current.component(.year, from: self)
    }
    var month: Int {
        return Calendar.current.component(.month, from: self)
    }
    var day: Int {
        return Calendar.current.component(.day, from: self)
    }
    var hour: Int {
        return Calendar.current.component(.hour, from: self)
    }
    var minute: Int {
        return Calendar.current.component(.minute, from: self)
    }
    // change date to local timezone string
    func toLocaleString(from date: Date? = nil) -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        dateFormatter.locale = Locale.current
        return dateFormatter.string(from: date ?? Date())
    }
}
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NotificationExtension)
public class NotificationExtension: CAPPushNotificationsPlugin {
    
    let sqlHandler = SQLiteHandler()

    // add observer for SilentNotification. onNotification() will be called when notification is arrived
    public override func load() {
        super.load()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(onNotification(_:)),
            name: Notification.Name("SilentNotification"),
            object: nil
        )
    }
    
    // observer callback (SilentNotification)
    @objc func onNotification(_ notification: Notification) {
        handleNotification(notification: notification)
    }
    

    @objc func handleNotification(notification: Notification) -> Void {
        let isLoggedIn: Bool = sqlHandler.isLoggedIn()
        if (!isLoggedIn) {
            return
        }
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
                // set 3 seconds delay. 인터벌 안줬을 시, 씹히는 현상 때문에
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
                let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
                
                self.showPushNotification(request: request)
            }
        }
    }
    
    // get push code
    @objc func getIdentifier(_ data: [String: Any]) -> String? {
        if let identifier: String = data["code"] as? String {
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
        content.userInfo = data
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
        DispatchQueue.main.async {
            // send push only inactive state
            let isActive = self.isApplicationActive()
            if !isActive {
                UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
            }
        }
    }
    
    @objc func notifyToListeners(identifier: String, data: [String: Any]) -> Void {
        DispatchQueue.main.async {
            let isActive: Bool = self.isApplicationActive()
            if let isShown: String = data["isShown"] as? String {
                // notify to listeners if application is active or "isShown" is false
                if isActive || isShown == "false" {
                    self.notifyListeners(identifier, data: data)
                }
            }
        }
    }
    
    /**
     * get is application active ( return true when application is in foreground )
     */
    @objc func isApplicationActive() -> Bool {
        switch UIApplication.shared.applicationState {
            case .active:
                return true
            default:
                return false
        }
    }
    
    @objc func getToken(_ call: CAPPluginCall) {
        Messaging.messaging().token { token, error in
          if let error = error {
            call.error("Failed to get instance FirebaseID", error)
          } else if let token = token {
            call.success([
                "value": token
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
        guard let filters: [String] = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }

        for filter in filters {
            let result: Bool = isSucceedResult(result: sqlHandler.insertFilter(key: filter), call)
            if !result {
                return;
            }
        }
        call.success()
    }

    @objc func removeFilters(_ call: CAPPluginCall) -> Void {
        guard let filters: [String] = call.getArray("filters", String.self) else {
            call.reject("filters undefined")
            return;
        }
        
        for filter in filters {
            let result: Bool = isSucceedResult(result:  sqlHandler.removeFilter(key: filter), call)
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
    
    // processing after database DML
    @objc func isSucceedResult(result: [String: Any], _ call: CAPPluginCall) -> Bool {
        let boolResult: Bool = result["success"] as! Bool
        if !boolResult {
            if let reason: String = result["reason"] as? String {
                call.reject(reason)
            } else {
                call.reject("Database quering Error: Something went wrong")
            }
        }
        return boolResult
    }
    
    /**
     * extract default date from HH:mm string
     */
    @objc func extractDefaultDate(data: String) -> Date? {
        let splittedData = data.components(separatedBy: ":")
        if splittedData.count != 2 {
            return nil
        }
        let calendar = Calendar.current
        let dateComponent = DateComponents(
            year: 2021,
            hour: Int(splittedData[0]),
            minute: Int(splittedData[1])
        )
        return calendar.date(from: dateComponent)!
    }

    /**
     * if currentDate is between startFrom and endAt, return false
     */
    @objc func compareDate(_ startFrom: String, _ endAt: String) -> Bool {
        let date = Date()
        let calendar = Calendar.current
        guard let startFromDate: Date = extractDefaultDate(data: startFrom),
              var currentDate: Date = extractDefaultDate(data: "\(date.hour):\(date.minute)"),
              var endAtDate: Date = extractDefaultDate(data: endAt) else {
            return false;
        }

        if startFromDate > endAtDate {
            endAtDate = calendar.date(byAdding: .day, value: 1, to: endAtDate)!
            // if current is lower than start, add 1 day to currentDate
            if (startFromDate > currentDate) {
                currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
            }
        }
        return !((startFromDate < currentDate) && (currentDate < endAtDate))
    }

    // check current time is between start and end
    @objc func isValidTime() -> Bool {
        sqlHandler.createFilterTable()
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
        if isTimeFilterOnObject.count > 0 {
            if let timeFilterOn: String = isTimeFilterOnObject[0]["value"] as? String {
                if timeFilterOn == "false" {
                    return true
                }
            }
        }
        if startTimeFilter.count > 0 && endTimeFilter.count > 0 {
            guard let startFrom: String = startTimeFilter[0]["value"] as? String,
                  let endAt: String = endTimeFilter[0]["value"] as? String else {
                return false
            }
            return compareDate(startFrom, endAt)
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
        let matchedFilters: Array<[String: Any]> = savedFilters.filter { (filter: [String: Any]) -> Bool in
            if let key = filter["key"] as? String {
                return filterList.contains(key)
            } else {
                return false
            }
            
        }
        
        for matchedFilter in matchedFilters {
            if let value: String = matchedFilter["value"] as? String {
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
    
    // check should show or not the push message
    @objc func shouldMessageShown(_ remoteMessageData: [String: Any]) -> Bool {
        if let messageShown: String = remoteMessageData["isShown"] as? String {
            return messageShown == "true"
        } else {
            return true
        }
    }

    @objc func getFilters(_ call: CAPPluginCall) -> Void {
        let allFilters: Array<[String: Any]> = sqlHandler.getAllFilters()
        call.success(["value": allFilters])
    }
}
