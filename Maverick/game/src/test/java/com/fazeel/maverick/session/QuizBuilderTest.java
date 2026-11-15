package com.fazeel.maverick.session;

import static com.fazeel.maverick.session.TestGames.game;
import static com.fazeel.maverick.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fazeel.maverick.domain.Game;
import com.fazeel.maverick.domain.GameQuestionLevel;
import com.fazeel.maverick.domain.GameQuestionScore;
import com.fazeel.maverick.domain.GameQuestionTime;
import com.fazeel.maverick.domain.Questions;

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
		java.time.Instant t = java.time.Instant.parse("2026-01-01T00:00:00Z");
		var seen = java.util.Map.of(1, t.plusSeconds(30), 2, t.plusSeconds(10), 3, t.plusSeconds(20));

		List<SessionQuestion> qs = builder.build(g, seen);

		assertThat(qs).extracting(SessionQuestion::getQuestionId).containsExactlyInAnyOrder(4, 5, 2);
	}

	@Test
	void aLevelOutOfNewQuestionsBorrowsUnseenOnesFromAnotherLevelBeforeRepeating() {
		// mix 2 easy + 1 medium; both easy questions already seen, three medium never seen
		Game g = game(1, new GameQuestionLevel(0, 2, 1, 0), ok(1, 1), ok(2, 1), ok(3, 2), ok(4, 2), ok(5, 2));
		java.time.Instant t = java.time.Instant.parse("2026-01-01T00:00:00Z");

		List<SessionQuestion> qs = builder.build(g, java.util.Map.of(1, t, 2, t));

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
		java.util.Map<Integer, java.time.Instant> seen = new java.util.HashMap<>();
		java.time.Instant t = java.time.Instant.parse("2026-01-01T00:00:00Z");
		List<Integer> asked = new java.util.ArrayList<>();
		for (int play = 0; play < 3; play++) {
			java.time.Instant now = t.plusSeconds(play);
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
		java.util.Set<Integer> picked = new java.util.HashSet<>();
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
}
