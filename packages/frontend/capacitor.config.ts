import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.werewolf.game',
  appName: 'WerewolfGame',
  webDir: 'dist',
  server: {
    androidScheme: 'http'
  }
};

export default config;
