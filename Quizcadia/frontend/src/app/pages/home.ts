import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounce, distinctUntilChanged, forkJoin, map, of, shareReplay, startWith, switchMap, timer } from 'rxjs';
import { CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { generalAndOwnCountry, WORLD, withinCatalog } from '../core/region';
import { World } from '../core/world';
import { GameCardView } from '../ui/game-card';
import { BlurText } from '../motion/blur-text';
import { DailyBanner } from '../ui/daily-banner';
import { ProgressStrip } from '../ui/progress-strip';
import { FriendsStrip } from '../ui/friends-strip';
import { DuelList } from '../ui/duel-list';

type Filter = 'ALL' | 'SINGLE' | 'MULTI';

/** The games page: today's challenge, recommendations and every game, with search. */
@Component({
  selector: 'mv-home',
  imports: [FormsModule, RouterLink, GameCardView, BlurText, DailyBanner, ProgressStrip, FriendsStrip, DuelList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <div>
        <span class="badge"><span class="dot"></span>{{ gameCount() }} games · new questions every play</span>
        <h1><mv-blur-text [text]="'Hi ' + (auth.user()?.userName ?? 'there') + ', ready to *play?*'" /></h1>
        <p class="muted">Pick a game. Easy questions are quick points; advanced ones pay the most.</p>
        <a class="btn btn-ghost custom-link" routerLink="/custom">🎛️ Build a custom quiz</a>
      </div>
      <label class="search">
        <span class="sr-only">Search games</span>
        <span class="glass-icon" aria-hidden="true">⌕</span>
        <input class="input" type="search" placeholder="Search games…" [ngModel]="query()" (ngModelChange)="query.set($event)" />
      </label>
    </section>

    <mv-progress-strip />
    <mv-daily-banner />
    <a class="card puzzle-strip" routerLink="/puzzles">
      <span class="pz-icon" aria-hidden="true">🧩</span>
      <span><strong>Puzzle of the Day</strong><span class="muted"> · Clue Trail, Letter Vault, Four by Four and a Brain Teaser</span></span>
      <span class="pz-go">Play →</span>
    </a>
    <mv-duel-list />
    <mv-friends-strip />

    @if (state().loading) {
      <div class="grid">@for (i of [1, 2, 3, 4]; track i) { <div class="skeleton tile"></div> }</div>
    } @else if (state().error) {
      <div class="alert" role="alert">{{ state().error }}</div>
    } @else {
      @if (!query() && picked().length) {
        <h2>Picked for you</h2>
        <div class="grid">@for (g of picked(); track g.gameId + g.mode; let i = $index) { <mv-game-card class="fade-up" [style.animation-delay.ms]="i * 70" [game]="g" /> }</div>
      }

      @if (!query()) {
        <a class="card international" routerLink="/international">
          <span class="intl-icon" aria-hidden="true">🌍</span>
          <span>
            <strong>International</strong>
            <span class="muted"> · Every topic with questions from all over the world, mixed together</span>
          </span>
          <span class="pz-go">Explore →</span>
        </a>
      }

      <div class="row section-head">
        <h2>{{ query() ? 'Results for “' + query() + '”' : 'All games' }}</h2>
        <div class="spacer"></div>
        <div class="pill-tabs" role="group" aria-label="Game type">
          @for (f of filters; track f.id) {
            <button type="button" [class.on]="filter() === f.id" [attr.aria-pressed]="filter() === f.id" (click)="filter.set(f.id)">{{ f.label }}</button>
          }
        </div>
      </div>
      @if (visible().length) {
        <div class="grid">@for (g of visible(); track g.gameId + g.mode; let i = $index) { <mv-game-card class="fade-up" [style.animation-delay.ms]="Math.min(i, 12) * 50" [game]="g" /> }</div>
      } @else {
        <div class="card empty">
          <h3>No games here yet</h3>
          <p class="muted">{{ query() ? 'Try another search.' : 'Games appear here once an admin creates them (or start the game engine with DEMO_DATA=true).' }}</p>
        </div>
      }
    }
  `,
  styles: `
    .puzzle-strip { display: flex; align-items: center; gap: 14px; padding: 14px 18px; margin: -16px 0 28px; color: var(--text); text-decoration: none; }
    .puzzle-strip:hover { border-color: var(--accent); }
    .pz-icon { font-size: 1.5rem; }
    .pz-go { margin-left: auto; font-weight: 800; color: var(--accent); white-space: nowrap; }
    .head { display: flex; justify-content: space-between; align-items: end; gap: 24px; flex-wrap: wrap; margin-bottom: 36px; }
    .head h1 { margin: 14px 0 8px; }
    .head p { margin: 0; }
    .custom-link { margin-top: 12px; padding-left: 0; }
    .search { position: relative; width: min(380px, 100%); }
    .search .input { padding-left: 42px; border-radius: 999px; }
    .glass-icon { position: absolute; left: 16px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 1.2rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 22px; margin-bottom: 44px; }
    .tile { height: 300px; }
    .section-head { margin-bottom: 16px; }
    .section-head h2 { margin: 0; }
    .empty { text-align: center; padding: 40px; }
    .international { display: flex; align-items: center; gap: 14px; padding: 14px 18px; margin: 0 0 22px; color: var(--text); text-decoration: none; }
    .international:hover { border-color: var(--accent); }
    .intl-icon { font-size: 1.5rem; }
  `,
})
export class Home {
  protected readonly Math = Math;
  protected readonly auth = inject(AuthService);
  private readonly catalog = inject(CatalogApi);

  protected readonly query = signal('');
  protected readonly filter = signal<Filter>('ALL');
  protected readonly filters: { id: Filter; label: string }[] = [
    { id: 'ALL', label: 'All' },
    { id: 'SINGLE', label: 'Solo' },
    { id: 'MULTI', label: 'Multiplayer' },
  ];

  private readonly world = inject(World);

  /**
   * The games a player sees: the shared (WORLD) topics plus their own country's games (the country they picked on
   * the World page or in the Country Cup, else the one their browser's language suggests). Other countries' games
   * never show. Follows a change of country.
   */
  private readonly listed$ = toObservable(this.world.country).pipe(
    distinctUntilChanged(),
    switchMap((country) =>
      forkJoin([
        this.catalog.games(WORLD),
        this.catalog.multiplayerGames(WORLD),
        country && country !== WORLD ? this.catalog.games(country).pipe(catchError(() => of([] as GameCard[]))) : of([] as GameCard[]),
        country && country !== WORLD ? this.catalog.multiplayerGames(country) : of([] as GameCard[]),
      ]),
    ),
    map(([s, m, ownS, ownM]) => generalAndOwnCountry([...s, ...m], [...ownS, ...ownM])),
    shareReplay(1),
  );
  private readonly listed = toSignal(this.listed$.pipe(catchError(() => of([] as GameCard[]))), { initialValue: [] as GameCard[] });

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      map((q) => q.trim()),
      // wait for a pause in typing, but not for the first list (nothing typed yet): it shows at once
      debounce((q) => (q ? timer(250) : of(0))),
      distinctUntilChanged(),
      switchMap((q) =>
        (q ? forkJoin([this.catalog.search(q), this.listed$]).pipe(map(([found, all]) => withinCatalog(found, all))) : this.listed$).pipe(
          map((games) => ({ loading: false, error: null as string | null, games })),
          catchError((e) => of({ loading: false, error: errorMessage(e), games: [] as GameCard[] })),
          startWith({ loading: true, error: null, games: [] as GameCard[] }),
        ),
      ),
    ),
    { initialValue: { loading: true, error: null, games: [] as GameCard[] } },
  );

  protected readonly recommended = toSignal(
    this.catalog.recommended(this.auth.user()?.userId ?? 0).pipe(map((r) => r.slice(0, 4))),
    { initialValue: [] as GameCard[] },
  );

  /** Recommendations from the listed games only. */
  protected readonly picked = computed(() => withinCatalog(this.recommended(), this.listed()));

  protected readonly gameCount = computed(() => this.visible().length);

  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.state().games.filter((g) => f === 'ALL' || g.mode === f);
  });
}
