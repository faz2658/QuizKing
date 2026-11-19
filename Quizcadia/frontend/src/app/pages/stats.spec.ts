import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Stats } from './stats';

describe('Admin user stats', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows account and activity numbers', async () => {
    const f = TestBed.createComponent(Stats);
    f.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/auth/admin/stats' && r.params.get('days') === '30').flush({
      total: 12, google: 9, password: 3, admins: 1, today: 2, last7Days: 5, last30Days: 12,
      signupsByDay: [{ day: '2026-11-14', count: 3 }, { day: '2026-11-15', count: 2 }],
      recent: [{ userId: 12, userName: 'Ada', email: 'ada@example.test', createdAt: '2026-11-15T10:00:00Z', google: true, admin: false }],
    });
    http.expectOne('/api/v1/matching/admin/online').flush([
      { userId: 3, name: 'Bo', country: 'CA', guest: false, onlineSince: new Date(Date.now() - 12 * 60000).toISOString(), lastSeen: new Date().toISOString() },
      { userId: 912345678, name: 'Visitor', country: null, guest: true, onlineSince: new Date().toISOString(), lastSeen: new Date().toISOString() },
    ]);
    http.expectOne('/api/v1/reports/admin/stats?days=30').flush({
      gamesAllTime: 400, gamesInPeriod: 120, activeToday: 4, active7Days: 8, active30Days: 11, guestPlayers30Days: 6,
      byDay: [{ day: '2026-11-14', games: 30, players: 5, guestGames: 2 }, { day: '2026-11-15', games: 40, players: 4, guestGames: 0 }],
      modes: [{ name: 'Solo', count: 80 }], topGames: [{ name: 'Art', count: 50 }], countries: [{ name: 'CA', count: 7 }],
      topPlayers: [{ userId: 3, name: 'Bo', country: 'CA', games: 20, points: 900 }],
    });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('9 Google · 3 password');
    expect(el.textContent).toContain('ada@example.test');
    expect(el.textContent).toContain('Canada');
    expect(el.textContent).toContain('Bo');
    expect(el.querySelectorAll('.chart .col').length).toBe(4);
    const people = el.querySelectorAll('.people li');
    expect(people.length).toBe(2);
    expect(people[0].textContent).toContain('Bo');
    expect(people[0].textContent).toContain('12 min');
    expect(people[1].textContent).toContain('guest');
    expect(people[1].textContent).toContain('just now');
    expect(el.querySelector('.online .count')?.textContent).toContain('2');

    (el.querySelectorAll('.seg button')[0] as HTMLButtonElement).click();
    http.expectOne('/api/v1/auth/admin/stats?days=7').flush({ total: 0, google: 0, password: 0, admins: 0, today: 0, last7Days: 0, last30Days: 0, signupsByDay: [], recent: [] });
    http.expectOne('/api/v1/reports/admin/stats?days=7').flush({ gamesAllTime: 0, gamesInPeriod: 0, activeToday: 0, active7Days: 0, active30Days: 0, guestPlayers30Days: 0, byDay: [], modes: [], topGames: [], countries: [], topPlayers: [] });
    // the retention panel loads on its own (ui/retention-panel.spec.ts covers it)
    http.expectOne((r) => r.url === '/api/v1/reports/admin/retention').flush({ asOf: '2026-11-15', players: 0, everyone: [], accounts: [], cohorts: [] });
    http.verify();
  });
});
