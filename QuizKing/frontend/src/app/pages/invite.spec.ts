import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, pendingInvite } from '../core/auth.service';
import { Join } from './invite';

describe('Invite links', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('remembers the code from /join and sends it with the sign-up, once', () => {
    const go = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const f = TestBed.createComponent(Join);
    f.componentRef.setInput('code', 'A1B');
    f.detectChanges();
    expect(pendingInvite()).toBe('a1b');
    expect(go).toHaveBeenCalledWith('/welcome', { replaceUrl: true });

    TestBed.inject(AuthService).register('Bo', 'bo@x.test', 'password123').subscribe();
    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.body).toEqual({ userName: 'Bo', email: 'bo@x.test', password: 'password123', invite: 'a1b' });
    req.flush({ token: 't', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { userId: 9, userName: 'Bo', email: 'bo@x.test', role: 'USER' } });
    expect(pendingInvite()).toBeNull();
  });
});
