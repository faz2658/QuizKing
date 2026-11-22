/** Shapes of the backend responses the UI uses. */

/** A question's level in solo games. */
export type Difficulty = 'EASY' | 'MEDIUM' | 'ADVANCED';

/** The signed-in player as the token describes them; GUEST for a guest account. */
export interface User {
  userId: number;
  userName: string;
  email: string;
  role: 'USER' | 'ADMIN' | 'GUEST';
}

/** What the login service returns on sign-in, registration and refresh. */
export interface TokenResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
  /** When the refresh cookie set with this response runs out; null when the server kept the current one. */
  sessionExpiresAt?: string | null;
}

/** A game in the catalog or the recommendations, without its questions. */
export interface GameCard {
  gameId: number;
  gameName: string;
  gameDescription?: string | null;
  gameImage?: string | null;
  gameRules?: string | null;
  categoryId: number;
  topic?: string | null;
  questionCount?: number;
  mode: 'SINGLE' | 'MULTI';
  reason?: string;
  /** The country whose question set this game is from (e.g. IN); null for world trivia. */
  country?: string | null;
}

// ---- single player (game engine) ----
export interface QuestionView {
  questionId: number;
  number: number;
  difficulty: Difficulty;
  stem: string;
  options: string[];
  timeLimitSeconds: number;
  secondsRemaining: number;
  points: number;
}

/** A solo game in progress or finished, from the game engine. */
export interface SessionView {
  sessionId: string;
  gameId: number;
  gameName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  totalQuestions: number;
  answeredQuestions: number;
  score: number;
  maxScore: number;
  currentQuestion: QuestionView | null;
  variant?: SessionVariant;
  /** Correct answers in a row right now. */
  combo?: number;
  /** The bonus the next correct answer earns. */
  nextMultiplier?: number;
  /** Lifelines still available. */
  lifelines?: Lifeline[];
}

/** The three lifelines, each usable once per game. */
export type Lifeline = 'FIFTY_FIFTY' | 'SKIP' | 'EXTRA_TIME';

/** The game after a lifeline; skipped holds the skipped question's outcome for a skip. */
export interface LifelineView {
  lifeline: Lifeline;
  skipped: AnswerView | null;
  session: SessionView;
}

/** What happened to an answer in a solo game, with the updated session. */
export interface AnswerView {
  correct: boolean;
  timedOut: boolean;
  correctAnswer: string;
  pointsAwarded: number;
  session: SessionView;
  combo?: number;
  multiplier?: number;
  skipped?: boolean;
  explanation?: string | null;
}

/** One question in the end-of-game review. */
export interface ReviewItem {
  questionId?: number;
  number: number;
  difficulty: Difficulty;
  stem: string;
  yourAnswer: string | null;
  correctAnswer: string;
  correct: boolean;
  timedOut: boolean;
  pointsAwarded: number;
  points: number;
  skipped?: boolean;
  multiplier?: number;
  explanation?: string | null;
}

/** A finished solo game with its review. */
export interface ResultView {
  sessionId: string;
  gameId: number;
  gameName: string;
  score: number;
  maxScore: number;
  percentage: number;
  correctAnswers: number;
  totalQuestions: number;
  review: ReviewItem[];
  variant?: SessionVariant;
  /** The day (DAILY) or challenge id (CHALLENGE). */
  variantKey?: string | null;
  /** The score a friend challenge had to beat. */
  opponentScore?: number | null;
}

// ---- adaptive ----
export interface AdaptiveQuestion {
  questionId: number;
  number: number;
  level: number;
  stem: string;
  options: string[];
  secondsRemaining: number;
  points: number;
}

/** An adaptive game: after each block of questions the level goes up or down, by the share answered right. */
export interface AdaptiveSession {
  sessionId: string;
  gameId: number;
  gameName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  level: number;
  score: number;
  answered: number;
  totalQuestions: number;
  currentQuestion: AdaptiveQuestion | null;
}

/** What happened to an adaptive answer, and whether the level changed. */
export interface AdaptiveAnswer {
  correct: boolean;
  correctAnswer: string;
  points: number;
  levelChange: 'UP' | 'DOWN' | 'SAME';
  session: AdaptiveSession;
}

