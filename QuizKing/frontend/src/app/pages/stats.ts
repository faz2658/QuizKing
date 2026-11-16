import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AccountStatsView, ActivityStatsView, AdminStatsApi } from '../core/admin-stats';
import { countryName, flag } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { AdminTabs } from '../ui/admin-tabs';

interface Bar { label: string; value: number; extra?: string }

/** Admin dashboard: sign-ups, active players and what people play. */
@Component({
  selector: 'mv-stats',
  imports: [DatePipe, AdminTabs],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mv-admin-tabs />
    <div class="head">
      <h1>Users &amp; activity</h1>
      <div class="seg" role="group" aria-label="Period">
        @for (d of periods; track d) {
          <button type="button" class="btn" [class.btn-primary]="days() === d" [attr.aria-pressed]="days() === d" (click)="setDays(d)">{{ d }} days</button>
        }
      </div>
    </div>
    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }

    @if (accounts(); as a) {
      <div class="tiles">
        <div class="card tile"><span class="muted">Accounts</span><strong>{{ a.total }}</strong>
          <small class="muted">{{ a.google }} Google · {{ a.password }} password</small></div>
        <div class="card tile"><span class="muted">New today</span><strong>{{ a.today }}</strong>
          <small class="muted">{{ a.last7Days }} this week · {{ a.last30Days }} in 30 days</small></div>
        @if (activity(); as g) {
          <div class="card tile"><span class="muted">Active today</span><strong>{{ g.activeToday }}</strong>
            <small class="muted">{{ g.active7Days }} in 7 days · {{ g.active30Days }} in 30</small></div>
          <div class="card tile"><span class="muted">Games played</span><strong>{{ g.gamesInPeriod }}</strong>
            <small class="muted">last {{ days() }} days · {{ g.gamesAllTime }} all time</small></div>
          <div class="card tile"><span class="muted">Guests</span><strong>{{ g.guestPlayers30Days }}</strong>
            <small class="muted">played in the last 30 days</small></div>
        }
      </div>
    } @else if (!error()) {
      <div class="skeleton" style="height: 120px"></div>
    }

    <div class="grid">
      <section class="card">
        <h2>Sign-ups per day</h2>
        <div class="chart" role="img" [attr.aria-label]="'Sign-ups per day, ' + signupTotal() + ' in total'">
          @for (b of signupBars(); track b.label) {
            <span class="col" [title]="b.label + ': ' + b.value"><span [style.height.%]="pct(b.value, signupMax())"></span></span>
          }
        </div>
        <div class="axis muted"><span>{{ signupBars()[0]?.label }}</span><span>today</span></div>
      </section>
      <section class="card">
        <h2>Games per day</h2>
        <div class="chart" role="img" [attr.aria-label]="'Games per day, ' + (activity()?.gamesInPeriod ?? 0) + ' in total'">
          @for (b of gameBars(); track b.label) {
            <span class="col" [title]="b.label + ': ' + b.value + ' games' + (b.extra ?? '')"><span class="alt" [style.height.%]="pct(b.value, gameMax())"></span></span>
          }
        </div>
        <div class="axis muted"><span>{{ gameBars()[0]?.label }}</span><span>today</span></div>
      </section>
    </div>

    @if (activity(); as g) {
      <div class="grid three">
        <section class="card"><h2>Game types</h2>
          @for (m of g.modes; track m.name) { <div class="line"><span>{{ m.name }}</span><strong>{{ m.count }}</strong></div> }
          @empty { <p class="muted">No games yet.</p> }
        </section>
        <section class="card"><h2>Top games</h2>
          @for (m of g.topGames; track m.name) { <div class="line"><span>{{ m.name }}</span><strong>{{ m.count }}</strong></div> }
          @empty { <p class="muted">No games yet.</p> }
        </section>
        <section class="card"><h2>Countries</h2>
          @for (c of g.countries; track c.name) { <div class="line"><span>{{ flag(c.name) }} {{ countryName(c.name) }}</span><strong>{{ c.count }}</strong></div> }
          @empty { <p class="muted">No one has picked a country yet.</p> }
        </section>
      </div>

      <section class="card">
        <h2>Most active players · last {{ days() }} days</h2>
        @if (g.topPlayers.length) {
          <table>
            <thead><tr><th>#</th><th>Player</th><th class="num">Games</th><th class="num">Points</th></tr></thead>
            <tbody>
              @for (p of g.topPlayers; track p.userId; let i = $index) {
                <tr><td>{{ i + 1 }}</td><td>{{ p.country ? flag(p.country) + ' ' : '' }}{{ p.name }}</td><td class="num">{{ p.games }}</td><td class="num">{{ p.points }}</td></tr>
              }
            </tbody>
          </table>
        } @else { <p class="muted">No games in this period.</p> }
      </section>
    }

    @if (accounts(); as a) {
      <section class="card">
        <h2>Newest accounts</h2>
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Sign-in</th><th>Joined</th></tr></thead>
          <tbody>
            @for (m of a.recent; track m.userId) {
              <tr>
                <td>{{ m.userName }}@if (m.admin) { <span class="tag">admin</span> }</td>
                <td class="email">{{ m.email }}</td>
                <td>{{ m.google ? 'Google' : 'Password' }}</td>
                <td>{{ m.createdAt | date: 'd MMM y, HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    }
  `,
  styles: `
    :host { display: block; padding-bottom: 56px; }
    .head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; justify-content: space-between; }
    .seg { display: flex; gap: 6px; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 14px; margin: 16px 0; }
    .tile { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; }
    .tile strong { font-size: 2rem; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; margin-bottom: 14px; }
    .grid.three { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
    section.card { padding: 16px 20px; margin-bottom: 14px; min-width: 0; }
    .grid section.card { margin-bottom: 0; }
    h2 { margin: 0 0 12px; font-size: 1.05rem; }
    .chart { display: flex; align-items: flex-end; gap: 2px; height: 140px; }
    .col { flex: 1; height: 100%; display: flex; align-items: flex-end; }
    .col span { display: block; width: 100%; min-height: 2px; border-radius: 3px 3px 0 0; background: var(--accent); }
    .col span.alt { background: var(--accent-2); }
    .axis { display: flex; justify-content: space-between; font-size: 0.8rem; margin-top: 6px; }
    .line { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid var(--border); }
    .line:last-child { border-bottom: 0; }
    .line strong { font-variant-numeric: tabular-nums; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 8px 6px; border-bottom: 1px solid var(--border); }
    th { color: var(--text-muted); font-weight: 600; font-size: 0.85rem; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .email { overflow-wrap: anywhere; }
    .tag { margin-left: 6px; font-size: 0.75rem; padding: 1px 6px; border-radius: 99px; background: var(--surface-strong); }
    section.card:has(table) { overflow-x: auto; }
  `,
})
export class Stats implements OnInit {
  private readonly api = inject(AdminStatsApi);
  protected readonly flag = flag;
  protected readonly countryName = countryName;
  protected readonly periods = [7, 30, 90];
  protected readonly days = signal(30);
  protected readonly accounts = signal<AccountStatsView | null>(null);
  protected readonly activity = signal<ActivityStatsView | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly signupBars = computed<Bar[]>(() =>
    (this.accounts()?.signupsByDay ?? []).map((d) => ({ label: short(d.day), value: d.count })));
  protected readonly gameBars = computed<Bar[]>(() =>
    (this.activity()?.byDay ?? []).map((d) => ({
      label: short(d.day), value: d.games, extra: ` · ${d.players} players${d.guestGames ? ` · ${d.guestGames} by guests` : ''}`,
    })));
  protected readonly signupMax = computed(() => Math.max(1, ...this.signupBars().map((b) => b.value)));
  protected readonly gameMax = computed(() => Math.max(1, ...this.gameBars().map((b) => b.value)));
  protected readonly signupTotal = computed(() => this.signupBars().reduce((n, b) => n + b.value, 0));

  ngOnInit(): void {
    this.load();
  }

  protected setDays(d: number): void {
    this.days.set(d);
    this.load();
  }

  protected pct(v: number, max: number): number {
    return (v / max) * 100;
  }

  private load(): void {
    this.error.set(null);
    const fail = (e: unknown) => this.error.set(errorMessage(e));
    this.api.accounts(this.days()).subscribe({ next: (a) => this.accounts.set(a), error: fail });
    this.api.activity(this.days()).subscribe({ next: (g) => this.activity.set(g), error: fail });
  }
}

/** "2026-11-15" → "15 Nov". */
function short(day: string): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });
}
