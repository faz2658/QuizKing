import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('quizcadia.session', JSON.stringify({ token: 'jwt', expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      user: { userId: 7, userName: 'Ada', email: 'ada@x.test', role: 'USER' } }));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  it('sends the token with API calls', () => {
    http.get('/api/v1/games').subscribe();
    expect(backend.expectOne('/api/v1/games').request.headers.get('Authorization')).toBe('Bearer jwt');
  });

  const renewed = (token: string, inMs = 15 * 60_000) => ({ token, tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + inMs).toISOString(), sessionExpiresAt: null,
    user: { userId: 7, userName: 'Ada', email: 'ada@x.test', role: 'USER' } });

  it('signs the player out when the token is rejected and the sign-in has ended', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.get('/api/v1/games').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/games').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush({ detail: 'Your session has ended.' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/welcome'], expect.anything());
  });

  it('renews the token once after a 401 and retries the call', () => {
    let body: unknown;
    http.get('/api/v1/games').subscribe((b) => (body = b));
    backend.expectOne('/api/v1/games').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(renewed('jwt-2'));
    const retry = backend.expectOne('/api/v1/games');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    retry.flush([{ id: 1 }]);
    expect(body).toEqual([{ id: 1 }]);
    expect(auth.token()).toBe('jwt-2');
  });

  it('a call that fails again after renewing is not retried forever', () => {
    let failed = false;
    http.get('/api/v1/games').subscribe({ error: () => (failed = true) });
    backend.expectOne('/api/v1/games').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(renewed('jwt-2'));
    backend.expectOne('/api/v1/games').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.verify();
    expect(failed).toBe(true);
  });

  it('renews a token that is about to run out before sending, once for a burst of calls', () => {
    localStorage.setItem('quizcadia.session', JSON.stringify({ token: 'old', expiresAt: new Date(Date.now() + 10_000).toISOString(),
      sessionExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
      user: { userId: 7, userName: 'Ada', email: 'ada@x.test', role: 'USER' } }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);

    http.get('/api/v1/games').subscribe();
    http.get('/api/v1/daily').subscribe();
    backend.expectOne('/api/v1/auth/refresh').flush(renewed('fresh'));
    expect(backend.expectOne('/api/v1/games').request.headers.get('Authorization')).toBe('Bearer fresh');
    expect(backend.expectOne('/api/v1/daily').request.headers.get('Authorization')).toBe('Bearer fresh');
  });

  it('stays signed in when the server cannot be reached', () => {
    http.get('/api/v1/games').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/games').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').error(new ProgressEvent('error'));
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('leaves a failed sign-in alone', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(true);
  });
});
