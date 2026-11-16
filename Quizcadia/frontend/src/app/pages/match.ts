import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, timer } from 'rxjs';
import { MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { flag } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { MatchView } from '../core/models';
import { TimerRing } from '../ui/timer-ring';
import { CountUp } from '../motion/count-up';
import { DecryptText } from '../motion/decrypt-text';
import { Sparks } from '../motion/sparks';

/** Fastest finger first. The match is polled every second; the server decides who wins each question. */
@Component({
  selector: 'mv-match',
  imports: [RouterLink, TimerRing, CountUp, DecryptText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <div class="card"><div class="alert" role="alert">{{ error() }}</div><a class="btn" routerLink="/">Back to games</a></div>
    } @else if (match(); as m) {
      <div class="layout">
        <section class="main">
          @if (m.status === 'COMPLETED') {
            <div class="card center">
              <span class="chip">{{ m.gameName }}</span>
              <h1>{{ myRank() === 1 ? 'You won! 🏆' : 'You finished #' + myRank() }}</h1>
              <p class="muted">{{ myScore() }} points · {{ m.totalQuestions }} questions</p>
              <div class="row"><a class="btn btn-primary" [routerLink]="['/multiplayer', m.gameId]">Rematch</a><a class="btn" routerLink="/">All games</a></div>
            </div>
          } @else if (m.currentQuestion; as q) {
            <div class="card stage">
              <header class="top">
                <span class="count">Question {{ q.number }}<span class="muted"> / {{ m.totalQuestions }}</span></span>
                <mv-timer-ring [seconds]="secondsLeft()" [fraction]="secondsLeft() / m.secondsPerQuestion" />
              </header>
              <h2 class="stem"><mv-decrypt-text [text]="q.stem" /></h2>
              <div class="options">
                @for (o of q.options; track o) {
                  <button type="button" class="option" [class.mine]="q.yourAnswer === o" [disabled]="!!q.yourAnswer || sending()"
                    (click)="answer(q.questionId, o, $any($event.currentTarget))">{{ o }}</button>
                }
              </div>
              <p class="feedback" aria-live="polite">
                @if (flash(); as f) { <span [class.good]="f.good" [class.bad]="!f.good">{{ f.text }}</span> }
                @else if (q.yourAnswer) { <span class="muted">Answer locked in. Waiting for the others…</span> }
                @else { <span class="muted">First correct answer wins {{ m.pointsPerQuestion }} points. Wrong answers cost 5.</span> }
              </p>
            </div>
          }
          @if (m.lastQuestion; as last) {
            <p class="last muted">
              Last question: <strong>{{ last.correctAnswer }}</strong>
              {{ last.winnerUserId === null ? '— nobody got it' : last.winnerUserId === me ? '— you were fastest!' : '— won by ' + nameOf(last.winnerUserId) }}
            </p>
          }
        </section>

        <aside class="card board" aria-label="Scoreboard">
          <h2>Scoreboard</h2>
          <ol>
            @for (p of m.standings; track p.userId; let i = $index) {
              <li [class.me]="p.userId === me"><span class="rank">{{ i + 1 }}</span><span class="who">{{ flag(p.country) }} {{ p.userId === me ? 'You' : (p.name || 'Player ' + (p.userId % 10000)) }}</span><strong><mv-count-up [value]="p.score" [duration]="500" /></strong></li>
            }
          </ol>
        </aside>
      </div>
    } @else {
      <div class="card center"><p>Getting everyone ready…</p></div>
    }
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 1fr 280px; gap: 20px; align-items: start; }
    .center { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 40px; }
    .top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .count { font-weight: 750; }
    .stem { font-size: clamp(1.25rem, 3vw, 1.8rem); margin: 4px 0 20px; }
    .options { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
    .option { font: inherit; font-weight: 650; font-size: 1.05rem; color: var(--text); background: var(--surface); border: 2px solid var(--border); border-radius: var(--radius); padding: 16px; cursor: pointer; text-align: left; }
    .option:hover:not(:disabled) { border-color: var(--accent-2); }
    .option:disabled { cursor: default; opacity: 0.6; }
    .option.mine { border-color: var(--accent); opacity: 1; }
    .feedback { margin: 16px 0 0; font-weight: 700; min-height: 1.5em; }
    .good { color: var(--good); } .bad { color: var(--bad); }
    .last { margin-top: 14px; }
    .board ol { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
    .board li { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 12px; background: var(--bg-sunken); }
    .board li.me { outline: 2px solid var(--accent); }
    .rank { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--surface); font-weight: 800; font-size: 0.8rem; }
    .who { flex: 1; font-weight: 650; }
    @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
  `,
})
export class MatchPage implements OnInit {
  readonly matchId = input.required<string>();
  private readonly api = inject(MultiplayerApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly me = inject(AuthService).user()!.userId;
  protected readonly flag = flag;

  protected nameOf(userId: number): string {
    const p = this.match()?.standings.find((s) => s.userId === userId);
    return p?.name || 'Player ' + (userId % 10000);
  }

  protected readonly match = signal<MatchView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly flash = signal<{ good: boolean; text: string } | null>(null);
  protected readonly secondsLeft = signal(0);
  private deadline = 0;
  private subs = new Subscription();

  protected readonly myRank = computed(() => (this.match()?.standings.findIndex((s) => s.userId === this.me) ?? 0) + 1);
  protected readonly myScore = computed(() => this.match()?.standings.find((s) => s.userId === this.me)?.score ?? 0);

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => {
      this.subs.unsubscribe();
      clearTimeout(this.retry);
    });
    this.follow();
    this.subs.add(timer(0, 250).subscribe(() => this.secondsLeft.set(Math.max(0, Math.ceil((this.deadline - Date.now()) / 1000)))));
  }

  private retry: ReturnType<typeof setTimeout> | undefined;

  /**
   * Keeps the screen up to date with one request per change instead of one per second: the server answers when the
   * match differs from what we show (an answer, a bot, the next question), then we ask again.
   */
  private follow(after?: string): void {
    if (this.subs.closed) return;
    this.subs.add(
      this.api.nextMatch(this.matchId(), this.me, after).subscribe({
        next: (m) => {
          this.error.set(null);
          this.show(m);
          if (m.status !== 'COMPLETED') this.follow(m.version);
        },
        error: (e) => {
          this.error.set(errorMessage(e));
          this.retry = setTimeout(() => this.follow(after), 2000); // a dropped connection: try again shortly
        },
      }),
    );
  }

  private readonly sparks = inject(Sparks);

  protected answer(questionId: number, option: string, button?: HTMLElement): void {
    this.sending.set(true);
    this.api.answer(this.matchId(), this.me, questionId, option).subscribe({
      next: (a) => {
        this.sending.set(false);
        if (a.wonQuestion && button) {
          this.sparks.burst(button, 'var(--accent)', 16);
        }
        this.flash.set(a.wonQuestion ? { good: true, text: 'Fastest finger! +points' } : a.correct ? { good: true, text: 'Correct, but someone was faster.' } : { good: false, text: 'Wrong answer (−5).' });
        this.show(a.match);
      },
      error: (e) => {
        this.sending.set(false);
        this.flash.set({ good: false, text: errorMessage(e, 'Too late for that question.') });
      },
    });
  }

  private show(m: MatchView): void {
    const previous = this.match()?.currentQuestion?.questionId;
    this.match.set(m);
    if (m.currentQuestion && m.currentQuestion.questionId !== previous) {
      this.flash.set(null);
      this.deadline = Date.now() + m.currentQuestion.secondsRemaining * 1000;
      this.secondsLeft.set(m.currentQuestion.secondsRemaining); // don't show the old question's 0 until the next tick
    }
    if (m.status === 'COMPLETED') {
      this.subs.unsubscribe();
    }
  }
}
