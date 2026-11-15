package com.stackroute.maverick.api;

import java.time.Instant;
import java.util.List;

import com.stackroute.maverick.match.MatchQuestion;
import com.stackroute.maverick.match.MultiplayerMatch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class Views {

	private Views() {
	}

	public record AnswerRequest(@Positive int userId, @Positive int questionId, @NotNull String answer) {
	}

	public record PlayerView(int userId, int score, int questionsWon) {
	}

	public record QuestionView(int questionId, int number, String stem, List<String> options, long secondsRemaining,
			String yourAnswer) {
	}

	public record RevealView(int questionId, String stem, String correctAnswer, Integer winnerUserId) {
	}

	/** What one player sees. The open question never includes its answer. */
	public record MatchView(String matchId, int gameId, String gameName, MultiplayerMatch.Status status,
			int totalQuestions, int pointsPerQuestion, int secondsPerQuestion, List<PlayerView> standings,
			QuestionView currentQuestion, RevealView lastQuestion) {

		static MatchView of(MultiplayerMatch m, int userId, Instant now) {
			QuestionView current = m.currentQuestion()
					.map(q -> new QuestionView(q.getQuestionId(), m.getCurrentIndex() + 1, q.getStem(), q.getOptions(),
							m.secondsRemaining(now), yourAnswer(q, userId)))
					.orElse(null);
			RevealView last = m.lastClosedQuestion()
					.map(q -> new RevealView(q.getQuestionId(), q.getStem(), q.getCorrectAnswer(), q.getWinnerUserId()))
					.orElse(null);
			List<PlayerView> standings = m.standings().stream()
					.map(p -> new PlayerView(p.getUserId(), p.getScore(), p.getQuestionsWon())).toList();
			return new MatchView(m.getMatchId(), m.getGameId(), m.getGameName(), m.getStatus(), m.getQuestions().size(),
					m.getPointsPerQuestion(), m.getSecondsPerQuestion(), standings, current, last);
		}

		private static String yourAnswer(MatchQuestion q, int userId) {
			return q.getAnswers().stream().filter(a -> a.userId() == userId).map(a -> a.answer()).findFirst()
					.orElse(null);
		}
	}

	public record AnswerView(boolean correct, boolean wonQuestion, int score, MatchView match) {
	}
}
