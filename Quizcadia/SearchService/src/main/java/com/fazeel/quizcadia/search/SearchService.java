package com.fazeel.quizcadia.search;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class SearchService {

	static final int MAX_RESULTS = 20;

	private final GameCatalog catalog;

	public SearchService(GameCatalog catalog) {
		this.catalog = catalog;
	}

	/** Exact (case-insensitive) matches first, then the rest alphabetically. Blank queries find nothing. */
	public List<GameCard> search(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		String q = query.trim();
		String lower = q.toLowerCase(Locale.ROOT);
		return catalog.gamesNamedLike(q).stream()
				.filter(g -> g.gameName() != null)
				.sorted(Comparator.comparing((GameCard g) -> !g.gameName().toLowerCase(Locale.ROOT).equals(lower))
						.thenComparing(g -> g.gameName().toLowerCase(Locale.ROOT)))
				.limit(MAX_RESULTS).toList();
	}
}
