# capacitor-notification-extensions  
Github link: https://github.com/no-dap/capacitor-notification-extensions  
Capacitor plugin with some features  
>- Background-handled data notification  
>- Client-side notification filtering (SQLite based)  
>    - Custom boolean filters  
>    - Time based filters  
>- Force-fire `localNotificationReceived` event listener which is not working properly  
>  in LocalNotification plugin  
>
  
Those features all works fine irrelevant with the app's state(on foreground, on background, or not on process).

# Dependency
![maintained](https://img.shields.io/badge/maintained-yes-green.svg?style=plastic)
![license](https://img.shields.io/badge/license-MIT-green?style=plastic)
![ionic-capacitor](https://img.shields.io/badge/capacitor-2.x-blue.svg?style=plastic)  
Works fine with Capacitor 2.x  
Not compatible with Capacitor 3.x  
Use SQLite via Helper(from [capacitor sqlite plugin](https://github.com/capacitor-community/sqlite))  

# Installation
```bash
npm i capacitor-notification-extensions --save
```
## Android
1. Add NotificationExtension class to your MainActivity.
```java
public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Initializes the Bridge
    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      add(YourAwesomePlugins.class);
      ...
      add(NotificationExtension.class);
      add(LocalNotificationExtension.class);
    }});
  }
}
```
2. Add meta data and intent filter to manifest inside application tag and add db_name to string values.
- AndroidManifest.xml
```xml
<?xml version='1.0' encoding='utf-8'?>
<manifest>
  <application...>
    ...
    <activity ...>
      <intent-filter>
        <action android:name="com.woot.notification.extensions.intent.action.Launch" />
        <category android:name="android.intent.category.DEFAULT" />
      </intent-filter>
    </activity>
    <meta-data android:name="com.woot.notification.extensions.local_database_name" android:value="@string/db_name" />
  </application>
</manifest>
```
- strings.xml
```xml
<?xml version='1.0' encoding='utf-8'?>
<resources>
  <string name="db_name">TODO</string>
</resources>
```

## iOS
1. Add silent notification to your AppDelegate
```swift
class AppDelegate: UIResponder, UIAppicationDelegate {
  ...
  func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable : Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
    NotificationCenter.default.post(name: Notification.Name("SilentNotification"), object: nil, userInfo: userInfo)
  }
  ...
}
```
2. Add some data to your Info.plist
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN">
<plist version="1.0">
<dict>
    ...
    <key>LocalDatabase</key>
    <string>TODO</string>
    ...
    <key>UIBackgroundModes</key>
    <array>
        <string>remote-notification</string>
    </array>
    ...
</dict>
</plist>
```

## Electron
Have no plans to support yet.

# Documentation
## [NotificationExtension](https://github.com/no-dap/capacitor-notification-extensions/blob/master/src/definitions.d.ts)  
>If you are new on firebase messaging, I recommend [reading this documentation](https://firebase.google.com/docs/cloud-messaging/concept-options) first to understand about two concepts of notification message.  
> 
NotificationExtension class is child of default plugin PushNotification. You can check arguments and return of methods from the link above.  
This plugin creates a sqlite table `notification_extensions_filter` with its own schema.

### Data notification payload
Message in both platform shouldn't contain `notification` key which makes message as alert message.
- Android  
    Get data from `yourMessagePayload.data`. [(Check how payload parsed)](https://github.com/no-dap/capacitor-notification-extensions/blob/master/android/src/main/java/com/woot/notification/extensions/FirebaseMessagingService.kt#L13)
- iOS  
    Get data from `yourMessagePayload.apns.payload.aps.custom_data`. [(Check how payload parsed)](https://github.com/no-dap/capacitor-notification-extensions/blob/master/ios/Plugin/Plugin.swift#L53)  
  
  
---
Both platforms' payload should contain keys below.
- isShown: Optional, boolean string('true' or 'false'), always true if not exists.
- body: Optional, string, body of notification message
- title: Optional, string, title of notification message
- filter: Optional, comma-separated string, hide notification if matched filter with false value exists.
- Any other data you that want to use in your application

### Filters
All key-value based filters which is added or removed by addFilters and removeFilters method will be saved in the local database.  
There are two filters that specially checks before show notification, which is time-based filter and logged-in filter.  
- logged-in filter  
    If you add a filter with key `is_logged_in`, this filter will always be checked on a message received even if payload doesn't contain filter key.  
- time-based filter
    If you add a filter with addTimeFilter method, three rows will be generated in the local database. (filter_start_from, filter_end_at, is_time_filter_on)  
    This filter will always be checked on a message received even if payload doesn't contain filter key.  
    addTimeFilter method only takes string with `HH:mm` format and will raise some validation error if is malformed.

### Usage
In your js application,  
```typescript
import { Plugins } from '@capacitor/core';

const { NotificationExtension } = Plugins

NotificationExtension.addListener('pushNotificationActionPerformed', (notification: PushNotificationActionPerformed) => {
  // Same as PushNotification plugin
  // You can deal with your payload 
});

NotificationExtension.addListener('pushNotificationReceived', (notification: YourPayloadType) => {
  // Same as PushNotification plugin
  // Only works when the app is on foreground
});

NotificationExtension.register();

NotificationExtension.addFilters({ filters: ['anyString', 'youPromised', 'withBackend'] });
// Any data notification that contains key named 'filter' with value matched above will be suppressed by plugin.

NotificationExtension.removeFilters({ filters: ['youPromised'] });
// filtering 'youPromised' stop working

NotificationExtension.getFilters().then((result) => {
  // result will be { value: ['anyString', 'withBackend'] }
});


NotificationExtension.addTimeFilter({ startFrom: '23:00', endAt: '07:00' });
// every notification that received between 11PM to 7AM will be suppressed
```

## LocalNotificationExtension  
`LocalNotification.addListener('localNotificationReceived')` doesn't work in android if you use capacitor 2.x,  
this class just a bug fix that only overrides a receiver to notify a message received event to listener.  
(This will be solved in capacitor 3.x, [merged commit link](https://github.com/ionic-team/capacitor-plugins/pull/217/commits/a499ddf4f8729119550c55f9c44549d29cf544f4))
Use this plugin for android only. (This class hasn't implemented for iOS)  

### Usage
```typescript
import { LocalNotification, Plugins } from '@capacitor/core';

const { LocalNotifications, LocalNotificationExtension } = Plugins;

class YourServiceOrComponent {
  constructor() {}
  
  get localNotificationPlugin() {
    return this.platform.is('android') ? LocalNotificationExtension : LocalNotifications;
  }
  
  yourMethod() {
    this.LocalNotificationPlugin.addListener('localNotificationReceived', (localNotification: LocalNotification) => {
      // Do what you want
    });
  }
}
```

# Issue & Questionnaire
Feel free to ask anything on project issues. Any kind of contributions and bug reports are also welcomed.
