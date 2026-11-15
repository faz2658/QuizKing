import { describe, expect, it } from 'vitest';
import { Achievement } from '../core/models';
import { sortBadges } from './badge-grid';

const badge = (id: string, progress: number, goal: number, unlockedAt: string | null): Achievement => ({
  id, title: id, description: '', icon: '', progress, goal, unlockedAt,
});

describe('sortBadges', () => {
  it('lists earned badges newest first, then the closest locked ones', () => {
    const sorted = sortBadges([
      badge('far', 1, 10, null), badge('old', 1, 1, '2026-11-01T00:00:00Z'),
      badge('close', 4, 5, null), badge('new', 1, 1, '2026-11-14T00:00:00Z'),
    ]);
    expect(sorted.map((b) => b.id)).toEqual(['new', 'old', 'close', 'far']);
  });
});
