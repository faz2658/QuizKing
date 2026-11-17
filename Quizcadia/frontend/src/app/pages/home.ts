import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { countryName, flag } from '../core/countries';
import { CountrySet, homeScope, WORLD } from '../core/region';
import { World } from '../core/world';
import { GameCardView } from '../ui/game-card';
import { BlurText } from '../motion/blur-text';
import { DailyBanner } from '../ui/daily-banner';
import { ProgressStrip } from '../ui/progress-strip';
import { FriendsStrip } from '../ui/friends-strip';

type Filter = 'ALL' | 'SINGLE' | 'MULTI';

@Component({
  selector: 'mv-home',
  imports: [FormsModule, RouterLink, GameCardView, BlurText, DailyBanner, ProgressStrip, FriendsStrip],
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
            <span class="muted"> · {{ local() ? 'Quizzes from ' + otherCountries() + ' other countries and world trivia' : 'Quizzes about ' + otherCountries() + ' countries' }}</span>
          </span>
          <span class="pz-go">Explore →</span>
        </a>
      }

      <div class="row section-head">
        <h2>{{ query() ? 'Results for “' + query() + '”' : local() ? 'Games for ' + localLabel() : 'World trivia' }}</h2>
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
  private readonly world = inject(World);

  protected readonly query = signal('');
  protected readonly filter = signal<Filter>('ALL');
  protected readonly filters: { id: Filter; label: string }[] = [
    { id: 'ALL', label: 'All' },
    { id: 'SINGLE', label: 'Solo' },
    { id: 'MULTI', label: 'Multiplayer' },
  ];

  /** Countries with their own question set; null while loading. */
  private readonly sets = toSignal<CountrySet[] | null, null>(this.catalog.countries(), { initialValue: null });

  /** The player's own country when it has a question set, otherwise WORLD (world trivia); null while loading. */
  private readonly scope = computed(() => {
    const sets = this.sets();
    return sets === null ? null : homeScope(this.world.country(), sets);
  });

  /** The player's country, when the home page shows its games. */
  protected readonly local = computed(() => {
    const s = this.scope();
    return s && s !== WORLD ? s : null;
  });

  protected readonly localLabel = computed(() => {
    const c = this.local();
    return c ? `${countryName(c)} ${flag(c)}` : '';
  });

  protected readonly otherCountries = computed(() => (this.sets() ?? []).filter((s) => s.country !== this.local() && s.games > 0).length);

  protected readonly state = toSignal(
    toObservable(computed(() => ({ q: this.query().trim(), scope: this.scope() }))).pipe(
      filter((v) => v.scope !== null || v.q !== ''),
      debounceTime(250),
      distinctUntilChanged((a, b) => a.q === b.q && (a.q !== '' || a.scope === b.scope)),
      switchMap(({ q, scope }) =>
        (q
          ? this.catalog.search(q)
          : forkJoin([this.catalog.games(scope!), this.catalog.multiplayerGames(scope!)]).pipe(map(([s, m]) => [...s, ...m]))
        ).pipe(
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

  /** Recommendations from the games this page shows (a player with their own country set sees just those). */
  protected readonly picked = computed(() => {
    if (!this.local()) {
      return this.recommended();
    }
    const ids = new Set(this.state().games.map((g) => g.gameId));
    return this.recommended().filter((g) => ids.has(g.gameId));
  });

  protected readonly gameCount = computed(() => this.visible().length);

  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.state().games.filter((g) => f === 'ALL' || g.mode === f);
  });
}
