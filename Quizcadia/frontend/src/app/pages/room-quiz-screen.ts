import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { HostAction, MatchView } from '../core/models';
import { followMatch, points, shares } from '../core/room-quiz';

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

/**
 * The big screen of a live room quiz: the question with a countdown and how many answered, then the answer with how
 * the room voted and the leaderboard, then the final standings. The host paces it from here (also with the space bar
 * or the right arrow key).
 */
@Component({
  selector: 'mv-room-quiz-screen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (match(); as m) {
      @switch (m.phase) {
        @case ('GET_READY') {
          <section class="ready fade-up">
            <span class="badge"><span class="dot"></span>Room quiz · {{ m.totalQuestions }} questions · medium</span>
            <h1>Get ready!</h1>
            <div class="count" aria-live="polite">{{ secondsToStart() }}</div>
            <p class="muted">{{ m.playerCount }} {{ m.playerCount === 1 ? 'player' : 'players' }} · answer on your phone</p>
            <div class="controls"><button class="btn btn-primary" (click)="act('NEXT')" [disabled]="busy()">Start now</button></div>
          </section>
        }
        @case ('QUESTION') {
          @if (m.currentQuestion; as q) {
            <section class="question">
              <div class="meta">
                <span>Question {{ q.number }} / {{ m.totalQuestions }}</span>
                <span class="timer" [class.low]="secondsLeft() <= 5" role="timer" [attr.aria-label]="secondsLeft() + ' seconds left'">{{ secondsLeft() }}</span>
              </div>
              <h1 class="stem">{{ q.stem }}</h1>
              <div class="options">
                @for (o of q.options; track $index; let i = $index) {
                  <div class="option" [class]="'c' + i"><b>{{ letters[i] }}</b> {{ o }}</div>
                }
              </div>
              <div class="progress" role="status">
                <div class="bar"><span [style.width.%]="answeredShare()"></span></div>
                <strong class="answered">{{ m.answered ?? 0 }} / {{ m.playerCount }} answered</strong>
              </div>
              <div class="controls">
                <button class="btn btn-primary" (click)="act('REVEAL')" [disabled]="busy()">Show answer</button>
                <button class="btn btn-ghost" (click)="act('END')" [disabled]="busy()">End quiz</button>
              </div>
            </section>
          }
        }
        @case ('REVEAL') {
          @if (m.lastQuestion; as r) {
            <section class="reveal">
              <div class="main">
                <div class="meta"><span>Question {{ r.number }} / {{ m.totalQuestions }}</span><span>{{ r.answered ?? 0 }} answered</span></div>
                <h1 class="stem small">{{ r.stem }}</h1>
                <ul class="votes" aria-label="How the room answered">
                  @for (o of r.options ?? []; track $index; let i = $index) {
                    <li [class.right]="o === r.correctAnswer" [class]="'c' + i">
                      <span class="label"><b>{{ letters[i] }}</b> {{ o }} @if (o === r.correctAnswer) { <span aria-label="correct answer">✓</span> }</span>
                      <span class="vote"><span class="fill" [style.width.%]="voteShares()[i]"></span></span>
                      <strong class="n">{{ r.counts?.[i] ?? 0 }}</strong>
                    </li>
                  }
                </ul>
              </div>
              <aside class="board">
                <h2>Leaderboard</h2>
                <ol>
                  @for (p of m.standings.slice(0, 8); track p.userId; let i = $index) {
                    <li class="pop"><span><b>{{ i + 1 }}</b> {{ p.name || 'Player' }}</span><strong>{{ fmt(p.score) }}</strong></li>
                  }
                </ol>
              </aside>
              <div class="controls wide">
                <button class="btn btn-primary big" (click)="act('NEXT')" [disabled]="busy()">
                  {{ (r.number ?? 0) >= m.totalQuestions ? 'Final results' : 'Next question' }} →
                </button>
                @if (secondsToNext() > 0) { <span class="muted">Next in {{ secondsToNext() }} s</span> }
                <button class="btn btn-ghost" (click)="act('END')" [disabled]="busy()">End quiz</button>
              </div>
            </section>
          }
        }
        @case ('FINISHED') {
          <section class="final">
            <h1>🏆 Final results</h1>
            <div class="podium">
              @for (p of m.standings.slice(0, 3); track p.userId; let i = $index) {
                <div class="step" [class]="'place-' + (i + 1)">
                  <span class="medal">{{ ['🥇', '🥈', '🥉'][i] }}</span>
                  <strong>{{ p.name || 'Player' }}</strong>
                  <span class="pts">{{ fmt(p.score) }} pts · {{ p.questionsWon }} right</span>
                </div>
              }
            </div>
            @if (m.standings.length > 3) {
              <ol class="rest" start="4">
                @for (p of m.standings.slice(3, 20); track p.userId) { <li>{{ p.name || 'Player' }} · {{ fmt(p.score) }}</li> }
              </ol>
            }
            <div class="controls center">
              <button class="btn btn-primary" (click)="again.emit((m.revealSeconds ?? 0) > 0)">New quiz</button>
            </div>
          </section>
        }
      }
      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
    } @else {
      <section class="ready">
        <h1>Picking 20 questions…</h1>
        @if (slow()) { <p class="muted">This is taking longer than usual. Check that the question service is running.</p> }
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .ready { text-align: center; padding: 40px 0; }
    .ready h1 { font-size: clamp(2.4rem, 6vw, 4.4rem); margin: 18px 0 6px; }
    .count { font-size: clamp(5rem, 16vw, 10rem); font-weight: 900; line-height: 1; color: var(--accent); }
    .meta { display: flex; justify-content: space-between; align-items: center; color: var(--text-muted); font-weight: 700; gap: 12px; }
    .timer { font-size: clamp(2.4rem, 5vw, 3.6rem); font-weight: 900; color: var(--text); min-width: 2.4ch; text-align: right; }
    .timer.low { color: var(--bad); }
    .stem { font-size: clamp(1.8rem, 3.8vw, 3.2rem); line-height: 1.15; margin: 14px 0 28px; }
    .stem.small { font-size: clamp(1.4rem, 2.8vw, 2.4rem); margin-bottom: 20px; }
    .options { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .option { padding: 26px 24px; border-radius: 18px; font-size: clamp(1.1rem, 2.2vw, 1.7rem); font-weight: 700; color: #fff; }
    .option b { margin-right: 10px; opacity: 0.8; }
    .c0 { --c: #e2445c; } .c1 { --c: #2f7ae5; } .c2 { --c: #e9a321; } .c3 { --c: #2bb673; } .c4 { --c: #8a5cf6; } .c5 { --c: #14a3b8; }
    .option { background: var(--c); }
    .progress { display: flex; align-items: center; gap: 16px; margin-top: 24px; }
    .bar { flex: 1; height: 14px; border-radius: 999px; background: var(--surface-strong); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--accent); transition: width 0.4s ease; }
    .answered { font-size: 1.3rem; white-space: nowrap; }
    .controls { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-top: 22px; }
    .controls.center { justify-content: center; }
    .controls.wide { grid-column: 1 / -1; }
    .big { padding: 16px 28px; font-size: 1.15rem; }
    .reveal { display: grid; grid-template-columns: 1fr 320px; gap: 28px; }
    .votes { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 12px; }
    .votes li { display: grid; grid-template-columns: minmax(0, 1.2fr) 1fr 3ch; gap: 14px; align-items: center; padding: 14px 18px;
      border-radius: 16px; background: var(--surface); border: 2px solid var(--border); opacity: 0.55; font-size: clamp(1rem, 1.8vw, 1.35rem); }
    .votes li.right { opacity: 1; border-color: var(--good); background: var(--good-bg); }
    .votes .label { font-weight: 700; }
    .votes .label b { color: var(--c); margin-right: 6px; }
    .vote { height: 18px; border-radius: 999px; background: var(--surface-strong); overflow: hidden; }
    .fill { display: block; height: 100%; background: var(--c); animation: grow 0.6s ease both; }
    @keyframes grow { from { width: 0 !important; } }
    .n { text-align: right; font-size: 1.3rem; }
    .board h2 { margin-top: 0; }
    .board ol { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
    .board li { display: flex; justify-content: space-between; gap: 10px; padding: 12px 14px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); font-size: 1.1rem; }
    .board li b { color: var(--text-muted); margin-right: 6px; }
    .pop { animation: pop 0.35s cubic-bezier(0.2, 1.4, 0.4, 1) both; }
    @keyframes pop { from { transform: scale(0.6); opacity: 0; } }
    .final { text-align: center; }
    .final h1 { font-size: clamp(2.2rem, 5vw, 3.4rem); }
    .podium { display: flex; justify-content: center; align-items: flex-end; gap: 18px; margin: 30px 0; }
    .step { display: flex; flex-direction: column; gap: 6px; align-items: center; justify-content: flex-end; width: 210px; padding: 20px 12px;
      border-radius: 18px 18px 0 0; background: var(--surface-strong); border: 1px solid var(--border); }
    .place-1 { order: 2; height: 250px; } .place-2 { order: 1; height: 200px; } .place-3 { order: 3; height: 160px; }
    .medal { font-size: 3rem; }
    .pts { color: var(--text-muted); font-weight: 700; }
    .rest { display: inline-flex; flex-direction: column; gap: 4px; text-align: left; }
    @media (max-width: 860px) { .reveal { grid-template-columns: 1fr; } .options { grid-template-columns: 1fr; } }
  `,
})
export class RoomQuizScreen implements OnInit {
  private readonly api = inject(MultiplayerApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly matchId = input.required<string>();
  /** The host wants another quiz (true: one that moves on by itself, like this one). */
  readonly again = output<boolean>();
  protected readonly letters = LETTERS;
  protected readonly fmt = points;
  protected readonly match = signal<MatchView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly slow = signal(false);
  private readonly now = signal(Date.now());
  /** When the open question closes, the first one opens or the next one opens by itself (local clock). */
  private readonly deadline = signal(0);
  private readonly startsAt = signal(0);
  private readonly nextAt = signal(0);
  private sub?: Subscription;

  protected readonly secondsLeft = computed(() => Math.max(0, Math.ceil((this.deadline() - this.now()) / 1000)));
  protected readonly secondsToStart = computed(() => Math.max(0, Math.ceil((this.startsAt() - this.now()) / 1000)));
  protected readonly secondsToNext = computed(() => Math.max(0, Math.ceil((this.nextAt() - this.now()) / 1000)));
  protected readonly answeredShare = computed(() => {
    const m = this.match();
    return m?.playerCount ? Math.round(((m.answered ?? 0) * 100) / m.playerCount) : 0;
  });
  protected readonly voteShares = computed(() => shares(this.match()?.lastQuestion?.counts ?? []));

  constructor() {
    const tick = setInterval(() => this.now.set(Date.now()), 250);
    const slow = setTimeout(() => this.slow.set(true), 15000);
    this.destroyRef.onDestroy(() => {
      clearInterval(tick);
      clearTimeout(slow);
      this.sub?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.sub = followMatch(this.api, this.matchId(), this.auth.user()?.userId ?? 0).subscribe((m) => this.show(m));
  }

  @HostListener('document:keydown', ['$event'])
  protected key(e: KeyboardEvent): void {
    const target = e.target as HTMLElement | null;
    if (target?.closest('input, textarea, button, select, a')) return;
    const phase = this.match()?.phase;
    if ((e.key === ' ' || e.key === 'ArrowRight') && phase && phase !== 'FINISHED') {
      e.preventDefault();
      this.act(phase === 'QUESTION' ? 'REVEAL' : 'NEXT');
    }
  }

  protected act(action: HostAction): void {
    this.busy.set(true);
    this.api.host(this.matchId(), action).subscribe({
      next: (m) => {
        this.busy.set(false);
        this.error.set(null);
        this.show(m);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  private show(m: MatchView): void {
    const now = Date.now();
    this.now.set(now);
    this.match.set(m);
    this.deadline.set(now + (m.currentQuestion?.secondsRemaining ?? 0) * 1000);
    this.startsAt.set(now + (m.secondsToStart ?? 0) * 1000);
    this.nextAt.set(now + (m.secondsToNext ?? 0) * 1000);
  }
}
