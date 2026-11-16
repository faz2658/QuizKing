import { describe, expect, it } from 'vitest';
import { levelFraction } from './progress-strip';

describe('levelFraction', () => {
  it('is how far the player is between this level and the next', () => {
    expect(levelFraction({ xp: 200, levelStartXp: 100, nextLevelXp: 300 })).toBe(0.5);
    expect(levelFraction({ xp: 100, levelStartXp: 100, nextLevelXp: 300 })).toBe(0);
    expect(levelFraction({ xp: 999, levelStartXp: 100, nextLevelXp: 300 })).toBe(1);
  });
});
