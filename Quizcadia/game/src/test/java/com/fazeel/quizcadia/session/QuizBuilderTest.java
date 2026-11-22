package com.fazeel.quizcadia.session;

import static com.fazeel.quizcadia.session.TestGames.game;
import static com.fazeel.quizcadia.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.GameQuestionScore;
import com.fazeel.quizcadia.domain.GameQuestionTime;
import com.fazeel.quizcadia.domain.Questions;

class QuizBuilderTest {

	private final QuizBuilder builder = new QuizBuilder(new Random(7));

	private static Questions ok(int id, int level) {
		return question(id, level, "right", "right", "wrong", "other", "nope");
	}

	@Test
	void honoursTheGamesLevelMixAndAsksEasyQuestionsFirst() {
		Game g = game(1, new GameQuestionLevel(0, 1, 1, 1), ok(1, 3), ok(2, 3), ok(3, 2), ok(4, 2), ok(5, 1),
				ok(6, 1));

		List<SessionQuestion> qs = builder.build(g);

		assertThat(qs).extracting(SessionQuestion::getDifficulty).containsExactly(Difficulty.EASY, Difficulty.MEDIUM,
				Difficulty.ADVANCED);
	}

	@Test
	void usesEveryValidQuestionWhenTheGameDefinesNoMix() {
		Game g = game(1, null, ok(1, 1), ok(2, 2), ok(3, 3));

		assertThat(builder.build(g)).hasSize(3);
	}

	@Test
	void asksForFewerQuestionsWhenNotEnoughExist() {
		Game g = game(1, new GameQuestionLevel(0, 5, 0, 0), ok(1, 1), ok(2, 1));

		assertThat(builder.build(g)).hasSize(2);
	}

	@Test
	void dropsMalformedQuestions() {
		Game g = game(1, null, ok(1, 1), question(2, 1, "missing", "a", "b"), // answer not among options
				question(3, 1, "a", "a"), // only one option
				question(4, 1, "", "a", "b"), // no answer
				null);

		assertThat(builder.build(g)).extracting(SessionQuestion::getQuestionId).containsExactly(1);
	}

	@Test
	void trueFalseQuestionsKeepTheirNaturalOrderAndBlankOptionsAreNotShown() {
		Game g = game(1, null, question(1, 1, "False", "False", "true", "", null));

		for (int seed = 0; seed < 5; seed++) {
			assertThat(new QuizBuilder(new Random(seed)).build(g).get(0).getOptions()).containsExactly("True", "False");
		}
	}

	@Test
	void shuffledOptionsStillContainTheCorrectAnswer() {
		Game g = game(1, null, ok(1, 1));

		SessionQuestion q = builder.build(g).get(0);

		assertThat(q.getOptions()).containsExactlyInAnyOrder("right", "wrong", "other", "nope");
		assertThat(q.getCorrectAnswer()).isEqualTo("right");
	}

	@Test
	void timeAndPointsComeFromTheGameWithDefaultsPerDifficulty() {
		Game g = TestGames.withTimesAndScores(game(1, null, ok(1, 1), ok(2, 3)), new GameQuestionTime(0, 7, 0, 0),
				new GameQuestionScore(0, 0, 0, 50));

		List<SessionQuestion> qs = builder.build(g);

		assertThat(qs.get(0).getTimeLimitSeconds()).isEqualTo(7);
		assertThat(qs.get(0).getPoints()).isEqualTo(Difficulty.EASY.defaultPoints());
		assertThat(qs.get(1).getTimeLimitSeconds()).isEqualTo(Difficulty.ADVANCED.defaultSeconds());
		assertThat(qs.get(1).getPoints()).isEqualTo(50);
	}

	@Test
	void unseenQuestionsComeFirstThenTheOnesSeenLongestAgo() {
		Game g = game(1, new GameQuestionLevel(0, 3, 0, 0), ok(1, 1), ok(2, 1), ok(3, 1), ok(4, 1), ok(5, 1));
		Instant t = Instant.parse("2026-01-01T00:00:00Z");
		var seen = Map.of(1, t.plusSeconds(30), 2, t.plusSeconds(10), 3, t.plusSeconds(20));

		List<SessionQuestion> qs = builder.build(g, seen);

		assertThat(qs).extracting(SessionQuestion::getQuestionId).containsExactlyInAnyOrder(4, 5, 2);
	}

