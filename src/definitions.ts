declare module '@capacitor/core' {
  interface PluginRegistry {
    NotificationExtension: NotificationExtensionPlugin;
  }
}

export interface NotificationExtensionPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
