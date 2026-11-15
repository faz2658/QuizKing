import { describe, expect, it } from 'vitest';
import { verdict } from './result';

describe('verdict', () => {
  it('matches the score band', () => {
    expect(verdict(100)).toBe('Outstanding!');
    expect(verdict(70)).toBe('Great game!');
    expect(verdict(50)).toBe('Nicely done.');
    expect(verdict(30)).toBe('Keep practising.');
    expect(verdict(0)).toBe('Tough round — try again!');
  });
});
