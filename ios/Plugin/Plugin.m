#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(NotificationExtension, "NotificationExtension",
           CAP_PLUGIN_METHOD(getToken, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(addTimeFilter, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeTimeFilter, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(addFilters, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeFilters, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(register, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(requestPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getDeliveredNotifications, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeDeliveredNotifications, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeAllDeliveredNotifications, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getFilters, CAPPluginReturnPromise);
)
