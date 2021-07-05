import Foundation
import Capacitor
import UserNotifications

typealias JSObject = [String:Any]

enum PushNotificationError: Error {
  case tokenParsingFailed
}

/**
 * Implement Push Notifications
 */
public class CAPPushNotificationsPlugin : CAPPlugin, UNUserNotificationCenterDelegate {
    // Local list of notification id -> JSObject for storing options
    // between notification requets
    var notificationRequestLookup = [String:JSObject]()
    // center.delegate = self 를 통해 UNUserNotificationCenter 프로토콜의 두개의 userNotificationCenter(~) 함수를 가져다 쓸 수 있다.
    let center = UNUserNotificationCenter.current()

    // add observer for remote notifications and failure
    public override func load() {
        center.delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(self.didRegisterForRemoteNotificationsWithDeviceToken(notification:)), name: Notification.Name(CAPNotifications.DidRegisterForRemoteNotificationsWithDeviceToken.name()), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(self.didFailToRegisterForRemoteNotificationsWithError(notification:)), name: Notification.Name(CAPNotifications.DidFailToRegisterForRemoteNotificationsWithError.name()), object: nil)
    }

    /**
    * Register for push notifications
    */
    @objc func register(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
          UIApplication.shared.registerForRemoteNotifications()
        }
        call.success()
    }

    /**
    * Request notification permission
    */
    @objc func requestPermission(_ call: CAPPluginCall) {
        self.bridge.notificationsDelegate.requestPermissions() { granted, error in
            guard error == nil else {
                call.error(error!.localizedDescription)
                return
            }
            call.success(["granted": granted])
        }
    }

    /**
    * Get notifications in Notification Center
    */
    @objc func getDeliveredNotifications(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().getDeliveredNotifications(completionHandler: { (notifications) in
          let ret = notifications.map({ (notification) -> [String:Any] in
            return self.makePushNotificationRequestJSObject(notification.request)
          })
          call.success([
            "notifications": ret
          ])
        })
    }

    /**
    * Remove specified notifications from Notification Center
    */
    @objc func removeDeliveredNotifications(_ call: CAPPluginCall) {
        guard let notifications = call.getArray("notifications", JSObject.self, []) else {
          call.error("Must supply notifications to remove")
          return
        }

        let ids = notifications.map { $0["id"] as? String ?? "" }

        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
        call.success()
    }

    /**
    * Remove all notifications from Notification Center
    */
    @objc func removeAllDeliveredNotifications(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        DispatchQueue.main.async(execute: {
          UIApplication.shared.applicationIconBadgeNumber = 0
        })
        call.success()
    }

    @objc func createChannel(_ call: CAPPluginCall) {
        call.unimplemented()
    }

    @objc func deleteChannel(_ call: CAPPluginCall) {
        call.unimplemented()
    }

    @objc func listChannels(_ call: CAPPluginCall) {
        call.unimplemented()
    }

    @objc public func didRegisterForRemoteNotificationsWithDeviceToken(notification: NSNotification){
        if let deviceToken = notification.object as? Data {
          let deviceTokenString = deviceToken.reduce("", {$0 + String(format: "%02X", $1)})
          notifyListeners("registration", data:[
            "value": deviceTokenString
          ])
        } else if let stringToken = notification.object as? String {
          notifyListeners("registration", data:[
            "value": stringToken
          ])
        } else {
          notifyListeners("registrationError", data: [
            "error": PushNotificationError.tokenParsingFailed.localizedDescription
          ])
        }
    }

    @objc public func didFailToRegisterForRemoteNotificationsWithError(notification: NSNotification){
        guard let error = notification.object as? Error else {
          return
        }
        notifyListeners("registrationError", data:[
          "error": error.localizedDescription
        ])
    }

    // MARK - : Below code is from CAPUNUserNotificationCenterDelegate.swift
    
    /**
     * Handle delegate willPresent action when the app is in the foreground.
     * This controls how a notification is presented when the app is running, such as
     * whether it should stay silent, display a badge, play a sound, or show an alert.
     */
    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                       willPresent notification: UNNotification,
                                       withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
      let request = notification.request
      var plugin: CAPPlugin
      var action = "localNotificationReceived"
      var presentationOptions: UNNotificationPresentationOptions = [];

      var notificationData = makeNotificationRequestJSObject(request)
      if (request.trigger?.isKind(of: UNPushNotificationTrigger.self) ?? false) {
        plugin = (self.bridge?.getOrLoadPlugin(pluginName: "PushNotifications"))!
        let options = plugin.getConfigValue("presentationOptions") as? [String] ?? ["badge"]

        action = "pushNotificationReceived"
        if options.contains("alert") {
          presentationOptions.update(with: .alert)
        }
        if options.contains("badge") {
          presentationOptions.update(with: .badge)
        }
        if options.contains("sound") {
          presentationOptions.update(with: .sound)
        }
        notificationData = makePushNotificationRequestJSObject(request)

      } else {
        plugin = (self.bridge?.getOrLoadPlugin(pluginName: "LocalNotifications"))!
        presentationOptions = [
          .badge,
          .sound,
          .alert
        ]
      }

      notifyListeners(action, data: notificationData)

      if let options = notificationRequestLookup[request.identifier] {
        let silent = options["silent"] as? Bool ?? false
        if silent {
          completionHandler(.init(rawValue:0))
          return
        }
      }

      completionHandler(presentationOptions)
    }

    /**
     * Handle didReceive action, called when a notification opens or activates
     * the app based on an action.
     */
    public func userNotificationCenter(_ center: UNUserNotificationCenter,
                                       didReceive response: UNNotificationResponse,
                                       withCompletionHandler completionHandler: @escaping () -> Void) {
      completionHandler()
      var data = JSObject()

      // Get the info for the original notification request
      let originalNotificationRequest = response.notification.request
      let actionId = response.actionIdentifier

      // We turn the two default actions (open/dismiss) into generic strings
      if actionId == UNNotificationDefaultActionIdentifier {
        data["actionId"] = "tap"
      } else if actionId == UNNotificationDismissActionIdentifier {
        data["actionId"] = "dismiss"
      } else {
        data["actionId"] = actionId
      }

      // If the type of action was for an input type, get the value
      if let inputType = response as? UNTextInputNotificationResponse {
        data["inputValue"] = inputType.userText
      }

      var action = "localNotificationActionPerformed"

      if (originalNotificationRequest.trigger?.isKind(of: UNPushNotificationTrigger.self) ?? false) {
        data["notification"] = makePushNotificationRequestJSObject(originalNotificationRequest)
        action = "pushNotificationActionPerformed"
      } else {
        data["notification"] = makeNotificationRequestJSObject(originalNotificationRequest)
      }

      notifyListeners(action, data: data, retainUntilConsumed: true)
    }

    /**
     * Turn a UNNotificationRequest into a JSObject to return back to the client.
     */
    func makeNotificationRequestJSObject(_ request: UNNotificationRequest) -> JSObject {
      let notificationRequest = notificationRequestLookup[request.identifier] ?? [:]
      return [
        "id": request.identifier,
        "title": request.content.title,
        "sound": notificationRequest["sound"]  ?? "",
        "body": request.content.body,
        "extra": request.content.userInfo,
        "actionTypeId": request.content.categoryIdentifier,
        "attachments": notificationRequest["attachments"]  ?? [],
      ]
    }

    /**
     * Turn a UNNotificationRequest into a JSObject to return back to the client.
     */
    func makePushNotificationRequestJSObject(_ request: UNNotificationRequest) -> JSObject {
      let content = request.content
      return [
        "id": request.identifier,
        "title": content.title,
        "subtitle": content.subtitle,
        "body": content.body,
        "badge": content.badge ?? 1,
        "data": content.userInfo,
      ]
    }
}
