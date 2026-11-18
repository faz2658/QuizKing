import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, tap, throwError, timer } from 'rxjs';
import { IDEMPOTENCY_HEADER } from './idempotency.interceptor';
import { Maintenance, isGatewayFailure } from './maintenance';

/** How many outages one request sits out before its error is passed on after all. */
const MAX_WAITS = 3;
/** A read that fails once, before the site is known to be down, is tried again after this long. */
const BLIP_RETRY_MS = 2_000;

/**
 * Watches /api calls for "the servers are restarting" failures. Once the site is in maintenance, reads (GET, HEAD)
 * wait for the servers to come back and are then sent again, so pages pick up where they were instead of showing an
 * error; a read that fails just once is tried again shortly. Writes are treated the same when they carry an
 * Idempotency-Key (all API writes do, see idempotency.interceptor.ts): the server answers a repeat with its first
 * answer, so an answer is never counted twice. A write without one is not repeated: its error goes to the page.
 */
export const maintenanceInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const maintenance = inject(Maintenance);
  const repeatable = req.method === 'GET' || req.method === 'HEAD' || req.headers.has(IDEMPOTENCY_HEADER);

  const send = (waits: number, blipRetried: boolean): Observable<HttpEvent<unknown>> =>
    next(req).pipe(
      tap((e) => {
        if (e instanceof HttpResponse) {
          maintenance.reportSuccess();
        }
      }),
      catchError((err: unknown) => {
        if (!isGatewayFailure(err)) {
          if (err instanceof HttpErrorResponse) {
            maintenance.reportSuccess(); // the server answered, even if with an error
          }
          return throwError(() => err);
        }
        const down = maintenance.reportFailure(req as HttpRequest<unknown>, err);
        if (!repeatable) {
          return throwError(() => err);
        }
        if (down && waits < MAX_WAITS) {
          return maintenance.back().pipe(switchMap(() => send(waits + 1, blipRetried)));
        }
        if (!down && !blipRetried) {
          // maybe a blip, maybe the start of an outage: one more try shortly tells which (a second failure turns
          // the banner on, and this request then waits for the servers like the others)
          return timer(BLIP_RETRY_MS).pipe(
            switchMap(() => maintenance.back()), // the banner came on meanwhile: wait for the servers
            switchMap(() => send(waits, true)),
          );
        }
        return throwError(() => err);
      }),
    );
  return send(0, false);
};
