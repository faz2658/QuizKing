import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { CatalogApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { GameCard } from '../core/models';
import { WORLD } from '../core/region';
import { GameCardView } from '../ui/game-card';

/**
 * Every topic as an International quiz: questions from every country and world trivia alike, instead of the
 * usual mix that leans on the player's own country.
 */
@Component({
  selector: 'mv-international',
  imports: [RouterLink, GameCardView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="btn btn-ghost back" routerLink="/">← Games</a>
    <h1>🌍 International</h1>
    <p class="muted lead">Pick a topic and get questions from all over the world: every country's history, sport, music and more, mixed together.</p>

    @if (state().loading) {
      <div class="grid">@for (i of [1, 2, 3]; track i) { <div class="skeleton tile"></div> }</div>
    } @else if (state().error) {
      <div class="alert" role="alert">{{ state().error }}</div>
    } @else if (state().games.length) {
      <div class="grid">
        @for (g of state().games; track g.gameId; let i = $index) {
          <mv-game-card class="fade-up" [style.animation-delay.ms]="Math.min(i, 12) * 50" [game]="g" [international]="true" />
        }
      </div>
    } @else {
      <div class="card empty"><h3>No topics yet</h3><p class="muted">Check back soon.</p></div>
    }
  `,
  styles: `
    .back { padding-left: 0; margin-bottom: 8px; }
    h1 { margin: 0 0 6px; }
    .lead { margin: 0 0 22px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 22px; margin-bottom: 44px; }
    .tile { height: 300px; }
    .empty { text-align: center; padding: 40px; }
  `,
})
export class International {
  protected readonly Math = Math;
  private readonly catalog = inject(CatalogApi);

  /** The shared topics' solo games, biggest first. */
  protected readonly state = toSignal(
    this.catalog.games(WORLD).pipe(
      map((games) => ({
        loading: false,
        error: null as string | null,
        games: games.filter((g) => g.mode === 'SINGLE').sort((a, b) => (b.questionCount ?? 0) - (a.questionCount ?? 0)),
      })),
      catchError((e) => of({ loading: false, error: errorMessage(e), games: [] as GameCard[] })),
      startWith({ loading: true, error: null, games: [] as GameCard[] }),
    ),
    { initialValue: { loading: true, error: null, games: [] as GameCard[] } },
  );
}
