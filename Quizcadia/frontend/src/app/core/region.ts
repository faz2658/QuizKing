import { GameCard } from './models';

/** The question-bank category of hand-written India & South Asia questions (questionGenerator packs/south-asia.json). */
export const SOUTH_ASIA_CATEGORY = 'India & South Asia';

const SOUTH_ASIA = ['IN', 'PK', 'BD', 'LK', 'NP', 'BT', 'MV'];

/** Time zones of South Asia, mapped to their country. Many browsers there report en-US, so the zone is the better hint. */
const ZONES: Record<string, string> = {
  'Asia/Kolkata': 'IN',
  'Asia/Calcutta': 'IN',
  'Asia/Karachi': 'PK',
  'Asia/Dhaka': 'BD',
  'Asia/Colombo': 'LK',
  'Asia/Kathmandu': 'NP',
  'Asia/Katmandu': 'NP',
  'Asia/Thimphu': 'BT',
  'Indian/Maldives': 'MV',
};

function browserZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? null;
  } catch {
    return null;
  }
}

/**
 * The South Asian country to show local games for, or null for players elsewhere. The player's chosen
 * country wins; otherwise the device's time zone decides.
 */
export function southAsianCountry(country: string | null, zone: string | null = browserZone()): string | null {
  if (country && SOUTH_ASIA.includes(country.toUpperCase())) {
    return country.toUpperCase();
  }
  return (zone && ZONES[zone]) || null;
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
