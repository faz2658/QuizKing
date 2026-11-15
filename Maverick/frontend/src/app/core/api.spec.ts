import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogApi, QuizApi } from './api';
import { GameCard } from './models';

describe('API clients', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('falls back to the game engine catalog when the game manager is down', () => {
    let games: GameCard[] = [];
    TestBed.inject(CatalogApi).games().subscribe((g) => (games = g));

    http.expectOne('/api/game/games').flush('down', { status: 502, statusText: 'Bad Gateway' });
    http.expectOne('/api/v1/games').flush([{ gameId: 101, gameName: 'World Capitals', categoryId: 1, topic: 'Geography', questionCount: 11 }]);

    expect(games).toEqual([expect.objectContaining({ gameId: 101, topic: 'Geography', mode: 'SINGLE', questionCount: 11 })]);
  });

  it('maps game manager games, including multiplayer ones and nested topics', () => {
    let games: GameCard[] = [];
    TestBed.inject(CatalogApi).games().subscribe((g) => (games = g));

    http.expectOne('/api/game/games').flush([
      { gameId: 5, gameName: 'Race', categoryId: 2, topic: { topicName: 'Rivers' }, questions: [{}, {}], gameType: { gameTypeId: 2 } },
    ]);

    expect(games[0]).toEqual(expect.objectContaining({ topic: 'Rivers', questionCount: 2, mode: 'MULTI' }));
  });

  it('retries starting a game while the engine is still loading it', () => {
    vi.useFakeTimers();
    let started = false;
    TestBed.inject(QuizApi).start(5, 9).subscribe(() => (started = true));

    http.expectOne('/api/v1/sessions').flush({ detail: 'loading', retryAfterSeconds: 1 }, { status: 503, statusText: 'Unavailable' });
    vi.advanceTimersByTime(1000);
    http.expectOne('/api/v1/sessions').flush({ sessionId: 's' });

    expect(started).toBe(true);
    vi.useRealTimers();
  });
});
