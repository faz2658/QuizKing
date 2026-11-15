package com.fazeel.quizking.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.domain.Match;
import com.fazeel.quizking.domain.MultiPlayerGame;

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
}
