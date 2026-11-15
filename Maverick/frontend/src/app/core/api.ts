import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { retryWhileLoading } from './http-errors';
import {
  Achievement,
  AdaptiveAnswer,
  AdaptiveSession,
  AnswerView,
  BoardPeriod,
  Category,
  DailyBoard,
  DailyView,
  GameCard,
  GameMode,
  GameReport,
  DraftQuestion,
  GenerationResult,
  ImportStatus,
  LobbyStatus,
  MatchAnswer,
  MatchView,
  Progress,
  ResultView,
  SessionView,
  TopicStats,
  UserSummary,
  XpBoard,
} from './models';

interface RawGame {
  gameId: number;
  gameName: string;
  gameDescription?: string;
  gameImage?: string;
  gameRules?: string;
  categoryId: number;
  topic?: string | { topicName?: string } | null;
  questionCount?: number;
  questions?: unknown[];
  gameType?: { gameTypeId?: number } | null;
}

function toCard(g: RawGame, mode: GameCard['mode'] = 'SINGLE'): GameCard {
  const topic = typeof g.topic === 'string' ? g.topic : (g.topic?.topicName ?? null);
  return {
    gameId: g.gameId,
    gameName: g.gameName,
    gameDescription: g.gameDescription ?? null,
    gameImage: g.gameImage ?? null,
    gameRules: g.gameRules ?? null,
    categoryId: g.categoryId,
    topic,
    questionCount: g.questionCount ?? g.questions?.length,
    mode: g.gameType?.gameTypeId === 2 ? 'MULTI' : mode,
  };
}

/** Game catalog: the game manager when it's up, otherwise the games the engine has cached (demo mode). */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  /**
   * Every solo game: the game manager's catalog plus games only the engine knows (its demo games).
   * Either source may be down; the page still works with the other one.
   */
  games(): Observable<GameCard[]> {
    const manager = this.http.get<RawGame[]>('/api/game/games').pipe(catchError(() => of(null)));
    const engine = this.http.get<RawGame[]>('/api/v1/games').pipe(catchError(() => of(null)));
    return forkJoin([manager, engine]).pipe(
      map(([m, e]) => {
        if (m === null && e === null) {
          throw new Error('No game service is reachable');
        }
        const seen = new Set<number>();
        return [...(m ?? []), ...(e ?? [])]
          .filter((g) => !seen.has(g.gameId) && !!seen.add(g.gameId))
          .map((g) => toCard(g));
      }),
    );
  }

  multiplayerGames(): Observable<GameCard[]> {
    return this.http.get<RawGame[]>('/api/game/games/mp').pipe(
      map((gs) => gs.map((g) => toCard(g, 'MULTI'))),
      catchError(() => of([])),
    );
  }

  recommended(userId: number): Observable<GameCard[]> {
    return this.http
      .get<{ gameId: number; gameName: string; gameImage: string | null; categoryId: number; mode: 'SINGLE' | 'MULTI'; reason: string }[]>(
        `/api/v1/recommendation/users/${userId}`,
      )
      .pipe(
        map((rs) => rs.map((r) => ({ ...r, gameDescription: null, mode: r.mode }) as GameCard)),
        catchError(() => of([])),
      );
  }

  search(q: string): Observable<GameCard[]> {
    return this.http
      .get<RawGame[]>('/api/v1/search', { params: new HttpParams().set('q', q) })
      .pipe(map((gs) => gs.map((g) => toCard(g))));
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/game/categories');
  }

  createGame(categoryId: number, topicName: string, body: unknown): Observable<RawGame> {
    return this.http.post<RawGame>(`/api/game/category/${categoryId}/${encodeURIComponent(topicName)}`, body);
  }

  createMultiplayerGame(categoryId: number, topicName: string, body: unknown): Observable<RawGame> {
    return this.http.post<RawGame>(`/api/game/category/mp/${categoryId}/${encodeURIComponent(topicName)}`, body);
  }
}

/** Question bank management (admin). */
@Injectable({ providedIn: 'root' })
export class QuestionBankApi {
  private readonly http = inject(HttpClient);

  stats(): Observable<TopicStats[]> {
    return this.http.get<TopicStats[]>('/api/v1/question-generator/stats');
  }

  importStatus(): Observable<ImportStatus> {
    return this.http.get<ImportStatus>('/api/v1/question-generator/import/opentdb');
  }

  startImport(): Observable<ImportStatus> {
    return this.http.post<ImportStatus>('/api/v1/question-generator/import/opentdb', null);
  }

  aiAvailable(): Observable<boolean> {
    return this.http.get<{ available: boolean }>('/api/v1/question-generator/ai/status').pipe(map((s) => s.available));
  }