// ---- multiplayer ----
export interface LobbyStatus {
  state: 'WAITING' | 'MATCHED' | 'EXPIRED';
  gameId: number;
  players: number[];
  closesAt: string | null;
  matchId: string | null;
}

/** A multiplayer match as one player (or the big screen) sees it. */
export interface MatchView {
  matchId: string;
  gameId: number;
  gameName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  totalQuestions: number;
  pointsPerQuestion: number;
  secondsPerQuestion: number;
  standings: { userId: number; score: number; questionsWon: number; name?: string | null; country?: string | null; bot?: boolean }[];
  currentQuestion: {
    questionId: number;
    number: number;
    stem: string;
    options: string[];
    secondsRemaining: number;
    yourAnswer: string | null;
  } | null;
  lastQuestion: {
    questionId: number;
    stem: string;
    correctAnswer: string;
    winnerUserId: number | null;
    /** 1-based position in the match. */
    number?: number;
    /** Room quiz: the options and how many picked each (same order). */
    options?: string[] | null;
    counts?: number[] | null;
    answered?: number;
  } | null;
  /** Changes whenever anything on the screen would; the server waits for a different one (see nextMatch). */
  version?: string;
  /** A live room quiz (party mode) is host-paced; anything else is a fastest-finger match. */
  format?: 'FASTEST_FINGER' | 'ROOM_QUIZ';
  phase?: MatchPhase;
  playerCount?: number;
  /** How many answered the open question. */
  answered?: number;
  /** Room quiz: seconds until the first question opens. */
  secondsToStart?: number;
  /** Room quiz: seconds until the next question opens by itself (0: the host moves on). */
  secondsToNext?: number;
  revealSeconds?: number;
  /** Room quiz: this player's rank and how their last answer went (null for the big screen). */
  you?: { rank: number | null; score: number; lastAnswer: string | null; lastCorrect: boolean; lastPoints: number } | null;
}

/** Where a room quiz is: the countdown, a question, its reveal, or the end. */
export type MatchPhase = 'GET_READY' | 'QUESTION' | 'REVEAL' | 'FINISHED';
/** What the host of a room quiz can do: reveal the answer, move on, or end the quiz. */
export type HostAction = 'REVEAL' | 'NEXT' | 'END';

/** What happened to an answer in a match, with the updated match. */
export interface MatchAnswer {
  correct: boolean;
  wonQuestion: boolean;
  score: number;
  match: MatchView;
}

// ---- reports ----
export type GameMode = 'SINGLE' | 'MULTI' | 'ADAPTIVE';

/** One finished game in a player's history. */
export interface GameReport {
  id: string;
  mode: GameMode;
  userId: number;
  gameId: number;
  gameName: string;
  score: number;
  maxScore: number | null;
  correctAnswers: number;
  totalQuestions: number;
  rank: number | null;
  players: number | null;
  highestLevel: number | null;
  completedAt: string;
}

/** A player's totals for one mode. */
export interface ModeSummary {
  gamesPlayed: number;
  bestScore: number;
  averageAccuracy: number;
}

/** A player's totals over every mode. */
export interface UserSummary {
  userId: number;
  gamesPlayed: number;
  averageAccuracy: number;
  multiplayerWins: number;
  modes: Record<GameMode, ModeSummary>;
}

// ---- question bank (admin) ----
export interface ImportStatus {
  state: 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED';
  fetched: number;
  added: number;
  topics: number;
  startedAt: string | null;
  finishedAt: string | null;
  message: string | null;
}

/** How many questions a topic has; shown on the welcome page. */
export interface TopicStats {
  category: string;
  topic: string;
  questions: number;
}

/** An AI-written question waiting for an admin to approve or reject it. */
export interface DraftQuestion {
  id: string;
  categoryId: number;
  topicName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  question: {
    questionStem: string;
    questionLevel: number;
    option1: string | null;
    option2: string | null;
    option3: string | null;
    option4: string | null;
    correctAnswer: string;
    explanation?: string | null;
  };
}

/** How a request for AI drafts went: how many were created and why the others were dropped. */
export interface GenerationResult {
  requested: number;
  created: number;
  invalidReplies: number;
  duplicates: number;
  drafts: DraftQuestion[];
}

// ---- game manager (admin) ----
export interface Category {
  categoryId: number;
  categoryName: string;
  topic: { topicId: number; topicName: string; questionCount: number }[] | null;
}

