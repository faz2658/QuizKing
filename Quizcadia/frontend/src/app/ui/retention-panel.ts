import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { AdminStatsApi, RetentionDay, RetentionView } from '../core/admin-stats';
import { errorMessage } from '../core/http-errors';

/** What good looks like for a casual game, shown beside each figure. */
const BENCHMARK: Record<number, string> = { 1: 'good: 35–40%', 7: 'good: 15%+', 30: 'good: 5–10%' };

/** 0.4167 → "42%"; null → "–". */
export function percent(rate: number | null | undefined): string {
  return rate == null ? '–' : `${Math.round(rate * 100)}%`;
}

/** "2026-11-02" → "Nov 2" (the date as written, whatever the viewer's time zone). */
export function weekLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/**
 * Retention for the admin stats page: day-1/7/30 retention of new players, and weekly cohorts (who started each week
 * and how many of them were still playing in the weeks after). "Active" means finishing a game that day.
 */
@Component({
  selector: 'mv-retention-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card retention" aria-labelledby="retention-title">
      <div class="row head">
        <h2 id="retention-title">Retention</h2>
        <span class="muted">new players in the last {{ weeks() }} weeks{{ view() ? ' · ' + view()!.players : '' }}</span>
        <div class="spacer"></div>
        <div class="pill-tabs" role="group" aria-label="Weeks">
          @for (w of [8, 12, 26]; track w) {
            <button type="button" [class.on]="weeks() === w" [attr.aria-pressed]="weeks() === w" (click)="load(w)">{{ w }} weeks</button>
          }
        </div>
      </div>

      @if (error()) {
        <div class="alert" role="alert">{{ error() }}</div>
      } @else if (view(); as v) {
        <div class="tiles">
          @for (d of v.everyone; track d.day; let i = $index) {
            <div class="tile">
              <span class="muted">Day {{ d.day }}</span>
              <strong>{{ pct(d.rate) }}</strong>
              <small class="muted">{{ detail(d) }}</small>
              <small class="muted">accounts {{ pct(v.accounts[i]?.rate) }} · {{ benchmark[d.day] }}</small>
            </div>
          }
        </div>

        <div class="table-wrap">
          <table>
            <caption class="sr-only">Share of each week's new players still playing in the weeks after</caption>
            <thead>
              <tr>
                <th scope="col">Started week of</th>
                <th scope="col" class="num">Players</th>
                @for (c of columns(); track c) { <th scope="col" class="num">{{ c === 0 ? 'Week 0' : '+' + c }}</th> }
              </tr>
            </thead>
            <tbody>
              @for (c of v.cohorts; track c.weekStart) {
                <tr>
                  <th scope="row">{{ label(c.weekStart) }}</th>
                  <td class="num">{{ c.players }}@if (c.guests) { <span class="muted"> ({{ c.guests }} guests)</span> }</td>
                  @for (col of columns(); track col) {
                    @let rate = c.weeks[col];
                    <td class="num cell" [class.empty]="rate == null || !c.players" [style.--heat]="rate ?? 0">
                      {{ c.players && rate != null ? pct(rate) : '' }}
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="muted small">Active = finished at least one game that day (UTC). Day N counts only players whose day N is over.
          Week columns: share of the week's new players who played again that many weeks later.</p>
      } @else {
        <div class="skeleton" style="height: 220px"></div>
      }
    </section>
  `,
  styles: `
    .retention { display: flex; flex-direction: column; gap: 16px; }
    .head { gap: 10px; align-items: baseline; }
    .head h2 { margin: 0; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
    .tile { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border-radius: var(--radius); background: var(--surface); border: 1px solid var(--border); }
    .tile strong { font-size: 2rem; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .table-wrap { overflow-x: auto; }
    table { border-collapse: separate; border-spacing: 3px; width: 100%; font-variant-numeric: tabular-nums; font-size: 0.9rem; }
    th, td { padding: 8px 10px; text-align: left; white-space: nowrap; }
    thead th { color: var(--text-muted); font-weight: 650; }
    .num { text-align: right; }
    .cell { border-radius: 8px; background: color-mix(in srgb, var(--accent-2) calc(var(--heat) * 85%), var(--surface)); font-weight: 650; }
    .cell.empty { background: transparent; }
    .small { font-size: 0.8rem; margin: 0; }
  `,
})
export class RetentionPanel implements OnInit {
  private readonly api = inject(AdminStatsApi);
  protected readonly weeks = signal(8);
  protected readonly view = signal<RetentionView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly benchmark = BENCHMARK;
  protected readonly pct = percent;
  protected readonly label = weekLabel;
  /** Week 0 and each later week the oldest cohort has had. */
  protected readonly columns = computed(() => {
    const longest = Math.max(0, ...(this.view()?.cohorts ?? []).map((c) => c.weeks.length));
    return Array.from({ length: longest }, (_, i) => i);
  });

  ngOnInit(): void {
    this.load(this.weeks());
  }

  protected load(weeks: number): void {
    this.weeks.set(weeks);
    this.view.set(null);
    this.error.set(null);
    this.api.retention(weeks).subscribe({ next: (v) => this.view.set(v), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected detail(d: RetentionDay): string {
    return d.eligible ? `${d.returned} of ${d.eligible} came back on day ${d.day}` : `too early: nobody has reached day ${d.day}`;
  }
}
