import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { WORLD } from '../core/region';
import { CatalogApi } from '../core/api';
import { CustomDifficulty, GameCard } from '../core/models';

/** Most topics one custom quiz can mix. */
export const MAX_TOPICS = 8;

/** Solo games with one entry per topic, alphabetically. */
export function topicsOf(games: GameCard[]): GameCard[] {
  const seen = new Set<string>();
  return games
    .filter((g) => g.mode === 'SINGLE')
    .filter((g) => {
      const key = (g.topic ?? g.gameName).toLowerCase();
      return seen.has(key) ? false : (seen.add(key), true);
    })
    .sort((a, b) => (a.topic ?? a.gameName).localeCompare(b.topic ?? b.gameName));
}

/** Build a quiz from several topics, at one difficulty or mixed, 5 to 20 questions. */
@Component({
  selector: 'mv-custom',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Custom quiz</h1>
    <p class="muted">Mix up to {{ max }} topics. New questions first, as always.</p>

    <section class="card block">
      <div class="row head"><h2>Topics</h2><span class="spacer"></span><span class="muted">{{ picked().size }} / {{ max }}</span></div>
      <div class="chips" role="group" aria-label="Topics">
        @for (g of topics(); track g.gameId) {
          <button type="button" class="pick" [class.on]="picked().has(g.gameId)" [attr.aria-pressed]="picked().has(g.gameId)"
            [disabled]="!picked().has(g.gameId) && picked().size >= max" (click)="toggle(g.gameId)">{{ g.topic ?? g.gameName }}</button>
        } @empty { <p class="muted">Loading topics…</p> }
      </div>
    </section>

    <section class="card block">
      <h2>Difficulty</h2>
      <div class="pill-tabs" role="group" aria-label="Difficulty">
        @for (d of difficulties; track d.id) {
          <button type="button" [class.on]="difficulty() === d.id" [attr.aria-pressed]="difficulty() === d.id" (click)="difficulty.set(d.id)">{{ d.label }}</button>
        }
      </div>
      <h2 class="len">Length: {{ count() }} questions</h2>
      <input type="range" min="5" max="20" step="1" [ngModel]="count()" (ngModelChange)="count.set(+$event)" aria-label="Number of questions" />
    </section>

    <button class="btn btn-primary go" [disabled]="!picked().size" (click)="start()">Start custom quiz</button>
  `,
  styles: `
    .block { margin-bottom: 18px; }
    .head h2, .block h2 { margin: 0 0 12px; }
    .len { margin-top: 20px !important; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .pick { border: 1px solid var(--border); border-radius: 999px; padding: 8px 14px; background: var(--bg-raised); color: var(--text); font-weight: 700; cursor: pointer; }
    .pick.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 16%, var(--bg-raised)); }
    .pick:disabled { opacity: 0.4; cursor: not-allowed; }
    input[type='range'] { width: min(420px, 100%); accent-color: var(--accent); }
    .go { font-size: 1.05rem; padding: 14px 28px; }
  `,
})
export class Custom {
  protected readonly max = MAX_TOPICS;
  private readonly router = inject(Router);
  protected readonly topics = toSignal(inject(CatalogApi).games(WORLD).pipe(map(topicsOf), catchError(() => of([] as GameCard[]))), {
    initialValue: [] as GameCard[],
  });
  protected readonly picked = signal(new Set<number>());
  protected readonly difficulty = signal<CustomDifficulty>('MIXED');
  protected readonly count = signal(10);
  protected readonly difficulties: { id: CustomDifficulty; label: string }[] = [
    { id: 'MIXED', label: 'Mixed' },
    { id: 'EASY', label: 'Easy' },
    { id: 'MEDIUM', label: 'Medium' },
    { id: 'ADVANCED', label: 'Hard' },
  ];
  protected readonly ready = computed(() => this.picked().size > 0);

  protected toggle(id: number): void {
    const next = new Set(this.picked());
    if (!next.delete(id) && next.size < MAX_TOPICS) {
      next.add(id);
    }
    this.picked.set(next);
  }

  protected start(): void {
    this.router.navigate(['/custom/play'], {
      queryParams: { games: [...this.picked()].join(','), difficulty: this.difficulty(), count: this.count() },
    });
  }
}
