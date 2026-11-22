package com.fazeel.quizcadia.recommend;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.graph.RecommendationGraph;

/** Recommendations from the graph, with limits kept between 1 and 50. */
@Service
public class RecommendationService {

	static final int MAX_LIMIT = 50;

	private final RecommendationGraph graph;

	public RecommendationService(RecommendationGraph graph) {
		this.graph = graph;
	}

	/** Games ranked for the player (see Recommender). */
	public List<Recommendation> forUser(int userId, int limit) {
		return Recommender.rank(graph.candidates(userId), clamp(limit));
	}

	/** Most played games overall (user 0 never exists, so no personal signals apply). */
	public List<Recommendation> trending(int limit) {
		return graph.candidates(0).stream().sorted(Comparator.comparingLong(Candidate::totalPlays).reversed())
				.limit(clamp(limit)).map(c -> Recommender.rank(List.of(c), 1).get(0)).toList();
	}

	/** Replaces the player's favourite categories. */
	public void setFavoriteCategories(int userId, Set<Integer> categoryIds) {
		graph.setFavoriteCategories(userId, categoryIds);
	}

	/** The category ids the player favours. */
	public List<Integer> favoriteCategories(int userId) {
		return graph.favoriteCategories(userId);
	}

	private static int clamp(int limit) {
		return Math.max(1, Math.min(MAX_LIMIT, limit));
	}
}
