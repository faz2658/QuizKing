import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MultiplayerApi } from './api';
import { MatchView } from './models';
import { followMatch, liveJoinUrl, normalizeRoomCode, ordinal, points, shares } from './room-quiz';

const view = (over: Partial<MatchView> = {}): MatchView => ({
  matchId: 'q1', gameId: 0, gameName: 'Room quiz', status: 'IN_PROGRESS', totalQuestions: 20, pointsPerQuestion: 1000,
  secondsPerQuestion: 20, standings: [], currentQuestion: null, lastQuestion: null, version: 'v1', ...over,
});

describe('room quiz helpers', () => {
  it('builds the short join link the QR code holds', () => {
    expect(liveJoinUrl('https://quizcadia.com', 'KING-4821')).toBe('https://quizcadia.com/live/KING-4821');
  });

  it('accepts codes the way people type them', () => {
    expect(normalizeRoomCode(' king-4821 ')).toBe('KING-4821');
    expect(normalizeRoomCode('4821')).toBe('KING-4821');
    expect(normalizeRoomCode('48 21')).toBe('KING-4821');
    expect(normalizeRoomCode('')).toBe('');
  });

  it('says ranks in English', () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111].map(ordinal)).toEqual(
      ['1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd', '23rd', '101st', '111th'],
    );
  });

  it('turns vote counts into shares', () => {
    expect(shares([3, 1, 0, 0])).toEqual([75, 25, 0, 0]);
    expect(shares([0, 0])).toEqual([0, 0]);
    expect(points(12450)).toBe('12,450');
  });
});

describe('followMatch', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('asks again with the last version until the match is over', async () => {
    const calls: (string | undefined)[] = [];
    const replies = [view({ version: 'a' }), view({ version: 'b', phase: 'REVEAL' }), view({ version: 'c', status: 'COMPLETED' })];
    const api = { nextMatch: (_m: string, _u: number, after?: string) => { calls.push(after); return of(replies[calls.length - 1]); } } as unknown as MultiplayerApi;
    const seen: string[] = [];
    let done = false;

    followMatch(api, 'q1', 7).subscribe({ next: (m) => seen.push(m.version!), complete: () => (done = true) });
    await vi.runAllTimersAsync();

    expect(calls).toEqual([undefined, 'a', 'b']);
    expect(seen).toEqual(['a', 'b', 'c']);
    expect(done).toBe(true);
  });

  it('retries a dropped connection with the same version', async () => {
    const calls: (string | undefined)[] = [];
    let fail = true;
    const api = {
      nextMatch: (_m: string, _u: number, after?: string) => {
        calls.push(after);
        if (calls.length === 1) return of(view({ version: 'a' }));
        if (fail) { fail = false; return throwError(() => new Error('offline')); }
        return new Subject<MatchView>(); // waits
      },
    } as unknown as MultiplayerApi;

    const sub = followMatch(api, 'q1', 7, 1000).subscribe();
    await vi.advanceTimersByTimeAsync(10);
    expect(calls).toEqual([undefined, 'a']);
    await vi.advanceTimersByTimeAsync(1000);
    expect(calls).toEqual([undefined, 'a', 'a']);
    sub.unsubscribe();
  });
});
