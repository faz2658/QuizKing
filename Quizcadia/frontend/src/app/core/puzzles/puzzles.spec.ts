import { describe, expect, it } from 'vitest';
import { BRAIN_TEASERS, CLUE_TRAILS, FOUR_BY_FOURS, LETTER_VAULTS } from './content';
import { clueTrailScore, findGroup, markGuess, matchesAnswer, oneAway, pick, puzzleDay, secondsUntilNextDay, seededShuffle } from './logic';
import { previousDay } from './progress';

describe('puzzle rules', () => {
  it('uses the UTC day and walks each list one entry per day', () => {
    expect(puzzleDay(new Date('2026-11-16T23:59:00Z'))).toBe('2026-11-16');
    expect(puzzleDay(new Date('2026-11-17T00:00:01Z'))).toBe('2026-11-17');
    const list = ['a', 'b', 'c'];
    expect([pick(list, '2026-11-16'), pick(list, '2026-11-17'), pick(list, '2026-11-18'), pick(list, '2026-11-19')])
      .toEqual(expect.arrayContaining(['a', 'b', 'c']));
    expect(pick(list, '2026-11-16')).toBe(pick(list, '2026-11-19'));
    expect(secondsUntilNextDay(new Date('2026-11-16T23:00:00Z'))).toBe(3600);
    expect(previousDay('2026-03-01')).toBe('2026-02-28');
  });

  it('accepts answers however they are typed', () => {
    expect(matchesAnswer('  marie   CURIE ', 'Marie Curie')).toBe(true);
    expect(matchesAnswer('madame curie', 'Marie Curie', ['Madame Curie'])).toBe(true);
    expect(matchesAnswer('the eiffel tower!', 'Eiffel Tower')).toBe(true);
    expect(matchesAnswer('Frida Kahlo', 'Frída Kahlo')).toBe(true);
    expect(matchesAnswer('curie', 'Marie Curie')).toBe(false);
    expect(matchesAnswer('', 'Marie Curie')).toBe(false);
    expect([1, 2, 5].map((n) => clueTrailScore(n, 5))).toEqual([5, 4, 1]);
  });

  it('marks letters fairly, including repeated letters', () => {
    expect(markGuess('PLANET', 'PLANET')).toEqual(['hit', 'hit', 'hit', 'hit', 'hit', 'hit']);
    expect(markGuess('TEMPO', 'OPERA')).toEqual(['miss', 'near', 'miss', 'near', 'near']);
    // two Ls guessed, one L in the answer: only one gets marked
    expect(markGuess('LLAMA', 'PLANE')).toEqual(['miss', 'hit', 'hit', 'miss', 'miss']);
  });

  it('checks Four by Four picks and spots "one away"', () => {
    const groups = [{ items: ['A', 'B', 'C', 'D'] }, { items: ['E', 'F', 'G', 'H'] }];
    expect(findGroup(groups, ['D', 'C', 'B', 'A'])).toBe(groups[0]);
    expect(findGroup(groups, ['A', 'B', 'C', 'E'])).toBeNull();
    expect(oneAway(groups, ['A', 'B', 'C', 'E'])).toBe(true);
    expect(oneAway(groups, ['A', 'B', 'E', 'F'])).toBe(false);
    const order = seededShuffle(['A', 'B', 'C', 'D', 'E'], 7);
    expect(order).toEqual(seededShuffle(['A', 'B', 'C', 'D', 'E'], 7));
    expect([...order].sort()).toEqual(['A', 'B', 'C', 'D', 'E']);
  });
});

describe('puzzle content', () => {
  it('Clue Trails have five clues and answers that match themselves', () => {
    expect(CLUE_TRAILS.length).toBeGreaterThanOrEqual(14);
    for (const c of CLUE_TRAILS) {
      expect(c.clues, c.answer).toHaveLength(5);
      expect(matchesAnswer(c.answer, c.answer, c.aliases)).toBe(true);
      c.aliases.forEach((a) => expect(matchesAnswer(a, c.answer, c.aliases)).toBe(true));
    }
  });

  it('Letter Vault words are 5 to 7 capital letters, without repeats', () => {
    const words = LETTER_VAULTS.map((v) => v.word);
    words.forEach((w) => expect(w).toMatch(/^[A-Z]{5,7}$/));
    expect(new Set(words).size).toBe(words.length);
  });

  it('every Four by Four has 4 groups of 4 and 16 different tiles', () => {
    for (const p of FOUR_BY_FOURS) {
      expect(p.groups).toHaveLength(4);
      p.groups.forEach((g) => expect(g.items, g.label).toHaveLength(4));
      const tiles = p.groups.flatMap((g) => g.items);
      expect(new Set(tiles).size, tiles.join(',')).toBe(16);
    }
  });

  it('every Brain Teaser has four options and a valid answer', () => {
    for (const t of BRAIN_TEASERS) {
      expect(t.options, t.question).toHaveLength(4);
      expect(t.answer).toBeGreaterThanOrEqual(0);
      expect(t.answer).toBeLessThan(4);
      expect(new Set(t.options).size).toBe(4);
      expect(t.why.length).toBeGreaterThan(10);
    }
  });
});
