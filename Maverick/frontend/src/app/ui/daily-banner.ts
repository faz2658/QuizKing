import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { QuizApi } from '../core/api';
import { DailyView } from '../core/models';

/** "Hours and minutes" until the next challenge. */
export function untilNext(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

/** Today's challenge on the home page: play it, resume it, or see how you did. */
@Component({
  selector: 'mv-daily-banner',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (daily(); as d) {
      @if (d.totalQuestions > 0) {
        <section class="daily fade-up" aria-labelledby="daily-title">
          <div class="icon" aria-hidden="true">👑</div>
          <div class="text">
            <span class="eyebrow">Daily Challenge · {{ d.date }}</span>
            <h2 id="daily-title">
              @switch (d.status) {
                @case ('COMPLETED') { You scored {{ d.score }} / {{ d.maxScore }} today }
                @case ('IN_PROGRESS') { Your challenge is waiting }
                @default { {{ d.totalQuestions }} questions. Same for everyone. One shot. }
              }
            </h2>
            <p class="muted">
              @if (d.status === 'COMPLETED') { Next challenge in {{ next() }}. }
              @else { Topics: {{ d.topics.slice(0, 5).join(', ') }}{{ d.topics.length > 5 ? '…' : '' }} }
            </p>
          </div>
          @switch (d.status) {
            @case ('COMPLETED') { <a class="btn" [routerLink]="['/result', d.sessionId]">See the leaderboard</a> }
            @case ('IN_PROGRESS') { <a class="btn btn-primary" routerLink="/daily">Resume</a> }
            @default { <a class="btn btn-primary" routerLink="/daily">Play today's challenge</a> }
          }
        </section>
      }
    }
  `,
  styles: `
    .daily {
      display: grid; grid-template-columns: auto 1fr auto; gap: 20px; align-items: center; padding: 22px 26px; margin-bottom: 32px;
      border-radius: var(--radius-lg, 22px); border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border));
      background: linear-gradient(120deg, color-mix(in srgb, var(--accent) 16%, var(--bg-raised)), color-mix(in srgb, var(--pink) 12%, var(--bg-raised)));
      box-shadow: 0 10px 40px color-mix(in srgb, var(--pink) 18%, transparent);
    }
    .icon { font-size: 2.4rem; filter: drop-shadow(0 4px 12px color-mix(in srgb, var(--accent) 60%, transparent)); }
    .eyebrow { font-size: 0.78rem; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--accent); }
    h2 { margin: 4px 0; font-size: 1.3rem; }
    p { margin: 0; }
    @media (max-width: 640px) { .daily { grid-template-columns: 1fr; text-align: center; justify-items: center; } }
  `,
})
export class DailyBanner {
  private readonly api = inject(QuizApi);
  protected readonly daily = toSignal(this.api.daily().pipe(catchError(() => of(null as DailyView | null))), {
    initialValue: null,
  });
  protected readonly next = computed(() => untilNext(this.daily()?.secondsUntilNext ?? 0));
}
