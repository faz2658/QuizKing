import { HttpErrorResponse } from '@angular/common/http';
import { Observable, retry, throwError, timer } from 'rxjs';

/** Human-readable message from an HTTP error (RFC 9457 problem details when the backend sent one). */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'The server is not reachable. Check that the Quiz King services are running.';
    }
    const detail = (err.error as { detail?: string } | null)?.detail;
    if (detail) {
      return detail;
    }
  }
  return fallback;
}

/**
 * Retries while the backend says "not ready yet" (503, or 404 with a retry hint) — e.g. a game that is
 * still being fetched from the game manager, or a match the engine hasn't created yet.
 */
export function retryWhileLoading<T>(maxAttempts = 8): (source: Observable<T>) => Observable<T> {
  return (source) =>
    source.pipe(
      retry({
        count: maxAttempts,
        delay: (err: unknown) => {
          if (err instanceof HttpErrorResponse) {
            const hint = (err.error as { retryAfterSeconds?: number } | null)?.retryAfterSeconds;
            if (err.status === 503 || (err.status === 404 && hint)) {
              return timer((hint ?? 2) * 1000);
            }
          }
          return throwError(() => err);
        },
      }),
    );
}