/**
 * What kind of solo game a session is: a normal game, the Daily Challenge, a custom quiz, a friend challenge or a
 * duel.
 */
export type SessionVariant = 'STANDARD' | 'DAILY' | 'CUSTOM' | 'CHALLENGE' | 'DUEL';

/** Today's Daily Challenge from the player's side. */
export interface DailyView {
  date: string;
  totalQuestions: number;
  topics: string[];
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
  sessionId: string | null;
  score: number | null;
  maxScore: number | null;
  secondsUntilNext: number;
}

/** A row of the Daily Challenge leaderboard. */
export interface DailyEntry {
  rank: number;
  userId: number;
  name: string;
  score: number;
  maxScore: number | null;
  correctAnswers: number;
  totalQuestions: number;
  country?: string | null;
}

/** The Daily Challenge leaderboard for one day, with the player's own row. */
export interface DailyBoard {
  date: string;
  players: number;
  top: DailyEntry[];
  you: DailyEntry | null;
  /** Your rank among players from your country and "top N%" of everyone (null if you haven't played). */
  place?: { country: string | null; countryRank: number; countryPlayers: number; topPercent: number } | null;
}

/** A country's row in the weekly Country Cup. */
export interface CupEntry {
  rank: number;
  country: string;
  points: number;
  players: number;
  /** The country's top player this week. */
  star: string | null;
}

/** The weekly Country Cup (Monday to Monday, UTC). {@code yours} is null until the player picks a country. */
export interface CupView {
  weekStart: string;
  endsAt: string;
  standings: CupEntry[];
  countries: number;
  yours: { country: string; rank: number; points: number; players: number; gapToNext: number; yourXp: number; yourRank: number } | null;
  lastWeek: { country: string; points: number } | null;
}

/** The player's run of days played. */
export interface Streak {
  current: number;
  best: number;
  playedToday: boolean;
  /** Streak shields left (from invites): each covers one missed day. */
  shields?: number;
}

/** The player's invite code and what it earned. */
export interface InviteView {
  code: string;
  invited: number;
  shields: number;
}

/** The player's XP, level and title, streak and games played. */
export interface Progress {
  userId: number;
  xp: number;
  level: number;
  title: string;
  levelStartXp: number;
  nextLevelXp: number;
  streak: Streak;
  gamesPlayed: number;
}

/** The time span a leaderboard covers. */
export type BoardPeriod = 'DAY' | 'WEEK' | 'ALL';

/** A row of the XP leaderboard. */
export interface XpEntry {
  rank: number;
  userId: number;
  name: string;
  xp: number;
  games: number;
  title: string;
  country?: string | null;
}

/** The XP leaderboard for a period, optionally for one category, with the player's own row. */
export interface XpBoard {
  period: BoardPeriod;
  categoryId: number | null;
  players: number;
  top: XpEntry[];
  you: XpEntry | null;
}

/** A badge and how far the player is towards it; unlockedAt is set once earned. */
export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  progress: number;
  goal: number;
  unlockedAt: string | null;
}

/** A player waiting in a private room. */
export interface RoomPlayer {
  userId: number;
  name: string;
}

/** A private room: who is in it, its state and, once started, its match. */
export interface RoomStatus {
  code: string;
  gameId: number;
  hostId: number;
  players: RoomPlayer[];
  state: 'WAITING' | 'STARTED' | 'CLOSED';
  matchId: string | null;
  maxPlayers: number;
  /** A big-screen party: the host shows the game and doesn't play. */
  party: boolean;
  /** A live room quiz: 20 random medium questions from every topic, no game. */
  quiz?: boolean;
}

/** A friend challenge from the caller's side; yourStatus is OWN for the challenger. */
export interface ChallengeView {
  id: string;
  challengerName: string;
  gameName: string;
  score: number;
  maxScore: number;
  correctAnswers: number;
  totalQuestions: number;
  yourStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'OWN';
  yourSessionId: string | null;
  yourScore: number | null;
}

/** One player of a challenge between two friends; the friend's score shows once you've finished. */
export interface DuelPlayer {
  name: string | null;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'DECLINED';
  score: number | null;
  maxScore: number | null;
  correctAnswers: number | null;
  /** Only for your own game. */
  sessionId: string | null;
}

