import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Feedback, QuizAdapter, QuizRunner, RunnerQuestion } from './quiz-runner';

interface FakeSession {
  index: number;
  questions: RunnerQuestion[];
}

function q(id: number, seconds = 10): RunnerQuestion {
  return { questionId: id, number: id, stem: `Q${id}`, options: ['a', 'b'], secondsRemaining: seconds, points: 10, timeLimitSeconds: seconds };
}

class FakeAdapter implements QuizAdapter<FakeSession> {
  session: FakeSession = { index: 0, questions: [q(1), q(2)] };
  answers: [number, string][] = [];
  refreshes = 0;
  failAnswer = false;
  /** How many refreshes keep returning the expired question before the server moves on. */
  staleRefreshes = 0;

  start(): Observable<FakeSession> {
    return of({ ...this.session });
  }

  refresh(): Observable<FakeSession> {
    this.refreshes++;
    if (this.staleRefreshes > 0) {
      this.staleRefreshes--;
      return of({ ...this.session });
    }
    this.session = { ...this.session, index: this.session.index + 1 };
    return of({ ...this.session });
  }

  answer(_s: FakeSession, questionId: number, option: string): Observable<{ feedback: Feedback; session: FakeSession }> {
    if (this.failAnswer) {
      return throwError(() => new Error('409'));
    }
    this.answers.push([questionId, option]);
    this.session = { ...this.session, index: this.session.index + 1 };
    return of({ feedback: { correct: option === 'a', timedOut: false, correctAnswer: 'a', points: option === 'a' ? 10 : 0 }, session: { ...this.session } });
  }

  question(s: FakeSession): RunnerQuestion | null {
    return s.questions[s.index] ?? null;
  }
}

describe('QuizRunner', () => {
  let adapter: FakeAdapter;
  let runner: QuizRunner<FakeSession>;
  let now: number;

  beforeEach(() => {
    vi.useFakeTimers();
    now = 0;
    adapter = new FakeAdapter();
    runner = new QuizRunner(adapter, undefined, { feedbackMs: 1000, expiryGraceMs: 2000, tickMs: 100, now: () => now });
  });

  afterEach(() => {
    runner.stop();
    vi.useRealTimers();
  });

  /** Moves the wall clock and the fake timers forward together, in small steps. */
  function advance(ms: number) {
    for (let t = 0; t < ms; t += 50) {
      now += 50;
      vi.advanceTimersByTime(50);
    }
  }

  it('shows the first question with a full timer', () => {
    runner.start();

    expect(runner.phase()).toBe('question');
    expect(runner.question()?.questionId).toBe(1);
    expect(runner.secondsLeft()).toBe(10);
    expect(runner.timeFraction()).toBe(1);
  });

  it('sends the chosen option, shows feedback, then moves to the next question', () => {
    runner.start();
    runner.choose('a');

    expect(adapter.answers).toEqual([[1, 'a']]);
    expect(runner.phase()).toBe('feedback');
    expect(runner.feedback()?.correct).toBe(true);
    expect(runner.selected()).toBe('a');

    advance(1000);
    expect(runner.phase()).toBe('question');
    expect(runner.question()?.questionId).toBe(2);
    expect(runner.feedback()).toBeNull();
  });

  it('ignores a second click while feedback is showing', () => {
    runner.start();
    runner.choose('b');
    runner.choose('a');

    expect(adapter.answers).toEqual([[1, 'b']]);
  });

  it('finishes after the last question', () => {
    runner.start();
    runner.choose('a');
    advance(1000);
    runner.choose('a');
    advance(1000);

    expect(runner.phase()).toBe('done');
    expect(runner.question()).toBeNull();
  });

  it('counts down and, when time is up, asks the server for the next question after the grace period', () => {
    runner.start();
    advance(4000);
    expect(runner.secondsLeft()).toBe(6);

    advance(6000);
    expect(runner.phase()).toBe('feedback');
    expect(runner.feedback()?.timedOut).toBe(true);
    expect(adapter.refreshes).toBe(0);

    advance(2000);
    expect(adapter.refreshes).toBe(1);
    expect(runner.question()?.questionId).toBe(2);
    expect(runner.phase()).toBe('question');
  });

  it('retries when the server has not closed the expired question yet', () => {
    adapter.staleRefreshes = 2;
    runner.start();
    advance(10000);
    advance(2000);
    expect(runner.phase()).toBe('feedback');

    advance(700);
    advance(700);
    expect(adapter.refreshes).toBe(3);
    expect(runner.question()?.questionId).toBe(2);
  });

  it('treats a rejected answer as too late and refreshes', () => {
    adapter.failAnswer = true;
    runner.start();
    runner.choose('a');

    expect(runner.feedback()?.note).toBe('Too late!');
    advance(1000);
    expect(adapter.refreshes).toBe(1);
  });

  it('reports start errors', () => {
    adapter.start = () => throwError(() => new Error('down'));
    runner.start();

    expect(runner.phase()).toBe('error');
    expect(runner.error()).toBeTruthy();
  });
});
