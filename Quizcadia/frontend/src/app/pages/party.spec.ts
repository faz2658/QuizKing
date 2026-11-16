import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { PartyScreen } from './party';

const room = (over: object = {}) => ({
  code: 'KING-4821', gameId: 7, hostId: 1, players: [{ userId: 2, name: 'Bo' }], state: 'WAITING', matchId: null,
  maxPlayers: 30, party: true, ...over,
});

describe('Party screen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows the code and who joined, and starts only with two players', async () => {
    const f = TestBed.createComponent(PartyScreen);
    f.componentRef.setInput('code', 'KING-4821');
    f.detectChanges();
    http.expectOne('/api/v1/matching/rooms/KING-4821').flush(room());
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.code')?.textContent).toContain('KING-4821');
    expect(el.textContent).toContain('1 / 30 joined');
    expect((el.querySelector('.big') as HTMLButtonElement).disabled).toBe(true);
    f.destroy();
  });

  it('follows the match as a screen once the host starts', async () => {
    const f = TestBed.createComponent(PartyScreen);
    f.componentRef.setInput('code', 'KING-4821');
    f.detectChanges();
    http.expectOne('/api/v1/matching/rooms/KING-4821').flush(room({ state: 'STARTED', matchId: 'm-1' }));
    await f.whenStable();
    http.expectOne((r) => r.url === '/api/v1/matches/m-1').flush({
      matchId: 'm-1', gameId: 7, gameName: 'Sports', status: 'IN_PROGRESS', totalQuestions: 10, pointsPerQuestion: 10,
      secondsPerQuestion: 20, lastQuestion: null,
      standings: [{ userId: 2, score: 10, questionsWon: 1, name: 'Bo', country: 'CA' }, { userId: 3, score: 0, questionsWon: 0, name: 'Cy' }],
      currentQuestion: { questionId: 5, number: 1, stem: 'Who won?', options: ['A team', 'B team'], secondsRemaining: 18, yourAnswer: null },
    });
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.stem')?.textContent).toContain('Who won?');
    expect(el.querySelectorAll('.option').length).toBe(2);
    expect(el.querySelector('.board')?.textContent).toContain('Bo');
    f.destroy();
  });
});
