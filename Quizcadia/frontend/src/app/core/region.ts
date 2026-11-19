import { GameCard } from './models';

/** The game manager's name for games outside any country category: the shared topics everyone plays. */
export const WORLD = 'WORLD';

/** A country with its own question set (GET /api/game/countries). */
export interface CountrySet {
  country: string;
  categoryId: number;
  categoryName: string;
  games: number;
  questions: number;
}

/**
 * The Games page's list: the shared (WORLD) games and the player's own country's games, each listed once (country
 * games first, so a player sees their own topics near the top). Other countries' games never show.
 */
export function generalAndOwnCountry(world: readonly GameCard[], own: readonly GameCard[]): GameCard[] {
  const seen = new Set<string>();
  return [...own, ...world].filter((g) => {
    const key = `${g.mode}:${g.gameId}`;
    return !seen.has(key) && !!seen.add(key);
  });
}

/**
 * Keeps only the games the catalog lists (the shared topics), so search results and recommendations never bring
 * back the old per-country games. Each player's country still shapes the questions, silently, in the game itself.
 */
export function withinCatalog(games: readonly GameCard[], catalog: readonly GameCard[]): GameCard[] {
  const listed = new Set(catalog.map((g) => `${g.mode}:${g.gameId}`));
  return games.filter((g) => listed.has(`${g.mode}:${g.gameId}`));
}
