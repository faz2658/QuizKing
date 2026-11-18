import { HttpClient, HttpErrorResponse, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { Maintenance, isGatewayFailure, retryAfterSeconds } from './maintenance';
import { maintenanceInterceptor } from './maintenance.interceptor';

const DOWN = { status: 502, statusText: 'Bad Gateway' };
const NGINX_503 = {
  status: 503,
  statusText: 'Service Unavailable',
  headers: { 'Retry-After': '15', 'X-Maintenance': '1' },
};

describe('isGatewayFailure / retryAfterSeconds', () => {
  it('counts unreachable servers, not a service\'s own "still loading" 503 or other errors', () => {
    expect(isGatewayFailure(new HttpErrorResponse({ status: 0 }))).toBe(true);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 502 }))).toBe(true);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 504 }))).toBe(true);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 503, error: { maintenance: true } }))).toBe(true);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 503, headers: new HttpHeaders({ 'X-Maintenance': '1' }) }))).toBe(true);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 503, error: { retryAfterSeconds: 2 } }))).toBe(false);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 500 }))).toBe(false);
    expect(isGatewayFailure(new HttpErrorResponse({ status: 401 }))).toBe(false);
    expect(isGatewayFailure(new Error('x'))).toBe(false);
  });

  it('reads Retry-After as seconds or a date, else the problem body', () => {
    const now = Date.parse('2026-11-17T10:00:00Z');
    expect(retryAfterSeconds(new HttpErrorResponse({ headers: new HttpHeaders({ 'Retry-After': '20' }) }))).toBe(20);
    expect(retryAfterSeconds(new HttpErrorResponse({ headers: new HttpHeaders({ 'Retry-After': 'Tue, 17 Nov 2026 10:00:45 GMT' }) }), now))
      .toBe(45);
    expect(retryAfterSeconds(new HttpErrorResponse({ error: { retryAfterSeconds: 7 } }))).toBe(7);
    expect(retryAfterSeconds(new HttpErrorResponse({ status: 502 }))).toBeNull();
  });
});

describe('Maintenance with maintenanceInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let maintenance: Maintenance;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    localStorage.setItem('quizcadia.session', JSON.stringify({ token: 'jwt', expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: { userId: 7, userName: 'Ada', email: 'ada@x.test', role: 'USER' } }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, maintenanceInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    maintenance = TestBed.inject(Maintenance);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('tries a read that failed once again shortly, without showing the banner', () => {
    const next = vi.fn();
    const error = vi.fn();
    http.get('/api/v1/games').subscribe({ next, error });
    backend.expectOne('/api/v1/games').flush(null, DOWN);
    expect(error).not.toHaveBeenCalled();

    vi.advanceTimersByTime(2_000);
    backend.expectOne('/api/v1/games').flush([1]);
    expect(next).toHaveBeenCalledWith([1]);
    expect(maintenance.active()).toBe(false);
  });

  it('turns the banner on when that second try fails too, and the read then waits', () => {
    const error = vi.fn();
    http.get('/api/v1/games').subscribe({ error });
    backend.expectOne('/api/v1/games').flush(null, DOWN);
    vi.advanceTimersByTime(2_000);
    backend.expectOne('/api/v1/games').flush(null, DOWN);

    expect(maintenance.active()).toBe(true);
    expect(error).not.toHaveBeenCalled();
  });

  it('passes a single failed write on as an error without showing the banner', () => {
    const error = vi.fn();
    http.post('/api/v1/sessions', {}).subscribe({ error });
    backend.expectOne('/api/v1/sessions').flush(null, DOWN);

    expect(error).toHaveBeenCalled();
    expect(maintenance.active()).toBe(false);
  });

  it('switches to maintenance on repeated failures, counts down, checks, and re-sends waiting reads when back', () => {
    const games = vi.fn();
    const gamesError = vi.fn();
    http.get('/api/v1/games').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/games').flush(null, { status: 0, statusText: '' });
    http.get('/api/v1/daily').subscribe({ next: games, error: gamesError });
    backend.expectOne('/api/v1/daily').flush(null, NGINX_503);

    expect(maintenance.active()).toBe(true);
    expect(gamesError).not.toHaveBeenCalled(); // it waits instead
    expect(maintenance.secondsLeft()).toBe(15); // from Retry-After

    vi.advanceTimersByTime(5_000);
    expect(maintenance.secondsLeft()).toBe(10);

    // still down at the first check: back off
    vi.advanceTimersByTime(10_000);
    const probe1 = backend.expectOne((r) => r.method === 'HEAD');
    expect(probe1.request.url).toBe('/api/v1/daily'); // the read that failed
    expect(probe1.request.headers.has('Authorization')).toBe(false); // past the interceptors
    probe1.flush(null, DOWN);
    expect(maintenance.active()).toBe(true);
    expect(maintenance.secondsLeft()).toBe(10); // second step of the backoff

    // up again: banner goes, the waiting read is sent again (with the token) and answers the page
    vi.advanceTimersByTime(10_000);
    backend.expectOne((r) => r.method === 'HEAD').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(maintenance.active()).toBe(false);
    backend.expectOne('/api/v1/games').flush([]); // the first read, which was waiting to try again, goes too
    const again = backend.expectOne('/api/v1/daily');
    expect(again.request.headers.get('Authorization')).toBe('Bearer jwt');
    again.flush({ ok: true });
    expect(games).toHaveBeenCalledWith({ ok: true });
    expect(gamesError).not.toHaveBeenCalled();
    backend.verify();
  });

  it('checks at once on "Try now"', () => {
    const back = vi.fn();
    maintenance.recovered.subscribe(back);
    for (const url of ['/api/v1/a', '/api/v1/b']) {
      http.get(url).subscribe({ error: () => undefined });
      backend.expectOne(url).flush(null, DOWN);
    }
    expect(maintenance.secondsLeft()).toBe(5);

    maintenance.tryNow();
    expect(maintenance.checking()).toBe(true);
    backend.expectOne((r) => r.method === 'HEAD').flush(null);
    expect(maintenance.active()).toBe(false);
    expect(back).toHaveBeenCalledTimes(1);
    backend.expectOne('/api/v1/b').flush({});
  });

  it('does not repeat writes, and does not sign the player out', () => {
    const auth = TestBed.inject(AuthService);
    http.get('/api/v1/a').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/a').flush(null, DOWN);
    const answerError = vi.fn();
    http.post('/api/v1/sessions/1/answers', { option: 'B' }).subscribe({ error: answerError });
    backend.expectOne('/api/v1/sessions/1/answers').flush(null, DOWN);

    expect(maintenance.active()).toBe(true);
    expect(answerError).toHaveBeenCalled();
    expect(auth.isLoggedIn()).toBe(true);

    // the probe uses the last read, not the write
    maintenance.tryNow();
    expect(backend.expectOne((r) => r.method === 'HEAD').request.url).toBe('/api/v1/a');
  });

  it('passes a service\'s own 503 and non-API calls straight through', () => {
    const error = vi.fn();
    for (let i = 0; i < 3; i++) {
      http.get('/api/v1/sessions/9').subscribe({ error });
      backend.expectOne('/api/v1/sessions/9').flush({ retryAfterSeconds: 2 }, { status: 503, statusText: 'Service Unavailable' });
      http.get('/assets/x.json').subscribe({ error });
      backend.expectOne('/assets/x.json').flush(null, DOWN);
    }
    expect(error).toHaveBeenCalledTimes(6);
    expect(maintenance.active()).toBe(false);
  });
});
