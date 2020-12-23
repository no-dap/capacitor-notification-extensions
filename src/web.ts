import { WebPlugin } from '@capacitor/core';
import { NotificationExtensionPlugin } from './definitions';

export class NotificationExtensionWeb extends WebPlugin implements NotificationExtensionPlugin {
  constructor() {
    super({
      name: 'NotificationExtension',
      platforms: ['web'],
    });
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

const NotificationExtension = new NotificationExtensionWeb();

export { NotificationExtension };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(NotificationExtension);
