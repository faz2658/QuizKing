import { Injectable, computed, signal } from '@angular/core';
import { puzzleDay } from './logic';

/** The four daily puzzles. */
export type PuzzleKind = 'clue' | 'vault' | 'four' | 'teaser';
/** The puzzles in the order the page shows them. */
export const PUZZLE_KINDS: PuzzleKind[] = ['clue', 'vault', 'four', 'teaser'];

/** How a player did on one of today's puzzles. `state` lets a half-played puzzle resume after a reload. */
export interface PuzzleResult {
  done: boolean;
  won: boolean;
  /** One line for sharing, e.g. "🕵️ Clue Trail: solved with 2 clues". */
  summary: string;
  state?: unknown;
}

interface Saved {
  day: string;
  results: Partial<Record<PuzzleKind, PuzzleResult>>;
  /** Consecutive days with at least one puzzle solved, ending on `lastSolvedDay`. */
  streak: number;
  lastSolvedDay: string | null;
}

const KEY = 'quizcadia.puzzles';

/**
 * Today's puzzle progress and the puzzle streak, kept in this browser (works without signing in).
 * A new day starts with empty results; the streak carries over if yesterday had a solve.
 */
@Injectable({ providedIn: 'root' })
export class PuzzleProgress {
  readonly day = signal(puzzleDay());
  private readonly saved = signal<Saved>(this.load(puzzleDay()));

  readonly results = computed(() => this.saved().results);
  readonly streak = computed(() => {
    const s = this.saved();
    return s.lastSolvedDay === this.day() || s.lastSolvedDay === previousDay(this.day()) ? s.streak : 0;
  });
  readonly doneCount = computed(() => PUZZLE_KINDS.filter((k) => this.results()[k]?.done).length);

  /** Today's result for one puzzle, if the player started it. */
  result(kind: PuzzleKind): PuzzleResult | undefined {
    return this.results()[kind];
  }

  /** Save progress (`done: false`) or a finished puzzle. */
  save(kind: PuzzleKind, result: PuzzleResult): void {
    const s = this.saved();
    const firstSolveToday = result.done && result.won && s.lastSolvedDay !== this.day();
    const next: Saved = {
      ...s,
      results: { ...s.results, [kind]: result },
      streak: firstSolveToday ? (s.lastSolvedDay === previousDay(this.day()) ? s.streak + 1 : 1) : s.streak,
      lastSolvedDay: firstSolveToday ? this.day() : s.lastSolvedDay,
    };
    this.saved.set(next);
    try {
      localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      // storage blocked: progress lasts for this visit only
    }
  }

  private load(day: string): Saved {
    let s: Saved = { day, results: {}, streak: 0, lastSolvedDay: null };
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) {
        s = { ...s, ...(JSON.parse(raw) as Saved) };
      }
    } catch {
      // nothing saved, or storage blocked
    }
    return s.day === day ? s : { ...s, day, results: {} };
  }
}

/** The day before a yyyy-mm-dd date. */
export function previousDay(day: string): string {
  return new Date(Date.parse(day + 'T00:00:00Z') - 86_400_000).toISOString().slice(0, 10);
}
