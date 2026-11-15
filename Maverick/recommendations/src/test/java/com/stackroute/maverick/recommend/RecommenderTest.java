package com.stackroute.maverick.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stackroute.maverick.recommend.Recommendation.Reason;

class RecommenderTest {

	static Candidate c(int id, long plays, long mine, boolean fav, long similar) {
		return new Candidate(id, "Game " + id, null, 1, GameMode.SINGLE, plays, mine, fav, similar);
	}

	@Test
	void favoriteCategoryBeatsPopularity() {
		List<Recommendation> r = Recommender.rank(List.of(c(1, 100, 0, false, 0), c(2, 1, 0, true, 0)), 10);

		assertThat(r).extracting(Recommendation::gameId).containsExactly(2, 1);
		assertThat(r.get(0).reason()).isEqualTo(Reason.FAVORITE_CATEGORY);
		assertThat(r.get(1).reason()).isEqualTo(Reason.POPULAR);
	}

	@Test
	void gamesAlreadyPlayedGoLastButStayInTheFeed() {
		List<Recommendation> r = Recommender.rank(List.of(c(1, 100, 5, true, 9), c(2, 0, 0, false, 0)), 10);

		assertThat(r).extracting(Recommendation::gameId).containsExactly(2, 1);
		assertThat(r.get(1).reason()).isEqualTo(Reason.PLAY_AGAIN);
		assertThat(r.get(0).reason()).isEqualTo(Reason.NEW);
	}

	@Test
	void similarPlayersOutweighPlainPopularity() {
		List<Recommendation> r = Recommender.rank(List.of(c(1, 50, 0, false, 0), c(2, 10, 0, false, 4)), 10);

		assertThat(r.get(0).gameId()).isEqualTo(2);
		assertThat(r.get(0).reason()).isEqualTo(Reason.PLAYERS_LIKE_YOU);
	}

	@Test
	void limitIsRespectedAndEmptyInputIsFine() {
		assertThat(Recommender.rank(List.of(c(1, 1, 0, false, 0), c(2, 2, 0, false, 0)), 1)).hasSize(1);
		assertThat(Recommender.rank(List.of(), 5)).isEmpty();
	}

	@Test
	void tiesAreBrokenByName() {
		List<Recommendation> r = Recommender.rank(List.of(c(2, 0, 0, false, 0), c(1, 0, 0, false, 0)), 10);

		assertThat(r).extracting(Recommendation::gameName).containsExactly("Game 1", "Game 2");
	}
}
