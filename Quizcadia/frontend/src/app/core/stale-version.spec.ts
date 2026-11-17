import { describe, expect, it } from 'vitest';
import { isMissingChunk, reloadIntoNewVersion } from './stale-version';

function memory(): Pick<Storage, 'getItem' | 'setItem'> {
  const m = new Map<string, string>();
  return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => void m.set(k, v) };
}

describe('stale version after a deploy', () => {
  it('recognises a missing page chunk in every browser\'s wording, and nothing else', () => {
    expect(isMissingChunk(new TypeError('Failed to fetch dynamically imported module: https://quizcadia.com/chunk-3dRnBfET.js'))).toBe(true); // Chrome
    expect(isMissingChunk(new TypeError('Importing a module script failed.'))).toBe(true); // Safari
    expect(isMissingChunk(new TypeError('error loading dynamically imported module: https://quizcadia.com/chunk-x.js'))).toBe(true); // Firefox
    expect(isMissingChunk(new Error('Cannot read properties of undefined'))).toBe(false);
    expect(isMissingChunk(null)).toBe(false);
  });

  it('reloads at the page the player was going to, but never loops', () => {
    const storage = memory();
    const went: string[] = [];
    const go = (u: string) => went.push(u);

    expect(reloadIntoNewVersion('/puzzles', go, storage, 1_000_000)).toBe(true);
    expect(went).toEqual(['/puzzles']);
    // the new version is missing it too (something else is wrong): don't reload again straight away
    expect(reloadIntoNewVersion('/puzzles', go, storage, 1_005_000)).toBe(false);
    // a different page, or the same one much later, is fine
    expect(reloadIntoNewVersion('/leaderboard', go, storage, 1_006_000)).toBe(true);
    expect(reloadIntoNewVersion('/puzzles', go, storage, 1_100_000)).toBe(true);
    expect(went).toEqual(['/puzzles', '/leaderboard', '/puzzles']);
  });
});
