import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { livePoll } from './live-poll';

describe('livePoll', () => {
  let hidden = false;
  beforeEach(() => {
    vi.useFakeTimers();
    hidden = false;
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  });
  afterEach(() => vi.useRealTimers());

  const setHidden = (h: boolean) => {
    hidden = h;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  it('asks at once, then every period, and slows down while nothing changes', () => {
    let n = 0;
    const seen: number[] = [];
    const sub = livePoll(() => of({ same: true }), { every: 1000, max: 3000 }).subscribe(() => seen.push(++n));
    expect(n).toBe(1); // the first request goes out at once
    vi.advanceTimersByTime(1000); // unchanged answer: next gap 1500
    expect(n).toBe(2);
    vi.advanceTimersByTime(1000);
    expect(n).toBe(2);
    vi.advanceTimersByTime(500);
    expect(n).toBe(3);
    vi.advanceTimersByTime(20_000); // capped at 3000 ms between requests
    expect(n).toBeLessThanOrEqual(3 + Math.ceil(20_000 / 3000) + 1);
    sub.unsubscribe();
  });

  it('goes back to full speed as soon as the answer changes', () => {
    let v = 0;
    let n = 0;
    const sub = livePoll(() => of(v), { every: 1000, max: 4000 }).subscribe(() => n++);
    vi.advanceTimersByTime(0);
    vi.advanceTimersByTime(1000);
    vi.advanceTimersByTime(1500); // slowed: 3 requests so far
    expect(n).toBe(3);
    v = 1; // something changes on the server
    vi.advanceTimersByTime(2250);
    expect(n).toBe(4);
    vi.advanceTimersByTime(1000); // changed answer: back to every 1000 ms
    expect(n).toBe(5);
    sub.unsubscribe();
  });

  it('stops while the tab is hidden and asks again straight away when it comes back', () => {
    let n = 0;
    const sub = livePoll(() => of(Math.random()), { every: 1000 }).subscribe(() => n++);
    vi.advanceTimersByTime(0);
    expect(n).toBe(1);
    setHidden(true);
    vi.advanceTimersByTime(60_000);
    expect(n).toBe(1); // nothing sent while hidden
    setHidden(false);
    vi.advanceTimersByTime(0);
    expect(n).toBe(2);
    sub.unsubscribe();
  });

  it('never sends two requests at once', () => {
    const replies = new Subject<number>();
    let calls = 0;
    const sub = livePoll(() => (calls++, replies), { every: 100 }).subscribe();
    vi.advanceTimersByTime(5000); // the first request never finishes
    expect(calls).toBe(1);
    sub.unsubscribe();
  });
});
