package com.fazeel.quizcadia.recommend;

import java.util.Comparator;
import java.util.List;

import com.fazeel.quizcadia.recommend.Recommendation.Reason;

/**
 * Ranks games for a user. Games the user hasn't played come first, ordered by favourite category,
 * similar players and popularity; games they already played follow, so the feed is never empty.
 */
public final class Recommender {

	static final double FAVORITE_WEIGHT = 3.0;
	static final double SIMILAR_WEIGHT = 2.0;
	static final double POPULAR_WEIGHT = 1.0;
	static final double ALREADY_PLAYED_PENALTY = 4.0;

	private Recommender() {
	}

	public static List<Recommendation> rank(List<Candidate> candidates, int limit) {
		long maxPlays = Math.max(1, candidates.stream().mapToLong(Candidate::totalPlays).max().orElse(1));
		long maxSimilar = Math.max(1, candidates.stream().mapToLong(Candidate::similarPlayers).max().orElse(1));
		return candidates.stream().map(c -> score(c, maxPlays, maxSimilar))
				.sorted(Comparator.comparing((Recommendation r) -> r.reason() == Reason.PLAY_AGAIN)
						.thenComparing(Comparator.comparingDouble(Recommendation::score).reversed())
						.thenComparing(Recommendation::gameName, Comparator.nullsLast(Comparator.naturalOrder())))
				.limit(Math.max(0, limit)).toList();
	}

	private static Recommendation score(Candidate c, long maxPlays, long maxSimilar) {
		double favorite = c.favoriteCategory() ? FAVORITE_WEIGHT : 0;
		double similar = SIMILAR_WEIGHT * c.similarPlayers() / maxSimilar;
		double popular = POPULAR_WEIGHT * c.totalPlays() / maxPlays;
		double played = c.myPlays() > 0 ? ALREADY_PLAYED_PENALTY : 0;
		double score = favorite + similar + popular - played;

		Reason reason;
		if (c.myPlays() > 0) {
			reason = Reason.PLAY_AGAIN;
		} else if (favorite > 0 && favorite >= similar && favorite >= popular) {
			reason = Reason.FAVORITE_CATEGORY;
		} else if (similar > 0 && similar >= popular) {
			reason = Reason.PLAYERS_LIKE_YOU;
		} else if (popular > 0) {
			reason = Reason.POPULAR;
		} else {
			reason = Reason.NEW;
		}
		return new Recommendation(c.gameId(), c.gameName(), c.gameImage(), c.categoryId(), c.mode(), reason,
				Math.round(score * 1000) / 1000.0);
	}
}
