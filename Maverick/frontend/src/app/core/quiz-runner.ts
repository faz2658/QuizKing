import { DestroyRef, Signal, computed, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { errorMessage } from './http-errors';

/** A question as the runner sees it (single-player and adaptive questions both fit). */
export interface RunnerQuestion {
  questionId: number;
  number: number;
  stem: string;
  options: string[];
  secondsRemaining: number;
  points: number;
  /** Total time for the question, for the timer ring. Falls back to the remaining time. */
  timeLimitSeconds?: number;
}

export interface Feedback {
  correct: boolean;
  timedOut: boolean;
  /** Unknown when the player ran out of time (revealed on the results page). */
  correctAnswer: string | null;
  points: number;
  note?: string;
}

/** How the runner talks to a particular game engine. */
export interface QuizAdapter<S> {
  start(): Observable<S>;
  refresh(session: S): Observable<S>;
  answer(session: S, questionId: number, option: string): Observable<{ feedback: Feedback; session: S }>;
  question(session: S): RunnerQuestion | null;
}

export type Phase = 'loading' | 'question' | 'feedback' | 'done' | 'error';

export interface RunnerTiming {
  /** How long feedback stays on screen before the next question. */
  feedbackMs: number;
  /** Server grace period: wait this long after the timer hits zero before asking for the next question. */
  expiryGraceMs: number;
  tickMs: number;
  now: () => number;
}

export const DEFAULT_TIMING: RunnerTiming = { feedbackMs: 1400, expiryGraceMs: 2300, tickMs: 200, now: () => Date.now() };

/**
 * Drives a timed quiz from the player's side. The server decides everything (score, timeouts, next
 * question); the runner only shows the countdown, sends the chosen option and shows feedback.
 */
export class QuizRunner<S> {
  private readonly _phase = signal<Phase>('loading');
  private readonly _session = signal<S | null>(null);
  private readonly _question = signal<RunnerQuestion | null>(null);
  private readonly _selected = signal<string | null>(null);
  private readonly _feedback = signal<Feedback | null>(null);
  private readonly _secondsLeft = signal(0);
  private readonly _error = signal<string | null>(null);

  readonly phase: Signal<Phase> = this._phase.asReadonly();
  readonly session: Signal<S | null> = this._session.asReadonly();
  readonly question: Signal<RunnerQuestion | null> = this._question.asReadonly();
  readonly selected: Signal<string | null> = this._selected.asReadonly();
  readonly feedback: Signal<Feedback | null> = this._feedback.asReadonly();
  readonly secondsLeft: Signal<number> = this._secondsLeft.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  /** 1 = full time left, 0 = time is up. */
  readonly timeFraction = computed(() => {
    const q = this._question();
    const total = q?.timeLimitSeconds ?? q?.secondsRemaining ?? 0;
    return total > 0 ? Math.max(0, Math.min(1, this._secondsLeft() / total)) : 0;
  });

  private deadline = 0;
  private ticker: ReturnType<typeof setInterval> | null = null;
  private pending: ReturnType<typeof setTimeout> | null = null;
  private subs = new Subscription();
  private expiryRetries = 0;

  constructor(
    private readonly adapter: QuizAdapter<S>,
    destroyRef?: DestroyRef,
    private readonly timing: RunnerTiming = DEFAULT_TIMING,
  ) {
    destroyRef?.onDestroy(() => this.stop());
  }

  start(): void {
    this._phase.set('loading');
    this._error.set(null);
    this.subs.add(this.adapter.start().subscribe({ next: (s) => this.show(s), error: (e) => this.fail(e) }));
  }

  choose(option: string): void {
    const s = this._session();
    const q = this._question();
    if (this._phase() !== 'question' || !s || !q) {
      return;
    }
    this._selected.set(option);
    this._phase.set('feedback');
    this.stopTicker();
    this.subs.add(
      this.adapter.answer(s, q.questionId, option).subscribe({
        next: ({ feedback, session }) => {
          this._feedback.set(feedback);
          this.later(this.timing.feedbackMs, () => this.show(session));
        },
        error: () => {
          // Most likely the question closed on the server just before our answer arrived.
          this._feedback.set({ correct: false, timedOut: true, correctAnswer: null, points: 0, note: 'Too late!' });
          this.later(this.timing.feedbackMs, () => this.refresh());
        },
      }),
    );
  }

  stop(): void {
    this.stopTicker();
    if (this.pending) {
      clearTimeout(this.pending);
      this.pending = null;
    }
    this.subs.unsubscribe();
    this.subs = new Subscription();
  }

  private show(session: S): void {
    this._session.set(session);
    const q = this.adapter.question(session);
    const previous = this._question();
    if (!q) {
      this._question.set(null);
      this._phase.set('done');
      this.stopTicker();
      return;
    }
    if (previous && previous.questionId === q.questionId && this._phase() !== 'loading' && this._feedback()?.timedOut) {
      // The server hasn't closed the expired question yet: ask again shortly.
      if (this.expiryRetries++ < 5) {
        this.later(700, () => this.refresh());
        return;
      }
    }
    this.expiryRetries = 0;
    this._question.set(q);
    this._selected.set(null);
    this._feedback.set(null);
    this._phase.set('question');
    this.deadline = this.timing.now() + q.secondsRemaining * 1000;
    this.tick();
    this.stopTicker();
    this.ticker = setInterval(() => this.tick(), this.timing.tickMs);
  }

  private tick(): void {
    const left = Math.max(0, Math.ceil((this.deadline - this.timing.now()) / 1000));
    this._secondsLeft.set(left);
    if (left === 0 && this._phase() === 'question') {
      this.stopTicker();
      this._phase.set('feedback');
      this._feedback.set({ correct: false, timedOut: true, correctAnswer: null, points: 0, note: "Time's up!" });
      this.later(this.timing.expiryGraceMs, () => this.refresh());
    }
  }

  private refresh(): void {
    const s = this._session();
    if (!s) {
      return;
    }
    this.subs.add(this.adapter.refresh(s).subscribe({ next: (n) => this.show(n), error: (e) => this.fail(e) }));
  }

  private later(ms: number, fn: () => void): void {
    if (this.pending) {
      clearTimeout(this.pending);
    }
    this.pending = setTimeout(() => {
      this.pending = null;
      fn();
    }, ms);
  }

  private stopTicker(): void {
    if (this.ticker) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
  }

  private fail(e: unknown): void {
    this.stop();
    this._error.set(errorMessage(e));
    this._phase.set('error');
  }
}
