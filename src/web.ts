import {
  NotificationChannel,
  NotificationChannelList, NotificationPermissionResponse,
  PushNotificationDeliveredList,
  WebPlugin,
} from '@capacitor/core';
import { NotificationExtensionPlugin } from './definitions';

export class NotificationExtensionWeb extends WebPlugin implements NotificationExtensionPlugin {
  constructor() {
    super({
      name: 'NotificationExtension',
      platforms: ['web'],
    });
  }

  private static webNotImplemented() {
    console.error('Push Notification for web not implemented')
  }

  async getToken(): Promise<{ value: string }> {
    NotificationExtensionWeb.webNotImplemented();
    return { value: '' };
  }

  async getFilters(): Promise<Array<{[props: string]: string}>> {
    NotificationExtensionWeb.webNotImplemented();
    return [];
  }

  async addTimeFilter(options: { startFrom: string, endAt: string }): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async removeTimeFilter(): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async addFilters(options: { filters: string[] }): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async removeFilters(options: { filters: string[] }): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async createChannel(channel: NotificationChannel): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async deleteChannel(channel: NotificationChannel): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async getDeliveredNotifications(): Promise<PushNotificationDeliveredList> {
    NotificationExtensionWeb.webNotImplemented();
    return { notifications: [] }
  }

  async listChannels(): Promise<NotificationChannelList> {
    NotificationExtensionWeb.webNotImplemented();
    return { channels: [] }
  }

  async register(): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async removeAllDeliveredNotifications(): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async removeDeliveredNotifications(delivered: PushNotificationDeliveredList): Promise<void> {
    NotificationExtensionWeb.webNotImplemented();
  }

  async requestPermission(): Promise<NotificationPermissionResponse> {
    NotificationExtensionWeb.webNotImplemented();
    return { granted: false }
  }
}

const NotificationExtension = new NotificationExtensionWeb();

export { NotificationExtension };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(NotificationExtension);
