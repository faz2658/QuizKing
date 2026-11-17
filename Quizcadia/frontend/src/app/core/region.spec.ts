import { describe, expect, it } from 'vitest';
import { GameCard } from './models';
import { localFirst, southAsianCountry } from './region';

const game = (gameId: number, categoryId: number): GameCard => ({ gameId, gameName: 'g' + gameId, categoryId, mode: 'SINGLE' });

describe('region', () => {
  it('finds South Asian players by their chosen country, or by time zone when the browser says en-US', () => {
    expect(southAsianCountry('in', 'America/Toronto')).toBe('IN');
    expect(southAsianCountry('PK', null)).toBe('PK');
    expect(southAsianCountry('US', 'Asia/Kolkata')).toBe('IN');
    expect(southAsianCountry(null, 'Asia/Dhaka')).toBe('BD');
    expect(southAsianCountry(null, 'Asia/Calcutta')).toBe('IN');
    expect(southAsianCountry('CA', 'America/Toronto')).toBeNull();
    expect(southAsianCountry(null, null)).toBeNull();
  });

  it('puts the local games first and keeps the order of both lists', () => {
    const { local, rest } = localFirst([game(1, 3), game(2, 14), game(3, 1), game(4, 14)], new Set([14]));
    expect(local.map((g) => g.gameId)).toEqual([2, 4]);
    expect(rest.map((g) => g.gameId)).toEqual([1, 3]);
  });
});
