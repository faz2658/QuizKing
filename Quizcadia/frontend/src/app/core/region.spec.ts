import { describe, expect, it } from 'vitest';
import { GameCard } from './models';
import { withinCatalog } from './region';

const card = (gameId: number, mode: 'SINGLE' | 'MULTI' = 'SINGLE') => ({ gameId, mode }) as GameCard;

describe('region', () => {
  it('keeps only games the catalog lists, matching solo and multiplayer separately', () => {
    const catalog = [card(1), card(2), card(2, 'MULTI')];

    expect(withinCatalog([card(1), card(7), card(2, 'MULTI'), card(1, 'MULTI')], catalog)).toEqual([card(1), card(2, 'MULTI')]);
    expect(withinCatalog([card(1)], [])).toEqual([]);
  });
});