/** Where a challenge between two friends stands. */
export type DuelState = 'WAITING' | 'DECLINED' | 'EXPIRED' | 'PLAYING' | 'FINISHED';

/** A challenge between two friends, from your side. */
export interface DuelView {
  id: string;
  gameName: string;
  gameId: number;
  international: boolean;
  /** 0 until the friend accepts (that's when the questions are picked). */
  totalQuestions: number;
  state: DuelState;
  role: 'CHALLENGER' | 'OPPONENT' | 'INVITED' | 'OUTSIDER';
  challengerName: string;
  you: DuelPlayer | null;
  friend: DuelPlayer | null;
  outcome: 'WON' | 'LOST' | 'TIE' | null;
  /** When it closes, while it is still open. */
  expiresAt: string | null;
}

/** Why a player flagged a question. */
export type FlagReason = 'WRONG_ANSWER' | 'UNCLEAR' | 'TYPO' | 'OFFENSIVE' | 'OTHER';

/** A question as the question bank stores it (admin). */
export interface BankQuestion {
  questionId: number;
  questionStem: string;
  questionLevel: number;
  questionType?: string | null;
  option1: string | null;
  option2: string | null;
  option3: string | null;
  option4: string | null;
  correctAnswer: string;
  explanation?: string | null;
}

/** A question players disliked or flagged, with their votes, reasons and notes. */
export interface FeedbackItem {
  questionId: number;
  categoryId: number;
  topicName: string;
  question: BankQuestion;
  ups: number;
  downs: number;
  flags: number;
  reasons: Partial<Record<FlagReason, number>>;
  notes: string[];
}

/** A question taken out of the games because of player feedback; an admin can restore it. */
export interface RetiredQuestion {
  questionId: number;
  categoryId: number;
  topicName: string;
  question: BankQuestion;
  why: string;
  ups: number;
  downs: number;
  flags: number;
  retiredAt: string;
}

/** The admin's question feedback page. */
export interface FeedbackReview {
  questions: FeedbackItem[];
  retired: RetiredQuestion[];
}

/** The difficulty of a custom quiz; MIXED mixes all three. */
export type CustomDifficulty = 'EASY' | 'MEDIUM' | 'ADVANCED' | 'MIXED';

/** How many players are online in a country. */
export interface CountryCount {
  country: string;
  players: number;
}

/** A multiplayer lobby with players waiting. */
export interface OpenLobby {
  gameId: number;
  players: number;
  closesAt: string;
}

/** Who is online now: by country, in open lobbies and in the quick-match queue. */
export interface OnlineView {
  online: number;
  countries: CountryCount[];
  lobbies: OpenLobby[];
  quickQueue: number;
}

/** The player's place in the quick-match queue; a bot joins when nobody else does in time. */
export interface QuickStatus {
  state: 'WAITING' | 'MATCHED' | 'NOT_IN_QUEUE' | 'NO_GAMES';
  queueSize: number;
  waitedSeconds: number;
  botInSeconds: number;
  matchId: string | null;
  gameId: number | null;
  withBot: boolean;
}

/** Countries ranked by their players' XP for a period. */
export interface CountryBoard {
  period: BoardPeriod;
  countries: { rank: number; country: string; xp: number; players: number }[];
}

/** Where a World Arena round is: before joining opens, joining, live, or finished. */
export type ArenaPhase = 'SOON' | 'JOINING' | 'LIVE' | 'FINISHED';

/** A player's place in a World Arena round. */
export interface ArenaStanding {
  rank: number;
  userId: number;
  name: string;
  country: string | null;
  survived: number;
  score: number;
}

/** A World Arena round as the player sees it. */
export interface ArenaRound {
  roundId: string;
  phase: ArenaPhase;
  startsAt: string;
  joinOpensAt: string;
  players: number;
  alive: number;
  you: { joined: boolean; alive: boolean; survived: number; score: number; rank: number | null };
  question: { index: number; number: number; total: number; stem: string; options: string[]; secondsLeft: number; yourAnswer: string | null } | null;
  reveal: { index: number; stem: string; correctAnswer: string; yourAnswer: string | null; eliminatedNow: number } | null;
  standings: ArenaStanding[];
  endsAt: string | null;
}

/** The round to show, the one before it, and the server time to sync clocks with. */
export interface ArenaView {
  serverTime: string;
  round: ArenaRound;
  previous: ArenaRound | null;
}
