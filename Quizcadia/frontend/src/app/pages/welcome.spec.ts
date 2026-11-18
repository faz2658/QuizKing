import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MESSAGES } from '../core/validation';
import { Welcome } from './welcome';

describe('Welcome sign-in forms', () => {
  let http: HttpTestingController;
  let f: ComponentFixture<Welcome>;
  let el: HTMLElement;

  const input = (name: string) => el.querySelector(`input[name=${name}]`) as HTMLInputElement;
  const type = (name: string, v: string) => {
    const i = input(name);
    i.value = v;
    i.dispatchEvent(new Event('input'));
    i.dispatchEvent(new Event('blur'));
  };
  const button = () => el.querySelector('button[type=submit]') as HTMLButtonElement;
  const submit = () => (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());
  const tab = async (label: string) => {
    [...el.querySelectorAll('[role=tab]')].find((b) => b.textContent?.trim() === label)!.dispatchEvent(new Event('click'));
    await f.whenStable();
  };
  const token = { token: 'jwt', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
    user: { userId: 4, userName: 'Ada', email: 'ada@x.test', role: 'USER' } };

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    f = TestBed.createComponent(Welcome);
    el = f.nativeElement as HTMLElement;
    await f.whenStable();
    http.match('/api/v1/question-generator/stats').forEach((r) => r.flush([]));
    http.match('/api/v1/auth/providers').forEach((r) => r.flush({ googleClientId: null, passwordReset: false }));
    await f.whenStable();
  });

  it('log in: submit stays off until email and password are filled, with a message on the bad field', async () => {
    expect(button().disabled).toBe(true);
    expect(errors()).toEqual([]); // nothing shouts before the player has typed

    type('email', 'ada');
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.emailInvalid]);
    expect(input('email').getAttribute('aria-invalid')).toBe('true');

    type('email', ' ada@x.test ');
    type('password', 'x');
    await f.whenStable();
    expect(errors()).toEqual([]);
    expect(button().disabled).toBe(false);

    submit();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'ada@x.test', password: 'x' });
    req.flush(token);
  });

  it('sign up: checks name characters, a real-looking email and 8 to 72 password characters before sending', async () => {
    await tab('Sign up');
    type('userName', '<b>Ada</b>');
    type('email', 'ada@localhost');
    type('password', 'short');
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.nameChars, MESSAGES.emailInvalid, MESSAGES.passwordSize]);
    expect(button().disabled).toBe(true);
    submit();
    http.expectNone('/api/v1/auth/register');

    type('userName', '  José Álvarez ');
    type('email', 'jose@example.test');
    type('password', 'long enough');
    await f.whenStable();
    expect(errors()).toEqual([]);
    submit();
    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.body).toMatchObject({ userName: 'José Álvarez', email: 'jose@example.test', password: 'long enough' });
    req.flush(token);
  });

  it('guest: only the name counts, and a server problem still shows its detail', async () => {
    await tab('Guest');
    expect(input('email')).toBeNull();
    submit();
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.nameRequired]); // shown for every field once submit was tried
    http.expectNone('/api/v1/auth/guest');

    type('userName', 'Visitor');
    await f.whenStable();
    expect(button().disabled).toBe(false);
    submit();
    http.expectOne('/api/v1/auth/guest').flush({ status: 400, detail: 'Names like that are not allowed.' },
      { status: 400, statusText: 'Bad Request' });
    await f.whenStable();
    expect(el.querySelector('.alert')?.textContent).toContain('Names like that are not allowed.');
  });

  it('switching tabs forgets the old messages and swaps the password rule', async () => {
    type('password', '');
    await f.whenStable();
    expect(errors()).toEqual([MESSAGES.currentPasswordRequired]);
    await tab('Sign up');
    expect(errors()).toEqual([]);
    type('password', 'abc');
    await f.whenStable();
    expect(errors()).toContain(MESSAGES.passwordSize);
  });
});
