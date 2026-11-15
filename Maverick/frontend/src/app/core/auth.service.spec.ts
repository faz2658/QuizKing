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
    localStorage.setItem('maverick.session', JSON.stringify({ token: 't', expiresAt: '2000-01-01T00:00:00Z',
      user: { userId: 1, userName: 'Old', email: '', role: 'USER' } }));

    expect(TestBed.runInInjectionContext(() => new AuthService()).isLoggedIn()).toBe(false);
  });

  it('guests get a large id that cannot clash with real accounts', () => {
    auth.playAsGuest('  Sam ');

    expect(auth.user()?.userName).toBe('Sam');
    expect(auth.user()!.userId).toBeGreaterThanOrEqual(900_000_000);
    expect(auth.token()).toBeNull();
    auth.logout();
    expect(auth.isLoggedIn()).toBe(false);
  });
});
