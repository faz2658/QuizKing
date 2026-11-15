package com.fazeel.maverick.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ComboAndLifelineTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

	static SessionQuestion four(int id) {
		return new SessionQuestion(id, Difficulty.EASY, "Q" + id, List.of("right", "w1", "w2", "w3"), "right", 10, 10);
	}

	static GameSession session(int questions) {
		List<SessionQuestion> qs = new ArrayList<>();
		for (int i = 1; i <= questions; i++) {
			qs.add(four(i));
		}
		return new GameSession("s", 1, 7, 1, "G", qs, T0);
	}

	@Test
	void comboMultipliesPointsFromTheThirdCorrectAnswerInARow() {
		GameSession s = session(6);
		int[] points = new int[6];
		for (int i = 1; i <= 6; i++) {
			points[i - 1] = s.answer(i, "right", T0.plusSeconds(i)).pointsAwarded();
		}
		assertThat(points).containsExactly(10, 10, 15, 15, 20, 20);
		assertThat(s.getScore()).isEqualTo(90);
		assertThat(s.maxScore()).isEqualTo(90);
		assertThat(s.percentage()).isEqualTo(100);
		assertThat(s.getBestCombo()).isEqualTo(6);
	}

	@Test
	void aWrongAnswerOrTimeoutBreaksTheCombo() {
		GameSession s = session(5);
		s.answer(1, "right", T0.plusSeconds(1));
		s.answer(2, "right", T0.plusSeconds(2));
		AnswerOutcome wrong = s.answer(3, "w1", T0.plusSeconds(3));
		assertThat(wrong.combo()).isZero();
		assertThat(s.answer(4, "right", T0.plusSeconds(4)).pointsAwarded()).isEqualTo(10); // back to x1

		s.expireOverdueQuestions(T0.plusSeconds(60)); // question 5 times out
		assertThat(s.getCombo()).isZero();
	}

	@Test
	void fiftyFiftyRemovesTwoWrongOptionsOnce() {
		GameSession s = session(3);
		s.useLifeline(Lifeline.FIFTY_FIFTY, 1, T0.plusSeconds(1), new Random(1));

		SessionQuestion q = s.getQuestions().get(0);
		assertThat(q.getRemovedOptions()).hasSize(2).doesNotContain("right");
		assertThatThrownBy(() -> s.useLifeline(Lifeline.FIFTY_FIFTY, 1, T0.plusSeconds(2), new Random(1)))
				.isInstanceOf(QuizRuleException.class).hasMessageContaining("already used");
	}

	@Test
	void fiftyFiftyNeedsFourOptions() {
		SessionQuestion trueFalse = new SessionQuestion(1, Difficulty.EASY, "Q", List.of("True", "False"), "True", 10, 10);
		GameSession s = new GameSession("s", 1, 7, 1, "G", List.of(trueFalse), T0);
		assertThatThrownBy(() -> s.useLifeline(Lifeline.FIFTY_FIFTY, 1, T0, new Random(1)))
				.isInstanceOf(QuizRuleException.class).hasMessageContaining("four options");
		assertThat(s.getUsedLifelines()).isEmpty(); // a refused lifeline isn't used up
	}

	@Test
	void extraTimeMovesTheDeadline() {
		GameSession s = session(2);
		s.useLifeline(Lifeline.EXTRA_TIME, 1, T0.plusSeconds(5), new Random(1));

		assertThat(s.secondsRemaining(T0.plusSeconds(5))).isEqualTo(15);
		assertThat(s.expireOverdueQuestions(T0.plusSeconds(13))).isEmpty(); // would have expired at 12s
	}

	@Test
	void skipMovesOnWithoutPointsAndKeepsTheCombo() {
		GameSession s = session(4);
		s.answer(1, "right", T0.plusSeconds(1));
		s.answer(2, "right", T0.plusSeconds(2));
		Optional<AnswerOutcome> skipped = s.useLifeline(Lifeline.SKIP, 3, T0.plusSeconds(3), new Random(1));

		assertThat(skipped).hasValueSatisfying(o -> {
			assertThat(o.skipped()).isTrue();
			assertThat(o.pointsAwarded()).isZero();
			assertThat(o.correctAnswer()).isNull();
		});
		assertThat(s.currentQuestion()).hasValueSatisfying(q -> assertThat(q.getQuestionId()).isEqualTo(4));
		assertThat(s.answer(4, "right", T0.plusSeconds(4)).pointsAwarded()).isEqualTo(15); // 3rd in a row
	}

	@Test
	void lifelinesOnlyWorkOnTheCurrentQuestionOfAGameInProgress() {
		GameSession s = session(1);
		assertThatThrownBy(() -> s.useLifeline(Lifeline.SKIP, 99, T0, new Random(1))).isInstanceOf(QuizRuleException.class);
		s.answer(1, "right", T0.plusSeconds(1));
		assertThatThrownBy(() -> s.useLifeline(Lifeline.SKIP, 1, T0.plusSeconds(2), new Random(1)))
				.hasMessageContaining("over");
	}

	@Test
	void theExplanationIsRevealedWithTheAnswerButNotOnASkip() {
		SessionQuestion explained = four(1);
		explained.setExplanation("Because.");
		SessionQuestion another = four(2);
		another.setExplanation("Also because.");
		GameSession s = new GameSession("s", 1, 7, 1, "G", List.of(explained, another), T0);

		assertThat(s.answer(1, "w1", T0.plusSeconds(1)).explanation()).isEqualTo("Because.");
		assertThat(s.useLifeline(Lifeline.SKIP, 2, T0.plusSeconds(2), new Random(1)).orElseThrow().explanation()).isNull();
		assertThat(explained.fresh().getExplanation()).isEqualTo("Because.");
	}
}