	@Test
	void aLevelOutOfNewQuestionsBorrowsUnseenOnesFromAnotherLevelBeforeRepeating() {
		// mix 2 easy + 1 medium; both easy questions already seen, three medium never seen
		Game g = game(1, new GameQuestionLevel(0, 2, 1, 0), ok(1, 1), ok(2, 1), ok(3, 2), ok(4, 2), ok(5, 2));
		Instant t = Instant.parse("2026-01-01T00:00:00Z");

		List<SessionQuestion> qs = builder.build(g, Map.of(1, t, 2, t));

		assertThat(qs).extracting(SessionQuestion::getQuestionId).containsExactlyInAnyOrder(3, 4, 5);
		assertThat(qs).extracting(SessionQuestion::getDifficulty).containsOnly(Difficulty.MEDIUM);
	}

	@Test
	void playingRepeatedlyCoversTheWholeTopicBeforeAnyQuestionRepeats() {
		Questions[] pool = new Questions[30];
		for (int i = 0; i < 30; i++) {
			pool[i] = ok(i + 1, i < 18 ? 1 : i < 27 ? 2 : 3); // 18 easy, 9 medium, 3 advanced
		}
		Game g = game(1, new GameQuestionLevel(0, 7, 3, 0), pool);
		Map<Integer, Instant> seen = new HashMap<>();
		Instant t = Instant.parse("2026-01-01T00:00:00Z");
		List<Integer> asked = new ArrayList<>();
		for (int play = 0; play < 3; play++) {
			Instant now = t.plusSeconds(play);
			for (SessionQuestion q : builder.build(g, seen)) {
				asked.add(q.getQuestionId());
				seen.put(q.getQuestionId(), now);
			}
		}

		assertThat(asked).hasSize(30).doesNotHaveDuplicates();
	}

	@Test
	void unseenPicksVaryBetweenPlayers() {
		Game g = game(1, new GameQuestionLevel(0, 2, 0, 0), ok(1, 1), ok(2, 1), ok(3, 1), ok(4, 1), ok(5, 1), ok(6, 1));
		Set<Integer> picked = new HashSet<>();
		for (int seed = 0; seed < 10; seed++) {
			new QuizBuilder(new Random(seed)).build(g).forEach(q -> picked.add(q.getQuestionId()));
		}
		assertThat(picked.size()).isGreaterThan(3);
	}

	@Test
	void gameWithoutAMixAsksTenQuestionsFromABigPool() {
		Questions[] many = new Questions[40];
		for (int i = 0; i < many.length; i++) {
			many[i] = ok(i + 1, 1 + i % 3);
		}

		assertThat(builder.build(game(1, null, many))).hasSize(QuizBuilder.DEFAULT_QUESTIONS);
	}

	@Test
	void gameWithoutQuestionsBuildsNothing() {
		Game g = game(1, null);
		g.setQuestions(null);

		assertThat(builder.build(g)).isEmpty();
	}

	// --- country mix ---

	private static Questions tagged(int id, int level, String country) {
		Questions q = ok(id, level);
		q.setCountry(country);
		return q;
	}

