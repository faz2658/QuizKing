package com.fazeel.quizcadia.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.match.MultiplayerMatch.HostAction;
import com.fazeel.quizcadia.match.MultiplayerMatch.Phase;
import com.fazeel.quizcadia.match.MultiplayerMatch.Status;

/** The live room quiz: everyone who's right scores (faster scores more) and the host's screen paces it. */
class RoomQuizTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
	/** When the first question opens, after the lead-in. */
	static final Instant OPEN = T0.plus(MultiplayerMatch.LEAD_IN);
	static final int SCREEN = 99;

	private static MultiplayerMatch quiz(int questions, int revealSeconds) {
		List<MatchQuestion> qs = IntStream.rangeClosed(1, questions)
				.mapToObj(i -> new MatchQuestion(i, "Q" + i, List.of("A" + i, "B", "C", "D"), "A" + i)).toList();
		return MultiplayerMatch.roomQuiz("q1", List.of(10, 20, 30), Map.of(10, "Ada", 20, "Bo", 30, "Cy"), qs, 20,
				revealSeconds, SCREEN, T0);
	}

	@Test
	void startsWithALeadInThenOpensTheFirstQuestion() {
		MultiplayerMatch m = quiz(3, 0);

		assertThat(m.phase(T0)).isEqualTo(Phase.GET_READY);
		assertThat(m.secondsToStart(T0)).isEqualTo(5);
		assertThatThrownBy(() -> m.answer(10, 1, "A1", T0.plusSeconds(1))).isInstanceOf(MatchRuleException.class);
		assertThat(m.phase(OPEN)).isEqualTo(Phase.QUESTION);
		assertThat(m.secondsRemaining(OPEN)).isEqualTo(20);
	}

	@Test
	void everyRightAnswerScoresAndFasterScoresMore() {
		MultiplayerMatch m = quiz(3, 0);

		var instant = m.answer(10, 1, "A1", OPEN);
		var halfway = m.answer(20, 1, "a1", OPEN.plusSeconds(10));
		var wrong = m.answer(30, 1, "B", OPEN.plusSeconds(1));

		assertThat(instant.correct()).isTrue();
		assertThat(instant.score()).isEqualTo(1000);
		assertThat(halfway.score()).isEqualTo(750);
		assertThat(wrong.correct()).isFalse();
		assertThat(wrong.score()).isZero(); // no penalty
		assertThat(m.standings()).extracting(PlayerScore::getUserId).containsExactly(10, 20, 30);
		assertThat(m.standings()).extracting(PlayerScore::getQuestionsWon).containsExactly(1, 1, 0);
	}

	@Test
	void anAnswerAtTheBuzzerScoresHalf() {
		MultiplayerMatch m = quiz(1, 0);
		var late = m.answer(10, 1, "A1", OPEN.plusSeconds(20));
		assertThat(late.score()).isEqualTo(500);
	}

	@Test
	void whenEveryoneAnsweredTheAnswerShowsAndWaitsForTheHost() {
		MultiplayerMatch m = quiz(3, 0);
		m.answer(10, 1, "A1", OPEN.plusSeconds(1));
		m.answer(20, 1, "A1", OPEN.plusSeconds(2));
		assertThat(m.phase(OPEN.plusSeconds(2))).isEqualTo(Phase.QUESTION);

		m.answer(30, 1, "B", OPEN.plusSeconds(3));

		assertThat(m.phase(OPEN.plusSeconds(3))).isEqualTo(Phase.REVEAL);
		assertThat(m.currentQuestion()).isEmpty();
		assertThat(m.lastClosedQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(1);
		// the answer stays up as long as the host likes
		assertThat(m.advanceClock(OPEN.plusSeconds(600))).isFalse();
		assertThat(m.phase(OPEN.plusSeconds(600))).isEqualTo(Phase.REVEAL);

		assertThat(m.host(SCREEN, HostAction.NEXT, OPEN.plusSeconds(601))).isTrue();

		assertThat(m.phase(OPEN.plusSeconds(601))).isEqualTo(Phase.QUESTION);
		assertThat(m.currentQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(2);
		assertThat(m.secondsRemaining(OPEN.plusSeconds(601))).isEqualTo(20);
	}

	@Test
	void timeRunningOutRevealsTheAnswerEvenIfSomeDidNotAnswer() {
		MultiplayerMatch m = quiz(2, 0);
		m.answer(10, 1, "A1", OPEN.plusSeconds(1));

		Instant after = OPEN.plusSeconds(20).plus(MultiplayerMatch.GRACE).plusMillis(1);
		assertThat(m.advanceClock(after)).isTrue();

		assertThat(m.phase(after)).isEqualTo(Phase.REVEAL);
		assertThat(m.lastClosedQuestion().get().getAnswers()).hasSize(1);
	}

	@Test
	void autoAdvanceOpensTheNextQuestionAfterTheReveal() {
		MultiplayerMatch m = quiz(2, 8);
		m.host(SCREEN, HostAction.NEXT, T0); // skip the lead-in
		m.host(SCREEN, HostAction.REVEAL, T0.plusSeconds(3));
		assertThat(m.secondsToNext(T0.plusSeconds(4))).isEqualTo(7);
		assertThat(m.nextChangeAt(T0.plusSeconds(4))).isEqualTo(T0.plusSeconds(11));

		m.advanceClock(T0.plusSeconds(11));

		assertThat(m.phase(T0.plusSeconds(11))).isEqualTo(Phase.QUESTION);
		assertThat(m.currentQuestion()).get().extracting(MatchQuestion::getQuestionId).isEqualTo(2);
	}

	@Test
	void nextAfterTheLastAnswerShowsTheFinalResults() {
		MultiplayerMatch m = quiz(1, 0);
		m.answer(10, 1, "A1", OPEN);
		m.host(SCREEN, HostAction.REVEAL, OPEN.plusSeconds(1));
		assertThat(m.getStatus()).isEqualTo(Status.IN_PROGRESS);

		m.host(SCREEN, HostAction.NEXT, OPEN.plusSeconds(2));

		assertThat(m.getStatus()).isEqualTo(Status.COMPLETED);
		assertThat(m.phase(OPEN.plusSeconds(2))).isEqualTo(Phase.FINISHED);
	}

	@Test
	void theHostCanEndTheQuizEarly() {
		MultiplayerMatch m = quiz(5, 0);
		m.answer(10, 1, "A1", OPEN.plusSeconds(1));

		m.host(SCREEN, HostAction.END, OPEN.plusSeconds(2));

		assertThat(m.getStatus()).isEqualTo(Status.COMPLETED);
		assertThat(m.standings().get(0).getScore()).isEqualTo(975);
		assertThatThrownBy(() -> m.answer(20, 2, "A2", OPEN.plusSeconds(3))).isInstanceOf(MatchRuleException.class);
	}

	@Test
	void onlyTheScreenPacesTheQuizAndItCannotAnswer() {
		MultiplayerMatch m = quiz(2, 0);
		assertThatThrownBy(() -> m.host(10, HostAction.NEXT, OPEN)).isInstanceOf(MatchRuleException.class);
		assertThatThrownBy(() -> m.answer(SCREEN, 1, "A1", OPEN)).isInstanceOf(MatchRuleException.class);
	}

	@Test
	void eachPlayerAnswersOnceAndNotAfterTheReveal() {
		MultiplayerMatch m = quiz(2, 0);
		m.answer(10, 1, "A1", OPEN.plusSeconds(1));
		assertThatThrownBy(() -> m.answer(10, 1, "B", OPEN.plusSeconds(2))).isInstanceOf(MatchRuleException.class);

		m.host(SCREEN, HostAction.REVEAL, OPEN.plusSeconds(3));

		assertThatThrownBy(() -> m.answer(20, 1, "A1", OPEN.plusSeconds(4))).isInstanceOf(MatchRuleException.class);
		assertThatThrownBy(() -> m.answer(20, 2, "A2", OPEN.plusSeconds(4))).isInstanceOf(MatchRuleException.class);
	}

	@Test
	void pointsForAnAnswerAreWhatItScored() {
		MultiplayerMatch m = quiz(1, 0);
		m.answer(10, 1, "A1", OPEN.plusSeconds(4));
		m.answer(20, 1, "B", OPEN.plusSeconds(4));
		MatchQuestion q = m.getQuestions().get(0);

		assertThat(m.pointsFor(q, q.getAnswers().get(0))).isEqualTo(900);
		assertThat(m.pointsFor(q, q.getAnswers().get(1))).isZero();
	}

	@Test
	void fastestFingerMatchesCannotBePacedByAHost() {
		MultiplayerMatch race = new MultiplayerMatch("m1", 1, "Race", 2, List.of(10, 20),
				List.of(new MatchQuestion(1, "Q1", List.of("A", "B"), "A")), 10, 10, T0).shownOn(SCREEN);
		assertThatThrownBy(() -> race.host(SCREEN, HostAction.NEXT, T0)).isInstanceOf(MatchRuleException.class);
		assertThat(race.phase(T0)).isEqualTo(Phase.QUESTION);
	}
}
