import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ReportsApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { Achievement } from '../core/models';

/** Earned badges first (newest first), then locked ones with progress bars. */
export function sortBadges(all: Achievement[]): Achievement[] {
  const earned = all.filter((a) => a.unlockedAt).sort((a, b) => b.unlockedAt!.localeCompare(a.unlockedAt!));
  const locked = all.filter((a) => !a.unlockedAt).sort((a, b) => b.progress / b.goal - a.progress / a.goal);
  return [...earned, ...locked];
}

@Component({
  selector: 'mv-badge-grid',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (badges().length) {
      <div class="row head"><h2>Achievements</h2><span class="spacer"></span><span class="muted">{{ earned() }} / {{ badges().length }} earned</span></div>
      <ul class="grid">
        @for (a of badges(); track a.id) {
          <li class="ach" [class.locked]="!a.unlockedAt" [attr.aria-label]="a.title + (a.unlockedAt ? ', earned' : ', locked')">
            <span class="icon" aria-hidden="true">{{ a.icon }}</span>
            <strong>{{ a.title }}</strong>
            <span class="muted desc">{{ a.description }}</span>
            @if (a.unlockedAt) {
              <span class="when">Earned {{ a.unlockedAt | date: 'mediumDate' }}</span>
            } @else if (a.goal > 1) {
              <span class="meter" role="progressbar" [attr.aria-valuenow]="a.progress" aria-valuemin="0" [attr.aria-valuemax]="a.goal">
                <span [style.width.%]="(100 * a.progress) / a.goal"></span>
              </span>
              <span class="muted small">{{ a.progress }} / {{ a.goal }}</span>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .head { margin: 32px 0 14px; }
    .head h2 { margin: 0; }
    .grid { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
    .ach { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 6px; padding: 18px 14px; border-radius: 20px;
      background: var(--bg-raised); border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--border));
      box-shadow: 0 6px 24px color-mix(in srgb, var(--pink) 14%, transparent); }
    .ach.locked { border-color: var(--border); box-shadow: none; }
    .ach.locked .icon { filter: grayscale(1); opacity: 0.45; }
    .icon { font-size: 2.2rem; }
    .desc { font-size: 0.85rem; }
    .when { font-size: 0.8rem; color: var(--accent); font-weight: 700; }
    .meter { width: 100%; height: 6px; border-radius: 999px; background: var(--bg-sunken); overflow: hidden; }
    .meter span { display: block; height: 100%; background: var(--accent); }
    .small { font-size: 0.8rem; }
  `,
})
export class BadgeGrid {
  private readonly api = inject(ReportsApi);
  private readonly userId = inject(AuthService).user()?.userId ?? 0;
  private readonly all = toSignal(this.api.achievements(this.userId).pipe(catchError(() => of([] as Achievement[]))), {
    initialValue: [] as Achievement[],
  });
  protected readonly badges = computed(() => sortBadges(this.all()));
  protected readonly earned = computed(() => this.all().filter((a) => a.unlockedAt).length);
}
