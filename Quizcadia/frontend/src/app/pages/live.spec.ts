import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MatchView } from '../core/models';
import { Live } from './live';

const room = (over: object = {}) => ({
  code: 'KING-4821', gameId: 0, hostId: 1, players: [{ userId: 900000001, name: 'Ada' }], state: 'WAITING', matchId: null,
  maxPlayers: 50, party: true, quiz: true, ...over,
});

const match = (over: Partial<MatchView> = {}): MatchView => ({
  matchId: 'q1', gameId: 0, gameName: 'Room quiz', status: 'IN_PROGRESS', totalQuestions: 20, pointsPerQuestion: 1000,
  secondsPerQuestion: 20, standings: [{ userId: 900000001, score: 0, questionsWon: 0, name: 'Ada' }], lastQuestion: null,
  currentQuestion: { questionId: 5, number: 1, stem: 'Capital of Peru?', options: ['Lima', 'Quito', 'Bogotá', 'Caracas'], secondsRemaining: 18, yourAnswer: null },
  version: 'v1', format: 'ROOM_QUIZ', phase: 'QUESTION', playerCount: 3, answered: 0, ...over,
});

const guestToken = { token: 't', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', user: { userId: 900000001, userName: 'Ada', email: '', role: 'GUEST' } };

describe('Live room quiz on a phone', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.clear());

  async function joined() {
    const f = TestBed.createComponent(Live);
    f.componentRef.setInput('code', 'KING-4821');
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    const input = el.querySelector('#name') as HTMLInputElement;
    input.value = 'Ada';
    input.dispatchEvent(new Event('input'));
    (el.querySelector('form button[type=submit]') as HTMLButtonElement).click();
    http.expectOne('/api/v1/auth/guest').flush(guestToken);
    http.expectOne({ method: 'POST', url: '/api/v1/matching/rooms/KING-4821/players' }).flush(room({ state: 'STARTED', matchId: 'q1' }));
    await f.whenStable();
    return { f, el };
  }

  it('asks for a code when opened without one', () => {
    const f = TestBed.createComponent(Live);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('#code')).not.toBeNull();
    f.destroy();
  });

  it('joins as a guest with just a name, no account', async () => {
    const f = TestBed.createComponent(Live);
    f.componentRef.setInput('code', 'KING-4821');
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    (el.querySelector('form button[type=submit]') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el.querySelector('.error')?.textContent).toContain('Enter a name');
    http.expectNone('/api/v1/auth/guest');

    const input = el.querySelector('#name') as HTMLInputElement;
    input.value = 'Ada';
    input.dispatchEvent(new Event('input'));
    (el.querySelector('form button[type=submit]') as HTMLButtonElement).click();
    const guest = http.expectOne('/api/v1/auth/guest');
    expect(guest.request.body).toEqual({ userName: 'Ada' });
    guest.flush(guestToken);
    http.expectOne({ method: 'POST', url: '/api/v1/matching/rooms/KING-4821/players' }).flush(room());
    await f.whenStable();
    f.detectChanges();
    expect(el.textContent).toContain("You're in, Ada!");
    f.destroy();
  });

  it('shows big answer buttons, then locks the answer in', async () => {
    const { f, el } = await joined();
    http.expectOne((r) => r.url === '/api/v1/matches/q1/next').flush(match());
    await f.whenStable();
    f.detectChanges();

    const buttons = el.querySelectorAll<HTMLButtonElement>('button.answer');
    expect(buttons.length).toBe(4);
    expect(el.textContent).toContain('Capital of Peru?');
    buttons[0].click();
    f.detectChanges();
    expect(el.querySelector('.locked')?.textContent).toContain('Lima');
    const sent = http.expectOne('/api/v1/matches/q1/answers');
    expect(sent.request.body).toEqual({ userId: 900000001, questionId: 5, answer: 'Lima' });
    sent.flush({ correct: true, wonQuestion: false, score: 950, match: match({ version: 'v2', currentQuestion: { ...match().currentQuestion!, yourAnswer: 'Lima' } }) });
    f.detectChanges();
    expect(el.querySelectorAll('button.answer').length).toBe(0);
    expect(el.querySelector('.locked')).not.toBeNull();
    f.destroy();
  });

  it('says correct or wrong and the rank between questions', async () => {
    const { f, el } = await joined();
    http.expectOne((r) => r.url === '/api/v1/matches/q1/next').flush(match({
      phase: 'REVEAL', currentQuestion: null,
      lastQuestion: { questionId: 5, stem: 'Capital of Peru?', correctAnswer: 'Lima', winnerUserId: null, number: 1, options: ['Lima', 'Quito'], counts: [2, 1], answered: 3 },
      you: { rank: 2, score: 850, lastAnswer: 'Lima', lastCorrect: true, lastPoints: 850 },
    }));
    await f.whenStable();
    f.detectChanges();
    expect(el.textContent).toContain('Correct!');
    expect(el.textContent).toContain('+850');
    expect(el.textContent).toContain("You're 2nd of 3");
    f.destroy();
  });

  it('shows the right answer after a wrong one', async () => {
    const { f, el } = await joined();
    http.expectOne((r) => r.url === '/api/v1/matches/q1/next').flush(match({
      phase: 'REVEAL', currentQuestion: null,
      lastQuestion: { questionId: 5, stem: 'Capital of Peru?', correctAnswer: 'Lima', winnerUserId: null, number: 1, options: ['Lima', 'Quito'], counts: [2, 1], answered: 3 },
      you: { rank: 3, score: 0, lastAnswer: 'Quito', lastCorrect: false, lastPoints: 0 },
    }));
    await f.whenStable();
    f.detectChanges();
    expect(el.querySelector('.result.bad')).not.toBeNull();
    expect(el.textContent).toContain('The answer was Lima');
    f.destroy();
  });
});
