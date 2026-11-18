import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { MatchView } from '../core/models';
import { RoomQuizScreen } from './room-quiz-screen';

const match = (over: Partial<MatchView> = {}): MatchView => ({
  matchId: 'q1', gameId: 0, gameName: 'Room quiz', status: 'IN_PROGRESS', totalQuestions: 20, pointsPerQuestion: 1000,
  secondsPerQuestion: 20, lastQuestion: null,
  standings: [{ userId: 2, score: 1900, questionsWon: 2, name: 'Bo' }, { userId: 3, score: 950, questionsWon: 1, name: 'Cy' }],
  currentQuestion: { questionId: 5, number: 3, stem: 'Capital of Peru?', options: ['Lima', 'Quito', 'Bogotá', 'Caracas'], secondsRemaining: 14, yourAnswer: null },
  version: 'v1', format: 'ROOM_QUIZ', phase: 'QUESTION', playerCount: 4, answered: 3, you: null, ...over,
});

describe('Room quiz big screen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  async function screen(m: MatchView) {
    const f = TestBed.createComponent(RoomQuizScreen);
    f.componentRef.setInput('matchId', 'q1');
    f.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/matches/q1/next').flush(m);
    await f.whenStable();
    f.detectChanges();
    return { f, el: f.nativeElement as HTMLElement };
  }

  it('shows the question, the countdown and how many answered', async () => {
    const { f, el } = await screen(match());
    expect(el.querySelector('.stem')?.textContent).toContain('Capital of Peru?');
    expect(el.querySelectorAll('.option').length).toBe(4);
    expect(el.querySelector('.timer')?.textContent?.trim()).toBe('14');
    expect(el.querySelector('.answered')?.textContent).toContain('3 / 4 answered');
    f.destroy();
  });

  it('lets the host show the answer early', async () => {
    const { f, el } = await screen(match());
    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Show answer')) as HTMLButtonElement).click();
    const req = http.expectOne('/api/v1/matches/q1/host');
    expect(req.request.body).toEqual({ action: 'REVEAL' });
    f.destroy();
  });

  it('reveals the answer with how the room voted and the leaderboard', async () => {
    const { f, el } = await screen(match({
      phase: 'REVEAL', currentQuestion: null,
      lastQuestion: { questionId: 5, stem: 'Capital of Peru?', correctAnswer: 'Lima', winnerUserId: null, number: 3, options: ['Lima', 'Quito', 'Bogotá', 'Caracas'], counts: [3, 1, 0, 0], answered: 4 },
    }));
    const votes = el.querySelectorAll('.votes li');
    expect(votes.length).toBe(4);
    expect(votes[0].classList).toContain('right');
    expect(votes[0].querySelector('.n')?.textContent).toBe('3');
    expect(el.querySelector('.board')?.textContent).toContain('Bo');
    expect(el.querySelector('.board')?.textContent).toContain('1,900');

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Next question')) as HTMLButtonElement).click();
    expect(http.expectOne('/api/v1/matches/q1/host').request.body).toEqual({ action: 'NEXT' });
    f.destroy();
  });

  it('offers the final results after the last answer', async () => {
    const { f, el } = await screen(match({
      phase: 'REVEAL', currentQuestion: null,
      lastQuestion: { questionId: 9, stem: 'Last?', correctAnswer: 'Yes', winnerUserId: null, number: 20, options: ['Yes', 'No'], counts: [1, 1], answered: 2 },
    }));
    expect(el.textContent).toContain('Final results');
    f.destroy();
  });

  it('shows the final standings on a podium', async () => {
    const { f, el } = await screen(match({ status: 'COMPLETED', phase: 'FINISHED', currentQuestion: null }));
    expect(el.querySelectorAll('.step').length).toBe(2);
    expect(el.querySelector('.place-1')?.textContent).toContain('Bo');
    f.destroy();
  });
});
