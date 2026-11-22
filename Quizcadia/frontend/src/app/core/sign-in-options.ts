import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';

/** What the server offers at sign-in. {@code passwordReset} is false until it can email reset links. */
export interface Providers {
  googleClientId: string | null;
  analyticsId?: string | null;
  passwordReset?: boolean;
}

/**
 * The server's sign-in options, fetched once per visit and shared: the Google button, analytics, the welcome and
 * forgot-password pages all need them (they used to fetch them separately). A failed request isn't kept, so the
 * next caller tries again.
 */
@Injectable({ providedIn: 'root' })
export class SignInOptions {
  private readonly http = inject(HttpClient);
  private cached?: Observable<Providers>;

  /** The sign-in options, fetched once and shared; a failed fetch is tried again next time. */
  get(): Observable<Providers> {
    this.cached ??= this.http.get<Providers>('/api/v1/auth/providers').pipe(
      catchError((e) => {
        this.cached = undefined;
        return throwError(() => e);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.cached;
  }
}
