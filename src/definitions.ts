import { PushNotificationsPlugin } from '@capacitor/core';
import {
  PushNotification,
  PushNotificationActionPerformed,
  PushNotificationToken,
} from '@capacitor/core/dist/esm/core-plugin-definitions';
import { PluginListenerHandle } from '@capacitor/core/dist/esm/definitions';

declare module '@capacitor/core' {
  interface PluginRegistry {
    NotificationExtension: NotificationExtensionPlugin;
  }
}

export interface NotificationExtensionPlugin extends PushNotificationsPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  /**
   * @deprecated Use getToken() instead.
   */
  addListener(eventName: 'registration', listenerFunc: (token: PushNotificationToken) => void): PluginListenerHandle;
  addListener(eventName: 'registrationError', listenerFunc: (error: any) => void): PluginListenerHandle;
  addListener(eventName: 'pushNotificationReceived', listenerFunc: (notification: PushNotification) => void): PluginListenerHandle;
  addListener(eventName: 'pushNotificationActionPerformed', listenerFunc: (notification: PushNotificationActionPerformed) => void): PluginListenerHandle;
  /**
   * Get FCM token from client with promise.
   * Use this method to get token rather than waiting 'registration' event listener
   * to control the procedure of initializing application.
   */
  getToken(): Promise<{ value: string }>
  /**
   * Save the range of time to SQLite to filtering push notification when it is received.
   * Push notification won't shown when current time is out of range.
   * @param options
   */
  addTimeFilter(options: { startFrom: string, endAt: string }): Promise<void>
  /**
   * Save the key - boolean field to SQLite to filtering push notification when it is received.
   * Push notification won't shown when any of the value from matched keys is false.
   * Extra is unique together with key, for more detail up filtering.
   * @param options
   */
  addFilter(options: { key: string, value: boolean, extra?: string }): Promise<void>
}
