package com.fazeel.quizcadia.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.MultiPlayerResult;

class BotPlayerTest {

	static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");

	static MultiplayerMatch withBot(int questions) {
		List<MatchQuestion> qs = new ArrayList<>();
		for (int i = 1; i <= questions; i++) {
			qs.add(new MatchQuestion(i, "Q" + i, List.of("right", "w1", "w2", "w3"), "right"));
		}
		return new MultiplayerMatch("m1", 7, "Race", 1, List.of(5, -2), qs, 10, 10, T0,
				Map.of(5, "Ada", -2, "🤖 Turing"), Map.of(5, "CA", -2, "JP"));
	}

	@Test
	void theBotAnswersEveryQuestionWithinTheTimeLimit() {
		MultiplayerMatch m = withBot(20);
		m.advanceClock(T0.plusSeconds(60 * 60)); // long after: every question was played out

		assertThat(m.getStatus()).isEqualTo(MultiplayerMatch.Status.COMPLETED);
		for (MatchQuestion q : m.getQuestions()) {
			var botAnswer = q.answerOf(-2).orElseThrow();
			long ms = java.time.Duration.between(q.getOpenedAt(), botAnswer.answeredAt()).toMillis();
			assertThat(ms).isBetween(2000L, 8000L);
		}
		long right = m.getQuestions().stream().filter(q -> q.answerOf(-2).orElseThrow().correct()).count();
		assertThat(right).isBetween(5L, 16L); // about 55% of 20
	}

	@Test
	void aFastHumanStillBeatsTheBot() {
		MultiplayerMatch m = withBot(1);
		var outcome = m.answer(5, 1, "right", T0.plusMillis(500));
		assertThat(outcome.wonQuestion()).isTrue();
		m.advanceClock(T0.plusSeconds(30));
		assertThat(m.getQuestions().get(0).getWinnerUserId()).isEqualTo(5);
	}

	@Test
	void namesAndFlagsAreKeptAndBotsGetNoReports() {
		MultiplayerMatch m = withBot(2);
		assertThat(m.getPlayers()).extracting(PlayerScore::getName).containsExactly("Ada", "🤖 Turing");
		assertThat(m.getPlayers()).extracting(PlayerScore::getCountry).containsExactly("CA", "JP");
		m.advanceClock(T0.plusSeconds(3600));

		MultiPlayerResult result = MatchService.toResult(m);
		assertThat(result.standings()).extracting(MultiPlayerResult.Standing::userId).containsExactly(5);
	}
}
