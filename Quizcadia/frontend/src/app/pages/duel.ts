import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi, QuizApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';
import { DuelView, GameCard } from '../core/models';
import { WORLD } from '../core/region';

/** The link to send a friend for a challenge. */
export function duelLink(id: string): string {
  return `${location.origin}/vs/${id}`;
}

/** Shares a challenge link (share sheet or clipboard) and returns a short note for the page. */
export async function shareDuel(d: DuelView): Promise<string | null> {
  const how = await shareLink('QuizCadia challenge', `I challenge you to ${d.gameName}${d.international ? ' (International)' : ''} on QuizCadia!`, duelLink(d.id));
  return how === 'copied' ? 'Link copied: send it to your friend.' : how === 'failed' ? `Send this link to your friend: ${duelLink(d.id)}` : null;
}

/** Pick a game (or its International version) and get a link to send to a friend. */
@Component({
  selector: 'mv-duel-new',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card box fade-up">
      <span class="icon" aria-hidden="true">⚔️</span>
      <h1>Challenge a friend</h1>
      @if (guest()) {
        <p class="muted">Challenges are for players with an account. Log out and sign up (it's free) to challenge your friends.</p>
        <a class="btn" routerLink="/">Back to games</a>
      } @else {
        <p class="muted">Pick a game and send the link. You both get the same questions; the higher score wins.</p>
        <form (ngSubmit)="create()" class="form">
          <label>
            <span>Game</span>
            <select class="input" name="game" [ngModel]="gameId()" (ngModelChange)="picked.set(+$event)" required [disabled]="!games().length">
              @if (!games().length) { <option [value]="0">Loading games…</option> }
              @for (g of games(); track g.gameId) { <option [value]="g.gameId">{{ g.gameName }}</option> }
            </select>
          </label>
          <label class="check">
            <input type="checkbox" name="intl" [ngModel]="international()" (ngModelChange)="international.set($event)" />
            <span>🌍 International: questions from all over the world</span>
          </label>
          @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary" type="submit" [disabled]="busy() || !gameId()">{{ busy() ? 'Creating…' : 'Create challenge' }}</button>
        </form>
      }
    </section>
  `,
  styles: `
    .box { max-width: 560px; margin: 40px auto; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 36px; text-align: center; }
    .icon { font-size: 3rem; }
    h1 { margin: 0; }
    .form { display: flex; flex-direction: column; gap: 14px; width: 100%; text-align: left; margin-top: 8px; }
    .form label { display: flex; flex-direction: column; gap: 6px; font-weight: 700; }
    .form .check { flex-direction: row; align-items: center; gap: 10px; font-weight: 600; }
  `,
})
export class DuelNew implements OnInit {
  /** Preselected from a game card (query string). */
  readonly game = input<string>('');
  readonly intl = input<string>('');
  private readonly api = inject(QuizApi);
  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly guest = computed(() => this.auth.user()?.role === 'GUEST');

  protected readonly games = toSignal(
    this.catalog.games(WORLD).pipe(
      map((gs) => gs.filter((g) => g.mode === 'SINGLE').sort((a, b) => a.gameName.localeCompare(b.gameName))),
      catchError(() => of([] as GameCard[])),
    ),
    { initialValue: [] as GameCard[] },
  );
  protected readonly picked = signal(0);
  /** The picked game, else the one from the link, else the first. */
  protected readonly gameId = computed(() => this.picked() || Number(this.game()) || this.games()[0]?.gameId || 0);
  protected readonly international = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.international.set(this.intl() === '1');
  }

  protected create(): void {
    if (this.guest() || !this.gameId()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.createDuel(this.gameId(), this.international()).subscribe({
      next: (d) => this.router.navigate(['/vs', d.id]),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

/** One challenge: share it, accept or decline it, play it, and see who won. */
@Component({
  selector: 'mv-duel',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state(); as st) {
      @if (st.error && !st.duel) {
        <section class="card box"><h2>Challenge not found</h2><div class="alert" role="alert">{{ st.error }}</div><a class="btn" routerLink="/">Back to games</a></section>
      } @else if (st.duel; as d) {
        <section class="card box fade-up">
          <span class="icon" aria-hidden="true">{{ icon(d) }}</span>
          <span class="muted">{{ d.role === 'CHALLENGER' ? 'Your challenge' : d.challengerName + ' challenges ' + (d.role === 'OPPONENT' ? 'you' : 'a friend') }}</span>
          <h1>{{ d.gameName }}@if (d.international) { <span class="chip">🌍 International</span> }</h1>

          @switch (d.state) {
            @case ('WAITING') {
              @if (d.role === 'CHALLENGER') {
                <p>Waiting for a friend to accept. Send them the link:</p>
                <div class="link"><code>{{ link(d) }}</code><button type="button" class="btn" (click)="share(d)">📤 Share</button></div>
              } @else {
                <p>Same questions for both of you, one try each. The higher score wins.</p>
                <div class="row">
                  <button type="button" class="btn btn-primary" (click)="accept(d)" [disabled]="busy()">Accept</button>
                  <button type="button" class="btn" (click)="decline(d)" [disabled]="busy()">Decline</button>
                </div>
              }
              @if (d.expiresAt) { <p class="muted small">Open until {{ when(d.expiresAt) }}</p> }
            }
            @case ('DECLINED') {
              <p>{{ d.role === 'CHALLENGER' ? (d.friend?.name ?? 'Your friend') + ' declined this challenge.' : 'This challenge was declined.' }}</p>
              <a class="btn btn-primary" routerLink="/vs/new">New challenge</a>
            }
            @case ('EXPIRED') {
              <p>This challenge expired before {{ d.totalQuestions ? 'anyone played it' : 'a friend accepted it' }}.</p>
              <a class="btn btn-primary" routerLink="/vs/new">New challenge</a>
            }
            @case ('PLAYING') {
              @if (d.you && d.friend) {
                <div class="versus">
                  <div><strong>You</strong><span class="muted">{{ label(d.you) }}</span>@if (d.you.score !== null) { <span class="pts">{{ d.you.score }}</span> }</div>
                  <span class="vs">vs</span>
                  <div><strong>{{ d.friend.name }}</strong><span class="muted">{{ label(d.friend) }}</span>@if (d.friend.score !== null) { <span class="pts">{{ d.friend.score }}</span> }</div>
                </div>
                @if (d.you.status === 'COMPLETED') {
                  <p class="muted">Waiting for {{ d.friend.name }} to play. You'll see who won here.</p>
                } @else {
                  <p class="muted">{{ d.totalQuestions }} questions, the same for both of you. One try.</p>
                  <a class="btn btn-primary" [routerLink]="['/vs', d.id, 'play']">{{ d.you.status === 'IN_PROGRESS' ? 'Resume' : 'Play now' }}</a>
                }
                @if (d.expiresAt) { <p class="muted small">Play by {{ when(d.expiresAt) }}</p> }
              } @else {
                <p>This challenge is between two other players.</p>
              }
            }
            @case ('FINISHED') {
              @if (d.you && d.friend) {
                <p class="verdict" [class.won]="d.outcome === 'WON'">{{ d.outcome === 'WON' ? 'You won! 🏆' : d.outcome === 'TIE' ? "It's a tie! 🤝" : d.friend.name + ' wins this time.' }}</p>
                <div class="versus">
                  <div [class.winner]="d.outcome === 'WON'"><strong>You</strong><span class="pts">{{ d.you.score ?? '–' }}</span><span class="muted">{{ detail(d.you) }}</span></div>
                  <span class="vs">vs</span>
                  <div [class.winner]="d.outcome === 'LOST'"><strong>{{ d.friend.name }}</strong><span class="pts">{{ d.friend.score ?? '–' }}</span><span class="muted">{{ detail(d.friend) }}</span></div>
                </div>
                <div class="row">
                  @if (d.you.sessionId && d.you.status === 'COMPLETED') { <a class="btn" [routerLink]="['/result', d.you.sessionId]">See your answers</a> }
                  <a class="btn btn-primary" [routerLink]="['/vs/new']" [queryParams]="{ game: d.gameId, intl: d.international ? 1 : null }">Rematch</a>
                </div>
              } @else {
                <p>This challenge is over.</p>
              }
            }
          }
          @if (note()) { <p class="muted small">{{ note() }}</p> }
          @if (st.error) { <div class="alert" role="alert">{{ st.error }}</div> }
        </section>
      } @else {
        <div class="skeleton" style="height: 280px; max-width: 560px; margin: 40px auto"></div>
      }
    }
  `,
  styles: `
    .box { max-width: 560px; margin: 40px auto; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 36px; }
    .icon { font-size: 3rem; }
    h1 { margin: 0; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; justify-content: center; }
    .link { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; justify-content: center; max-width: 100%; }
    .link code { padding: 8px 12px; border-radius: 10px; background: var(--bg-sunken); overflow-wrap: anywhere; }
    .versus { display: grid; grid-template-columns: 1fr auto 1fr; gap: 14px; align-items: center; width: 100%; margin: 6px 0; }
    .versus > div { display: flex; flex-direction: column; gap: 2px; padding: 14px; border-radius: 14px; border: 1px solid var(--border); }
    .versus .winner { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 30%, transparent); }
    .vs { font-weight: 900; color: var(--text-muted); }
    .pts { font-size: 1.8rem; font-weight: 900; font-variant-numeric: tabular-nums; }
    .verdict { font-size: 1.3rem; font-weight: 800; margin: 4px 0; }
    .small { font-size: 0.85rem; margin: 0; }
  `,
})
export class DuelPage {
  readonly duelId = input.required<string>();
  private readonly api = inject(QuizApi);
  protected readonly busy = signal(false);
  protected readonly note = signal<string | null>(null);
  private readonly changed = signal<DuelView | null>(null);
  private readonly actionError = signal<string | null>(null);

  private readonly loaded = toSignal(
    toObservable(this.duelId).pipe(
      switchMap((id) =>
        this.api.duel(id).pipe(
          map((duel) => ({ duel: duel as DuelView | null, error: null as string | null })),
          catchError((e) => of({ duel: null as DuelView | null, error: errorMessage(e) })),
          startWith({ duel: null as DuelView | null, error: null as string | null }),
        ),
      ),
    ),
  );

  protected readonly state = computed(() => {
    const l = this.loaded();
    return l ? { duel: this.changed() ?? l.duel, error: this.actionError() ?? l.error } : null;
  });

  protected link(d: DuelView): string {
    return duelLink(d.id);
  }

  protected async share(d: DuelView): Promise<void> {
    this.note.set(await shareDuel(d));
  }

  protected accept(d: DuelView): void {
    this.act(this.api.acceptDuel(d.id));
  }

  protected decline(d: DuelView): void {
    this.act(this.api.declineDuel(d.id));
  }

  private act(call: ReturnType<QuizApi['acceptDuel']>): void {
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: (d) => {
        this.busy.set(false);
        this.changed.set(d);
      },
      error: (e) => {
        this.busy.set(false);
        this.actionError.set(errorMessage(e));
      },
    });
  }

  protected icon(d: DuelView): string {
    return d.state === 'FINISHED' ? (d.outcome === 'WON' ? '🏆' : d.outcome === 'TIE' ? '🤝' : '⚔️') : d.state === 'DECLINED' || d.state === 'EXPIRED' ? '⌛' : '⚔️';
  }

  protected label(p: NonNullable<DuelView['you']>): string {
    return p.status === 'COMPLETED' ? 'Finished' : p.status === 'IN_PROGRESS' ? 'Playing' : 'Not played yet';
  }

  protected detail(p: NonNullable<DuelView['you']>): string {
    return p.status !== 'COMPLETED' ? "Didn't play in time" : `${p.correctAnswers}/${this.state()?.duel?.totalQuestions} right`;
  }

  protected when(iso: string): string {
    return new Date(iso).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }
}
