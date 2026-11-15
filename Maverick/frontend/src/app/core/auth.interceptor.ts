import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Adds the bearer token to API calls, and sends the player back to sign in when it is no longer accepted. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  if (token && req.url.startsWith('/api/')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req).pipe(
    catchError((err: unknown) => {
      // 401 from anything but the sign-in calls themselves: the token expired or the server's secret changed
      if (err instanceof HttpErrorResponse && err.status === 401 && token && !req.url.startsWith('/api/v1/auth/')) {
        auth.logout();
        router.navigate(['/welcome'], { queryParams: { next: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
