import { describe, expect, it } from 'vitest';
import { GameCard } from '../core/models';
import { topicsOf } from './custom';

const card = (gameId: number, topic: string, mode: GameCard['mode'] = 'SINGLE'): GameCard => ({ gameId, gameName: `${topic} game`, categoryId: 1, topic, mode });

describe('topicsOf', () => {
  it('keeps one solo game per topic, sorted', () => {
    expect(topicsOf([card(1, 'History'), card(2, 'Art'), card(3, 'art'), card(4, 'Music', 'MULTI')]).map((g) => g.gameId)).toEqual([2, 1]);
  });
});
