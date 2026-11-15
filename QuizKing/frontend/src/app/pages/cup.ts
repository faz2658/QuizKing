import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BoardsApi } from '../core/api';
import { allCountries, countryName, flag, guessCountry } from '../core/countries';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';
import { CupView } from '../core/models';
import { World } from '../core/world';

/** "2d 4h", "3h 12m" or "9m": time left until the cup resets. */
export function timeLeft(ms: number): string {
  const m = Math.max(0, Math.floor(ms / 60000));
  const d = Math.floor(m / 1440);
  const h = Math.floor((m % 1440) / 60);
  return d ? `${d}d ${h}h` : h ? `${h}h ${m % 60}m` : `${m}m`;
}

/** The weekly Country Cup: countries score their best players' XP; recruit friends to climb. */
@Component({
  selector: 'mv-cup',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge"><span class="dot"></span>Ends in {{ left() }}</span>
    <h1>🏆 Country Cup</h1>
    <p class="muted lead">Every game you play this week scores for your country. A country's score is its best 20 players' XP,
      so every friend you bring makes your country stronger. Resets Monday 00:00 UTC.</p>

    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
    @if (cup(); as c) {
      @if (c.lastWeek) {
        <div class="card champ">👑 Last week's champion: <strong>{{ flag(c.lastWeek.country) }} {{ countryName(c.lastWeek.country) }}</strong> with {{ c.lastWeek.points }} points</div>
      }

      @if (c.yours; as y) {
        <div class="card yours">
          <div class="big-flag" aria-hidden="true">{{ flag(y.country) }}</div>
          <div class="info">
            <h2>{{ countryName(y.country) }} is #{{ y.rank }}{{ c.countries > 1 ? ' of ' + c.countries : '' }}</h2>
            <p class="muted">
              {{ y.points }} points from {{ y.players }} {{ y.players === 1 ? 'player' : 'players' }}
              @if (y.gapToNext > 0 && y.rank > 1) { · {{ y.gapToNext }} points to climb a place }
              @if (y.yourXp) { · your part: {{ y.yourXp }} XP{{ y.yourRank ? ' (#' + y.yourRank + ' in your country)' : '' }} }
            </p>
            <div class="row">
              <a class="btn btn-primary" routerLink="/">Play for {{ countryName(y.country) }}</a>
              <button class="btn" (click)="recruit(y.country, y.rank)">📣 {{ shareLabel() }}</button>
            </div>
          </div>
        </div>
      } @else {
        <form class="card pick" (ngSubmit)="saveCountry()">
          <h2>Which country do you play for?</h2>
          <div class="row">
            <select class="input" name="country" [(ngModel)]="picked" aria-label="Your country">
              @for (o of countries; track o.code) { <option [value]="o.code">{{ flag(o.code) }} {{ o.name }}</option> }
            </select>
            <button class="btn btn-primary" type="submit">Join the cup</button>
          </div>
        </form>
      }

      <h2>This week</h2>
      @if (c.standings.length) {
        <ol class="table">
          @for (e of c.standings; track e.country) {
            <li [class.mine]="e.country === c.yours?.country">
              <span class="rank">{{ e.rank <= 3 ? ['🥇', '🥈', '🥉'][e.rank - 1] : e.rank }}</span>
              <span class="who">{{ flag(e.country) }} {{ countryName(e.country) }}
                <small class="muted">{{ e.players }} {{ e.players === 1 ? 'player' : 'players' }}{{ e.star ? ' · ⭐ ' + e.star : '' }}</small></span>
              <span class="bar" aria-hidden="true"><span [style.width.%]="(e.points / max()) * 100"></span></span>
              <strong>{{ e.points }}</strong>
            </li>
          }
        </ol>
      } @else {
        <p class="muted">No points yet this week. Play a game and put your country on the board!</p>
      }
    } @else if (!error()) {
      <div class="skeleton" style="height: 360px"></div>
    }
  `,
  styles: `
    :host { display: block; max-width: 820px; margin: 0 auto; padding-bottom: 56px; }
    h1 { margin: 8px 0; }
    .lead { max-width: 64ch; }
    h2 { margin: 26px 0 12px; font-size: 1.15rem; }
    .card { padding: 18px 22px; }
    .champ { margin: 16px 0; }
    .yours { display: flex; gap: 20px; align-items: center; margin-top: 16px; border-color: var(--accent); }
    .yours h2 { margin: 0 0 4px; font-size: 1.4rem; }
    .yours p { margin: 0 0 12px; }
    .big-flag { font-size: 4rem; line-height: 1; }
    .row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .pick { margin-top: 16px; }
    .pick h2 { margin-top: 0; }
    .pick select { max-width: 320px; }
    .table { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .table li { display: grid; grid-template-columns: 2.4em 1fr 30% 4.5em; gap: 12px; align-items: center; padding: 10px 14px;
      border-radius: 12px; background: var(--surface); border: 1px solid var(--border); }
    .table li.mine { border-color: var(--accent); }
    .rank { font-weight: 800; text-align: center; }
    .who { display: flex; flex-direction: column; font-weight: 650; }
    .who small { font-weight: 400; }
    .bar { height: 8px; border-radius: 99px; background: var(--surface-strong); overflow: hidden; }
    .bar span { display: block; height: 100%; background: linear-gradient(90deg, var(--accent), var(--accent-2)); }
    .table strong { text-align: right; }
    @media (max-width: 560px) { .table li { grid-template-columns: 2em 1fr 4em; } .bar { display: none; } .yours { flex-direction: column; text-align: center; } .yours .row { justify-content: center; } }
  `,
})
export class Cup implements OnInit {
  private readonly api = inject(BoardsApi);
  private readonly world = inject(World);
  protected readonly flag = flag;
  protected readonly countryName = countryName;
  protected readonly countries = allCountries();
  protected readonly cup = signal<CupView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly shareLabel = signal('Recruit friends');
  private readonly now = signal(Date.now());
  protected picked = this.world.country() ?? guessCountry() ?? 'US';

  protected readonly left = computed(() => {
    const c = this.cup();
    return c ? timeLeft(new Date(c.endsAt).getTime() - this.now()) : '…';
  });
  protected readonly max = computed(() => Math.max(1, ...(this.cup()?.standings.map((e) => e.points) ?? [1])));

  constructor() {
    const t = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(t));
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.cup().subscribe({ next: (c) => this.cup.set(c), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected saveCountry(): void {
    this.world.setCountry(this.picked);
    setTimeout(() => this.load(), 600); // the reports service has the country a moment later
  }

  protected async recruit(country: string, rank: number): Promise<void> {
    const how = await shareLink('Quiz King Country Cup',
      `${flag(country)} ${countryName(country)} is #${rank} in this week's Quiz King Country Cup. Every game you play scores for us. Join in:`,
      `${location.origin}/cup`);
    this.shareLabel.set(how === 'copied' ? 'Link copied!' : how === 'failed' ? 'Copy failed' : 'Recruit friends');
    if (how !== 'shared') setTimeout(() => this.shareLabel.set('Recruit friends'), 2500);
  }
}
