package com.fazeel.quizking.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.adaptive.AdaptiveSession.Status;

class AdaptiveSessionTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
	static final AdaptiveRules RULES = new AdaptiveRules(4, 60, 30, 12, 20, 10);

	static List<PooledQuestion> pool(int perLevel) {
		List<PooledQuestion> p = new ArrayList<>();
		int id = 1;
		for (int level = 1; level <= 3; level++) {
			for (int i = 0; i < perLevel; i++, id++) {
				p.add(new PooledQuestion(id, level, "Q" + id, List.of("right", "wrong"), "right"));
			}
		}
		return p;
	}

	static AdaptiveSession session(List<PooledQuestion> pool, AdaptiveRules rules) {
		return new AdaptiveSession("s", 1, 2, "Game", 3, 4, pool, rules, T0);
	}

	static void answerCurrent(AdaptiveSession s, boolean correctly, int second) {
		AskedQuestion q = s.currentQuestion().orElseThrow();
		s.answer(q.getQuestionId(), correctly ? "right" : "wrong", T0.plusSeconds(second));
	}

	@Test
	void startsOnEasyQuestions() {
		AdaptiveSession s = session(pool(10), RULES);

		assertThat(s.getLevel()).isEqualTo(1);
		assertThat(s.currentQuestion()).get().extracting(AskedQuestion::getLevel).isEqualTo(1);
	}

	@Test
	void movesUpAfterAGoodBlock() {
		AdaptiveSession s = session(pool(10), RULES);
		answerCurrent(s, true, 1);
		answerCurrent(s, true, 2);
		answerCurrent(s, false, 3);
		answerCurrent(s, false, 4); // 50% -> stays

		assertThat(s.getLevel()).isEqualTo(1);

		answerCurrent(s, true, 5);
		answerCurrent(s, true, 6);
		answerCurrent(s, true, 7);
		answerCurrent(s, false, 8); // 75% -> up

		assertThat(s.getLevel()).isEqualTo(2);
		assertThat(s.currentQuestion()).get().extracting(AskedQuestion::getLevel).isEqualTo(2);
	}

	@Test
	void sixtyPercentIsEnoughToMoveUp() {
		// The old engine computed (score*100)/size with ints; this checks the threshold itself.
		AdaptiveSession s = session(pool(10), new AdaptiveRules(5, 60, 30, 12, 20, 10));
		answerCurrent(s, true, 1);
		answerCurrent(s, true, 2);
		answerCurrent(s, true, 3);
		answerCurrent(s, false, 4);
		answerCurrent(s, false, 5);

		assertThat(s.getLevel()).isEqualTo(2);
	}

	@Test
	void movesDownAfterABadBlockButNeverBelowEasy() {
		AdaptiveSession s = session(pool(10), RULES);
		for (int i = 0; i < 4; i++) {
			answerCurrent(s, true, i);
		}
		assertThat(s.getLevel()).isEqualTo(2);
		for (int i = 0; i < 4; i++) {
			answerCurrent(s, false, 10 + i);
		}
		assertThat(s.getLevel()).isEqualTo(1);
		for (int i = 0; i < 4; i++) {
			answerCurrent(s, false, 20 + i);
		}
		assertThat(s.getLevel()).isEqualTo(1);
		assertThat(s.getHighestLevel()).isEqualTo(2);
	}

	@Test
	void neverGoesAboveAdvanced() {
		AdaptiveSession s = session(pool(10), new AdaptiveRules(1, 60, 30, 10, 20, 10));
		for (int i = 0; i < 5; i++) {
			answerCurrent(s, true, i);
		}
		assertThat(s.getLevel()).isEqualTo(3);
	}

	@Test
	void harderQuestionsAreWorthMore() {
		AdaptiveSession s = session(pool(10), new AdaptiveRules(1, 60, 30, 10, 20, 10));
		answerCurrent(s, true, 1); // level 1: 10
		answerCurrent(s, true, 2); // level 2: 20
		answerCurrent(s, true, 3); // level 3: 30

		assertThat(s.getScore()).isEqualTo(60);
	}

	@Test
	void fallsBackToTheNearestLevelWhenALevelRunsOut() {
		List<PooledQuestion> p = new ArrayList<>(pool(1));
		AdaptiveSession s = session(p, new AdaptiveRules(1, 60, 30, 10, 20, 10));
		answerCurrent(s, false, 1); // level 1 exhausted, stays at level 1

		assertThat(s.currentQuestion()).get().extracting(AskedQuestion::getLevel).isEqualTo(2);
	}

	@Test
	void endsAfterTheConfiguredNumberOfQuestionsOrWhenThePoolIsEmpty() {
		AdaptiveSession s = session(pool(10), new AdaptiveRules(4, 60, 30, 3, 20, 10));
		for (int i = 0; i < 3; i++) {
			answerCurrent(s, true, i);
		}
		assertThat(s.getStatus()).isEqualTo(Status.COMPLETED);

		AdaptiveSession small = session(pool(1).subList(0, 2), RULES);
		answerCurrent(small, true, 1);
		answerCurrent(small, true, 2);
		assertThat(small.getStatus()).isEqualTo(Status.COMPLETED);
		assertThat(small.getAsked()).hasSize(2);
	}

	@Test
	void timeoutCountsAsAWrongAnswer() {
		AdaptiveSession s = session(pool(10), new AdaptiveRules(1, 60, 30, 10, 20, 10));
		answerCurrent(s, true, 1); // up to level 2

		assertThat(s.expireOverdue(T0.plusSeconds(1 + 20 + 3))).isTrue();

		assertThat(s.getAsked().get(1).isTimedOut()).isTrue();
		assertThat(s.getLevel()).isEqualTo(1);
	}

	@Test
	void onlyTheCurrentQuestionCanBeAnswered() {
		AdaptiveSession s = session(pool(10), RULES);
		int current = s.currentQuestion().orElseThrow().getQuestionId();

		assertThatThrownBy(() -> s.answer(current + 1, "right", T0)).isInstanceOf(AdaptiveRuleException.class);
	}

	@Test
	void emptyPoolCannotStart() {
		assertThatThrownBy(() -> session(List.of(), RULES)).isInstanceOf(AdaptiveRuleException.class);
	}
}
