import { countryName, flag } from './countries';
import { DailyBoard, ReviewItem } from './models';

/** Spoiler-free row of squares: 🟩 right, 🟥 wrong, ⬛ the timer ran out (or skipped). */
export function emojiGrid(review: ReviewItem[]): string {
  return review.map((q) => (q.correct ? '🟩' : q.timedOut || q.skipped ? '⬛' : '🟥')).join('');
}

/** A title worth sharing, e.g. "History Sage" (falls back to just the rank for long or missing names). */
export function rankTitle(percentage: number, subject?: string | null): string {
  const rank = percentage >= 90 ? 'Grandmaster' : percentage >= 75 ? 'Sage' : percentage >= 50 ? 'Scholar' : percentage >= 25 ? 'Apprentice' : 'Rookie';
  const s = (subject ?? '').trim();
  return s && s.length <= 24 ? `${s} ${rank}` : rank;
}

/** "Top 4% today · #2 in 🇨🇦 Canada", or null when there's nothing to brag about yet (too few players). */
export function bragLine(board: DailyBoard | null | undefined): string | null {
  const p = board?.place;
  if (!board || !p) return null;
  const parts: string[] = [];
  if (board.players >= 5 && p.topPercent <= 50) parts.push(`Top ${p.topPercent}% today`);
  if (p.country && p.countryPlayers >= 2) parts.push(`#${p.countryRank} in ${flag(p.country)} ${countryName(p.country)}`);
  else if (board.you && board.players >= 2) parts.push(`#${board.you.rank} of ${board.players}`);
  return parts.length ? parts.join(' · ') : null;
}

/** The text people paste into group chats, Wordle style. */
export function dailyShareText(day: string, review: ReviewItem[], correct: number, total: number, brag: string | null, url: string): string {
  return [`✨ QuizCadia Daily ${day}`, `${emojiGrid(review)} ${correct}/${total}`, brag, url].filter(Boolean).join('\n');
}
