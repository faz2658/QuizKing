import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { friendshipTitle } from '../core/knowme';
import { KnowMePlay } from './know-me';

const quiz = (over: object = {}) => ({
  id: 'abcd2345', ownerName: 'Ada', own: false, players: 0, leaderboard: [], createdAt: '2026-11-15T00:00:00Z',
  yourAnswers: null, yourScore: null,
  questions: [
    { text: 'Cats or dogs?', options: ['Cats', 'Dogs'], answer: null },
    { text: 'Tea or coffee?', options: ['Tea', 'Coffee'], answer: null },
  ],
  ...over,
});

describe('How well do you know me?', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('gives a verdict for every score', () => {
    expect(friendshipTitle(10, 10)).toContain('Soulmate');
    expect(friendshipTitle(8, 10)).toContain('Best friend');
    expect(friendshipTitle(0, 10)).toContain('know each other');
  });

  it('asks one question at a time, then shows the score, verdict and leaderboard', async () => {
    const f = TestBed.createComponent(KnowMePlay);
    f.componentRef.setInput('id', 'abcd2345');
    f.detectChanges();
    http.expectOne('/api/v1/knowme/abcd2345').flush(quiz());
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('How well do you know Ada?');

    (el.querySelectorAll('.choice')[1] as HTMLButtonElement).click(); // Dogs
    await f.whenStable();
    expect(el.textContent).toContain('Tea or coffee?');
    (el.querySelectorAll('.choice')[0] as HTMLButtonElement).click(); // Tea

    const req = http.expectOne('/api/v1/knowme/abcd2345/answers');
    expect(req.request.body).toEqual({ answers: [1, 0] });
    req.flush(quiz({
      yourAnswers: [1, 0], yourScore: 2, players: 1,
      questions: [
        { text: 'Cats or dogs?', options: ['Cats', 'Dogs'], answer: 1 },
        { text: 'Tea or coffee?', options: ['Tea', 'Coffee'], answer: 0 },
      ],
      leaderboard: [{ rank: 1, name: 'Bo', score: 2, you: true }],
    }));
    await f.whenStable();
    expect(el.textContent).toContain('2/2');
    expect(el.textContent).toContain('Soulmate');
    expect(el.textContent).toContain("You're the first of Ada's friends to play.");
  });

  it('shows the owner their link and leaderboard instead of the questions', async () => {
    const f = TestBed.createComponent(KnowMePlay);
    f.componentRef.setInput('id', 'abcd2345');
    f.detectChanges();
    http.expectOne('/api/v1/knowme/abcd2345').flush(quiz({ own: true, questions: [{ text: 'Cats or dogs?', options: ['Cats', 'Dogs'], answer: 0 }] }));
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('code')?.textContent).toContain('/know-me/abcd2345');
    expect(el.textContent).toContain('Nobody has played yet');
  });
});
