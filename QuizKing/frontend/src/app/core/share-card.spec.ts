import { describe, expect, it } from 'vitest';
import { shareText } from './share-card';

describe('shareText', () => {
  it('sums up the result with a challenge to beat it', () => {
    const text = shareText({ player: 'Ada', title: 'Daily Challenge', score: 180, maxScore: 260, percentage: 69, correct: 8, total: 10,
      rankLine: '#2 of 14 today', url: 'https://quizking.example' });
    expect(text).toBe('✨ I scored 180/260 (8/10 right) in Daily Challenge on Quizcadia (#2 of 14 today). Can you beat me? https://quizking.example');
  });
});
