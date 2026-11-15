package com.stackroute.maverick.session;

import static com.stackroute.maverick.session.TestGames.game;
import static com.stackroute.maverick.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.GameQuestionLevel;
import com.stackroute.maverick.domain.GameQuestionScore;
import com.stackroute.maverick.domain.GameQuestionTime;
import com.stackroute.maverick.domain.Questions;

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
	void twoOptionQuestionsAreFineAndBlankOptionsAreNotShown() {
		Game g = game(1, null, question(1, 1, "True", "True", "False", "", null));

		assertThat(builder.build(g).get(0).getOptions()).containsExactlyInAnyOrder("True", "False");
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
	void gameWithoutQuestionsBuildsNothing() {
		Game g = game(1, null);
		g.setQuestions(null);

		assertThat(builder.build(g)).isEmpty();
	}
}
