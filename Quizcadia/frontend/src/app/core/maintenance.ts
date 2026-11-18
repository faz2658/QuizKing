import { HttpBackend, HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Observable, Subject, defer, of, take } from 'rxjs';

/** Gateway failures within this window, across all /api calls, switch the site into "under maintenance". */
const FAILURES_TO_TRIGGER = 2;
const FAILURE_WINDOW_MS = 20_000;
/** Seconds between health checks while down, unless the server says otherwise with Retry-After. */
const BACKOFF_SECONDS = [5, 10, 15, 20, 30];
const MAX_WAIT_SECONDS = 120;
/** Cheap call used when the failed request can't be repeated (a POST): any answer means nginx and the service are up. */
const DEFAULT_PROBE = '/api/game/categories';

/**
 * True for errors that mean "the servers are restarting" rather than "this request was wrong": no answer at all
 * (status 0), the proxy couldn't reach a service (502, 504), or the proxy's own maintenance answer (503 marked
 * `maintenance`). A plain 503 from a service ("game still loading") is not one: it is retried by retryWhileLoading.
 */
export function isGatewayFailure(err: unknown): err is HttpErrorResponse {
  if (!(err instanceof HttpErrorResponse)) {
    return false;
  }
  if (err.status === 0 || err.status === 502 || err.status === 504) {
    return true;
  }
  return err.status === 503 && isMaintenanceAnswer(err);
}

function isMaintenanceAnswer(err: HttpErrorResponse): boolean {
  if (err.headers?.get('X-Maintenance')) {
    return true;
  }
  const body = err.error as { maintenance?: boolean } | string | null;
  return typeof body === 'object' && body?.maintenance === true;
}

/** Seconds from a Retry-After header (seconds or an HTTP date), or the problem body's retryAfterSeconds. */
export function retryAfterSeconds(err: HttpErrorResponse, now = Date.now()): number | null {
  const header = err.headers?.get('Retry-After');
  if (header) {
    const secs = Number(header);
    if (Number.isFinite(secs) && secs >= 0) {
      return Math.round(secs);
    }
    const at = Date.parse(header);
    if (!Number.isNaN(at)) {
      return Math.max(0, Math.round((at - now) / 1000));
    }
  }
  const hint = (err.error as { retryAfterSeconds?: number } | null)?.retryAfterSeconds;
  return typeof hint === 'number' && hint >= 0 ? hint : null;
}

/**
 * Notices when the QuizCadia servers are down (a deploy restarting them, usually), shows the maintenance banner with
 * a countdown, checks with backoff until they answer again, then tells waiting requests to go ahead.
 */
@Injectable({ providedIn: 'root' })
export class Maintenance {
  private readonly backend = inject(HttpBackend);

  private readonly _active = signal(false);
  private readonly _secondsLeft = signal(0);
  private readonly _checking = signal(false);
  /** The site is down; the banner shows. */
  readonly active = this._active.asReadonly();
  /** Seconds until the next check. */
  readonly secondsLeft = this._secondsLeft.asReadonly();
  /** A check is in flight. */
  readonly checking = this._checking.asReadonly();

  private readonly back$ = new Subject<void>();
  /** Fires each time the servers answer again after being down. */
  readonly recovered: Observable<void> = this.back$.asObservable();

  private failures: number[] = [];
  private attempt = 0;
  private hint: number | null = null;
  private probeUrl = DEFAULT_PROBE;
  private ticker: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTicker());
  }

  /** A call to the API failed because the servers are unreachable. Returns whether the site is now in maintenance. */
  reportFailure(req: HttpRequest<unknown>, err: HttpErrorResponse): boolean {
    const hint = retryAfterSeconds(err);
    if (hint !== null) {
      this.hint = hint;
    }
    if (req.method === 'GET' || req.method === 'HEAD') {
      this.probeUrl = req.urlWithParams;
    }
    if (this._active()) {
      return true;
    }
    const now = Date.now();
    this.failures = [...this.failures.filter((t) => now - t < FAILURE_WINDOW_MS), now];
    if (this.failures.length >= FAILURES_TO_TRIGGER) {
      this.enter();
    }
    return this._active();
  }

  /** A call to the API got an answer: earlier failures were a blip. */
  reportSuccess(): void {
    if (!this._active()) {
      this.failures = [];
    }
  }

  /** Emits once the servers are back (at once if they never went away). */
  back(): Observable<void> {
    return defer(() => (this._active() ? this.back$.pipe(take(1)) : of(undefined)));
  }

  /** Checks right away instead of waiting for the countdown. */
  tryNow(): void {
    if (this._active() && !this._checking()) {
      this.check();
    }
  }

  private enter(): void {
    this._active.set(true);
    this.attempt = 0;
    this.schedule();
  }

  private schedule(): void {
    this.stopTicker();
    const wait = Math.min(MAX_WAIT_SECONDS, this.hint ?? BACKOFF_SECONDS[Math.min(this.attempt, BACKOFF_SECONDS.length - 1)]);
    this.hint = null;
    this.attempt++;
    this._secondsLeft.set(wait);
    if (wait === 0) {
      this.check();
      return;
    }
    this.ticker = setInterval(() => {
      const left = this._secondsLeft() - 1;
      this._secondsLeft.set(Math.max(0, left));
      if (left <= 0) {
        this.check();
      }
    }, 1000);
  }

  private check(): void {
    this.stopTicker();
    this._checking.set(true);
    // straight to the network, past the interceptors: no token, no retries, no counting this call as a failure
    this.backend.handle(new HttpRequest('HEAD', this.probeUrl)).subscribe({
      next: (e) => {
        if ('status' in e && e.status) {
          this.done(true);
        }
      },
      error: (err: unknown) => {
        // 401, 404, 405, a service's own 503...: an answer, so the servers are up
        if (err instanceof HttpErrorResponse && isGatewayFailure(err)) {
          const hint = retryAfterSeconds(err);
          if (hint !== null) {
            this.hint = hint;
          }
          this.done(false);
        } else {
          this.done(true);
        }
      },
    });
  }

  private done(up: boolean): void {
    if (!this._checking()) {
      return;
    }
    this._checking.set(false);
    if (!up) {
      this.schedule();
      return;
    }
    this._active.set(false);
    this._secondsLeft.set(0);
    this.failures = [];
    this.probeUrl = DEFAULT_PROBE;
    this.back$.next();
  }

  private stopTicker(): void {
    if (this.ticker) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
  }
}
