import { ChangeDetectionStrategy, Component, computed, inject, OnInit } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ReportsApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { Progress } from '../core/models';
import { StreakReminderPush } from '../core/push';

/** Fraction of the way from the current level to the next (0..1). */
export function levelFraction(p: Pick<Progress, 'xp' | 'levelStartXp' | 'nextLevelXp'>): number {
  const span = p.nextLevelXp - p.levelStartXp;
  return span > 0 ? Math.max(0, Math.min(1, (p.xp - p.levelStartXp) / span)) : 1;
}

/** Level, XP towards the next level and the daily streak. */
@Component({
  selector: 'mv-progress-strip',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (progress(); as p) {
      <section class="strip fade-up" aria-label="Your progress">
        <div class="level" [title]="'Level ' + p.level">
          <span class="lv">{{ p.level }}</span>
          <div>
            <strong>{{ p.title }}</strong>
            <span class="muted">{{ p.xp }} XP · {{ p.nextLevelXp - p.xp }} to level {{ p.level + 1 }}</span>
          </div>
        </div>
        <div class="bar" role="progressbar" aria-label="XP to next level" [attr.aria-valuenow]="pct()" aria-valuemin="0" aria-valuemax="100">
          <span [style.width.%]="pct()"></span>
        </div>
        <div class="streak" [class.cold]="p.streak.current === 0" [class.pending]="p.streak.current > 0 && !p.streak.playedToday">
          <span class="flame" aria-hidden="true">🔥</span>
          <div>
            <strong>{{ p.streak.current }}-day streak@if (p.streak.shields) { <span class="shields" title="Streak shields: each covers one missed day"> · 🛡️ {{ p.streak.shields }}</span> }</strong>
            <span class="muted">
              @if (p.streak.current === 0) { Play today to start one }
              @else if (!p.streak.playedToday) { Play today to keep it! }
              @else { Best: {{ p.streak.best }} {{ p.streak.best === 1 ? 'day' : 'days' }} }
            </span>
            @switch (push.state()) {
              @case ('off') {
                <button type="button" class="remind" [disabled]="push.busy()" (click)="push.enable()">🔔 Turn on streak reminders</button>
              }
              @case ('on') {
                <button type="button" class="remind on" [disabled]="push.busy()" (click)="push.disable()" title="Turn streak reminders off">🔔 Reminders on</button>
              }
              @case ('install') {
                <span class="remind-note">📲 Add QuizCadia to your Home Screen to get streak reminders</span>
              }
              @case ('denied') {
                <span class="remind-note">🔕 Notifications are blocked for this site in your browser settings</span>
              }
            }
            @if (push.error()) { <span class="remind-note" role="alert">{{ push.error() }}</span> }
          </div>
        </div>
        <a class="btn btn-ghost" routerLink="/leaderboard">Leaderboard</a>
      </section>
    }
  `,
  styles: `
    .strip { display: grid; grid-template-columns: auto 1fr auto auto; gap: 18px 24px; align-items: center; padding: 16px 20px; margin-bottom: 24px;
      border-radius: var(--radius-lg, 22px); border: 1px solid var(--border); background: var(--bg-raised); }
    .level, .streak { display: flex; align-items: center; gap: 12px; }
    .level div, .streak div { display: flex; flex-direction: column; line-height: 1.25; }
    .lv { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; font-weight: 900; font-size: 1.2rem; color: #fff;
      background: linear-gradient(135deg, var(--accent-2), var(--pink), var(--accent)); box-shadow: 0 6px 18px color-mix(in srgb, var(--pink) 40%, transparent); }
    .bar { height: 10px; border-radius: 999px; background: var(--bg-sunken); overflow: hidden; min-width: 120px; }
    .bar span { display: block; height: 100%; background: linear-gradient(90deg, var(--accent-2), var(--pink), var(--accent)); transition: width 0.8s ease; }
    .flame { font-size: 1.8rem; }
    .remind { align-self: flex-start; margin-top: 4px; padding: 3px 10px; border-radius: 999px; border: 1px solid var(--border-strong);
      background: transparent; color: var(--text); font: inherit; font-size: 0.8rem; font-weight: 650; cursor: pointer; }
    .remind.on { border-color: color-mix(in srgb, var(--good) 50%, transparent); color: var(--good); }
    .remind-note { margin-top: 4px; font-size: 0.78rem; color: var(--text-muted); }
    .streak.cold .flame { filter: grayscale(1); opacity: 0.5; }
    .streak.pending .flame { animation: flicker 1.4s ease-in-out infinite; }
    @keyframes flicker { 50% { transform: scale(1.15) rotate(-6deg); } }
    @media (prefers-reduced-motion: reduce) { .streak.pending .flame { animation: none; } }
    @media (max-width: 760px) { .strip { grid-template-columns: 1fr; } }
  `,
})
export class ProgressStrip implements OnInit {
  protected readonly push = inject(StreakReminderPush);
  private readonly api = inject(ReportsApi);
  private readonly userId = inject(AuthService).user()?.userId ?? 0;
  protected readonly progress = toSignal(this.api.progress(this.userId).pipe(catchError(() => of(null as Progress | null))), {
    initialValue: null,
  });
  ngOnInit(): void {
    void this.push.refresh();
  }

  protected readonly pct = computed(() => {
    const p = this.progress();
    return p ? Math.round(100 * levelFraction(p)) : 0;
  });
}
