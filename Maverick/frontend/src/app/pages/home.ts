import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { GameCardView } from '../ui/game-card';
import { BlurText } from '../motion/blur-text';
import { DailyBanner } from '../ui/daily-banner';
import { ProgressStrip } from '../ui/progress-strip';

type Filter = 'ALL' | 'SINGLE' | 'MULTI';

@Component({
  selector: 'mv-home',
  imports: [FormsModule, RouterLink, GameCardView, BlurText, DailyBanner, ProgressStrip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <div>
        <span class="badge"><span class="dot"></span>{{ visible().length }} games · new questions every play</span>
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

    @if (state().loading) {
      <div class="grid">@for (i of [1, 2, 3, 4]; track i) { <div class="skeleton tile"></div> }</div>
    } @else if (state().error) {
      <div class="alert" role="alert">{{ state().error }}</div>
    } @else {
      @if (!query() && recommended().length) {
        <h2>Picked for you</h2>
        <div class="grid">@for (g of recommended(); track g.gameId + g.mode; let i = $index) { <mv-game-card class="fade-up" [style.animation-delay.ms]="i * 70" [game]="g" /> }</div>
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
