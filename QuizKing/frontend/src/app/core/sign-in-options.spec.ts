import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SignInOptions } from './sign-in-options';

describe('SignInOptions', () => {
  let http: HttpTestingController;
  let options: SignInOptions;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    options = TestBed.inject(SignInOptions);
  });

  it('asks the server once, however many parts of the page need the answer', () => {
    const seen: (string | null)[] = [];
    options.get().subscribe((p) => seen.push(p.googleClientId));
    options.get().subscribe((p) => seen.push(p.googleClientId));
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: 'abc', passwordReset: false });
    options.get().subscribe((p) => seen.push(p.googleClientId)); // later callers get the same answer
    http.expectNone('/api/v1/auth/providers');
    expect(seen).toEqual(['abc', 'abc', 'abc']);
  });

  it("doesn't keep a failed answer, so the next caller asks again", () => {
    let failed = false;
    options.get().subscribe({ error: () => (failed = true) });
    http.expectOne('/api/v1/auth/providers').flush('down', { status: 502, statusText: 'Bad Gateway' });
    expect(failed).toBe(true);

    let id: string | null = null;
    options.get().subscribe((p) => (id = p.googleClientId));
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: 'xyz' });
    expect(id).toBe('xyz');
  });
});
