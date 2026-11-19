import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable, map } from 'rxjs';
import { QuizApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { SessionView } from '../core/models';
import { QuizAdapter, QuizRunner } from '../core/quiz-runner';
import { QuizStage } from '../ui/quiz-stage';
import { CountUp } from '../motion/count-up';
import { LifelineBar } from '../ui/lifeline-bar';
import { CustomDifficulty, Lifeline } from '../core/models';
import { errorMessage } from '../core/http-errors';

export function singlePlayerAdapter(api: QuizApi, gameId: number, userId: number, start?: () => Observable<SessionView>): QuizAdapter<SessionView> {
  return {
    start: start ?? (() => api.start(gameId, userId)),
    refresh: (s) => api.session(s.sessionId),
    answer: (s, questionId, option) =>
      api.answer(s.sessionId, questionId, option).pipe(
        map((a) => ({
          feedback: {
            correct: a.correct,
            timedOut: a.timedOut,
            correctAnswer: a.correctAnswer,
            points: a.pointsAwarded,
            note: a.correct && (a.multiplier ?? 1) > 1 ? `Combo ×${a.multiplier}!` : undefined,
            explanation: a.explanation,
          },
          session: a.session,
        })),
      ),
    question: (s) => s.currentQuestion,
  };
}

@Component({
  selector: 'mv-play',
  imports: [QuizStage, RouterLink, CountUp, LifelineBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      @switch (runner.phase()) {
        @case ('loading') {
          <div class="card center"><div class="spinner" aria-hidden="true"></div><p>Shuffling questions…</p></div>
        }
        @case ('error') {
          <div class="card center">
            <h2>Couldn't start this game</h2>
            <div class="alert" role="alert">{{ runner.error() }}</div>
            <div class="row"><button class="btn btn-primary" (click)="runner.start()">Try again</button><a class="btn" routerLink="/">Back to games</a></div>
          </div>
        }
        @default {
          @if (runner.session(); as s) {
            <div class="hud">
              <div><span class="muted">Game</span><strong>{{ s.gameName }}</strong></div>
              <div><span class="muted">Score</span><strong class="score"><mv-count-up [value]="s.score" [duration]="600" /></strong></div>
              @if ((s.combo ?? 0) >= 2) {
                <div class="combo" aria-live="polite"><span aria-hidden="true">🔥</span> {{ s.combo }} in a row · next ×{{ s.nextMultiplier }}</div>
              }
              <div class="progress" role="progressbar" [attr.aria-valuenow]="s.answeredQuestions" aria-valuemin="0" [attr.aria-valuemax]="s.totalQuestions">
                <span [style.width.%]="100 * s.answeredQuestions / s.totalQuestions"></span>
              </div>
            </div>
            @if (runner.question(); as q) {
              <mv-quiz-stage [question]="q" [total]="s.totalQuestions" [secondsLeft]="runner.secondsLeft()"
                [fraction]="runner.timeFraction()" [selected]="runner.selected()" [feedback]="runner.feedback()"
                [locked]="runner.phase() !== 'question'" (choose)="runner.choose($event)"
                [canAdvance]="runner.canAdvance()" (next)="runner.next()">
                @let difficulty = s.currentQuestion?.difficulty ?? 'EASY';
                <span badges [class]="'chip chip-' + difficulty.toLowerCase()">{{ difficulty }}</span>
              </mv-quiz-stage>
              <mv-lifeline-bar [available]="s.lifelines ?? []" [disabled]="runner.phase() !== 'question' || lifelineBusy()"
                [optionCount]="q.options.length" (use)="useLifeline($event)" />
              @if (lifelineError()) { <p class="alert" role="alert">{{ lifelineError() }}</p> }
            }
          }
        }
      }
    </div>
  `,
  styles: `
    .wrap { max-width: 860px; margin: 0 auto; }
    .center { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px; }
    .hud { display: grid; grid-template-columns: 1fr auto; gap: 8px 24px; align-items: end; margin-bottom: 16px; }
    .hud div { display: flex; flex-direction: column; }
    .hud div:nth-child(2) { text-align: right; }
    .score { font-size: 2.2rem; font-weight: 900; letter-spacing: -0.03em; font-variant-numeric: tabular-nums; }
    .combo { grid-column: 1 / -1; justify-self: end; font-weight: 900; color: var(--accent); animation: pop 0.4s ease; }
    @keyframes pop { from { transform: scale(0.7); opacity: 0; } }
    .progress { grid-column: 1 / -1; height: 8px; border-radius: 999px; background: var(--bg-sunken); overflow: hidden; }
    .progress span { display: block; height: 100%; background: linear-gradient(90deg, var(--accent-2), var(--pink), var(--accent)); box-shadow: 0 0 14px color-mix(in srgb, var(--pink) 60%, transparent); transition: width 0.4s ease; }
    .spinner { width: 42px; height: 42px; border-radius: 50%; border: 4px solid var(--bg-sunken); border-top-color: var(--accent); animation: spin 0.8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `,
})
export class Play implements OnInit {
  readonly gameId = input<string>('0');
  /** 'daily' for today's challenge, 'challenge' for a friend's score to beat, 'duel' for a friend challenge (set by the route). */
  readonly mode = input<string>('');
  readonly challengeId = input<string>('');
  /** A challenge between two friends (mode 'duel'). */
  readonly duelId = input<string>('');
  /** Custom quiz settings from the query string. */
  readonly games = input<string>('');
  readonly difficulty = input<string>('MIXED');
  readonly count = input<string>('10');
  /** '1' when started from the International page: questions from every country. */
  readonly intl = input<string>('');
  private readonly api = inject(QuizApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected runner!: QuizRunner<SessionView>;
  protected readonly lifelineBusy = signal(false);
  protected readonly lifelineError = signal<string | null>(null);

  protected useLifeline(lifeline: Lifeline): void {
    const s = this.runner.session();
    const q = this.runner.question();
    if (!s || !q || this.lifelineBusy()) {
      return;
    }
    this.lifelineBusy.set(true);
    this.lifelineError.set(null);
    this.api.lifeline(s.sessionId, lifeline, q.questionId).subscribe({
      next: (r) => {
        this.lifelineBusy.set(false);
        this.runner.update(r.session);
      },
      error: (e) => {
        this.lifelineBusy.set(false);
        this.lifelineError.set(errorMessage(e));
      },
    });
  }

  constructor() {
    effect(() => {
      if (this.runner?.phase() === 'done') {
        const id = this.runner.session()?.sessionId;
        if (id && this.mode() === 'duel') {
          this.router.navigate(['/vs', this.duelId()]);
        } else if (id) {
          this.router.navigate(['/result', id]);
        }
      }
    });
  }

  ngOnInit(): void {
    const start =
      this.mode() === 'daily' ? () => this.api.startDaily()
      : this.mode() === 'challenge' ? () => this.api.acceptChallenge(this.challengeId())
      : this.mode() === 'duel' ? () => this.api.playDuel(this.duelId())
      : this.mode() === 'custom'
        ? () => this.api.startCustom(this.games().split(',').map(Number).filter((n) => n > 0), this.difficulty() as CustomDifficulty, Number(this.count()))
      : this.intl() === '1' ? () => this.api.start(Number(this.gameId()), this.auth.user()!.userId, true)
      : undefined;
    this.runner = new QuizRunner(singlePlayerAdapter(this.api, Number(this.gameId()), this.auth.user()!.userId, start), this.destroyRef);
    this.runner.start();
  }
}
