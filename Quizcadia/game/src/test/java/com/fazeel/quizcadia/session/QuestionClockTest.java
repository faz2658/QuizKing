package com.fazeel.quizcadia.session;

import static com.fazeel.quizcadia.session.TestGames.sq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** A question's clock starts when the player's screen shows it, not while the previous result is still showing. */
class QuestionClockTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

	/** Two 10-second questions; the first is answered at T0 + 3 s, which asks the second. */
	static GameSession afterFirstAnswer() {
		GameSession s = new GameSession("s1", 1, 42, 7, "Game", List.of(sq(1, Difficulty.EASY, "A", 10, 10), sq(2, Difficulty.EASY, "B", 10, 10)), T0);
		s.answer(1, "A", T0.plusSeconds(3));
		return s;
	}

	@Test
	void theBugAnAnswerNearTheEndOfTheOnScreenTimerWasRefused() {
		// the app shows the result for 4 s (wrong answer with an explanation), then the question with 10 s on its timer
		GameSession s = afterFirstAnswer();

		// at 9.5 s on screen = 16.5 s since the answer: past the server's 10 s + 2 s grace, so "too late"
		assertThatThrownBy(() -> s.answer(2, "B", T0.plusSeconds(3).plusMillis(16_500))).isInstanceOf(QuizRuleException.class);
	}

	@Test
	void whenTheScreenReportsTheQuestionTheClockStartsThere() {
		GameSession s = afterFirstAnswer();

		assertThat(s.shown(2, T0.plusSeconds(7))).isTrue(); // shown 4 s after the answer

		assertThat(s.secondsRemaining(T0.plusSeconds(7))).isEqualTo(10);
		AnswerOutcome o = s.answer(2, "B", T0.plusSeconds(7).plusMillis(9_500)); // 9.5 s into its own 10 s
		assertThat(o.correct()).isTrue();
		assertThat(o.timedOut()).isFalse();
	}

	@Test
	void theClockCanMoveAtMostFiveSecondsAndOnlyOnce() {
		GameSession s = afterFirstAnswer();

		assertThat(s.shown(2, T0.plusSeconds(60))).isTrue(); // a minute later: capped at answer + 5 s
		assertThat(s.secondsRemaining(T0.plusSeconds(8))).isEqualTo(10);
		assertThat(s.shown(2, T0.plusSeconds(61))).isFalse(); // once only
		assertThat(s.secondsRemaining(T0.plusSeconds(13))).isEqualTo(5);
	}

	@Test
	void onlyForTheOpenUnansweredCurrentQuestion() {
		GameSession s = afterFirstAnswer();

		assertThat(s.shown(1, T0.plusSeconds(4))).isFalse(); // not the current question
		assertThat(s.shown(99, T0.plusSeconds(4))).isFalse();
		s.answer(2, "B", T0.plusSeconds(5));
		assertThat(s.shown(2, T0.plusSeconds(6))).isFalse(); // answered (and the game is over)
	}

	@Test
	void theFirstQuestionCanBeShownLateToo() {
		GameSession s = new GameSession("s1", 1, 42, 7, "Game", List.of(sq(1, Difficulty.EASY, "A", 10, 10)), T0);

		assertThat(s.shown(1, T0.plusSeconds(2))).isTrue(); // the page took 2 s to load
		assertThat(s.secondsRemaining(T0.plusSeconds(2))).isEqualTo(10);
	}
}
