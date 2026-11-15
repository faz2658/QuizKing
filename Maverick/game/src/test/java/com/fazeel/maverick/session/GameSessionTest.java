package com.fazeel.maverick.session;

import static com.fazeel.maverick.session.TestGames.sq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class GameSessionTest {

	private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

	private GameSession session(SessionQuestion... qs) {
		return new GameSession("s1", 1, 42, 7, "Game", List.of(qs), T0);
	}

	@Test
	void correctAnswerEarnsTheQuestionsPointsAndWrongAnswerEarnsNothing() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.ADVANCED, "B", 20, 30));

		AnswerOutcome first = s.answer(1, "A", T0.plusSeconds(3));
		AnswerOutcome second = s.answer(2, "wrong-2", T0.plusSeconds(5));

		assertThat(first.correct()).isTrue();
		assertThat(first.pointsAwarded()).isEqualTo(10);
		assertThat(second.correct()).isFalse();
		assertThat(second.correctAnswer()).isEqualTo("B");
		assertThat(s.getScore()).isEqualTo(10);
		assertThat(s.maxScore()).isEqualTo(40);
		assertThat(s.percentage()).isEqualTo(25);
		assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
	}

	@Test
	void answersAreComparedIgnoringCaseAndSurroundingSpaces() {
		GameSession s = session(sq(1, Difficulty.EASY, "Canberra", 10, 10));

		assertThat(s.answer(1, "  canberra ", T0.plusSeconds(1)).correct()).isTrue();
	}

	@Test
	void singleQuestionGameCanBePlayedToTheEnd() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10));

		s.answer(1, "A", T0.plusSeconds(2));

		assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
		assertThat(s.currentQuestion()).isEmpty();
		assertThat(s.percentage()).isEqualTo(100);
	}

	@Test
	void answerWithinTheGracePeriodStillCounts() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10));

		AnswerOutcome o = s.answer(1, "A", T0.plusSeconds(11));

		assertThat(o.correct()).isTrue();
		assertThat(o.timedOut()).isFalse();
	}

	@Test
	void questionExpiresAfterItsTimeLimitAndTheNextOneStartsAtTheDeadline() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.MEDIUM, "B", 15, 20));

		List<AnswerOutcome> expired = s.expireOverdueQuestions(T0.plusSeconds(13));

		assertThat(expired).singleElement().satisfies(o -> {
			assertThat(o.timedOut()).isTrue();
			assertThat(o.pointsAwarded()).isZero();
		});
		assertThat(s.currentQuestion()).get().extracting(SessionQuestion::getQuestionId).isEqualTo(2);
		assertThat(s.getQuestions().get(1).getAskedAt()).isEqualTo(T0.plusSeconds(12));
		assertThat(s.secondsRemaining(T0.plusSeconds(13))).isEqualTo(14);
	}

	@Test
	void lateAnswerIsRejectedBecauseTheQuestionAlreadyMovedOn() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.EASY, "B", 10, 10));

		assertThatThrownBy(() -> s.answer(1, "A", T0.plusSeconds(15))).isInstanceOf(QuizRuleException.class)
				.hasMessageContaining("not the current question");
		assertThat(s.getScore()).isZero();
	}

	@Test
	void walkingAwayExpiresEveryRemainingQuestionAndEndsTheGame() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.EASY, "B", 10, 10));

		assertThat(s.expireOverdueQuestions(T0.plusSeconds(3600))).hasSize(2);
		assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
		assertThat(s.getCompletedAt()).isEqualTo(T0.plusSeconds(24));
		assertThat(s.getScore()).isZero();
	}

	@Test
	void questionCannotBeAnsweredTwice() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.EASY, "B", 10, 10));
		s.answer(1, "A", T0.plusSeconds(1));

		assertThatThrownBy(() -> s.answer(1, "A", T0.plusSeconds(2))).isInstanceOf(QuizRuleException.class);
		assertThat(s.getScore()).isEqualTo(10);
	}

	@Test
	void noAnswersAreAcceptedAfterTheGameIsOver() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10));
		s.answer(1, "A", T0.plusSeconds(1));

		assertThatThrownBy(() -> s.answer(1, "A", T0.plusSeconds(2))).isInstanceOf(QuizRuleException.class)
				.hasMessageContaining("over");
	}

	@Test
	void nullAnswerIsSimplyWrong() {
		GameSession s = session(sq(1, Difficulty.EASY, "A", 10, 10));

		assertThat(s.answer(1, null, T0.plusSeconds(1)).correct()).isFalse();
	}

	@Test
	void sessionNeedsQuestions() {
		assertThatThrownBy(() -> new GameSession("s", 1, 1, 1, "g", List.of(), T0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
