import { Observable, Subscription } from 'rxjs';

export interface LivePollOptions {
  /** Normal gap between requests, in ms. */
  every: number;
  /** When answers stop changing, the gap grows (x1.5 each time) up to this; any change goes back to {@link every}. */
  max?: number;
  /** Wait before the first request (default 0: ask at once). */
  firstAfter?: number;
}

/**
 * Asks the server for something repeatedly, politely: never two requests at once, nothing while the tab is hidden
 * or the phone is locked (and straight away again when it's back), and less often while nothing changes. Errors end
 * the stream, like a plain HTTP call.
 */
export function livePoll<T>(fetch: () => Observable<T>, { every, max = every, firstAfter = 0 }: LivePollOptions): Observable<T> {
  return new Observable<T>((out) => {
    let delay = every;
    let last: string | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let inflight: Subscription | null = null;
    let busy = false; // separate from inflight: a reply can arrive synchronously, before subscribe() returns
    let stopped = false;
    const hidden = () => typeof document !== 'undefined' && document.hidden;

    const schedule = (ms: number) => {
      clearTimeout(timer);
      if (!stopped && !hidden()) timer = setTimeout(run, ms);
    };
    const run = () => {
      if (stopped || busy || hidden()) return;
      busy = true;
      inflight = fetch().subscribe({
        next: (v) => {
          const now = JSON.stringify(v);
          delay = now === last ? Math.min(max, Math.round(delay * 1.5)) : every;
          last = now;
          out.next(v);
        },
        error: (e) => {
          busy = false;
          out.error(e);
        },
        complete: () => {
          busy = false;
          schedule(delay);
        },
      });
    };
    const onVisibility = () => {
      if (hidden()) {
        clearTimeout(timer);
      } else {
        delay = every;
        schedule(0);
      }
    };

    document.addEventListener('visibilitychange', onVisibility);
    if (firstAfter > 0) schedule(firstAfter);
    else run(); // ask straight away, like startWith(0)
    return () => {
      stopped = true;
      clearTimeout(timer);
      inflight?.unsubscribe();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  });
}
