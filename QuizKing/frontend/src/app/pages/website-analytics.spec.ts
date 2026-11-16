import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { WebsiteAnalytics } from './website-analytics';

const empty = { propertyId: null, activeNow: 0, totals: null, byDay: [], pages: [], countries: [], devices: [], channels: [], events: [] };

describe('Website analytics (admin)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows Google Analytics numbers', async () => {
    const f = TestBed.createComponent(WebsiteAnalytics);
    f.detectChanges();
    http.expectOne('/api/v1/reports/admin/analytics?days=28').flush({
      ...empty, configured: true, error: null, propertyId: '564461039', days: 28, activeNow: 3,
      totals: { activeUsers: 120, newUsers: 90, sessions: 200, pageViews: 1500, avgSessionSeconds: 185, engagementRate: 0.62 },
      byDay: [{ day: '2026-11-15', activeUsers: 40, pageViews: 500 }, { day: '2026-11-16', activeUsers: 80, pageViews: 1000 }],
      pages: [{ name: '/daily', value: 300 }], countries: [{ name: 'Canada', value: 70 }],
      devices: [{ name: 'mobile', value: 80 }, { name: 'desktop', value: 20 }], channels: [{ name: 'Direct', value: 150 }],
      events: [{ name: 'share', value: 12 }],
    });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('1,500');
    expect(el.textContent).toContain('3m 5s');
    expect(el.textContent).toContain('62% engaged');
    expect(el.textContent).toContain('/daily');
    expect(el.textContent).toContain('Mobile');
    expect(el.textContent).toContain('80%');
    expect(el.querySelectorAll('.chart .col').length).toBe(2);
  });

  it('explains the setup when Google Analytics is not connected', async () => {
    const f = TestBed.createComponent(WebsiteAnalytics);
    f.detectChanges();
    http.expectOne('/api/v1/reports/admin/analytics?days=28').flush({ ...empty, configured: false, days: 0, error: 'Set GA_CREDENTIALS_FILE to the key file.' });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Connect Google Analytics');
    expect(el.textContent).toContain('Set GA_CREDENTIALS_FILE');
  });

  it('shows why Google refused', async () => {
    const f = TestBed.createComponent(WebsiteAnalytics);
    f.detectChanges();
    http.expectOne('/api/v1/reports/admin/analytics?days=28').flush({ ...empty, configured: true, days: 28, error: 'Google refused access. Add x@y as a Viewer.' });
    await f.whenStable();
    expect((f.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('Viewer');
  });
});
