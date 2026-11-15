import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { QuizApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { ResultView } from '../core/models';
import { CountUp } from '../motion/count-up';
import { Sparks } from '../motion/sparks';
import { DailyBoard } from '../ui/daily-board';

export function verdict(percentage: number): string {
  if (percentage >= 90) return 'Outstanding!';
  if (percentage >= 70) return 'Great game!';
  if (percentage >= 50) return 'Nicely done.';
  if (percentage >= 25) return 'Keep practising.';
  return 'Tough round — try again!';
}

@Component({
  selector: 'mv-result',
  imports: [RouterLink, CountUp, DailyBoard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state(); as st) {
      @if (st.error) {
        <div class="alert" role="alert">{{ st.error }}</div>
      } @else if (st.result; as r) {
        <section class="card summary fade-up">
          <div class="dial" [style.--p]="r.percentage">
            <span class="pct"><mv-count-up [value]="r.percentage" [duration]="1200" suffix="%" /></span>
            <span class="muted">of max score</span>
          </div>
          <div class="text">
            <span class="chip">{{ r.variant === 'DAILY' ? 'Daily Challenge · ' + r.variantKey : r.gameName }}</span>
            <h1>{{ title() }}</h1>
            <div class="stats">
              <div><strong><mv-count-up [value]="r.score" [duration]="1200" /></strong><span class="muted">/ {{ r.maxScore }} points</span></div>
              <div><strong>{{ r.correctAnswers }}</strong><span class="muted">/ {{ r.totalQuestions }} correct</span></div>
            </div>
            <div class="row">
              @if (r.variant === 'DAILY') {
                <a class="btn btn-primary" routerLink="/">Play more games</a>
              } @else if (r.gameId > 0) {
                <a class="btn btn-primary" [routerLink]="['/play', r.gameId]">Play again</a>
                <a class="btn" [routerLink]="['/adaptive', r.gameId]">Try adaptive</a>
                <a class="btn btn-ghost" routerLink="/">All games</a>
              } @else {
                <a class="btn btn-primary" routerLink="/">All games</a>
              }
            </div>
          </div>
        </section>

        @if (r.variant === 'DAILY') { <mv-daily-board [date]="r.variantKey" /> }

        <h2 class="review-title">Review</h2>
        <ol class="review">
          @for (item of r.review; track item.number) {
            <li class="card fade-up" [style.animation-delay.ms]="200 + $index * 60" [class.good]="item.correct" [class.bad]="!item.correct">
              <div class="row">
                <span [class]="'chip chip-' + item.difficulty.toLowerCase()">{{ item.difficulty }}</span>
                <span class="spacer"></span>
                <strong>{{ item.pointsAwarded }}/{{ item.points }}</strong>
              </div>
              <h3>{{ item.number }}. {{ item.stem }}</h3>
              <p>
                @if (item.timedOut) { <span class="no">Ran out of time.</span> }
                @else if (item.correct) { <span class="yes">✓ {{ item.yourAnswer }}</span> }
                @else { <span class="no">✗ {{ item.yourAnswer }}</span> }
                @if (!item.correct) { <span class="muted"> · answer: <strong>{{ item.correctAnswer }}</strong></span> }
              </p>
            </li>
          }
        </ol>
      } @else {
        <div class="skeleton" style="height: 240px"></div>
      }
    }
  `,
  styles: `
    .summary { display: grid; grid-template-columns: auto 1fr; gap: 32px; align-items: center; }
    .dial {
      width: 180px; aspect-ratio: 1; border-radius: 50%; display: grid; place-content: center; text-align: center;
      background: radial-gradient(closest-side, var(--bg-raised) 78%, transparent 79% 100%),
        conic-gradient(from 0deg, var(--accent-2), var(--pink) calc(var(--p) * 0.5%), var(--accent) calc(var(--p) * 1%), var(--surface-strong) 0);
      box-shadow: 0 0 60px color-mix(in srgb, var(--pink) 25%, transparent);
    }
    .pct { font-size: 2.6rem; font-weight: 900; font-variant-numeric: tabular-nums; }
    .stats { display: flex; gap: 32px; margin: 8px 0 20px; }
    .stats strong { font-size: 1.6rem; margin-right: 4px; }
    .review-title { margin-top: 36px; }
    .review { list-style: none; padding: 0; display: grid; gap: 12px; }
    .review li { padding: 18px; border-left: 5px solid var(--border); }
    .review li.good { border-left-color: var(--good); }
    .review li.bad { border-left-color: var(--bad); }
    .review h3 { margin: 10px 0 6px; font-size: 1.05rem; }
    .review p { margin: 0; }
    .yes { color: var(--good); font-weight: 700; }
    .no { color: var(--bad); font-weight: 700; }
    @media (max-width: 640px) { .summary { grid-template-columns: 1fr; justify-items: center; text-align: center; } .stats { justify-content: center; } }
  `,
})
export class Result {
  readonly sessionId = input.required<string>();
  private readonly api = inject(QuizApi);

  protected readonly state = toSignal(
    toObservable(this.sessionId).pipe(
      switchMap((id) =>
        this.api.result(id).pipe(
          map((result) => ({ result, error: null as string | null })),
          catchError((e) => of({ result: null as ResultView | null, error: errorMessage(e) })),
          startWith({ result: null as ResultView | null, error: null as string | null }),
        ),
      ),
    ),
  );

  protected readonly title = computed(() => verdict(this.state()?.result?.percentage ?? 0));
  private readonly sparks = inject(Sparks);

  constructor() {
    effect(() => {
      const r = this.state()?.result;
      if (r && r.percentage >= 70) {
        setTimeout(() => this.sparks.confetti(), 400);
      }
    });
  }
}
