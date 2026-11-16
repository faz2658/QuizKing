import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription, catchError, map, of, switchMap, timer } from 'rxjs';
import { ArenaApi, BoardsApi, CatalogApi, MultiplayerApi } from '../core/api';
import { allCountries, countryName, flag } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { ArenaView, CountryBoard, GameCard, QuickStatus } from '../core/models';
import { World } from '../core/world';
import { WorldMap } from '../ui/world-map';

/** "m:ss" for a countdown. */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Play with the world: Quick Match, who's online where, open lobbies, the next arena round, countries. */
@Component({
  selector: 'mv-world',
  imports: [FormsModule, RouterLink, WorldMap],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero card">
      <div>
        <span class="eyebrow"><span class="live-dot"></span> {{ world.online()?.online ?? 1 }} online now</span>
        <h1>Play the world</h1>
        <p class="muted">One tap, any topic: you're matched with players online right now. Nobody around? A bot keeps you company.</p>
        @switch (quick()?.state) {
          @case ('WAITING') {
            <div class="row">
              <div class="spinner" aria-hidden="true"></div>
              <strong aria-live="polite">Finding players… {{ quick()!.queueSize }} in the queue
                @if (quick()!.botInSeconds > 0) { · bot joins in {{ quick()!.botInSeconds }}s }</strong>
              <button class="btn btn-ghost" (click)="cancelQuick()">Cancel</button>
            </div>
          }
          @case ('NO_GAMES') { <div class="alert">There are no multiplayer games yet. Try again in a minute.</div> }
          @default { <button class="btn btn-primary big" (click)="startQuick()">🌍 Quick Match</button> }
        }
        @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
      </div>
      <label class="country">
        <span class="muted small">Your country</span>
        <select class="input" [ngModel]="world.country()" (ngModelChange)="world.setCountry($event)">
          <option [ngValue]="null">Not set</option>
          @for (c of countries; track c.code) { <option [ngValue]="c.code">{{ flag(c.code) }} {{ c.name }}</option> }
        </select>
      </label>
    </section>

    @if (arena(); as a) {
      <a class="arena card" routerLink="/arena">
        <span class="big-icon" aria-hidden="true">🏟️</span>
        <div>
          <strong>World Arena</strong>
          <span class="muted">
            @switch (a.round.phase) {
              @case ('LIVE') { Live now · {{ a.round.alive }} of {{ a.round.players }} still standing }
              @case ('JOINING') { Starts in {{ countdown() }} · {{ a.round.players }} joined · join now }
              @default { Next round in {{ countdown() }} · last one standing wins }
            }
          </span>
        </div>
        <span class="btn">{{ a.round.phase === 'LIVE' ? 'Watch' : 'Open' }}</span>
      </a>
    }

    <div class="cols">
      <section class="card">
        <h2>Online around the world</h2>
        <mv-world-map [points]="world.online()?.countries ?? []" />
      </section>
      <section class="card">
        <h2>Open lobbies</h2>
        @for (l of world.online()?.lobbies ?? []; track l.gameId) {
          <div class="lobby">
            <span>{{ gameName(l.gameId) }}</span>
            <span class="muted">{{ l.players }} waiting</span>
            <a class="btn" [routerLink]="['/multiplayer', l.gameId]">Join</a>
          </div>
        } @empty {
          <p class="muted">No open lobbies right now. Quick Match starts one for you.</p>
        }
        <h2 class="top-countries">Top countries this week</h2>
        @for (c of (countryBoard()?.countries ?? []).slice(0, 8); track c.country) {
          <div class="lobby"><span>{{ c.rank }}. {{ flag(c.country) }} {{ name(c.country) }}</span><span class="muted">{{ c.players }} players</span><strong>{{ c.xp }} XP</strong></div>
        } @empty {
          <p class="muted">Set your country to put it on the board.</p>
        }
      </section>
    </div>
  `,
  styles: `
    .hero { display: flex; justify-content: space-between; gap: 24px; align-items: start; flex-wrap: wrap; margin-bottom: 18px; }
    .hero h1 { margin: 8px 0; }
    .eyebrow { font-weight: 800; color: var(--good); display: inline-flex; align-items: center; gap: 6px; }
    .live-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--good); box-shadow: 0 0 0 0 var(--good); animation: live 1.6s infinite; }
    @keyframes live { 70% { box-shadow: 0 0 0 8px transparent; } }
    .big { font-size: 1.15rem; padding: 14px 30px; margin-top: 8px; }
    .country { display: flex; flex-direction: column; gap: 4px; min-width: 220px; }
    .small { font-size: 0.85rem; }
    .spinner { width: 22px; height: 22px; border-radius: 50%; border: 3px solid var(--bg-sunken); border-top-color: var(--accent); animation: spin 0.8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .arena { display: grid; grid-template-columns: auto 1fr auto; gap: 16px; align-items: center; margin-bottom: 18px; text-decoration: none; color: inherit;
      border-color: color-mix(in srgb, var(--accent) 45%, var(--border)); }
    .arena div { display: flex; flex-direction: column; }
    .big-icon { font-size: 2.2rem; }
    .cols { display: grid; grid-template-columns: 3fr 2fr; gap: 18px; }
    .cols h2 { margin-top: 0; }
    .lobby { display: grid; grid-template-columns: 1fr auto auto; gap: 10px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--border); }
    .top-countries { margin-top: 22px !important; }
    @media (max-width: 860px) { .cols { grid-template-columns: 1fr; } }
  `,
})
export class WorldPage implements OnInit {
  protected readonly world = inject(World);
  private readonly mp = inject(MultiplayerApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly flag = flag;
  protected readonly name = countryName;
  protected readonly countries = allCountries();
  protected readonly quick = signal<QuickStatus | null>(null);
  protected readonly error = signal<string | null>(null);
  private poll = new Subscription();

  private readonly games = toSignal(inject(CatalogApi).multiplayerGames().pipe(catchError(() => of([] as GameCard[]))), {
    initialValue: [] as GameCard[],
  });
  protected readonly countryBoard = toSignal(inject(BoardsApi).countries('WEEK').pipe(catchError(() => of(null as CountryBoard | null))), {
    initialValue: null,
  });
  private readonly arenaApi = inject(ArenaApi);
  protected readonly arena = toSignal(
    timer(0, 5000).pipe(switchMap(() => this.arenaApi.view().pipe(catchError(() => of(null as ArenaView | null))))),
    { initialValue: null },
  );
  private readonly now = toSignal(timer(0, 1000).pipe(map(() => Date.now())), { initialValue: Date.now() });
  protected readonly countdown = computed(() => {
    const a = this.arena();
    if (!a) return '';
    const skew = Date.parse(a.serverTime) - Date.now();
    return clock((Date.parse(a.round.startsAt) - (this.now() + skew)) / 1000);
  });

  ngOnInit(): void {
    this.world.refresh();
    this.destroyRef.onDestroy(() => this.poll.unsubscribe());
  }

  protected gameName(id: number): string {
    return this.games().find((g) => g.gameId === id)?.gameName ?? `Game ${id}`;
  }

  protected startQuick(): void {
    this.error.set(null);
    this.mp.joinQuick().subscribe({
      next: (s) => {
        this.onQuick(s);
        this.poll.unsubscribe();
        this.poll = timer(1000, 1000).pipe(switchMap(() => this.mp.quickStatus())).subscribe({
          next: (n) => this.onQuick(n),
          error: (e) => this.error.set(errorMessage(e)),
        });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected cancelQuick(): void {
    this.poll.unsubscribe();
    this.mp.leaveQuick().subscribe({ error: () => undefined });
    this.quick.set(null);
  }

  private onQuick(s: QuickStatus): void {
    this.quick.set(s);
    if (s.state === 'MATCHED' && s.matchId) {
      this.poll.unsubscribe();
      this.router.navigate(['/match', s.matchId]);
    } else if (s.state !== 'WAITING') {
      this.poll.unsubscribe();
    }
  }
}
