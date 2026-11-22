/** Pure rules for the four daily puzzles: no Angular, no storage, so they're easy to test. */

/** The UTC day everyone shares, as "YYYY-MM-DD" (same rollover as the Daily Challenge). */
export function puzzleDay(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/** Days since 1970-01-01 for a "YYYY-MM-DD" day: picks each puzzle type's entry for that day. */
export function dayNumber(day: string): number {
  return Math.floor(Date.parse(day + 'T00:00:00Z') / 86_400_000);
}

/** The entry for a day: walks the list in order, then starts again. */
export function pick<T>(list: readonly T[], day: string, offset = 0): T {
  const n = list.length;
  return list[(((dayNumber(day) + offset) % n) + n) % n];
}

/** Seconds until the next UTC midnight. */
export function secondsUntilNextDay(now = new Date()): number {
  const next = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
  return Math.max(0, Math.round((next - now.getTime()) / 1000));
}

/** Lower case, no accents, no punctuation, single spaces, no leading "the"/"a"/"an". */
export function normalize(text: string): string {
  return text
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9 ]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^(the|a|an) /, '');
}

/** Clue Trail: does a typed guess match the answer or one of its accepted names? */
export function matchesAnswer(guess: string, answer: string, aliases: readonly string[] = []): boolean {
  const g = normalize(guess);
  return g.length > 0 && [answer, ...aliases].some((a) => normalize(a) === g);
}

/** Clue Trail: points for solving after `cluesShown` clues (5 clues: 5 points down to 1). */
export function clueTrailScore(cluesShown: number, totalClues: number): number {
  return Math.max(1, totalClues - cluesShown + 1);
}

/** How a guessed letter scores in Letter Vault. */
export type LetterMark = 'hit' | 'near' | 'miss';

/**
 * Letter Vault: mark each guessed letter. "hit" = right letter, right place; "near" = in the word elsewhere;
 * "miss" = not in the word (or already used up by other marks, so repeated letters are counted fairly).
 */
export function markGuess(guess: string, answer: string): LetterMark[] {
  const g = guess.toUpperCase().split('');
  const a = answer.toUpperCase().split('');
  const marks: LetterMark[] = g.map(() => 'miss');
  const left = new Map<string, number>();
  g.forEach((ch, i) => {
    if (ch === a[i]) {
      marks[i] = 'hit';
    } else {
      left.set(a[i], (left.get(a[i]) ?? 0) + 1);
    }
  });
  g.forEach((ch, i) => {
    if (marks[i] !== 'hit' && (left.get(ch) ?? 0) > 0) {
      marks[i] = 'near';
      left.set(ch, left.get(ch)! - 1);
    }
  });
  return marks;
}

/** Four by Four: the group all four picked items belong to, or null if they're mixed. */
export function findGroup<G extends { items: readonly string[] }>(groups: readonly G[], picked: readonly string[]): G | null {
  if (picked.length !== 4) {
    return null;
  }
  return groups.find((grp) => picked.every((p) => grp.items.includes(p))) ?? null;
}

/** Four by Four: "one away" when three of the four picked items share a group. */
export function oneAway<G extends { items: readonly string[] }>(groups: readonly G[], picked: readonly string[]): boolean {
  return groups.some((grp) => picked.filter((p) => grp.items.includes(p)).length === 3);
}

/** A stable shuffle for a day, so everyone sees the same tile order. */
export function seededShuffle<T>(items: readonly T[], seed: number): T[] {
  const out = [...items];
  let s = (seed * 2654435761) >>> 0;
  for (let i = out.length - 1; i > 0; i--) {
    s = (s * 1664525 + 1013904223) >>> 0;
    const j = s % (i + 1);
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}
