import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withNavigationErrorHandler } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
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
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