  generateDrafts(categoryId: number, topicName: string, level: number, trueFalse: boolean, count: number): Observable<GenerationResult> {
    return this.http.post<GenerationResult>('/api/v1/question-generator/ai/drafts', { categoryId, topicName, level, trueFalse, count });
  }

  pendingDrafts(): Observable<DraftQuestion[]> {
    return this.http.get<DraftQuestion[]>('/api/v1/question-generator/ai/drafts');
  }

  approveDraft(id: string, question: DraftQuestion['question']): Observable<unknown> {
    return this.http.post(`/api/v1/question-generator/ai/drafts/${id}/approve`, question);
  }

  rejectDraft(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/question-generator/ai/drafts/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class QuizApi {
  private readonly http = inject(HttpClient);

  start(gameId: number, userId: number): Observable<SessionView> {
    return this.http.post<SessionView>('/api/v1/sessions', { gameId, userId }).pipe(retryWhileLoading());
  }

  session(id: string): Observable<SessionView> {
    return this.http.get<SessionView>(`/api/v1/sessions/${id}`);
  }

  answer(id: string, questionId: number, answer: string): Observable<AnswerView> {
    return this.http.post<AnswerView>(`/api/v1/sessions/${id}/answers`, { questionId, answer });
  }

  result(id: string): Observable<ResultView> {
    return this.http.get<ResultView>(`/api/v1/sessions/${id}/result`);
  }

  /** Today's challenge and whether the player has played it. */
  daily(): Observable<DailyView> {
    return this.http.get<DailyView>('/api/v1/daily');
  }

  startDaily(): Observable<SessionView> {
    return this.http.post<SessionView>('/api/v1/daily/sessions', null);
  }
}

@Injectable({ providedIn: 'root' })
export class BoardsApi {
  private readonly http = inject(HttpClient);

  leaderboard(period: BoardPeriod, categoryId?: number | null): Observable<XpBoard> {
    let params = new HttpParams().set('period', period);
    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }
    return this.http.get<XpBoard>('/api/v1/reports/leaderboards', { params });
  }

  daily(date?: string): Observable<DailyBoard> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<DailyBoard>('/api/v1/reports/daily', { params });
  }
}

@Injectable({ providedIn: 'root' })
export class AdaptiveApi {
  private readonly http = inject(HttpClient);

  start(gameId: number, userId: number): Observable<AdaptiveSession> {
    return this.http.post<AdaptiveSession>('/api/v1/adaptive/sessions', { gameId, userId }).pipe(retryWhileLoading());
  }

  session(id: string): Observable<AdaptiveSession> {
    return this.http.get<AdaptiveSession>(`/api/v1/adaptive/sessions/${id}`);
  }

  answer(id: string, questionId: number, answer: string): Observable<AdaptiveAnswer> {
    return this.http.post<AdaptiveAnswer>(`/api/v1/adaptive/sessions/${id}/answers`, { questionId, answer });
  }
}

@Injectable({ providedIn: 'root' })
export class MultiplayerApi {
  private readonly http = inject(HttpClient);

  join(gameId: number, userId: number): Observable<LobbyStatus> {
    return this.http.post<LobbyStatus>(`/api/v1/matching/${gameId}/players/${userId}`, null);
  }

  lobby(gameId: number, userId: number): Observable<LobbyStatus> {
    return this.http.get<LobbyStatus>(`/api/v1/matching/${gameId}/players/${userId}`);
  }

  leave(gameId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/matching/${gameId}/players/${userId}`);
  }

  match(matchId: string, userId: number): Observable<MatchView> {
    return this.http
      .get<MatchView>(`/api/v1/matches/${matchId}`, { params: new HttpParams().set('userId', userId) })
      .pipe(retryWhileLoading(10));
  }

  answer(matchId: string, userId: number, questionId: number, answer: string): Observable<MatchAnswer> {
    return this.http.post<MatchAnswer>(`/api/v1/matches/${matchId}/answers`, { userId, questionId, answer });
  }
}

@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  /** Every badge, earned or not. */
  achievements(userId: number): Observable<Achievement[]> {
    return this.http.get<Achievement[]>(`/api/v1/reports/users/${userId}/achievements`);
  }

  /** XP, level and streak. */
  progress(userId: number): Observable<Progress> {
    return this.http.get<Progress>(`/api/v1/reports/users/${userId}/progress`);
  }

  summary(userId: number): Observable<UserSummary> {
    return this.http.get<UserSummary>(`/api/v1/reports/users/${userId}/summary`);
  }

  reports(userId: number, mode?: GameMode): Observable<GameReport[]> {
    const params = mode ? new HttpParams().set('mode', mode) : undefined;
    return this.http.get<GameReport[]>(`/api/v1/reports/users/${userId}`, { params });
  }
}

