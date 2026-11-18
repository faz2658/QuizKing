package com.fazeel.quizcadia.api;

import java.time.Instant;
import java.util.List;

import com.fazeel.quizcadia.match.MatchQuestion;
import com.fazeel.quizcadia.match.MultiplayerMatch;
import com.fazeel.quizcadia.match.MultiplayerMatch.Phase;
import com.fazeel.quizcadia.match.PlayerAnswer;
import com.fazeel.quizcadia.match.PlayerScore;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class Views {

	private Views() {
	}

	public record AnswerRequest(@Positive int userId, @Positive int questionId, @NotNull String answer) {
	}

	/** The host's big screen paces a room quiz. */
	public record HostRequest(@NotNull MultiplayerMatch.HostAction action) {
	}

	public record PlayerView(int userId, int score, int questionsWon, String name, String country, boolean bot) {
	}

	public record QuestionView(int questionId, int number, String stem, List<String> options, long secondsRemaining,
			String yourAnswer) {
	}

	/**
	 * The last closed question. Room quizzes add how many picked each option ({@code counts}, in the order of
	 * {@code options}) and how many answered.
	 */
	public record RevealView(int questionId, String stem, String correctAnswer, Integer winnerUserId, int number,
			List<String> options, List<Integer> counts, int answered) {
	}

	/** One player's own result in a room quiz: their rank and how their last answer went. */
	public record YouView(Integer rank, int score, String lastAnswer, boolean lastCorrect, int lastPoints) {
	}

	/**
	 * What one player sees. The open question never includes its answer.
	 *
	 * @param phase where the match is (fastest-finger matches: QUESTION until FINISHED)
	 * @param answered how many players answered the open question
	 * @param secondsToStart room quiz: until the first question opens
	 * @param secondsToNext room quiz: until the next question opens by itself (0 when the host moves on)
	 * @param you room quiz: this player's own result (null for the screen)
	 */
	public record MatchView(String matchId, int gameId, String gameName, MultiplayerMatch.Status status,
			int totalQuestions, int pointsPerQuestion, int secondsPerQuestion, List<PlayerView> standings,
			QuestionView currentQuestion, RevealView lastQuestion, String version, MultiplayerMatch.Format format,
			Phase phase, int playerCount, int answered, long secondsToStart, long secondsToNext, int revealSeconds,
			YouView you) {

		static MatchView of(MultiplayerMatch m, int userId, Instant now) {
			Phase phase = m.phase(now);
			// during the lead-in the first question is still a secret
			QuestionView current = m.currentQuestion().filter(q -> phase != Phase.GET_READY)
					.map(q -> new QuestionView(q.getQuestionId(), m.getCurrentIndex() + 1, q.getStem(), q.getOptions(),
							m.secondsRemaining(now), yourAnswer(q, userId)))
					.orElse(null);
			RevealView last = m.lastClosedQuestion().map(q -> reveal(m, q)).orElse(null);
			List<PlayerScore> ordered = m.standings();
			List<PlayerView> standings = ordered.stream()
					.map(p -> new PlayerView(p.getUserId(), p.getScore(), p.getQuestionsWon(), p.getName(), p.getCountry(),
							p.isBot()))
					.toList();
			int answered = m.currentQuestion().map(q -> q.getAnswers().size()).orElse(0);
			YouView you = m.isRoomQuiz() ? you(m, ordered, userId) : null;
			String version;
			if (m.isRoomQuiz() && !m.isScreen(userId)) {
				// a phone only needs news when the phase changes or its own answer lands, not at everyone else's
				version = Integer.toHexString(java.util.Objects.hash(m.getStatus(), phase, m.getCurrentIndex(),
						current == null ? null : current.yourAnswer(), phase == Phase.QUESTION ? null : you));
			} else {
				// what this player would see, without the ticking clock: equal versions mean nothing new to show
				version = Integer.toHexString(java.util.Objects.hash(m.getStatus(), m.getCurrentIndex(), standings,
						current == null ? null : current.yourAnswer(), answered, last, phase));
			}
			return new MatchView(m.getMatchId(), m.getGameId(), m.getGameName(), m.getStatus(), m.getQuestions().size(),
					m.getPointsPerQuestion(), m.getSecondsPerQuestion(), standings, current, last, version, m.getFormat(),
					phase, m.getPlayers().size(), answered, m.secondsToStart(now), m.secondsToNext(now),
					m.getRevealSeconds(), you);
		}

		private static RevealView reveal(MultiplayerMatch m, MatchQuestion q) {
			List<Integer> counts = null;
			if (m.isRoomQuiz()) {
				counts = q.getOptions().stream().map(o -> (int) q.getAnswers().stream()
						.filter(a -> a.answer() != null && a.answer().trim().equalsIgnoreCase(o)).count()).toList();
			}
			return new RevealView(q.getQuestionId(), q.getStem(), q.getCorrectAnswer(), q.getWinnerUserId(),
					m.getCurrentIndex(), m.isRoomQuiz() ? q.getOptions() : null, counts, q.getAnswers().size());
		}

		private static YouView you(MultiplayerMatch m, List<PlayerScore> ordered, int userId) {
			for (int i = 0; i < ordered.size(); i++) {
				PlayerScore p = ordered.get(i);
				if (p.getUserId() != userId) {
					continue;
				}
				// tied scores share a rank
				int rank = 1 + (int) ordered.stream().filter(o -> o.getScore() > p.getScore()).count();
				PlayerAnswer last = m.lastClosedQuestion()
						.flatMap(q -> q.getAnswers().stream().filter(a -> a.userId() == userId).findFirst()).orElse(null);
				int points = last == null ? 0 : m.pointsFor(m.lastClosedQuestion().get(), last);
				return new YouView(rank, p.getScore(), last == null ? null : last.answer(), last != null && last.correct(),
						points);
			}
			return null;
		}

		private static String yourAnswer(MatchQuestion q, int userId) {
			return q.getAnswers().stream().filter(a -> a.userId() == userId).map(a -> a.answer()).findFirst()
					.orElse(null);
		}
	}

	public record AnswerView(boolean correct, boolean wonQuestion, int score, MatchView match) {
	}
}
