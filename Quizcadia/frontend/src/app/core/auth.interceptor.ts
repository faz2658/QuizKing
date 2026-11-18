import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Sign-in calls: they make or end a session themselves, so they are never renewed or retried. */
const SESSION_CALL = /^\/api\/v1\/auth\/(login|register|guest|google|password\/|refresh|logout)/;

/**
 * Adds the bearer token to API calls. The token is short-lived: it is renewed from the refresh cookie just before
 * it runs out, and once more when a call comes back 401. Only when the server says the sign-in has ended is the
 * player signed out and sent to sign in; a server that can't be reached leaves them signed in.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const withToken = (r: HttpRequest<unknown>, token: string | null) =>
    token ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : r;
  if (SESSION_CALL.test(req.url)) {
    return next(withToken(req, auth.token()));
  }

  const ended = (err: unknown): Observable<never> => {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      auth.endLocally();
      router.navigate(['/welcome'], { queryParams: { next: router.url } });
    }
    return throwError(() => err);
  };
  const renewed = (): Observable<string> => auth.refresh().pipe(map((t) => t.token), catchError(ended));

  const token$: Observable<string | null> = auth.isLoggedIn() && auth.expiresSoon() ? renewed() : of(auth.token());
  return token$.pipe(
    switchMap((token) =>
      next(withToken(req, token)).pipe(
        catchError((err: unknown) => {
          // 401 with a token: it ran out early or the server's secret changed. Renew once and try again.
          if (err instanceof HttpErrorResponse && err.status === 401 && token) {
            return renewed().pipe(switchMap((fresh) => next(withToken(req, fresh))));
          }
          return throwError(() => err);
        }),
      ),
    ),
  );
};
