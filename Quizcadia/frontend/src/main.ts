import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { moveOldStorageKeys } from './app/core/storage-rename';

moveOldStorageKeys(); // before any service reads its saved values

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));

// Installable app: register the service worker once the page has loaded (not during `ng serve`).
if ('serviceWorker' in navigator && location.port !== '4200') {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => console.warn('Service worker not registered', err));
  });
}
