package com.fazeel.quizcadia.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.match.MultiplayerMatch.AnswerOutcome;
import com.fazeel.quizcadia.match.MultiplayerMatch.Status;

class MultiplayerMatchTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

	static MatchQuestion q(int id, String correct) {
		return new MatchQuestion(id, "Q" + id, List.of(correct, "wrong"), correct);
	}

	private MultiplayerMatch match(int... questionIds) {
		List<MatchQuestion> qs = Arrays.stream(questionIds).mapToObj(i -> q(i, "A" + i)).toList();
		return new MultiplayerMatch("m1", 1, "Race", 2, List.of(10, 20, 30), qs, 10, 10, T0);
	}

	@Test
	void fastestCorrectAnswerWinsThePoints() {
		MultiplayerMatch m = match(1, 2);

		AnswerOutcome slow = m.answer(20, 1, "wrong", T0.plusMillis(500));
		AnswerOutcome fast = m.answer(10, 1, "A1", T0.plusMillis(900));
		AnswerOutcome late = m.answer(30, 1, "a1", T0.plusMillis(1200));

		assertThat(fast.wonQuestion()).isTrue();
		assertThat(fast.score()).isEqualTo(10);
		assertThat(late.correct()).isTrue();
		assertThat(late.wonQuestion()).isFalse();
		assertThat(late.score()).isZero();
		assertThat(slow.correct()).isFalse();
		assertThat(m.lastClosedQuestion()).get().extracting(MatchQuestion::getWinnerUserId).isEqualTo(10);
	}

	@Test
	void questionClosesWhenEveryoneAnswered() {
		MultiplayerMatch m = match(1, 2);
		m.answer(10, 1, "A1", T0.plusSeconds(1));
		m.answer(20, 1, "A1", T0.plusSeconds(1));
		assertThat(m.currentQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(1);

		m.answer(30, 1, "A1", T0.plusSeconds(2));

		assertThat(m.currentQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(2);
		assertThat(m.secondsRemaining(T0.plusSeconds(2))).isEqualTo(10);
	}

	@Test
	void wrongAnswerCostsAPenaltyButScoreNeverGoesNegative() {
		MultiplayerMatch m = match(1, 2, 3);
		m.answer(10, 1, "A1", T0.plusSeconds(1));
		m.answer(20, 1, "x", T0.plusSeconds(1));
		m.answer(30, 1, "x", T0.plusSeconds(1));

		AnswerOutcome o = m.answer(10, 2, "x", T0.plusSeconds(2));

		assertThat(o.score()).isEqualTo(10 - MultiplayerMatch.WRONG_ANSWER_PENALTY);
		assertThat(m.standings()).filteredOn(p -> p.getUserId() == 20).first()
				.extracting(PlayerScore::getScore).isEqualTo(0);
	}

	@Test
	void eachPlayerAnswersAQuestionOnce() {
		MultiplayerMatch m = match(1, 2);
		m.answer(10, 1, "x", T0.plusSeconds(1));

		assertThatThrownBy(() -> m.answer(10, 1, "A1", T0.plusSeconds(2))).isInstanceOf(MatchRuleException.class)
				.hasMessageContaining("already answered");
	}

	@Test
	void outsidersCannotAnswer() {
		MultiplayerMatch m = match(1);

		assertThatThrownBy(() -> m.answer(99, 1, "A1", T0)).isInstanceOf(MatchRuleException.class);
	}

	@Test
	void timeRunsOutAndTheMatchEnds() {
		MultiplayerMatch m = match(1, 2);
		m.answer(10, 1, "A1", T0.plusSeconds(1));

		assertThat(m.advanceClock(T0.plusSeconds(12))).isTrue();
		assertThat(m.currentQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(2);

		m.advanceClock(T0.plusSeconds(60));
		assertThat(m.getStatus()).isEqualTo(Status.COMPLETED);
		assertThat(m.getCompletedAt()).isEqualTo(T0.plusSeconds(22));
		assertThatThrownBy(() -> m.answer(20, 2, "A2", T0.plusSeconds(61))).isInstanceOf(MatchRuleException.class);
	}

	@Test
	void answerForAnAlreadyClosedQuestionIsRejected() {
		MultiplayerMatch m = match(1, 2);

		assertThatThrownBy(() -> m.answer(10, 1, "A1", T0.plusSeconds(15))).isInstanceOf(MatchRuleException.class)
				.hasMessageContaining("no longer open");
	}

	@Test
	void standingsBreakTiesByQuestionsWonThenWrongAnswers() {
		MultiplayerMatch m = match(1, 2, 3);
		m.answer(10, 1, "A1", T0.plusSeconds(1)); // 10 wins q1
		m.answer(20, 1, "x", T0.plusSeconds(1));
		m.answer(30, 1, "x", T0.plusSeconds(1));
		m.answer(20, 2, "A2", T0.plusSeconds(2)); // 20 wins q2
		m.answer(10, 2, "x", T0.plusSeconds(2));
		m.answer(30, 2, "x", T0.plusSeconds(2));

		assertThat(m.standings()).extracting(PlayerScore::getUserId).startsWith(20);
	}

	@Test
	void needsTwoPlayersAndAQuestion() {
		assertThatThrownBy(() -> new MultiplayerMatch("m", 1, "g", 1, List.of(1), List.of(q(1, "a")), 10, 10, T0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MultiplayerMatch("m", 1, "g", 1, List.of(1, 2), List.of(), 10, 10, T0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
