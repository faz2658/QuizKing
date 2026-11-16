import { describe, expect, it } from 'vitest';
import { moveOldStorageKeys } from './storage-rename';

function memoryStorage(entries: Record<string, string>): Storage {
  const map = new Map(Object.entries(entries));
  return {
    get length() { return map.size; },
    key: (i: number) => [...map.keys()][i] ?? null,
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
    removeItem: (k: string) => void map.delete(k),
    clear: () => map.clear(),
  };
}

describe('moveOldStorageKeys', () => {
  it('moves Quiz King values to their QuizCadia names and keeps newer ones', () => {
    const s = memoryStorage({
      'quizking.session': '{"token":"t"}',
      'quizking.fx': '{"sound":false}',
      'quizking.country': 'CA',
      'quizcadia.country': 'IN', // already set under the new name: wins
      'other.key': 'x',
    });
    moveOldStorageKeys(s);
    expect(s.getItem('quizcadia.session')).toBe('{"token":"t"}');
    expect(s.getItem('quizcadia.fx')).toBe('{"sound":false}');
    expect(s.getItem('quizcadia.country')).toBe('IN');
    expect(s.getItem('quizking.session')).toBeNull();
    expect(s.getItem('quizking.country')).toBeNull();
    expect(s.getItem('other.key')).toBe('x');
  });
});
