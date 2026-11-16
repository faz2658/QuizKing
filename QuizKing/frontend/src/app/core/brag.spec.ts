import { describe, expect, it } from 'vitest';
import { bragLine, dailyShareText, emojiGrid, rankTitle } from './brag';
import { DailyBoard, ReviewItem } from './models';

const q = (correct: boolean, timedOut = false) => ({ number: 1, difficulty: 'EASY', stem: '', yourAnswer: null, correctAnswer: '',
  correct, timedOut, pointsAwarded: 0, points: 10 }) as ReviewItem;
const board = (over: Partial<DailyBoard> = {}): DailyBoard => ({ date: '2026-11-15', players: 40, top: [],
  you: { rank: 3, userId: 1, name: 'Ada', score: 80, maxScore: 100, correctAnswers: 8, totalQuestions: 10, country: 'CA' },
  place: { country: 'CA', countryRank: 2, countryPlayers: 9, topPercent: 8 }, ...over });

describe('brag lines', () => {
  it('draws a spoiler-free emoji row', () => {
    expect(emojiGrid([q(true), q(false), q(false, true), q(true)])).toBe('🟩🟥⬛🟩');
  });

  it('gives a title for every score', () => {
    expect(rankTitle(95, 'History')).toBe('History Grandmaster');
    expect(rankTitle(60, 'History')).toBe('History Scholar');
    expect(rankTitle(10, 'A very long game name that does not fit')).toBe('Rookie');
  });

  it('brags about the percentile and the country rank only when it means something', () => {
    expect(bragLine(board())).toBe('Top 8% today · #2 in 🇨🇦 Canada');
    expect(bragLine(board({ players: 3, place: { country: 'CA', countryRank: 1, countryPlayers: 1, topPercent: 34 } }))).toBe('#3 of 3');
    expect(bragLine(board({ place: null }))).toBeNull();
  });

  it('builds the paste-anywhere daily text', () => {
    expect(dailyShareText('2026-11-15', [q(true), q(false)], 1, 2, 'Top 8% today', 'https://x/daily'))
      .toBe('👑 Quizcadia Daily 2026-11-15\n🟩🟥 1/2\nTop 8% today\nhttps://x/daily');
  });
});
