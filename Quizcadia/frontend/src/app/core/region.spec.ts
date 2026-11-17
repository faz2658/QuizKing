import { describe, expect, it } from 'vitest';
import { CountrySet, homeScope, internationalSets, WORLD } from './region';

const set = (country: string, games = 2): CountrySet => ({ country, categoryId: 1, categoryName: country, games, questions: 30 });
const names: Record<string, string> = { IN: 'India', JP: 'Japan', BR: 'Brazil', GB: 'United Kingdom' };

describe('region', () => {
  const sets = [set('IN'), set('JP'), set('BR'), set('GB', 0)];

  it('shows players their own country\'s games, or world trivia when their country has no set yet', () => {
    expect(homeScope('IN', sets)).toBe('IN');
    expect(homeScope('jp', sets)).toBe('JP');
    expect(homeScope('FR', sets)).toBe(WORLD);
    expect(homeScope('GB', sets)).toBe(WORLD); // a set without games yet (just after deploy)
    expect(homeScope(null, sets)).toBe(WORLD);
    expect(homeScope('IN', [])).toBe(WORLD); // the list failed to load
  });

  it('offers every other country on the International page, by name', () => {
    expect(internationalSets('IN', sets, (c) => names[c]).map((s) => s.country)).toEqual(['BR', 'JP']);
    expect(internationalSets(WORLD, sets, (c) => names[c]).map((s) => s.country)).toEqual(['BR', 'IN', 'JP']);
  });
});
