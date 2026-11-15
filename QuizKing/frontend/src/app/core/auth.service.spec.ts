import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('logs in, keeps the token and survives a reload', () => {
    auth.login('ada@x.test', 'secret-123').subscribe();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'ada@x.test', password: 'secret-123' });
    req.flush({ token: 'jwt', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { userId: 7, userName: 'Ada', email: 'ada@x.test', role: 'ADMIN' } });

    expect(auth.token()).toBe('jwt');
    expect(auth.isAdmin()).toBe(true);
    expect(TestBed.runInInjectionContext(() => new AuthService()).user()?.userId).toBe(7);
  });

  it('drops an expired stored session', () => {
    localStorage.setItem('quizking.session', JSON.stringify({ token: 't', expiresAt: '2000-01-01T00:00:00Z',
      user: { userId: 1, userName: 'Old', email: '', role: 'USER' } }));

    expect(TestBed.runInInjectionContext(() => new AuthService()).isLoggedIn()).toBe(false);
  });

  it('guests get a server-issued token', () => {
    auth.playAsGuest('  Sam ').subscribe();
    const req = http.expectOne('/api/v1/auth/guest');
    expect(req.request.body).toEqual({ userName: 'Sam' });
    req.flush({ token: 'guest-jwt', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { userId: 912_345_678, userName: 'Sam', email: '', role: 'GUEST' } });

    expect(auth.token()).toBe('guest-jwt');
    expect(auth.user()?.role).toBe('GUEST');
    auth.logout();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('drops sessions saved without a token by older versions', () => {
    localStorage.setItem('quizking.session', JSON.stringify({ token: null, expiresAt: null,
      user: { userId: 900_000_001, userName: 'Old guest', email: '', role: 'GUEST' } }));

    expect(TestBed.runInInjectionContext(() => new AuthService()).isLoggedIn()).toBe(false);
  });
  it('signs in with a Google credential', () => {
    auth.signInWithGoogle('google-id-token').subscribe();
    const req = http.expectOne('/api/v1/auth/google');
    expect(req.request.body).toEqual({ credential: 'google-id-token' });
    req.flush({ token: 'jwt-g', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { userId: 9, userName: 'Gail', email: 'gail@x.test', role: 'USER' } });
    expect(auth.token()).toBe('jwt-g');
  });
});
