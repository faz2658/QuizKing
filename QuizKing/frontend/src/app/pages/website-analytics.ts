import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { GaRow, WebsiteAnalyticsApi, WebsiteAnalyticsView } from '../core/admin-stats';
import { errorMessage } from '../core/http-errors';
import { AdminTabs } from '../ui/admin-tabs';

/** Google Analytics for admins: visitors, page views, where they come from and what they open. */
@Component({
  selector: 'mv-website-analytics',
  imports: [AdminTabs, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mv-admin-tabs />
    <div class="head">
      <h1>Website analytics</h1>
      <div class="seg" role="group" aria-label="Period">
        @for (d of periods; track d) {
          <button type="button" class="btn" [class.btn-primary]="days() === d" [attr.aria-pressed]="days() === d" (click)="setDays(d)">{{ d }} days</button>
        }
      </div>
    </div>
    <p class="muted small">From Google Analytics. Only visitors who allowed analytics are counted, and numbers can lag by a few minutes.</p>

    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }

    @if (view(); as v) {
      @if (!v.configured) {
        <section class="card setup">
          <h2>Connect Google Analytics</h2>
          <p>{{ v.error }}</p>
          <ol>
            <li>In Google Cloud, create a service account and download its JSON key.</li>
            <li>In Google Analytics, open <strong>Admin → Property access management</strong> and add the service account's email as a <strong>Viewer</strong>.</li>
            <li>On the server, put the key in <code>~/quiz-king/secrets/ga-service-account.json</code> and set <code>GA_PROPERTY_ID</code> and <code>GA_CREDENTIALS_FILE=/run/secrets/ga-service-account.json</code> in <code>.env</code>.</li>
          </ol>
        </section>
      } @else if (v.error) {
        <div class="alert" role="alert">{{ v.error }}</div>
      } @else if (v.totals; as t) {
        <div class="tiles">
          <div class="card tile live"><span class="muted">Active right now</span><strong>{{ v.activeNow }}</strong><small class="muted">last 30 minutes</small></div>
          <div class="card tile"><span class="muted">Visitors</span><strong>{{ t.activeUsers | number }}</strong><small class="muted">{{ t.newUsers | number }} new</small></div>
          <div class="card tile"><span class="muted">Page views</span><strong>{{ t.pageViews | number }}</strong><small class="muted">{{ t.sessions | number }} visits</small></div>
          <div class="card tile"><span class="muted">Time per visit</span><strong>{{ duration(t.avgSessionSeconds) }}</strong><small class="muted">{{ t.engagementRate * 100 | number: '1.0-0' }}% engaged</small></div>
        </div>

        <section class="card">
          <h2>Visitors and page views per day</h2>
          <div class="chart" role="img" [attr.aria-label]="'Visitors per day over ' + days() + ' days'">
            @for (d of v.byDay; track d.day) {
              <span class="col" [title]="d.day + ': ' + d.activeUsers + ' visitors, ' + d.pageViews + ' page views'">
                <span class="views" [style.height.%]="pct(d.pageViews, maxViews())"></span>
                <span class="users" [style.height.%]="pct(d.activeUsers, maxViews())"></span>
              </span>
            } @empty { <p class="muted">No visits in this period yet.</p> }
          </div>
          <div class="legend muted"><span class="k views"></span>Page views <span class="k users"></span>Visitors</div>
        </section>

        <div class="grid">
          <section class="card"><h2>Top pages</h2>
            @for (r of v.pages; track r.name) { <div class="line"><span class="mono">{{ r.name }}</span><strong>{{ r.value | number }}</strong></div> }
            @empty { <p class="muted">No page views yet.</p> }
          </section>
          <section class="card"><h2>Countries</h2>
            @for (r of v.countries; track r.name) { <div class="line"><span>{{ r.name }}</span><strong>{{ r.value | number }}</strong></div> }
            @empty { <p class="muted">No visitors yet.</p> }
          </section>
          <section class="card"><h2>Where visitors come from</h2>
            @for (r of v.channels; track r.name) { <div class="line"><span>{{ r.name }}</span><strong>{{ r.value | number }}</strong></div> }
            @empty { <p class="muted">No visits yet.</p> }
          </section>
          <section class="card"><h2>Devices</h2>
            @for (r of v.devices; track r.name) {
              <div class="bar-line"><span>{{ cap(r.name) }}</span><span class="bar"><span [style.width.%]="share(r, v.devices)"></span></span><strong>{{ share(r, v.devices) | number: '1.0-0' }}%</strong></div>
            } @empty { <p class="muted">No visitors yet.</p> }
          </section>
          <section class="card"><h2>Events</h2>
            @for (r of v.events; track r.name) { <div class="line"><span class="mono">{{ r.name }}</span><strong>{{ r.value | number }}</strong></div> }
            @empty { <p class="muted">No events yet.</p> }
          </section>
        </div>
      }
    } @else if (!error()) {
      <div class="skeleton" style="height: 220px"></div>
    }
  `,
  styles: `
    :host { display: block; padding-bottom: 56px; }
    .head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; justify-content: space-between; }
    .seg { display: flex; gap: 6px; }
    .small { font-size: 0.85rem; margin: 6px 0 14px; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 14px; margin-bottom: 14px; }
    .tile { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; }
    .tile strong { font-size: 2rem; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .tile.live strong { color: var(--accent); }
    section.card { padding: 16px 20px; margin-bottom: 14px; min-width: 0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; }
    .grid section.card { margin-bottom: 0; }
    h2 { margin: 0 0 12px; font-size: 1.05rem; }
    .chart { display: flex; align-items: flex-end; gap: 2px; height: 150px; }
    .col { flex: 1; height: 100%; position: relative; }
    .col span { position: absolute; bottom: 0; left: 0; right: 0; border-radius: 3px 3px 0 0; min-height: 1px; }
    .col .views { background: color-mix(in srgb, var(--accent-2) 45%, transparent); }
    .col .users { background: var(--accent); left: 20%; right: 20%; }
    .legend { display: flex; gap: 8px; align-items: center; font-size: 0.8rem; margin-top: 8px; }
    .legend .k { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
    .legend .k.views { background: color-mix(in srgb, var(--accent-2) 45%, transparent); margin-left: 0; }
    .legend .k.users { background: var(--accent); margin-left: 10px; }
    .line { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid var(--border); }
    .line:last-child { border-bottom: 0; }
    .line span { min-width: 0; overflow-wrap: anywhere; }
    .line strong, .bar-line strong { font-variant-numeric: tabular-nums; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.85rem; }
    .bar-line { display: grid; grid-template-columns: 6em 1fr 3.5em; gap: 10px; align-items: center; padding: 6px 0; }
    .bar-line strong { text-align: right; }
    .bar { height: 8px; border-radius: 99px; background: var(--surface-strong); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--accent); }
    .setup ol { margin: 8px 0 0; padding-left: 20px; display: grid; gap: 6px; }
    code { font-size: 0.85em; overflow-wrap: anywhere; }
  `,
})
export class WebsiteAnalytics implements OnInit {
  private readonly api = inject(WebsiteAnalyticsApi);
  protected readonly periods = [7, 28, 90];
  protected readonly days = signal(28);
  protected readonly view = signal<WebsiteAnalyticsView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly maxViews = computed(() => Math.max(1, ...(this.view()?.byDay ?? []).map((d) => d.pageViews)));

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

  protected share(r: GaRow, all: GaRow[]): number {
    const total = all.reduce((s, x) => s + x.value, 0) || 1;
    return (100 * r.value) / total;
  }

  protected cap(s: string): string {
    return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
  }

  protected duration(seconds: number): string {
    const s = Math.round(seconds);
    return s >= 60 ? `${Math.floor(s / 60)}m ${s % 60}s` : `${s}s`;
  }

  private load(): void {
    this.error.set(null);
    this.view.set(null);
    this.api.view(this.days()).subscribe({ next: (v) => this.view.set(v), error: (e) => this.error.set(errorMessage(e)) });
  }
}
