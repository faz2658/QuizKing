import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { retryWhileLoading } from './http-errors';
import { World } from './world';
import {
  Achievement,
  AdaptiveAnswer,
  AdaptiveSession,
  AnswerView,
  BoardPeriod,
  BankQuestion,
  ArenaView,
  Category,
  ChallengeView,
  CountryBoard,
  CustomDifficulty,
  DailyBoard,
  DailyView,
  GameCard,
  GameMode,
  GameReport,
  DraftQuestion,
  GenerationResult,
  FeedbackReview,
  FlagReason,
  ImportStatus,
  Lifeline,
  LifelineView,
  LobbyStatus,
  MatchAnswer,
  MatchView,
  Progress,
  QuickStatus,
  ResultView,
  RoomStatus,
  CupView,
  InviteView,
  SessionView,
  TopicStats,
  UserSummary,
  XpBoard,
} from './models';
import { CountrySet, WORLD } from './region';

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
  country?: string | null;
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
    country: g.country ?? null,
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
  games(country?: string): Observable<GameCard[]> {
    const params = country ? new HttpParams().set('country', country) : undefined;
    const manager = this.http.get<RawGame[]>('/api/game/games', { params }).pipe(catchError(() => of(null)));
    // the engine's own demo games are world trivia
    const engine = !country || country === WORLD ? this.http.get<RawGame[]>('/api/v1/games').pipe(catchError(() => of(null))) : of([]);
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

  multiplayerGames(country?: string): Observable<GameCard[]> {
    const params = country ? new HttpParams().set('country', country) : undefined;
    return this.http.get<RawGame[]>('/api/game/games/mp', { params }).pipe(
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

  /** Countries with their own question set (empty if the game manager can't say). */
  countries(): Observable<CountrySet[]> {
    return this.http.get<CountrySet[]>('/api/game/countries').pipe(catchError(() => of([])));
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

  /** A player's thumbs up/down (1/-1) and/or flag on a question. */
  feedback(questionId: number, rating: number, flag?: FlagReason | null, note?: string | null): Observable<{ retired: boolean }> {
    return this.http.post<{ retired: boolean }>('/api/v1/question-generator/feedback', { questionId, rating, flag: flag ?? null, note: note ?? null });
  }

  feedbackReview(): Observable<FeedbackReview> {
    return this.http.get<FeedbackReview>('/api/v1/question-generator/feedback');
  }

  dismissFeedback(questionId: number): Observable<void> {
    return this.http.post<void>(`/api/v1/question-generator/feedback/${questionId}/dismiss`, null);
  }

  restoreQuestion(questionId: number): Observable<BankQuestion> {
    return this.http.post<BankQuestion>(`/api/v1/question-generator/feedback/${questionId}/restore`, null);
  }

  updateQuestion(categoryId: number, topicName: string, q: BankQuestion): Observable<BankQuestion> {
    return this.http.put<BankQuestion>(`/api/v1/question-generator/category/${categoryId}/${encodeURIComponent(topicName)}/${q.questionId}`, q);
  }

  deleteQuestion(categoryId: number, topicName: string, questionId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/question-generator/category/${categoryId}/${encodeURIComponent(topicName)}/${questionId}`);
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
  private readonly world = inject(World);

  /** The player's country, sent when a game starts so most questions come from it (never shown to the player). */
  private country(): { country?: string } {
    const c = this.world.country();
    return c ? { country: c } : {};
  }

  /** @param international an International quiz: questions from every country alike, not mostly the player's own */
  start(gameId: number, userId: number, international = false): Observable<SessionView> {
    const from = international ? { international: true } : this.country();
    return this.http.post<SessionView>('/api/v1/sessions', { gameId, userId, ...from }).pipe(retryWhileLoading());
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

  lifeline(id: string, lifeline: Lifeline, questionId: number): Observable<LifelineView> {
    return this.http.post<LifelineView>(`/api/v1/sessions/${id}/lifelines`, { lifeline, questionId });
  }

  /** Today's challenge and whether the player has played it. */
  daily(): Observable<DailyView> {
    return this.http.get<DailyView>('/api/v1/daily');
  }

  startDaily(): Observable<SessionView> {
    return this.http.post<SessionView>('/api/v1/daily/sessions', null);
  }

  startCustom(gameIds: number[], difficulty: CustomDifficulty, count: number): Observable<SessionView> {
    return this.http.post<SessionView>('/api/v1/sessions/custom', { gameIds, difficulty, count, ...this.country() }).pipe(retryWhileLoading());
  }

  /** Turns a finished game into a challenge link for friends. */
  createChallenge(sessionId: string): Observable<ChallengeView> {
    return this.http.post<ChallengeView>('/api/v1/challenges', { sessionId });
  }

  challenge(id: string): Observable<ChallengeView> {
    return this.http.get<ChallengeView>(`/api/v1/challenges/${id}`);
  }

  acceptChallenge(id: string): Observable<SessionView> {
    return this.http.post<SessionView>(`/api/v1/challenges/${id}/sessions`, null);
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

  countries(period: BoardPeriod): Observable<CountryBoard> {
    return this.http.get<CountryBoard>('/api/v1/reports/leaderboards/countries', { params: new HttpParams().set('period', period) });
  }

  daily(date?: string): Observable<DailyBoard> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<DailyBoard>('/api/v1/reports/daily', { params });
  }

  cup(): Observable<CupView> {
    return this.http.get<CupView>('/api/v1/reports/cup');
  }

  invites(): Observable<InviteView> {
    return this.http.get<InviteView>('/api/v1/reports/invites/me');
  }
}

@Injectable({ providedIn: 'root' })
export class AdaptiveApi {
  private readonly http = inject(HttpClient);
  private readonly world = inject(World);

  /** Sends the player's country (never shown) so other countries' questions stay out of shared topics. */
  start(gameId: number, userId: number): Observable<AdaptiveSession> {
    const c = this.world.country();
    return this.http.post<AdaptiveSession>('/api/v1/adaptive/sessions', { gameId, userId, ...(c ? { country: c } : {}) }).pipe(retryWhileLoading());
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

  joinQuick(): Observable<QuickStatus> {
    return this.http.post<QuickStatus>('/api/v1/matching/quick', null);
  }

  quickStatus(): Observable<QuickStatus> {
    return this.http.get<QuickStatus>('/api/v1/matching/quick');
  }

  leaveQuick(): Observable<void> {
    return this.http.delete<void>('/api/v1/matching/quick');
  }

  createRoom(gameId: number, party = false): Observable<RoomStatus> {
    return this.http.post<RoomStatus>('/api/v1/matching/rooms', { gameId, party });
  }

  room(code: string): Observable<RoomStatus> {
    return this.http.get<RoomStatus>(`/api/v1/matching/rooms/${encodeURIComponent(code)}`);
  }

  joinRoom(code: string): Observable<RoomStatus> {
    return this.http.post<RoomStatus>(`/api/v1/matching/rooms/${encodeURIComponent(code)}/players`, null);
  }

  leaveRoom(code: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/matching/rooms/${encodeURIComponent(code)}/players`);
  }

  startRoom(code: string): Observable<RoomStatus> {
    return this.http.post<RoomStatus>(`/api/v1/matching/rooms/${encodeURIComponent(code)}/start`, null);
  }

  match(matchId: string, userId: number): Observable<MatchView> {
    return this.http
      .get<MatchView>(`/api/v1/matches/${matchId}`, { params: new HttpParams().set('userId', userId) })
      .pipe(retryWhileLoading(10));
  }

  /**
   * The match once it differs from {@code after} (its {@code version}): at once if it already does, otherwise the
   * server holds the request until someone answers, a bot answers or the question closes (at most 20 s).
   */
  nextMatch(matchId: string, userId: number, after?: string): Observable<MatchView> {
    let params = new HttpParams().set('userId', userId);
    if (after) params = params.set('after', after);
    return this.http.get<MatchView>(`/api/v1/matches/${matchId}/next`, { params }).pipe(retryWhileLoading(10));
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

@Injectable({ providedIn: 'root' })
export class ArenaApi {
  private readonly http = inject(HttpClient);

  view(): Observable<ArenaView> {
    return this.http.get<ArenaView>('/api/v1/arena');
  }

  join(country: string | null): Observable<ArenaView> {
    return this.http.post<ArenaView>('/api/v1/arena/join', { country });
  }

  answer(questionIndex: number, answer: string): Observable<ArenaView> {
    return this.http.post<ArenaView>('/api/v1/arena/answers', { questionIndex, answer });
  }
}
