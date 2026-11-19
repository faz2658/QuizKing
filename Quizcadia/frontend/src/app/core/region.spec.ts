import { describe, expect, it } from 'vitest';
import { GameCard } from './models';
import { generalAndOwnCountry, withinCatalog } from './region';

const card = (gameId: number, mode: GameCard['mode'] = 'SINGLE', country: string | null = null): GameCard =>
  ({ gameId, gameName: `G${gameId}`, categoryId: 1, mode, country }) as GameCard;

describe('generalAndOwnCountry', () => {
  it('lists the player\'s own country first, then the shared games, each once', () => {
    const world = [card(1), card(2), card(2, 'MULTI')];
    const own = [card(40, 'SINGLE', 'IN'), card(41, 'MULTI', 'IN'), card(1)];

    expect(generalAndOwnCountry(world, own).map((g) => `${g.mode}:${g.gameId}`)).toEqual(['SINGLE:40', 'MULTI:41', 'SINGLE:1', 'SINGLE:2', 'MULTI:2']);
  });

  it('is just the shared games when no country is picked', () => {
    expect(generalAndOwnCountry([card(1), card(2)], []).map((g) => g.gameId)).toEqual([1, 2]);
  });

  it('keeps search results and recommendations inside that list', () => {
    const listed = generalAndOwnCountry([card(1)], [card(40, 'SINGLE', 'IN')]);
    expect(withinCatalog([card(1), card(40), card(77, 'SINGLE', 'JP')], listed).map((g) => g.gameId)).toEqual([1, 40]);
  });
});
