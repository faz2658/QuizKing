import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { map, tap } from 'rxjs';
import { AdaptiveApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { AdaptiveSession } from '../core/models';
import { QuizAdapter, QuizRunner } from '../core/quiz-runner';
import { QuizStage } from '../ui/quiz-stage';

export const LEVEL_NAMES = ['', 'Easy', 'Medium', 'Advanced'];

@Component({
  selector: 'mv-adaptive',
  imports: [QuizStage, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      @switch (runner.phase()) {
        @case ('loading') { <div class="card center"><p>Calibrating difficulty…</p></div> }
        @case ('error') {
          <div class="card center">
            <h2>Couldn't start this game</h2>
            <div class="alert" role="alert">{{ runner.error() }}</div>
            <a class="btn" routerLink="/">Back to games</a>
          </div>
        }
        @case ('done') {
          @if (runner.session(); as s) {
            <section class="card center done">
              <span class="chip">{{ s.gameName }} · adaptive</span>
              <h1>{{ s.score }} points</h1>
              <p class="muted">You answered {{ s.answered }} questions and finished on <strong>{{ levelName(s.level) }}</strong>.
                Best level reached: <strong>{{ levelName(best()) }}</strong>.</p>
              <div class="ladder" aria-hidden="true">
                @for (l of [1, 2, 3]; track l) { <span [class.on]="l <= best()">{{ levelName(l) }}</span> }
              </div>
              <div class="row">
                <button class="btn btn-primary" (click)="restart()">Play again</button>
                <a class="btn" routerLink="/me">My stats</a>
              </div>
            </section>
          }
        }
        @default {
          @if (runner.session(); as s) {
            <div class="hud">
              <div class="ladder" role="img" [attr.aria-label]="'Current level ' + levelName(s.level)">
                @for (l of [1, 2, 3]; track l) { <span [class.on]="l <= s.level" [class.current]="l === s.level">{{ levelName(l) }}</span> }
              </div>
              <div class="score"><span class="muted">Score</span> <strong>{{ s.score }}</strong></div>
            </div>
            @if (levelNote()) { <p class="level-note" aria-live="polite">{{ levelNote() }}</p> }
            @if (runner.question(); as q) {
              <mv-quiz-stage [question]="q" [total]="s.totalQuestions" [secondsLeft]="runner.secondsLeft()"
                [fraction]="runner.timeFraction()" [selected]="runner.selected()" [feedback]="runner.feedback()"
                [locked]="runner.phase() !== 'question'" (choose)="runner.choose($event)">
                <span badges class="chip">Level {{ levelName(s.level) }}</span>
              </mv-quiz-stage>
            }
          }
        }
      }
    </div>
  `,
  styles: `
    .wrap { max-width: 860px; margin: 0 auto; }
    .center { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px; }
    .done h1 { font-size: 3rem; margin: 8px 0 0; }
    .hud { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
    .score strong { font-size: 1.7rem; color: var(--accent); margin-left: 6px; }
    .ladder { display: flex; gap: 6px; }
    .ladder span { padding: 6px 14px; border-radius: 999px; background: var(--bg-sunken); color: var(--text-muted); font-weight: 700; font-size: 0.85rem; border: 1px solid var(--border); }
    .ladder span.on { background: color-mix(in srgb, var(--accent-2) 25%, transparent); color: var(--text); border-color: var(--accent-2); }
    .ladder span.current { background: var(--accent-2); color: #fff; }
    .level-note { font-weight: 750; color: var(--accent); margin: 0 0 10px; }
  `,
})
export class Adaptive implements OnInit {
  readonly gameId = input.required<string>();
  private readonly api = inject(AdaptiveApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected runner!: QuizRunner<AdaptiveSession>;
  protected readonly levelNote = signal<string | null>(null);
  private readonly highest = signal(1);
  protected readonly best = computed(() => Math.max(this.highest(), this.runner?.session()?.level ?? 1));

  ngOnInit(): void {
    this.restart();
  }

  protected restart(): void {
    this.runner?.stop();
    this.highest.set(1);
    this.levelNote.set(null);
    const userId = this.auth.user()!.userId;
    const gameId = Number(this.gameId());
    const adapter: QuizAdapter<AdaptiveSession> = {
      start: () => this.api.start(gameId, userId),
      refresh: (s) => this.api.session(s.sessionId).pipe(tap((n) => this.track(n))),
      answer: (s, questionId, option) =>
        this.api.answer(s.sessionId, questionId, option).pipe(
          tap((a) => {
            this.levelNote.set(a.levelChange === 'UP' ? '↑ Level up! Questions get harder.' : a.levelChange === 'DOWN' ? '↓ Back down a level.' : null);
            this.track(a.session);
          }),
          map((a) => ({ feedback: { correct: a.correct, timedOut: false, correctAnswer: a.correctAnswer, points: a.points }, session: a.session })),
        ),
      question: (s) => s.currentQuestion && { ...s.currentQuestion, timeLimitSeconds: undefined },
    };
    this.runner = new QuizRunner(adapter, this.destroyRef);
    this.runner.start();
  }

  protected levelName(l: number): string {
    return LEVEL_NAMES[l] ?? `Level ${l}`;
  }

  private track(s: AdaptiveSession): void {
    this.highest.update((h) => Math.max(h, s.level));
  }
}
