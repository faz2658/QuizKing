import { browserZone, countryFromZone } from './countries';
import { GameCard } from './models';

/** The question-bank category of hand-written India & South Asia questions (questionGenerator packs/south-asia.json). */
export const SOUTH_ASIA_CATEGORY = 'India & South Asia';

const SOUTH_ASIA = ['IN', 'PK', 'BD', 'LK', 'NP', 'BT', 'MV'];

/**
 * The South Asian country to show local games for, or null for players elsewhere. The player's chosen
 * country wins; otherwise the device's time zone decides.
 */
export function southAsianCountry(country: string | null, zone: string | null = browserZone()): string | null {
  if (country && SOUTH_ASIA.includes(country.toUpperCase())) {
    return country.toUpperCase();
  }
  const fromZone = countryFromZone(zone);
  return fromZone && SOUTH_ASIA.includes(fromZone) ? fromZone : null;
}

/** Splits games into the region's own games and the rest, keeping each list's order. */
export function localFirst(games: GameCard[], localCategoryIds: ReadonlySet<number>): { local: GameCard[]; rest: GameCard[] } {
  const local: GameCard[] = [];
  const rest: GameCard[] = [];
  for (const g of games) {
    (localCategoryIds.has(g.categoryId) ? local : rest).push(g);
  }
  return { local, rest };
}

/**
 * The games a player's list shows. Players from South Asia see only their region's games (search still finds
 * everything, region's games first). Everyone else, or a region with no games yet, sees every game.
 */
export function gamesFor(
  games: GameCard[],
  localCategoryIds: ReadonlySet<number>,
  inRegion: boolean,
  searching: boolean,
): { games: GameCard[]; regionalOnly: boolean } {
  if (!inRegion) {
    return { games, regionalOnly: false };
  }
  const { local, rest } = localFirst(games, localCategoryIds);
  if (searching) {
    return { games: [...local, ...rest], regionalOnly: false };
  }
  return local.length ? { games: local, regionalOnly: true } : { games, regionalOnly: false };
}
