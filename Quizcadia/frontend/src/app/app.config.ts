import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withNavigationErrorHandler } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { idempotencyInterceptor } from './core/idempotency.interceptor';
import { maintenanceInterceptor } from './core/maintenance.interceptor';
import { isMissingChunk, reloadIntoNewVersion } from './core/stale-version';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // a tab from before the last deploy can't load the new pages' code: reload into the new version there
      withNavigationErrorHandler((e) => {
        if (isMissingChunk(e.error)) {
          reloadIntoNewVersion(e.url);
        }
      }),
    ),
    // order matters: the key is added first, so every retry further along (renewed token, servers back) carries it
    provideHttpClient(withInterceptors([idempotencyInterceptor, authInterceptor, maintenanceInterceptor])),
  ],
};
