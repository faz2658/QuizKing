import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { GameCardView } from '../ui/game-card';

type Filter = 'ALL' | 'SINGLE' | 'MULTI';

@Component({
  selector: 'mv-home',
  imports: [FormsModule, GameCardView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <div>
        <h1>Hi {{ auth.user()?.userName }}, ready to play?</h1>
        <p class="muted">Pick a game. Easy questions are quick points; advanced ones pay the most.</p>
      </div>
      <label class="search">
        <span class="sr-only">Search games</span>
        <input class="input" type="search" placeholder="Search games…" [ngModel]="query()" (ngModelChange)="query.set($event)" />
      </label>
    </section>

    @if (state().loading) {
      <div class="grid">@for (i of [1, 2, 3, 4]; track i) { <div class="skeleton tile"></div> }</div>
    } @else if (state().error) {
      <div class="alert" role="alert">{{ state().error }}</div>
    } @else {
      @if (!query() && recommended().length) {
        <h2>Picked for you</h2>
        <div class="grid">@for (g of recommended(); track g.gameId + g.mode) { <mv-game-card [game]="g" /> }</div>
      }

      <div class="row section-head">
        <h2>{{ query() ? 'Results for “' + query() + '”' : 'All games' }}</h2>
        <div class="spacer"></div>
        <div class="filters" role="group" aria-label="Game type">
          @for (f of filters; track f.id) {
            <button type="button" class="chip" [class.on]="filter() === f.id" (click)="filter.set(f.id)">{{ f.label }}</button>
          }
        </div>
      </div>
      @if (visible().length) {
        <div class="grid">@for (g of visible(); track g.gameId + g.mode) { <mv-game-card [game]="g" /> }</div>
      } @else {
        <div class="card empty">
          <h3>No games here yet</h3>
          <p class="muted">{{ query() ? 'Try another search.' : 'Games appear here once an admin creates them (or start the game engine with DEMO_DATA=true).' }}</p>
        </div>
      }
    }
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: end; gap: 24px; flex-wrap: wrap; margin-bottom: 28px; }
    .search { width: min(360px, 100%); }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 20px; margin-bottom: 36px; }
    .tile { height: 280px; }
    .section-head { margin-bottom: 12px; }
    .section-head h2 { margin: 0; }
    .filters { display: flex; gap: 6px; }
    .filters .chip { cursor: pointer; font: inherit; font-size: 0.78rem; font-weight: 700; }
    .filters .chip.on { color: var(--accent-ink); background: var(--accent); border-color: var(--accent); }
    .empty { text-align: center; padding: 40px; }
  `,
})
export class Home {
  protected readonly auth = inject(AuthService);
  private readonly catalog = inject(CatalogApi);

  protected readonly query = signal('');
  protected readonly filter = signal<Filter>('ALL');
  protected readonly filters: { id: Filter; label: string }[] = [
    { id: 'ALL', label: 'All' },
    { id: 'SINGLE', label: 'Solo' },
    { id: 'MULTI', label: 'Multiplayer' },
  ];

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      debounceTime(250),
      map((q) => q.trim()),
      distinctUntilChanged(),
      switchMap((q) =>
        (q ? this.catalog.search(q) : forkJoin([this.catalog.games(), this.catalog.multiplayerGames()]).pipe(map(([s, m]) => [...s, ...m]))).pipe(
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

  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.state().games.filter((g) => f === 'ALL' || g.mode === f);
  });
}
