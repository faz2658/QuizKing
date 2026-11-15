import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import QRCode from 'qrcode';
import { catchError, EMPTY, interval, startWith, Subscription, switchMap } from 'rxjs';
import { CatalogApi, MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { flag } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { GameCard, MatchView, RoomStatus } from '../core/models';

/** Pick a multiplayer game for a big-screen party. */
@Component({
  selector: 'mv-party-start',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge"><span class="dot"></span>For a TV, projector or classroom screen</span>
    <h1>Party mode</h1>
    <p class="muted lead">Open this on the big screen. Everyone joins on their phone with a QR code, up to 30 players,
      and the screen shows the questions and a live leaderboard.</p>
    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
    <h2>Pick a game</h2>
    <div class="grid">
      @for (g of games(); track g.gameId) {
        <button class="card spotlight pick" [disabled]="busy()" (click)="start(g.gameId)">
          <strong>{{ g.gameName }}</strong><span class="muted">{{ g.topic || (g.questionCount ? g.questionCount + " questions" : "Multiplayer") }}</span>
        </button>
      } @empty {
        <p class="muted">No multiplayer games yet. <a routerLink="/">Back to games</a></p>
      }
    </div>
  `,
  styles: `
    :host { display: block; max-width: 900px; margin: 0 auto; padding-bottom: 48px; }
    h1 { margin: 8px 0; }
    .lead { max-width: 60ch; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; }
    .pick { display: flex; flex-direction: column; gap: 4px; padding: 18px; text-align: left; cursor: pointer; color: inherit; font: inherit; }
  `,
})
export class PartyStart {
  private readonly catalog = inject(CatalogApi);
  private readonly api = inject(MultiplayerApi);
  private readonly router = inject(Router);
  protected readonly games = toSignal(this.catalog.multiplayerGames(), { initialValue: [] as GameCard[] });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected start(gameId: number): void {
    this.busy.set(true);
    this.api.createRoom(gameId, true).subscribe({
      next: (r) => this.router.navigate(['/party', r.code]),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
/** How long the previous question's answer stays on screen after it closes. */
const REVEAL_MS = 4000;

/** The big screen: join code and QR while players gather, then the live questions, reveals and leaderboard. */
@Component({
  selector: 'mv-party-screen',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <div class="alert" role="alert">{{ error() }}</div>
      <a class="btn" routerLink="/party">Start a new party</a>
    } @else if (match(); as m) {
      @if (m.status === 'COMPLETED') {
        <section class="final">
          <h1>🏆 Final results</h1>
          <div class="podium">
            @for (p of podium(); track p.userId; let i = $index) {
              <div class="step" [class]="'place-' + (i + 1)">
                <span class="medal">{{ ['🥇', '🥈', '🥉'][i] }}</span>
                <strong>{{ flag(p.country) }} {{ p.name || 'Player' }}</strong>
                <span class="pts">{{ p.score }} pts</span>
              </div>
            }
          </div>
          <ol class="rest" start="4">
            @for (p of m.standings.slice(3); track p.userId) { <li>{{ flag(p.country) }} {{ p.name || 'Player' }} · {{ p.score }}</li> }
          </ol>
          <div class="row center">
            <button class="btn btn-primary" (click)="again(m.gameId)">Play again</button>
            <a class="btn btn-ghost" routerLink="/">Done</a>
          </div>
        </section>
      } @else {
        <section class="live">
          <div class="stage">
            @if (reveal(); as r) {
              <div class="reveal fade-up" role="status">
                <span class="muted">The answer was</span>
                <strong>{{ r.correctAnswer }}</strong>
                <span>{{ r.winner ? '⚡ ' + r.winner + ' got it first' : 'Nobody got it' }}</span>
              </div>
            }
            @if (m.currentQuestion; as q) {
              <div class="meta">
                <span>Question {{ q.number }} / {{ m.totalQuestions }}</span>
                <span class="timer" [class.low]="seconds() <= 5">{{ seconds() }}</span>
              </div>
              <h1 class="stem">{{ q.stem }}</h1>
              <div class="options">
                @for (o of q.options; track $index; let i = $index) {
                  <div class="option" [class]="'c' + i"><b>{{ letters[i] }}</b> {{ o }}</div>
                }
              </div>
            }
          </div>
          <aside class="board">
            <h2>Leaderboard</h2>
            <ol>
              @for (p of m.standings.slice(0, 8); track p.userId) {
                <li><span>{{ flag(p.country) }} {{ p.name || 'Player' }}</span><strong>{{ p.score }}</strong></li>
              }
            </ol>
            <p class="muted small">Join: {{ shortLink() }}</p>
          </aside>
        </section>
      }
    } @else if (room(); as r) {
      <section class="join">
        <div class="how">
          <span class="badge"><span class="dot"></span>Party mode</span>
          <h1>Scan to join</h1>
          <p class="muted">or open <strong>{{ shortLink() }}</strong> and enter</p>
          <div class="code">{{ r.code }}</div>
          <div class="row">
            <button class="btn btn-primary big" [disabled]="r.players.length < 2 || busy()" (click)="start()">
              {{ r.players.length < 2 ? 'Waiting for players…' : 'Start with ' + r.players.length + ' players' }}
            </button>
            <button class="btn btn-ghost" (click)="fullscreen()">⛶ Full screen</button>
          </div>
        </div>
        <div class="qr card">
          @if (qr()) { <img [src]="qr()" alt="QR code to join the party" width="320" height="320" /> }
        </div>
        <div class="players">
          <h2>{{ r.players.length }} / {{ r.maxPlayers }} joined</h2>
          <ul>@for (p of r.players; track p.userId) { <li class="pop">{{ p.name }}</li> }</ul>
        </div>
      </section>
    } @else {
      <div class="skeleton" style="height: 420px"></div>
    }
  `,
  styles: `
    :host { display: block; }
    .join { display: grid; grid-template-columns: 1.1fr 0.9fr; gap: 28px; align-items: center; }
    .join h1 { font-size: clamp(2.2rem, 5vw, 3.6rem); margin: 10px 0 4px; }
    .code { font-size: clamp(2.6rem, 7vw, 5rem); font-weight: 900; letter-spacing: 0.06em; margin: 12px 0 22px; }
    .big { padding: 16px 26px; font-size: 1.1rem; }
    .qr { padding: 18px; background: #fff; justify-self: center; border-radius: 22px; }
    .qr img { display: block; width: min(320px, 70vw); height: auto; }
    .players { grid-column: 1 / -1; }
    .players ul { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 10px; }
    .players li { padding: 8px 14px; border-radius: 999px; background: var(--surface-strong); border: 1px solid var(--border); font-weight: 650; }
    .pop { animation: pop 0.35s cubic-bezier(0.2, 1.4, 0.4, 1) both; }
    @keyframes pop { from { transform: scale(0.4); opacity: 0; } }
    .live { display: grid; grid-template-columns: 1fr 300px; gap: 28px; min-height: 70vh; }
    .meta { display: flex; justify-content: space-between; align-items: center; color: var(--text-muted); font-weight: 700; }
    .timer { font-size: 2.6rem; font-weight: 900; color: var(--text); width: 2.4ch; text-align: right; }
    .timer.low { color: var(--bad); }
    .stem { font-size: clamp(1.8rem, 3.6vw, 3rem); line-height: 1.15; margin: 14px 0 28px; }
    .options { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .option { padding: 24px; border-radius: 18px; font-size: clamp(1.1rem, 2vw, 1.6rem); font-weight: 700; color: #fff; }
    .option b { margin-right: 10px; opacity: 0.8; }
    .c0 { background: #e2445c; } .c1 { background: #2f7ae5; } .c2 { background: #e9a321; } .c3 { background: #2bb673; }
    .reveal { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; padding: 14px 20px; margin-bottom: 18px;
      border-radius: 16px; background: var(--good-bg); border: 1px solid color-mix(in srgb, var(--good) 35%, transparent); }
    .reveal strong { font-size: clamp(1.4rem, 3vw, 2.2rem); color: var(--good); }
    .reveal span:last-child { font-weight: 700; margin-left: auto; }
    .board ol { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
    .board li { display: flex; justify-content: space-between; padding: 12px 14px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); font-size: 1.05rem; }
    .small { font-size: 0.85rem; margin-top: 14px; }
    .final { text-align: center; }
    .final h1 { font-size: clamp(2.2rem, 5vw, 3.4rem); }
    .podium { display: flex; justify-content: center; align-items: flex-end; gap: 18px; margin: 30px 0; }
    .step { display: flex; flex-direction: column; gap: 6px; align-items: center; justify-content: flex-end; width: 200px; padding: 20px 12px;
      border-radius: 18px 18px 0 0; background: var(--surface-strong); border: 1px solid var(--border); }
    .place-1 { order: 2; height: 240px; } .place-2 { order: 1; height: 190px; } .place-3 { order: 3; height: 150px; }
    .medal { font-size: 3rem; }
    .pts { color: var(--text-muted); font-weight: 700; }
    .rest { display: inline-flex; flex-direction: column; gap: 4px; text-align: left; }
    .center { justify-content: center; margin-top: 20px; }
    @media (max-width: 860px) { .join, .live { grid-template-columns: 1fr; } }
  `,
})
export class PartyScreen implements OnInit {
  private readonly api = inject(MultiplayerApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly code = input.required<string>();
  protected readonly letters = LETTERS;
  protected readonly flag = flag;
  protected readonly room = signal<RoomStatus | null>(null);
  protected readonly match = signal<MatchView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly qr = signal<string | null>(null);
  protected readonly seconds = signal(0);
  private readonly revealUntil = signal(0);
  private readonly now = signal(Date.now());
  private lastRevealId: number | null = null;
  private poll?: Subscription;

  protected readonly joinUrl = computed(() => `${location.origin}/room/${this.code()}`);
  protected readonly shortLink = computed(() => `${location.host}/room/${this.code()}`);
  protected readonly podium = computed(() => this.match()?.standings.slice(0, 3) ?? []);
  protected readonly reveal = computed(() => {
    const m = this.match();
    if (!m?.lastQuestion || this.now() > this.revealUntil()) return null;
    const winner = m.standings.find((p) => p.userId === m.lastQuestion?.winnerUserId);
    return { correctAnswer: m.lastQuestion.correctAnswer, winner: winner ? winner.name || 'Player' : null };
  });

  constructor() {
    effect(() => {
      QRCode.toDataURL(this.joinUrl(), { width: 640, margin: 1, color: { dark: '#14112b', light: '#ffffff' } })
        .then((url) => this.qr.set(url))
        .catch(() => this.qr.set(null));
    });
    const tick = setInterval(() => {
      this.now.set(Date.now());
      this.seconds.update((s) => Math.max(0, s - 1));
    }, 1000);
    this.destroyRef.onDestroy(() => {
      clearInterval(tick);
      this.poll?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.poll = interval(1500).pipe(startWith(0), switchMap(() => this.api.room(this.code()))).subscribe({
      next: (r) => {
        this.room.set(r);
        if (r.state === 'STARTED' && r.matchId) this.follow(r.matchId);
        if (r.state === 'CLOSED') this.error.set('This party has closed.');
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected start(): void {
    this.busy.set(true);
    this.api.startRoom(this.code()).subscribe({
      next: (r) => {
        this.room.set(r);
        if (r.matchId) this.follow(r.matchId);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected again(gameId: number): void {
    this.api.createRoom(gameId, true).subscribe({
      next: (r) => this.router.navigate(['/party', r.code]).then(() => location.reload()),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected fullscreen(): void {
    document.documentElement.requestFullscreen?.().catch(() => undefined);
  }

  /** Switches from the room to the match: the engine may take a moment to create it. */
  private follow(matchId: string): void {
    this.poll?.unsubscribe();
    const me = this.auth.user()?.userId ?? 0;
    // the engine may still be creating the match: skip failed polls instead of ending the stream
    this.poll = interval(1000).pipe(startWith(0), switchMap(() => this.api.match(matchId, me).pipe(catchError(() => EMPTY)))).subscribe({
      next: (m) => {
        this.match.set(m);
        this.seconds.set(m.currentQuestion?.secondsRemaining ?? 0);
        const id = m.lastQuestion?.questionId ?? null;
        if (id !== null && id !== this.lastRevealId) {
          if (this.lastRevealId !== null) this.revealUntil.set(Date.now() + REVEAL_MS);
          this.lastRevealId = id;
        }
        if (m.status === 'COMPLETED') this.poll?.unsubscribe();
      },
    });
  }
}
