import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { MESSAGES } from '../core/validation';
import { ForgotPassword, ResetPassword } from './password';

describe('Forgot / reset password', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('asks for a link and says it was sent without revealing whether the account exists', async () => {
    const f = TestBed.createComponent(ForgotPassword);
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: null, passwordReset: true });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const input = el.querySelector('input[name=email]') as HTMLInputElement;
    input.value = ' ada@x.test ';
    input.dispatchEvent(new Event('input'));
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/v1/auth/password/forgot');
    expect(req.request.body).toEqual({ email: 'ada@x.test' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await f.whenStable();
    expect(el.textContent).toContain('If an account uses ada@x.test');
  });

  it('checks the two passwords match, then saves and signs in', async () => {
    const f = TestBed.createComponent(ResetPassword);
    f.componentRef.setInput('token', 'tok-123');
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const type = (name: string, v: string) => {
      const i = el.querySelector(`input[name=${name}]`) as HTMLInputElement;
      i.value = v;
      i.dispatchEvent(new Event('input'));
    };
    const submit = () => (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    type('password', 'new-password');
    type('confirm', 'new-passwrd');
    submit();
    await f.whenStable();
    expect(el.textContent).toContain("don't match");
    http.expectNone('/api/v1/auth/password/reset');

    const go = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    type('confirm', 'new-password');
    submit();
    const req = http.expectOne('/api/v1/auth/password/reset');
    expect(req.request.body).toEqual({ token: 'tok-123', password: 'new-password' });
    req.flush({ token: 'jwt', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { userId: 4, userName: 'Ada', email: 'ada@x.test', role: 'USER' } });
    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(true);
    expect(go).toHaveBeenCalledWith('/');
  });

  it('shows a clear message for a link without a token', async () => {
    const f = TestBed.createComponent(ResetPassword);
    await f.whenStable();
    expect((f.nativeElement as HTMLElement).textContent).toContain('This link is incomplete');
  });
  it('explains that reset by email is off, and shows no form, until the server can send email', async () => {
    const f = TestBed.createComponent(ForgotPassword);
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: 'x', passwordReset: false });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain("Password reset by email isn't available yet");
    expect(el.querySelector('form')).toBeNull();
  });

  it('forgot: keeps submit off and says why until the email has the shape of one', async () => {
    const f = TestBed.createComponent(ForgotPassword);
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: null, passwordReset: true });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const input = el.querySelector('input[name=email]') as HTMLInputElement;
    const button = el.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    input.value = 'ada';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await f.whenStable();
    expect(el.querySelector('.field-error')?.textContent).toContain(MESSAGES.emailInvalid);
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectNone('/api/v1/auth/password/forgot');
    input.value = 'ada@localhost'; // older accounts may have such an address: it still gets a link
    input.dispatchEvent(new Event('input'));
    await f.whenStable();
    expect(el.querySelector('.field-error')).toBeNull();
    expect(button.disabled).toBe(false);
  });

  it('reset: explains the 8 to 72 rule inline and re-checks the confirm box when the first one changes', async () => {
    const f = TestBed.createComponent(ResetPassword);
    f.componentRef.setInput('token', 'tok-123');
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const type = (name: string, v: string) => {
      const i = el.querySelector(`input[name=${name}]`) as HTMLInputElement;
      i.value = v;
      i.dispatchEvent(new Event('input'));
      i.dispatchEvent(new Event('blur'));
    };
    const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());
    type('password', 'short');
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.passwordSize]);
    type('password', 'long enough');
    type('confirm', 'long enough');
    await f.whenStable();
    expect(errors()).toEqual([]);
    expect((el.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(false);
    type('password', 'long enough!');
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.mismatch]);
  });
});
