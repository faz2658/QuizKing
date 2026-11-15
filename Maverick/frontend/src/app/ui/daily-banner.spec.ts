import { describe, expect, it } from 'vitest';
import { untilNext } from './daily-banner';

describe('untilNext', () => {
  it('shows hours and minutes, or just minutes in the last hour', () => {
    expect(untilNext(5 * 3600 + 7 * 60 + 30)).toBe('5h 7m');
    expect(untilNext(59 * 60)).toBe('59m');
    expect(untilNext(0)).toBe('0m');
  });
});
