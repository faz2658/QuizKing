import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api';
import { countryName, flag } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { CountrySet, homeScope, internationalSets, WORLD } from '../core/region';
import { World } from '../core/world';
import { GameCardView } from '../ui/game-card';

/** Every other country's question set, and the world trivia, one tap away from the home page. */
@Component({
  selector: 'mv-international',
  imports: [RouterLink, GameCardView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="btn btn-ghost back" routerLink="/">← Games</a>
    <h1>🌍 International</h1>
    <p class="muted lead">Quizzes about other countries, written for the people who live there. Pick a country, or play world trivia.</p>

    <nav class="chips" aria-label="Countries">
      <a class="chip" [class.on]="selected() === WORLD" [routerLink]="[]" [queryParams]="{ c: WORLD }" [attr.aria-current]="selected() === WORLD ? 'page' : null">
        🌐 World trivia
      </a>
      @for (s of countries(); track s.country) {
        <a class="chip" [class.on]="selected() === s.country" [routerLink]="[]" [queryParams]="{ c: s.country }"
           [attr.aria-current]="selected() === s.country ? 'page' : null">
          {{ flag(s.country) }} {{ countryName(s.country) }}
        </a>
      }
    </nav>

    <h2>{{ selected() === WORLD ? 'World trivia' : flag(selected()) + ' ' + countryName(selected()) }}</h2>
    @if (state().loading) {
      <div class="grid">@for (i of [1, 2, 3]; track i) { <div class="skeleton tile"></div> }</div>
    } @else if (state().error) {
      <div class="alert" role="alert">{{ state().error }}</div>
    } @else if (state().games.length) {
      <div class="grid">@for (g of state().games; track g.gameId + g.mode; let i = $index) { <mv-game-card class="fade-up" [style.animation-delay.ms]="Math.min(i, 12) * 50" [game]="g" /> }</div>
    } @else {
      <div class="card empty"><h3>No games here yet</h3><p class="muted">Try another country.</p></div>
    }
  `,
  styles: `
    .back { padding-left: 0; margin-bottom: 8px; }
    h1 { margin: 0 0 6px; }
    .lead { margin: 0 0 22px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; }
    .chip { padding: 8px 14px; border-radius: 999px; border: 1px solid var(--border); background: var(--surface); color: var(--text);
      text-decoration: none; font-weight: 600; font-size: 0.92rem; white-space: nowrap; }
    .chip:hover { border-color: var(--accent); }
    .chip.on { background: var(--text); color: var(--bg); border-color: var(--text); }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 22px; margin-bottom: 44px; }
    .tile { height: 300px; }
    .empty { text-align: center; padding: 40px; }
  `,
})
export class International {
  protected readonly Math = Math;
  protected readonly WORLD = WORLD;
  protected readonly flag = flag;
  protected readonly countryName = countryName;
  private readonly catalog = inject(CatalogApi);
  private readonly world = inject(World);
  private readonly route = inject(ActivatedRoute);

  private readonly sets = toSignal(this.catalog.countries(), { initialValue: [] as CountrySet[] });
  private readonly own = computed(() => homeScope(this.world.country(), this.sets()));

  /** Every country but the player's own (theirs is on the home page). */
  protected readonly countries = computed(() => internationalSets(this.own(), this.sets(), countryName));

  private readonly param = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('c')?.toUpperCase() ?? null)), { initialValue: null });

  protected readonly selected = computed(() => {
    const p = this.param();
    return p && (p === WORLD || /^[A-Z]{2}$/.test(p)) ? p : WORLD;
  });

  protected readonly state = toSignal(
    toObservable(this.selected).pipe(
      switchMap((c) =>
        forkJoin([this.catalog.games(c), this.catalog.multiplayerGames(c)]).pipe(
          map(([s, m]) => ({ loading: false, error: null as string | null, games: [...s, ...m] })),
          catchError((e) => of({ loading: false, error: errorMessage(e), games: [] as GameCard[] })),
          startWith({ loading: true, error: null, games: [] as GameCard[] }),
        ),
      ),
    ),
    { initialValue: { loading: true, error: null, games: [] as GameCard[] } },
  );
}
