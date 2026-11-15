import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AiQuizPlay } from './ai-quiz';

const BASE = '/api/v1/question-generator/topic-quizzes/q1';
const view = (over: object = {}) => ({
  id: 'q1', topic: 'Space', creatorName: 'Ada', status: 'READY', written: 2, target: 2, problem: null, players: 0,
  leaderboard: [], yourAnswers: null, yourScore: null, createdAt: '2026-11-15T00:00:00Z',
  questions: [
    { text: 'Red planet?', options: ['Mars', 'Venus', 'Earth', 'Jupiter'], answer: null },
    { text: 'Biggest planet?', options: ['Mars', 'Venus', 'Earth', 'Jupiter'], answer: null },
  ],
  ...over,
});

describe('AI quiz', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => vi.useRealTimers());

  it('shows the AI writing, then refreshes until the quiz is ready', () => {
    const f = TestBed.createComponent(AiQuizPlay);
    f.componentRef.setInput('id', 'q1');
    f.detectChanges();
    http.expectOne(BASE).flush(view({ status: 'GENERATING', written: 1, target: 8, questions: [] }));
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('Writing your Space quiz');
    vi.advanceTimersByTime(2000);
    http.expectOne(BASE).flush(view());
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('one go only');
    f.destroy();
  });

  it('counts a question the timer ran out on as -1', () => {
    const f = TestBed.createComponent(AiQuizPlay);
    f.componentRef.setInput('id', 'q1');
    f.detectChanges();
    http.expectOne(BASE).flush(view());
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    (el.querySelector('.big') as HTMLButtonElement).click();
    f.detectChanges();
    (el.querySelectorAll('.choice')[0] as HTMLButtonElement).click(); // Mars
    f.detectChanges();
    vi.advanceTimersByTime(20_000); // second question times out
    const req = http.expectOne(`${BASE}/answers`);
    expect(req.request.body).toEqual({ answers: [0, -1] });
    req.flush(view({ yourAnswers: [0, -1], yourScore: 1, players: 1, leaderboard: [{ rank: 1, name: 'Bo', score: 1, you: true }],
      questions: [
        { text: 'Red planet?', options: ['Mars', 'Venus', 'Earth', 'Jupiter'], answer: 0 },
        { text: 'Biggest planet?', options: ['Mars', 'Venus', 'Earth', 'Jupiter'], answer: 3 },
      ] }));
    f.detectChanges();
    expect(el.textContent).toContain('1/2');
    expect(el.textContent).toContain('time ran out');
    f.destroy();
  });
});
