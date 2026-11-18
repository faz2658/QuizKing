import { Observable, Subscription } from 'rxjs';
import { MultiplayerApi } from './api';
import { MatchView } from './models';

/** Where phones join a room quiz: what the QR code on the big screen holds. */
export function liveJoinUrl(origin: string, code: string): string {
  return `${origin}/live/${encodeURIComponent(code)}`;
}

/** What people type: any case, spaces around, or just the four digits of KING-1234 (same rule as the server). */
export function normalizeRoomCode(input: string): string {
  const c = input.trim().toUpperCase().replace(/\s+/g, '');
  return /^\d{4}$/.test(c) ? `KING-${c}` : c;
}

/** 1st, 2nd, 3rd, 4th, … 11th, 12th, 13th, 21st, … */
export function ordinal(n: number): string {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`;
}

/** Each option's share of the answers, in whole percent (0 when nobody answered). */
export function shares(counts: readonly number[]): number[] {
  const total = counts.reduce((a, b) => a + b, 0);
  return counts.map((c) => (total === 0 ? 0 : Math.round((c * 100) / total)));
}

/** 12,450 */
export function points(n: number): string {
  return n.toLocaleString('en-US');
}

/**
 * The match as it changes, through the server's long poll: each request waits until there is something new for this
 * viewer, so screens and phones update at once without asking every second. A dropped connection is retried after
 * {@code retryMs}; the stream completes when the match is over.
 */
export function followMatch(api: MultiplayerApi, matchId: string, userId: number, retryMs = 2000): Observable<MatchView> {
  return new Observable<MatchView>((out) => {
    let stopped = false;
    let inflight: Subscription | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    const ask = (after?: string) => {
      if (stopped) return;
      inflight = api.nextMatch(matchId, userId, after).subscribe({
        next: (m) => {
          out.next(m);
          if (m.status === 'COMPLETED') {
            stopped = true;
            out.complete();
          } else {
            // a reply can arrive synchronously (tests): ask again on the next tick, not inside this one
            setTimeout(() => ask(m.version), 0);
          }
        },
        error: () => {
          retry = setTimeout(() => ask(after), retryMs);
        },
      });
    };
    ask();
    return () => {
      stopped = true;
      clearTimeout(retry);
      inflight?.unsubscribe();
    };
  });
}
