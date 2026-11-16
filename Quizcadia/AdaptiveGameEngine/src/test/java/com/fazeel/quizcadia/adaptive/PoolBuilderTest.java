package com.fazeel.quizcadia.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Game;

class PoolBuilderTest {

	static Game game(int perLevel) {
		List<Game.Question> qs = new ArrayList<>();
		int id = 1;
		for (int level = 1; level <= 3; level++) {
			for (int i = 0; i < perLevel; i++, id++) {
				qs.add(new Game.Question(id, level, "Q" + id, "a", "b", null, null, "a"));
			}
		}
		return new Game(1, "G", 1, null, qs);
	}

	@Test
	void keepsAtMostPerLevelQuestionsPreferringUnseenThenOldest() {
		Instant t = Instant.parse("2026-01-01T00:00:00Z");
		// level 1 has ids 1..5: 1,2,3 seen (3 longest ago), 4,5 never seen
		Map<Integer, Instant> seen = Map.of(1, t.plusSeconds(30), 2, t.plusSeconds(20), 3, t.plusSeconds(10));

		List<PooledQuestion> pool = new PoolBuilder(new Random(1)).build(game(5), seen, 3);

		assertThat(pool).hasSize(9);
		assertThat(pool.stream().filter(p -> p.level() == 1).map(PooledQuestion::questionId))
				.containsExactlyInAnyOrder(4, 5, 3);
	}
}