	/** 30 questions each (10 per level) for India, general and Kenya: ids 100+, 200+ and 300+. */
	private static Game mixedGame(GameQuestionLevel mix) {
		List<Questions> qs = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			int level = i % 3 + 1;
			qs.add(tagged(100 + i, level, "IN"));
			qs.add(tagged(200 + i, level, null));
			qs.add(tagged(300 + i, level, "KE"));
		}
		return game(1, mix, qs.toArray(Questions[]::new));
	}

	private static Map<String, Long> origin(List<SessionQuestion> quiz) {
		Map<String, Long> by = new HashMap<>();
		quiz.forEach(q -> by.merge(q.getQuestionId() < 200 ? "IN" : q.getQuestionId() < 300 ? "general" : "KE", 1L, Long::sum));
		return by;
	}

	@Test
	void aPlayersGameIsThreeQuartersTheirCountryAndAQuarterGeneral() {
		List<SessionQuestion> quiz = builder.build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), Map.of(), "in");

		assertThat(quiz).hasSize(12);
		assertThat(origin(quiz)).containsEntry("IN", 9L).containsEntry("general", 3L).doesNotContainKey("KE");
	}

	@Test
	void theMixKeepsTheLevelOrderAndNoRepeats() {
		List<SessionQuestion> quiz = builder.build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), Map.of(), "IN");

		assertThat(quiz.stream().map(SessionQuestion::getQuestionId).distinct()).hasSize(12);
		List<Integer> levels = quiz.stream().map(q -> Difficulty.fromLevel(levelOf(q.getQuestionId())).level()).toList();
		assertThat(levels).isSorted();
	}

	private static int levelOf(int id) {
		return id % 100 % 3 + 1;
	}

	@Test
	void whenTheCountryIsUsedUpGeneralQuestionsFillInBeforeAnythingRepeats() {
		Map<Integer, Instant> seen = new HashMap<>();
		for (int i = 0; i < 30; i++) {
			seen.put(100 + i, Instant.ofEpochSecond(i));
		}
		List<SessionQuestion> quiz = builder.build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), seen, "IN");

		assertThat(origin(quiz)).containsEntry("general", 12L).doesNotContainKeys("IN", "KE");
	}

	@Test
	void withFewGeneralQuestionsTheCountryFillsTheRest() {
		List<Questions> qs = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			qs.add(tagged(100 + i, 1, "IN"));
		}
		qs.add(tagged(200, 1, null));
		List<SessionQuestion> quiz = builder.build(game(1, new GameQuestionLevel(0, 10, 0, 0), qs.toArray(Questions[]::new)),
				Map.of(), "IN");

		assertThat(origin(quiz)).containsEntry("IN", 9L).containsEntry("general", 1L);
	}

	@Test
	void aPlayerWhoseCountryHasNoQuestionsGetsGeneralOnes() {
		List<SessionQuestion> quiz = builder.build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), Map.of(), "US");

		assertThat(origin(quiz)).containsEntry("general", 12L).hasSize(1);
	}

	@Test
	void withoutACountryOnlyGeneralQuestionsAreUsed() {
		for (long seed = 0; seed < 20; seed++) {
			List<SessionQuestion> quiz = new QuizBuilder(new Random(seed))
					.build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), Map.of(), null);
			assertThat(origin(quiz)).containsEntry("general", 12L).hasSize(1);
		}
	}

	@Test
	void withoutACountryAGameWithNoGeneralQuestionsUsesWhatItHas() {
		Game kenyaOnly = game(1, new GameQuestionLevel(0, 2, 0, 0), tagged(1, 1, "KE"), tagged(2, 1, "KE"));

		assertThat(builder.build(kenyaOnly, Map.of(), null)).hasSize(2);
	}

	@Test
	void aGameWithOnlyOtherCountriesQuestionsStillPlays() {
		Game kenyaOnly = game(1, new GameQuestionLevel(0, 3, 0, 0), tagged(1, 1, "KE"), tagged(2, 1, "KE"), tagged(3, 1, "KE"));

		assertThat(builder.build(kenyaOnly, Map.of(), "IN")).hasSize(3);
	}

	@Test
	void anInternationalQuizTakesQuestionsFromEveryCountry() {
		Map<String, Long> all = new HashMap<>();
		for (long seed = 0; seed < 20; seed++) {
			origin(new QuizBuilder(new Random(seed)).build(mixedGame(new GameQuestionLevel(0, 4, 4, 4)), Map.of(),
					QuizBuilder.EVERYWHERE)).forEach((k, v) -> all.merge(k, v, Long::sum));
		}

		assertThat(all).containsKeys("IN", "general", "KE");
	}
}
