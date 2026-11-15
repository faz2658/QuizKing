import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { ReportsApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { GameMode, GameReport, UserSummary } from '../core/models';
import { CountUp } from '../motion/count-up';
import { BadgeGrid } from '../ui/badge-grid';
import { ProgressStrip } from '../ui/progress-strip';

const MODE_LABEL: Record<GameMode, string> = { SINGLE: 'Timed', MULTI: 'Multiplayer', ADAPTIVE: 'Adaptive' };

@Component({
  selector: 'mv-profile',
  imports: [DatePipe, CountUp, BadgeGrid, ProgressStrip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>My stats</h1>
    <mv-progress-strip />
    @if (data(); as d) {
      @if (d.error) {
        <div class="alert" role="alert">{{ d.error }}</div>
      } @else if (d.summary; as s) {
        <div class="tiles">
          <div class="card tile"><span class="muted">Games played</span><strong><mv-count-up [value]="s.gamesPlayed" /></strong></div>
          <div class="card tile"><span class="muted">Average accuracy</span><strong><mv-count-up [value]="s.averageAccuracy" suffix="%" /></strong></div>
          <div class="card tile"><span class="muted">Multiplayer wins</span><strong><mv-count-up [value]="s.multiplayerWins" /></strong></div>
        </div>

        <div class="modes">
          @for (m of modes; track m) {
            <div class="card mode">
              <h3>{{ label(m) }}</h3>
              <p class="muted">{{ s.modes[m].gamesPlayed }} played · best {{ s.modes[m].bestScore }} · {{ s.modes[m].averageAccuracy }}% accuracy</p>
              <div class="bar"><span [style.width.%]="s.modes[m].averageAccuracy"></span></div>
            </div>
          }
        </div>

        <mv-badge-grid />

        <div class="row history-head">
          <h2>History</h2>
          <span class="spacer"></span>
          <div class="row" role="group" aria-label="Filter by mode">
            <button type="button" class="chip" [class.on]="mode() === null" (click)="mode.set(null)">All</button>
            @for (m of modes; track m) {
              <button type="button" class="chip" [class.on]="mode() === m" (click)="mode.set(m)">{{ label(m) }}</button>
            }
          </div>
        </div>
        @if (history().length) {
          <div class="card table-wrap">
            <table>
              <thead><tr><th>Game</th><th>Mode</th><th>Score</th><th>Correct</th><th>Result</th><th>When</th></tr></thead>
              <tbody>
                @for (r of history(); track r.id) {
                  <tr>
                    <td>{{ r.gameName }}</td>
                    <td><span class="chip">{{ label(r.mode) }}</span></td>
                    <td class="num">{{ r.score }}{{ r.maxScore ? ' / ' + r.maxScore : '' }}</td>
                    <td class="num">{{ r.correctAnswers }} / {{ r.totalQuestions }}</td>
                    <td>{{ outcome(r) }}</td>
                    <td class="muted">{{ r.completedAt | date: 'medium' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="card empty muted">No games yet. Results show up here a moment after you finish a game.</div>
        }
      } @else {
        <div class="skeleton" style="height: 200px"></div>
      }
    }
  `,
  styles: `
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 16px; }
    .tile { display: flex; flex-direction: column; gap: 4px; }
    .tile strong { font-size: 2.2rem; font-weight: 850; }
    .modes { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }
    .mode h3 { margin: 0 0 4px; }
    .bar { height: 8px; border-radius: 999px; background: var(--bg-sunken); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--accent-2); }
    .history-head { margin: 32px 0 12px; }
    .history-head h2 { margin: 0; }
    .chip { cursor: pointer; font: inherit; font-size: 0.78rem; font-weight: 700; }
    .chip.on { color: var(--accent-ink); background: var(--accent); border-color: var(--accent); }
    .table-wrap { padding: 0; overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 12px 16px; border-bottom: 1px solid var(--border); white-space: nowrap; }
    th { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
    tr:last-child td { border-bottom: 0; }
    .num { font-variant-numeric: tabular-nums; }
    .empty { text-align: center; padding: 32px; }
  `,
})
export class Profile {
  private readonly api = inject(ReportsApi);
  private readonly userId = inject(AuthService).user()!.userId;

  protected readonly modes: GameMode[] = ['SINGLE', 'ADAPTIVE', 'MULTI'];
  protected readonly mode = signal<GameMode | null>(null);

  protected readonly data = toSignal(
    forkJoin([this.api.summary(this.userId), this.api.reports(this.userId)]).pipe(
      map(([summary, reports]) => ({ summary, reports, error: null as string | null })),
      catchError((e) => of({ summary: null as UserSummary | null, reports: [] as GameReport[], error: errorMessage(e) })),
      startWith({ summary: null as UserSummary | null, reports: [] as GameReport[], error: null as string | null }),
    ),
  );

  protected readonly history = computed(() => {
    const m = this.mode();
    return (this.data()?.reports ?? []).filter((r) => m === null || r.mode === m);
  });

  protected label(m: GameMode): string {
    return MODE_LABEL[m];
  }

  protected outcome(r: GameReport): string {
    if (r.mode === 'MULTI') return r.rank === 1 ? '🏆 Won' : `#${r.rank} of ${r.players}`;
    if (r.mode === 'ADAPTIVE') return `Reached level ${r.highestLevel}`;
    return r.maxScore ? `${Math.round((100 * r.score) / r.maxScore)}%` : '';
  }
}
