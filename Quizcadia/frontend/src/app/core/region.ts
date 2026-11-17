/** The game manager's name for world trivia: games that belong to no country's question set. */
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
 * Which games the home page shows a player: their own country's set when it has one, otherwise world trivia.
 * Every other country's set is one tap away on the International page.
 */
export function homeScope(country: string | null, sets: readonly CountrySet[]): string {
  const c = country?.toUpperCase();
  return c && sets.some((s) => s.country === c && s.games > 0) ? c : WORLD;
}

/** The sets the International page offers a player: every country but their own, by name. */
export function internationalSets(own: string, sets: readonly CountrySet[], name: (code: string) => string): CountrySet[] {
  return sets.filter((s) => s.country !== own && s.games > 0).sort((a, b) => name(a.country).localeCompare(name(b.country)));
}
