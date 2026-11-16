import { describe, expect, it } from 'vitest';
import { optionsOf } from './feedback-review';

describe('optionsOf', () => {
  it('drops empty option slots', () => {
    expect(optionsOf({ questionId: 1, questionStem: 'Q', questionLevel: 1, option1: 'True', option2: 'False', option3: '', option4: null, correctAnswer: 'True' }))
      .toEqual(['True', 'False']);
  });
});
