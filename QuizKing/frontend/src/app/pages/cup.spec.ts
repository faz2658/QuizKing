import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Cup, timeLeft } from './cup';

describe('Country Cup', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('formats the time left', () => {
    expect(timeLeft((2 * 24 * 60 + 4 * 60 + 5) * 60000)).toBe('2d 4h');
    expect(timeLeft((3 * 60 + 12) * 60000)).toBe('3h 12m');
    expect(timeLeft(9 * 60000)).toBe('9m');
  });

  it("shows your country's place, the gap and the standings", async () => {
    const f = TestBed.createComponent(Cup);
    f.detectChanges();
    http.expectOne('/api/v1/reports/cup').flush({
      weekStart: '2026-11-09T00:00:00Z', endsAt: new Date(Date.now() + 3_600_000).toISOString(), countries: 2,
      standings: [
        { rank: 1, country: 'IN', points: 900, players: 12, star: 'Ada' },
        { rank: 2, country: 'CA', points: 500, players: 4, star: 'Bo' },
      ],
      yours: { country: 'CA', rank: 2, points: 500, players: 4, gapToNext: 401, yourXp: 120, yourRank: 1 },
      lastWeek: { country: 'BR', points: 700 },
    });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Canada is #2 of 2');
    expect(el.textContent).toContain('401 points to climb a place');
    expect(el.textContent).toContain("Last week's champion");
    expect(el.querySelectorAll('.table li').length).toBe(2);
  });
});
