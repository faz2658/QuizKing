import { describe, expect, it } from 'vitest';
import { DuelPlayer, DuelView } from '../core/models';
import { duelStatus } from './duel-list';

const player = (p: Partial<DuelPlayer>): DuelPlayer => ({ name: 'Bo', status: 'NOT_STARTED', score: null, maxScore: null, correctAnswers: null, sessionId: null, ...p });
const duel = (d: Partial<DuelView>): DuelView => ({
  id: 'abcd2345', gameName: 'Capitals', gameId: 5, international: false, totalQuestions: 10, state: 'PLAYING', role: 'CHALLENGER',
  challengerName: 'Ada', you: player({ name: 'Ada' }), friend: player({}), outcome: null, expiresAt: null, ...d,
});

describe('duelStatus', () => {
  it('asks the invited friend to answer', () => {
    expect(duelStatus(duel({ state: 'WAITING', role: 'INVITED', you: null, friend: null }))).toEqual({ text: 'Ada challenges you', action: true });
  });

  it('tells the challenger they are waiting for a friend', () => {
    expect(duelStatus(duel({ state: 'WAITING', friend: null })).action).toBe(false);
  });

  it("is your turn until you've played, then you wait for your friend", () => {
    expect(duelStatus(duel({}))).toEqual({ text: 'Your turn vs Bo', action: true });
    expect(duelStatus(duel({ you: player({ status: 'COMPLETED', score: 40 }) }))).toEqual({ text: 'Waiting for Bo to play', action: false });
  });

  it('shows the winner and both scores', () => {
    const done = duel({ state: 'FINISHED', outcome: 'LOST', you: player({ status: 'COMPLETED', score: 30 }), friend: player({ status: 'COMPLETED', score: 50 }) });
    expect(duelStatus(done).text).toBe('Bo won · 30 vs 50');
    expect(duelStatus({ ...done, outcome: 'WON' }).text).toBe('🏆 You won · 30 vs 50');
  });
});
