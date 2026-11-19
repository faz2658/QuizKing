import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { RetentionView } from '../core/admin-stats';
import { percent, RetentionPanel, weekLabel } from './retention-panel';

const view: RetentionView = {
  asOf: '2026-11-18',
  players: 40,
  everyone: [
    { day: 1, eligible: 30, returned: 12, rate: 0.4 },
    { day: 7, eligible: 20, returned: 3, rate: 0.15 },
    { day: 30, eligible: 0, returned: 0, rate: null },
  ],
  accounts: [
    { day: 1, eligible: 10, returned: 6, rate: 0.6 },
    { day: 7, eligible: 8, returned: 2, rate: 0.25 },
    { day: 30, eligible: 0, returned: 0, rate: null },
  ],
  cohorts: [
    { weekStart: '2026-11-02', players: 20, guests: 8, weeks: [1, 0.35, 0.2] },
    { weekStart: '2026-11-09', players: 0, guests: 0, weeks: [null, null] },
    { weekStart: '2026-11-16', players: 20, guests: 4, weeks: [1] },
  ],
};

describe('Retention panel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  async function render() {
    const f = TestBed.createComponent(RetentionPanel);
    f.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/reports/admin/retention' && r.params.get('weeks') === '8').flush(view);
    await f.whenStable();
    f.detectChanges();
    return { f, el: f.nativeElement as HTMLElement };
  }

  it('shows day 1, 7 and 30 retention with the accounts figure and what good looks like', async () => {
    const { f, el } = await render();
    const tiles = [...el.querySelectorAll('.tile')].map((t) => [...t.children].map((c) => c.textContent!.trim()).join(' | '));
    expect(tiles[0]).toContain('Day 1 | 40%');
    expect(tiles[0]).toContain('12 of 30 came back on day 1');
    expect(tiles[0]).toContain('accounts 60%');
    expect(tiles[0]).toContain('good: 35–40%');
    expect(tiles[2]).toContain('Day 30 | –');
    expect(tiles[2]).toContain('too early'); // not shown as 0%
    f.destroy();
  });

  it('draws the weekly cohort grid, leaving weeks still to come empty', async () => {
    const { f, el } = await render();
    const rows = [...el.querySelectorAll('tbody tr')];
    expect(rows).toHaveLength(3);
    const first = [...rows[0].querySelectorAll('td')].map((c) => c.textContent!.trim());
    expect(first).toEqual(['20 (8 guests)', '100%', '35%', '20%']);
    const last = [...rows[2].querySelectorAll('td.cell')].map((c) => c.textContent!.trim());
    expect(last).toEqual(['100%', '', '']); // the newest week has only its own week so far
    expect(rows[1].querySelectorAll('td.cell.empty')).toHaveLength(3); // nobody started that week
    expect(el.querySelectorAll('thead th')).toHaveLength(5); // week, players, week 0, +1, +2
    f.destroy();
  });

  it('can look further back', async () => {
    const { f, el } = await render();
    ([...el.querySelectorAll('.pill-tabs button')].find((b) => b.textContent?.includes('26')) as HTMLButtonElement).click();
    http.expectOne((r) => r.url === '/api/v1/reports/admin/retention' && r.params.get('weeks') === '26').flush(view);
    await f.whenStable();
    f.detectChanges();
    expect(el.textContent).toContain('new players in the last 26 weeks');
    f.destroy();
  });

  it('formats percentages and week labels', () => {
    expect([percent(0.4167), percent(1), percent(0), percent(null)]).toEqual(['42%', '100%', '0%', '–']);
    expect(weekLabel('2026-11-02')).toBe('Nov 2');
  });
});
