import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Maintenance } from '../core/maintenance';

/** Full-width strip under the top bar while the servers are down (usually a deploy), with the time to the next check. */
@Component({
  selector: 'mv-maintenance-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (maintenance.active()) {
      <div class="maint" role="status" aria-live="polite">
        <div class="container inner">
          <span class="spin" aria-hidden="true">🛠️</span>
          <p>
            @if (offline()) {
              <strong>You're offline.</strong> QuizCadia will carry on when your connection is back.
            } @else {
              <strong>QuizCadia is being updated.</strong> We'll be back in a moment; your game and sign-in are kept.
            }
            <span class="when">
              @if (maintenance.checking()) { Checking… } @else { Trying again in <span class="t">{{ countdown() }}</span> }
            </span>
          </p>
          <button type="button" class="btn btn-ghost" [disabled]="maintenance.checking()" (click)="maintenance.tryNow()">Try now</button>
        </div>
      </div>
    }
  `,
  styles: `
    .maint { background: color-mix(in srgb, var(--accent) 18%, var(--bg)); border-top: 1px solid color-mix(in srgb, var(--accent) 45%, transparent); }
    .inner { display: flex; align-items: center; gap: 12px; padding-top: 10px; padding-bottom: 10px; flex-wrap: wrap; }
    p { margin: 0; flex: 1 1 260px; font-size: 0.95rem; }
    .when { color: var(--text-muted); white-space: nowrap; margin-left: 4px; }
    .t { font-variant-numeric: tabular-nums; font-weight: 700; color: var(--text); }
    .spin { font-size: 1.2rem; }
  `,
})
export class MaintenanceBanner {
  protected readonly maintenance = inject(Maintenance);

  protected readonly countdown = computed(() => {
    const s = this.maintenance.secondsLeft();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });

  protected offline(): boolean {
    return typeof navigator !== 'undefined' && navigator.onLine === false;
  }
}
