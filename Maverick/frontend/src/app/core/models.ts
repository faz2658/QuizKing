/** Shapes of the backend responses the UI uses. */

export type Difficulty = 'EASY' | 'MEDIUM' | 'ADVANCED';

export interface User {
  userId: number;
  userName: string;
  email: string;
  role: 'USER' | 'ADMIN' | 'GUEST';
}

export interface TokenResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

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

export type Lifeline = 'FIFTY_FIFTY' | 'SKIP' | 'EXTRA_TIME';

export interface LifelineView {
  lifeline: Lifeline;
  skipped: AnswerView | null;
  session: SessionView;
}

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

export interface MatchView {
  matchId: string;
  gameId: number;
  gameName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  totalQuestions: number;
  pointsPerQuestion: number;
  secondsPerQuestion: number;
  standings: { userId: number; score: number; questionsWon: number }[];
  currentQuestion: {
    questionId: number;
    number: number;
    stem: string;
    options: string[];
    secondsRemaining: number;
    yourAnswer: string | null;
  } | null;
  lastQuestion: { questionId: number; stem: string; correctAnswer: string; winnerUserId: number | null } | null;
}

export interface MatchAnswer {
  correct: boolean;
  wonQuestion: boolean;
  score: number;
  match: MatchView;
}

// ---- reports ----
export type GameMode = 'SINGLE' | 'MULTI' | 'ADAPTIVE';

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

export interface ModeSummary {
  gamesPlayed: number;
  bestScore: number;
  averageAccuracy: number;
}

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

export interface TopicStats {
  category: string;
  topic: string;
  questions: number;
}

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

export type SessionVariant = 'STANDARD' | 'DAILY' | 'CUSTOM' | 'CHALLENGE';

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

export interface DailyEntry {
  rank: number;
  userId: number;
  name: string;
  score: number;
  maxScore: number | null;
  correctAnswers: number;
  totalQuestions: number;
}

export interface DailyBoard {
  date: string;
  players: number;
  top: DailyEntry[];
  you: DailyEntry | null;
}

export interface Streak {
  current: number;
  best: number;
  playedToday: boolean;
}

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

export type BoardPeriod = 'DAY' | 'WEEK' | 'ALL';

export interface XpEntry {
  rank: number;
  userId: number;
  name: string;
  xp: number;
  games: number;
  title: string;
}

export interface XpBoard {
  period: BoardPeriod;
  categoryId: number | null;
  players: number;
  top: XpEntry[];
  you: XpEntry | null;
}

export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  progress: number;
  goal: number;
  unlockedAt: string | null;
}

export interface RoomPlayer {
  userId: number;
  name: string;
}

export interface RoomStatus {
  code: string;
  gameId: number;
  hostId: number;
  players: RoomPlayer[];
  state: 'WAITING' | 'STARTED' | 'CLOSED';
  matchId: string | null;
  maxPlayers: number;
}

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

export type FlagReason = 'WRONG_ANSWER' | 'UNCLEAR' | 'TYPO' | 'OFFENSIVE' | 'OTHER';

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

export interface FeedbackReview {
  questions: FeedbackItem[];
  retired: RetiredQuestion[];
}

export type CustomDifficulty = 'EASY' | 'MEDIUM' | 'ADVANCED' | 'MIXED';
