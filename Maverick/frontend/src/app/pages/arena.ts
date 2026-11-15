import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, catchError, map, of, switchMap, timer } from 'rxjs';
import { ArenaApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { flag } from '../core/countries';
import { GameFx } from '../core/game-fx';
import { errorMessage } from '../core/http-errors';
import { ArenaRound, ArenaView } from '../core/models';
import { World } from '../core/world';
import { clock } from './world';

/** The World Arena: join the next round, play live, watch when you're out, see who's the last one standing. */
@Component({
  selector: 'mv-arena',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <h1>🏟️ World Arena</h1>
      <p class="muted">Every 15 minutes, everyone online plays the same 12 questions. Miss one and you're out. Last one standing wins (+50 XP).</p>
    </section>
    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }

    @if (view(); as v) {
      @let r = v.round;
      @switch (r.phase) {
        @case ('LIVE') {
          <section class="card live">
            <div class="row status">
              <span class="chip live-chip">● LIVE</span>
              <span><strong>{{ r.alive }}</strong> of {{ r.players }} still standing</span>
              <span class="spacer"></span>
              @if (r.you.joined) { <span [class]="r.you.alive ? 'chip good' : 'chip bad'">{{ r.you.alive ? 'You: in ✓ ' + r.you.score + ' pts' : 'You are out · watching' }}</span> }
              @else { <span class="chip">Watching</span> }
            </div>
            @if (r.question; as q) {
              <p class="muted">Question {{ q.number }} of {{ q.total }} · {{ q.secondsLeft }}s</p>
              <div class="timer"><span [style.width.%]="(100 * q.secondsLeft) / 15"></span></div>
              <h2>{{ q.stem }}</h2>
              <div class="options">
                @for (o of q.options; track o; let i = $index) {
                  <button type="button" class="option" [class.chosen]="(q.yourAnswer ?? picked()) === o"
                    [disabled]="!r.you.joined || !r.you.alive || !!q.yourAnswer || !!picked()" (click)="answer(q.index, o)">
                    <span class="key">{{ i + 1 }}</span> {{ o }}
                  </button>
                }
              </div>
            } @else if (r.reveal; as rv) {
              <p class="muted">Answer</p>
              <h2>{{ rv.stem }}</h2>
              <p class="reveal">✓ {{ rv.correctAnswer }}</p>
              @if (rv.yourAnswer) { <p [class]="rv.yourAnswer.toLowerCase() === rv.correctAnswer.toLowerCase() ? 'good-text' : 'bad-text'">You said: {{ rv.yourAnswer }}</p> }
              <p class="muted">{{ rv.eliminatedNow }} {{ rv.eliminatedNow === 1 ? 'player' : 'players' }} out this question. Next question coming…</p>
            }
          </section>
        }
        @default {
          <section class="card wait">
            <span class="muted">Next round</span>
            <div class="count">{{ countdown() }}</div>
            <p><strong>{{ r.players }}</strong> {{ r.players === 1 ? 'player has' : 'players have' }} joined</p>
            @if (r.you.joined) {
              <p class="good-text">You're in! Keep this page open; the round starts automatically.</p>
            } @else if (r.phase === 'JOINING') {
              <button class="btn btn-primary big" (click)="join()">Join the round</button>
            } @else {
              <p class="muted">Joining opens 10 minutes before the start.</p>
            }
          </section>
        }
      }

      @if (v.previous; as p) {
        <section class="card results">
          <h2>{{ p.phase === 'FINISHED' ? 'Last round' : 'Standings' }}</h2>
          @if (p.you.rank) { <p>You finished <strong>#{{ p.you.rank }}</strong> of {{ p.players }}{{ p.you.rank === 1 && p.players > 1 ? ' 👑 Last one standing!' : '' }}</p> }
          <ol>
            @for (s of p.standings; track s.userId) {
              <li [class.me]="s.userId === me"><span class="rank">{{ s.rank === 1 ? '👑' : s.rank }}</span>
                <span>{{ flag(s.country) }} {{ s.name }}</span><span class="muted">{{ s.survived }} right</span><strong>{{ s.score }}</strong></li>
            }
          </ol>
        </section>
      }
      @if (r.phase === 'LIVE' && r.standings.length) {
        <section class="card results">
          <h2>Leaders</h2>
          <ol>
            @for (s of r.standings; track s.userId) {
              <li [class.me]="s.userId === me"><span class="rank">{{ s.rank }}</span><span>{{ flag(s.country) }} {{ s.name }}</span>
                <span class="muted">{{ s.survived }} right</span><strong>{{ s.score }}</strong></li>
            }
          </ol>
        </section>
      }
    } @else {
      <div class="skeleton" style="height: 260px"></div>
    }
    <p class="muted"><a routerLink="/world">← Back to Play the world</a></p>
  `,
  styles: `
    .head h1 { margin-bottom: 4px; }
    .wait { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 36px; }
    .count { font-size: clamp(3rem, 12vw, 5rem); font-weight: 900; font-variant-numeric: tabular-nums; letter-spacing: -0.03em; }
    .big { font-size: 1.1rem; padding: 14px 30px; }
    .live .status { gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
    .live-chip { color: var(--bad); border-color: var(--bad); font-weight: 900; }
    .chip.good { color: var(--good); border-color: var(--good); }
    .chip.bad { color: var(--bad); border-color: var(--bad); }
    .timer { height: 8px; background: var(--bg-sunken); border-radius: 999px; overflow: hidden; }
    .timer span { display: block; height: 100%; background: linear-gradient(90deg, var(--accent-2), var(--pink)); transition: width 1s linear; }
    .options { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; }
    .option { text-align: left; padding: 14px 16px; border-radius: 16px; border: 1px solid var(--border); background: var(--bg-raised); color: var(--text); font-weight: 700; cursor: pointer; }
    .option.chosen { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, var(--bg-raised)); }
    .option:disabled { cursor: default; }
    .key { display: inline-grid; place-items: center; width: 24px; height: 24px; border-radius: 8px; background: var(--bg-sunken); margin-right: 8px; font-size: 0.8rem; }
    .reveal { font-size: 1.3rem; font-weight: 900; color: var(--good); }
    .good-text { color: var(--good); font-weight: 700; }
    .bad-text { color: var(--bad); font-weight: 700; }
    .results { margin-top: 18px; }
    .results ol { list-style: none; padding: 0; display: grid; gap: 6px; }
    .results li { display: grid; grid-template-columns: 36px 1fr auto 60px; gap: 10px; align-items: center; padding: 8px 12px; border-radius: 12px; background: var(--bg-sunken); }
    .results li.me { outline: 2px solid var(--accent); }
    .results li strong { text-align: right; }
    .rank { font-weight: 900; text-align: center; }
  `,
})
export class Arena implements OnInit {
  private readonly api = inject(ArenaApi);
  private readonly world = inject(World);
  private readonly fx = inject(GameFx);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly me = inject(AuthService).user()?.userId;
  protected readonly flag = flag;
  protected readonly view = signal<ArenaView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly picked = signal<string | null>(null);
  private poll = new Subscription();
  private lastQuestion = -1;
  private readonly now = toSignal(timer(0, 500).pipe(map(() => Date.now())), { initialValue: Date.now() });
  private skew = 0;
  protected readonly countdown = computed(() => {
    const v = this.view();
    return v ? clock((Date.parse(v.round.startsAt) - (this.now() + this.skew)) / 1000) : '';
  });

  ngOnInit(): void {
    this.poll = timer(0, 1000)
      .pipe(switchMap(() => this.api.view().pipe(catchError((e) => (this.error.set(errorMessage(e)), of(null))))))
      .subscribe((v) => v && this.show(v));
    this.destroyRef.onDestroy(() => this.poll.unsubscribe());
  }

  protected join(): void {
    this.api.join(this.world.country()).subscribe({ next: (v) => this.show(v), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected answer(index: number, option: string): void {
    this.picked.set(option);
    this.api.answer(index, option).subscribe({
      next: (v) => this.show(v),
      error: (e) => {
        this.picked.set(null);
        this.error.set(errorMessage(e));
      },
    });
  }

  private show(v: ArenaView): void {
    this.error.set(null);
    this.skew = Date.parse(v.serverTime) - Date.now();
    const q = v.round.question;
    if (q && q.index !== this.lastQuestion) {
      this.lastQuestion = q.index;
      this.picked.set(null);
    }
    const before: ArenaRound | undefined = this.view()?.round;
    if (before?.question && !v.round.question && v.round.reveal && before.you.alive) {
      this.fx.play(v.round.you.alive ? 'correct' : 'wrong');
    }
    this.view.set(v);
  }
}
