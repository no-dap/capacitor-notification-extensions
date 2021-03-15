# capacitor-notification-extensions  
Capacitor plugin with some features  
- Handle data notification  
- Client-side notification filtering (SQLite based)  
    - Custom boolean filters  
    - Time based filters  
- Force-fire `localNotificationReceived` event listener which is not working properly in LocalNotification plugin  

# Depedency
Works fine with Capacitor 2.x  
Not competible with Capacitor 3.x  
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
2. Add meta data to manifest inside application tag and add db_name to string values.
- AndroidManifest.xml
```xml
<?xml version='1.0' encoding='utf-8'?>
<manifest>
  <application ...>
    ...
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
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
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
```

## Electron
Have no plans to support for now.

# Documentation
## [NotificationExtension](https://github.com/no-dap/capacitor-notification-extensions/blob/master/src/definitions.d.ts)  
NotificationExtension class is child of default plugin PushNotification. You can check arguments and return of methods from the link above.  
This plugin create a sqlite table `notification_extensions_filter` with its own schema.

### Data notification payload
- Android
    Get data from `yourMessagePayload.data`. [(Check how payload parsed)](https://github.com/no-dap/capacitor-notification-extensions/blob/master/android/src/main/java/com/woot/notification/extensions/FirebaseMessagingService.kt#L13)
- iOS
    Get data from `yourMessagePayload.apns.payload.aps.custom_data`. [(Check how payload parsed)](https://github.com/no-dap/capacitor-notification-extensions/blob/master/ios/Plugin/Plugin.swift#L53)  
  
---
Both platforms' payload should contains keys below.
- isShown: Optional, boolean string('true' or 'false'), always true if not exists.
- body: Optional, string, body of notification message
- title: Optional, string, title of notification message
- filter: Optional, comma-separated string, hide notifification if matched filter with false value exists.
- Any other data you that want to use in your application

### Filters
All key-value based filters which is added or removed by addFilters and removeFilters method will be saved in local database.  
There are two filters that specially checks before show notification, which is time-based filter and logged-in filter.  
- logged-in filter  
    If you add filter with key `is_logged_in`, this filter will be always checked on message received even if payload doesn't contains filter key.  
- time-based filter
    If you add a filter with addTimeFilter method, three rows will be generated in local database. (filter_start_from, filter_end_at, is_time_filter_on)  
    This filter will be always check on message received even if payload doesn't contains filter key.  
    addTimeFilter method only 

### Usage
In your js application,  
```typescript
import { Plugins } from '@capacitor/core';

const { NotificationExtension } = Plugins

NotificationExtension.addListener('pushNotificationActionPerformed', (notification: PushNotificationActionPerformed) => {
  // Same as PushNotification plugin
});

NotificationExtension.addListener('pushNotificationReceived', (notification: YourPayloadType) => {
  // Same as PushNotification plugin
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
this class just a bug fix that only overrides a receiver to notify message received event to listener.  
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
Feel free to ask anything on project issues. Any kind of contribution and bug report also welcomed.
