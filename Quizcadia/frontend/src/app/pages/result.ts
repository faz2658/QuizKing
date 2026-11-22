import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { BoardsApi, QuizApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { shareCard } from '../core/share-card';
import { shareLink } from '../core/invite';
import { bragLine, dailyShareText, emojiGrid, rankTitle } from '../core/brag';
import { errorMessage } from '../core/http-errors';
import { DailyBoard as DailyBoardData, ResultView } from '../core/models';
import { CountUp } from '../motion/count-up';
import { Sparks } from '../motion/sparks';
import { GameFx } from '../core/game-fx';
import { DailyBoard } from '../ui/daily-board';
import { QuestionFeedback } from '../ui/question-feedback';
import { Support } from '../ui/support';

/** A short line for the result screen, by the share of points scored. */
export function verdict(percentage: number): string {
  if (percentage >= 90) return 'Outstanding!';
  if (percentage >= 70) return 'Great game!';
  if (percentage >= 50) return 'Nicely done.';
  if (percentage >= 25) return 'Keep practising.';
  return 'Tough round — try again!';
}

/** The end-of-game screen with the score, the review and what to play next. */
@Component({
  selector: 'mv-result',
  imports: [RouterLink, CountUp, DailyBoard, QuestionFeedback, Support],
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
            <p class="rank-title">🏅 {{ rankName() }}@if (brag()) { <span> · {{ brag() }}</span> }</p>
            @if (r.variant === 'DAILY' && r.review.length) { <p class="grid-line" aria-label="Your answers, right and wrong">{{ grid() }}</p> }
            @if (r.variant === 'CHALLENGE' && r.opponentScore !== null && r.opponentScore !== undefined) {
              <p class="vs" [class.won]="r.score > r.opponentScore">
                {{ r.score > r.opponentScore ? '🏆 You beat the challenge' : r.score === r.opponentScore ? '🤝 A tie' : '😤 Not this time' }}:
                {{ r.score }} vs {{ r.opponentScore }}
              </p>
            }
            <div class="stats">
              <div><strong><mv-count-up [value]="r.score" [duration]="1200" /></strong><span class="muted">/ {{ r.maxScore }} points</span></div>
              <div><strong>{{ r.correctAnswers }}</strong><span class="muted">/ {{ r.totalQuestions }} correct</span></div>
            </div>
            <div class="row">
              <button type="button" class="btn" (click)="share(r)" [disabled]="sharing()">📤 Share result</button>
              @if (r.variant === 'DUEL') {
                <a class="btn btn-primary" [routerLink]="['/vs', r.variantKey]">⚔️ Back to the challenge</a>
              }
              @if (r.variant !== 'DAILY' && r.variant !== 'CHALLENGE' && r.variant !== 'DUEL') {
                <button type="button" class="btn" (click)="challengeFriends(r)" [disabled]="sharing()">⚔️ Challenge a friend</button>
              }
              @if (r.variant === 'DAILY') {
                <button type="button" class="btn" (click)="copyEmoji(r)">🟩 Copy emoji result</button>
                <a class="btn btn-primary" routerLink="/">Play more games</a>
              } @else if (r.gameId > 0) {
                <a class="btn btn-primary" [routerLink]="['/play', r.gameId]">Play again</a>
                <a class="btn" [routerLink]="['/adaptive', r.gameId]">Try adaptive</a>
                <a class="btn btn-ghost" routerLink="/">All games</a>
              } @else {
                <a class="btn btn-primary" routerLink="/">All games</a>
              }
            </div>
            @if (shareNote()) { <p class="muted share-note">{{ shareNote() }}</p> }
          </div>
        </section>

        <mv-support kind="card" placement="results" />

        @if (r.variant === 'DAILY') { <mv-daily-board [date]="r.variantKey" /> }

        <h2 class="review-title">Review</h2>
        <ol class="review">
          @for (item of r.review; track item.number) {
            <li class="card fade-up" [style.animation-delay.ms]="200 + $index * 60" [class.good]="item.correct" [class.bad]="!item.correct">
              <div class="row">
                <span [class]="'chip chip-' + item.difficulty.toLowerCase()">{{ item.difficulty }}</span>
                <span class="spacer"></span>
                @if ((item.multiplier ?? 1) > 1 && item.correct) { <span class="chip combo">🔥 ×{{ item.multiplier }}</span> }
                <strong>{{ item.pointsAwarded }}/{{ item.points }}</strong>
              </div>
              <h3>{{ item.number }}. {{ item.stem }}</h3>
              <p>
                @if (item.skipped) { <span class="muted">Skipped (lifeline).</span> }
                @else if (item.timedOut) { <span class="no">Ran out of time.</span> }
                @else if (item.correct) { <span class="yes">✓ {{ item.yourAnswer }}</span> }
                @else { <span class="no">✗ {{ item.yourAnswer }}</span> }
                @if (!item.correct && !item.skipped) { <span class="muted"> · answer: <strong>{{ item.correctAnswer }}</strong></span> }
              </p>
              @if (item.explanation) { <p class="why">💡 {{ item.explanation }}</p> }
              @if (item.questionId) { <mv-question-feedback [questionId]="item.questionId" /> }
            </li>
          }
        </ol>
      } @else {
        <div class="skeleton" style="height: 240px"></div>
      }
    }
  `,
  styles: `
    .rank-title { margin: -4px 0 6px; font-weight: 700; color: var(--text-muted); }
    .grid-line { font-size: 1.5rem; letter-spacing: 2px; margin: 0 0 10px; }
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
    .vs { font-weight: 800; font-size: 1.1rem; margin: 0 0 8px; }
    .vs.won { color: var(--good); }
    .review { list-style: none; padding: 0; display: grid; gap: 12px; }
    .review li { padding: 18px; border-left: 5px solid var(--border); }
    .review li.good { border-left-color: var(--good); }
    .review li.bad { border-left-color: var(--bad); }
    .review h3 { margin: 10px 0 6px; font-size: 1.05rem; }
    .review p { margin: 0; }
    .yes { color: var(--good); font-weight: 700; }
    .why { margin-top: 6px; color: var(--text-muted); }
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
  /** Today's board for a Daily Challenge result, for the brag line ("Top 4% today · #2 in Canada"). */
  private readonly daily = signal<DailyBoardData | null>(null);
  protected readonly brag = computed(() => bragLine(this.daily()));
  protected readonly grid = computed(() => emojiGrid(this.state()?.result?.review ?? []));
  protected readonly rankName = computed(() => {
    const r = this.state()?.result;
    return r ? rankTitle(r.percentage, r.variant === 'DAILY' ? 'Daily' : r.gameName) : '';
  });
  private readonly sparks = inject(Sparks);
  private readonly fx = inject(GameFx);
  private readonly auth = inject(AuthService);
  private readonly boards = inject(BoardsApi);
  protected readonly sharing = signal(false);
  protected readonly shareNote = signal<string | null>(null);

  protected challengeFriends(r: ResultView): void {
    this.sharing.set(true);
    this.shareNote.set(null);
    this.api.createChallenge(r.sessionId).subscribe({
      next: async (c) => {
        const how = await shareLink('QuizCadia challenge', `I scored ${c.score} in ${c.gameName} on QuizCadia. Can you beat me?`,
          `${location.origin}/challenge/${c.id}`);
        this.sharing.set(false);
        this.shareNote.set(how === 'copied' ? 'Challenge link copied: send it to a friend.' : how === 'failed' ? `Share this link: ${location.origin}/challenge/${c.id}` : null);
      },
      error: (e) => {
        this.sharing.set(false);
        this.shareNote.set(errorMessage(e));
      },
    });
  }

  protected async share(r: ResultView): Promise<void> {
    this.sharing.set(true);
    this.shareNote.set(null);
    try {
      let rankLine: string | null = null;
      if (r.variant === 'DAILY') {
        const board = this.daily() ?? (await firstValueFrom(this.boards.daily(r.variantKey ?? undefined)).catch(() => null));
        rankLine = bragLine(board) ?? (board?.you ? `#${board.you.rank} of ${board.players} in today's challenge` : null);
      }
      const title = r.variant === 'DAILY' ? `the Daily Challenge (${r.variantKey})` : r.gameName;
      const how = await shareCard({ player: this.auth.user()?.userName ?? 'A player', title, score: r.score, maxScore: r.maxScore,
        percentage: r.percentage, correct: r.correctAnswers, total: r.totalQuestions, rankLine, url: location.origin });
      this.shareNote.set(how === 'downloaded' ? 'Saved the card as an image: post it anywhere.' : null);
    } catch (e) {
      if ((e as Error)?.name !== 'AbortError') {
        this.shareNote.set("Couldn't create the share card on this device.");
      }
    } finally {
      this.sharing.set(false);
    }
  }

  protected async copyEmoji(r: ResultView): Promise<void> {
    const text = dailyShareText(r.variantKey ?? '', r.review, r.correctAnswers, r.totalQuestions, this.brag(), `${location.origin}/daily`);
    const how = await shareLink('QuizCadia Daily', text, '');
    this.shareNote.set(how === 'copied' ? 'Copied! Paste it in your group chat.' : how === 'failed' ? text : null);
  }

  constructor() {
    effect(() => {
      const r = this.state()?.result;
      if (r?.variant === 'DAILY' && !this.daily()) {
        this.boards.daily(r.variantKey ?? undefined).subscribe({ next: (b) => this.daily.set(b), error: () => undefined });
      }
    });
    effect(() => {
      const r = this.state()?.result;
      if (r && r.percentage >= 70) {
        setTimeout(() => {
          this.sparks.confetti();
          this.fx.play('fanfare');
        }, 400);
      }
    });
  }
}
