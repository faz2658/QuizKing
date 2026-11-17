package com.fazeel.quizcadia.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

class MatchFactoryTest {

	static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
	static final Match MATCH = new Match("m", 7, List.of(1, 2), NOW);

	static MultiPlayerGame game(int perLevel, int count, MultiPlayerGame.Level mix) {
		List<MultiPlayerGame.Question> qs = new ArrayList<>();
		int id = 1;
		for (int level = 1; level <= 3; level++) {
			for (int i = 0; i < perLevel; i++, id++) {
				qs.add(new MultiPlayerGame.Question(id, level, "Q" + id, "a", "b", "c", null, "a"));
			}
		}
		return new MultiPlayerGame(7, "Race", 1, 15, 10, qs, count, mix);
	}

	@Test
	void asksTheGamesMixFromABigPoolEasyFirst() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(MATCH, game(100, 0,
				new MultiPlayerGame.Level(3, 3, 2)), NOW);

		assertThat(m.getQuestions()).hasSize(8);
		List<Integer> levels = m.getQuestions().stream().map(q -> (q.getQuestionId() - 1) / 100 + 1).toList();
		assertThat(levels).containsExactly(1, 1, 1, 2, 2, 2, 3, 3);
	}

	@Test
	void withoutAMixUsesTheQuestionCountOrTen() {
		assertThat(new MatchFactory(new Random(1)).create(MATCH, game(100, 6, null), NOW).getQuestions()).hasSize(6);
		assertThat(new MatchFactory(new Random(1)).create(MATCH, game(100, 0, null), NOW).getQuestions())
				.hasSize(MatchFactory.DEFAULT_QUESTIONS);
	}

	@Test
	void prefersQuestionsNoPlayerHasSeenEvenFromAnotherLevel() {
		// level 1 has ids 1..4: 1 and 2 seen by both players, 3 by one, 4 by nobody
		Map<Integer, Integer> seenBy = Map.of(1, 2, 2, 2, 3, 1);

		MultiplayerMatch m = new MatchFactory(new Random(3)).create(MATCH, game(4, 0,
				new MultiPlayerGame.Level(2, 0, 0)), NOW, seenBy);

		// only question 4 is new to both players at this level; the second slot borrows a question nobody
		// has seen from another level rather than repeating 1, 2 or 3
		assertThat(m.getQuestions()).extracting(MatchQuestion::getQuestionId).hasSize(2).contains(4)
				.doesNotContain(1, 2, 3);
	}

	@Test
	void aShortLevelIsFilledFromTheOthers() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(MATCH, game(2, 0,
				new MultiPlayerGame.Level(5, 0, 0)), NOW);

		assertThat(m.getQuestions()).hasSize(5);
	}

	// --- country mix ---

	/** 30 questions each (10 per level) for India, general and Kenya: ids 100+, 200+ and 300+. */
	static MultiPlayerGame countryGame(MultiPlayerGame.Level mix) {
		List<MultiPlayerGame.Question> qs = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			int level = i % 3 + 1;
			qs.add(new MultiPlayerGame.Question(100 + i, level, "IN" + i, "a", "b", "c", null, "a", "IN"));
			qs.add(new MultiPlayerGame.Question(200 + i, level, "G" + i, "a", "b", "c", null, "a", null));
			qs.add(new MultiPlayerGame.Question(300 + i, level, "KE" + i, "a", "b", "c", null, "a", "KE"));
		}
		return new MultiPlayerGame(7, "History", 1, 15, 10, qs, 0, mix);
	}

	static Match match(List<Integer> players, Map<Integer, String> countries) {
		return new Match("m", 7, players, NOW, Map.of(), countries);
	}

	static Map<String, Long> origin(MultiplayerMatch m) {
		Map<String, Long> by = new java.util.HashMap<>();
		m.getQuestions().forEach(q -> by.merge(q.getQuestionId() < 200 ? "IN" : q.getQuestionId() < 300 ? "general" : "KE",
				1L, Long::sum));
		return by;
	}

	@Test
	void playersFromOneCountryGetThreeQuartersOfTheirCountrysQuestions() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, 2), Map.of(1, "IN", 2, "in")),
				countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW);

		assertThat(m.getQuestions()).hasSize(12);
		assertThat(origin(m)).containsEntry("IN", 9L).containsEntry("general", 3L).doesNotContainKey("KE");
		List<Integer> levels = m.getQuestions().stream().map(q -> q.getQuestionId() % 100 % 3 + 1).toList();
		assertThat(levels).isSorted();
	}

	@Test
	void playersFromDifferentCountriesGetGeneralQuestionsOnly() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, 2), Map.of(1, "IN", 2, "KE")),
				countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW);

		assertThat(origin(m)).containsEntry("general", 12L).hasSize(1);
	}

	@Test
	void aPlayerWithAnUnknownCountryAmongKnownOnesCountsAsMixed() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, 2), Map.of(1, "IN")),
				countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW);

		assertThat(origin(m)).containsEntry("general", 12L).hasSize(1);
	}

	@Test
	void botsDoNotChangeTheMix() {
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, -5), Map.of(1, "IN", -5, "KE")),
				countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW);

		assertThat(origin(m)).containsEntry("IN", 9L).containsEntry("general", 3L);
	}

	@Test
	void withNoCountriesKnownEveryQuestionIsEligibleAsBefore() {
		Map<String, Long> seen = new java.util.HashMap<>();
		for (long seed = 0; seed < 20; seed++) {
			origin(new MatchFactory(new Random(seed)).create(MATCH, countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW))
					.forEach((k, v) -> seen.merge(k, v, Long::sum));
		}

		assertThat(seen).containsKeys("IN", "general", "KE");
	}

	@Test
	void sameCountryPlayersGetGeneralQuestionsBeforeRepeatingTheirOwn() {
		Map<Integer, Integer> seenBy = new java.util.HashMap<>();
		for (int i = 0; i < 30; i++) {
			seenBy.put(100 + i, 2);
		}
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, 2), Map.of(1, "IN", 2, "IN")),
				countryGame(new MultiPlayerGame.Level(4, 4, 4)), NOW, seenBy);

		assertThat(origin(m)).containsEntry("general", 12L).hasSize(1);
	}

	@Test
	void aMixedMatchOnAGameWithoutGeneralQuestionsStillPlays() {
		List<MultiPlayerGame.Question> kenya = List.of(
				new MultiPlayerGame.Question(1, 1, "K1", "a", "b", null, null, "a", "KE"),
				new MultiPlayerGame.Question(2, 1, "K2", "a", "b", null, null, "a", "KE"));
		MultiplayerMatch m = new MatchFactory(new Random(1)).create(match(List.of(1, 2), Map.of(1, "IN", 2, "US")),
				new MultiPlayerGame(7, "Kenya", 1, 15, 10, kenya, 2, null), NOW);

		assertThat(m.getQuestions()).hasSize(2);
	}

	@Test
	void readsTheCountryOfEachQuestionFromTheGameDefinition() {
		MultiPlayerGame.Question q = tools.jackson.databind.json.JsonMapper.builder().build().readValue(
				"{\"questionId\":5,\"questionLevel\":1,\"questionStem\":\"Q\",\"option1\":\"a\",\"option2\":\"b\","
						+ "\"correctAnswer\":\"a\",\"country\":\"IN\",\"explanation\":\"x\"}",
				MultiPlayerGame.Question.class);

		assertThat(q.country()).isEqualTo("IN");
		assertThat(q.questionId()).isEqualTo(5);
	}
}
