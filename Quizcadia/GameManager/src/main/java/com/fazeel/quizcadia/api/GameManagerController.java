package com.fazeel.quizcadia.api;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.api.Summaries.CategorySummary;
import com.fazeel.quizcadia.api.Summaries.CountrySummary;
import com.fazeel.quizcadia.api.Summaries.GameSummary;
import com.fazeel.quizcadia.api.Summaries.TopicSummary;
import com.fazeel.quizcadia.catalog.CatalogCards;
import com.fazeel.quizcadia.catalog.GameCatalogService;
import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

/**
 * Game manager API. Paths are kept from the original service so existing callers keep working;
 * endpoints that only echoed their path variables back were removed.
 */
@RestController
@RequestMapping("/api/game")
public class GameManagerController {

	private final GameCatalogService catalog;
	private final CatalogCards cards;

	/**
	 * The home page's lists. Building them reads every game or category with all its questions embedded (about
	 * 1.4 MB from the database), so each list is built once and shared until the catalog changes, or for a minute.
	 */
	private record Snapshot<T>(long version, long builtAt, List<T> items) {
	}

	private static final long MAX_AGE_MS = 60_000;

	private final ConcurrentHashMap<String, Snapshot<?>> lists = new ConcurrentHashMap<>();

	public GameManagerController(GameCatalogService catalog, CatalogCards cards) {
		this.catalog = catalog;
		this.cards = cards;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> shared(String name, Supplier<List<T>> build) {
		long v = catalog.version();
		long now = System.currentTimeMillis();
		Snapshot<T> s = (Snapshot<T>) lists.get(name);
		if (s != null && s.version() == v && now - s.builtAt() < MAX_AGE_MS) {
			return s.items();
		}
		synchronized (lists) { // one rebuild at a time; everyone else waits for it instead of reading the database too
			s = (Snapshot<T>) lists.get(name);
			if (s != null && s.version() == v && now - s.builtAt() < MAX_AGE_MS) {
				return s.items();
			}
			List<T> items = List.copyOf(build.get());
			lists.put(name, new Snapshot<>(v, now, items));
			return items;
		}
	}

	// categories / question bank

	/**
	 * The catalog lists are the same for every visitor, so they may be cached: browsers check back each time (and
	 * get a cheap "304 not modified" from {@link CatalogEtags} while nothing changed), a CDN keeps them for 30 s.
	 */
	static final CacheControl SHARED_LIST = CacheControl.maxAge(Duration.ZERO).mustRevalidate().cachePublic()
			.sMaxAge(Duration.ofSeconds(30));

	private static <T> ResponseEntity<List<T>> cacheable(List<T> items) {
		return ResponseEntity.ok().cacheControl(SHARED_LIST).body(items);
	}

	/**
	 * Every category with its topics and how many questions each has (no questions themselves); cacheable briefly.
	 */
	@GetMapping("/categories")
	public ResponseEntity<List<CategorySummary>> categories() {
		return cacheable(categoryList());
	}

	private List<CategorySummary> categoryList() {
		return shared("categories", () -> cards.categories().stream().map(CategorySummary::of).toList());
	}

	/** Category id to country code, for categories that are a country's own question set. */
	private Map<Integer, String> countryOfCategory() {
		Map<Integer, String> m = new HashMap<>();
		for (CategorySummary c : categoryList()) {
			if (c.countryCode() != null) {
				m.put(c.categoryId(), c.countryCode());
			}
		}
		return m;
	}

	/**
	 * Countries with their own question set, with how many solo games and questions each has: what the
	 * International page lists.
	 */
	@GetMapping("/countries")
	public ResponseEntity<List<CountrySummary>> countries() {
		return cacheable(shared("countries", () -> {
			Map<String, Integer> games = new HashMap<>();
			for (GameSummary g : allGames()) {
				if (g.country() != null) {
					games.merge(g.country(), 1, Integer::sum);
				}
			}
			return categoryList().stream().filter(c -> c.countryCode() != null)
					.map(c -> new CountrySummary(c.countryCode(), c.categoryId(), c.categoryName(),
							games.getOrDefault(c.countryCode(), 0),
							c.topic().stream().mapToInt(TopicSummary::questionCount).sum()))
					.sorted(Comparator.comparing(CountrySummary::country)).toList();
		}));
	}

	/**
	 * Keeps the games of one country's question set ({@code country=IN}), or the world trivia that belongs to no
	 * country ({@code country=WORLD}); without {@code country}, every game.
	 */
	private static List<GameSummary> forCountry(List<GameSummary> all, String country) {
		if (country == null || country.isBlank()) {
			return all;
		}
		String c = country.trim().toUpperCase(Locale.ROOT);
		return all.stream().filter(g -> "WORLD".equals(c) ? g.country() == null : c.equals(g.country())).toList();
	}

	private List<GameSummary> allGames() {
		return shared("games", () -> {
			Map<Integer, String> countries = countryOfCategory();
			return cards.games().stream().map(g -> GameSummary.of(g, countries.get(g.categoryId()))).toList();
		});
	}

	private List<GameSummary> allMultiPlayerGames() {
		return shared("mp", () -> {
			Map<Integer, String> countries = countryOfCategory();
			return cards.multiPlayerGames().stream().map(g -> GameSummary.of(g, countries.get(g.categoryId()))).toList();
		});
	}

	/** Adds or merges a category and its topics (admin); questions are merged by id, never wiped. */
	@PostMapping("/category")
	public Category saveCategory(@RequestBody Category category) {
		return catalog.mergeCategory(category);
	}

	// single-player games

	/**
	 * Creates a solo game on a topic (admin). Fails when the topic hasn't enough questions for the game's level
	 * mix.
	 */
	@PostMapping("/category/{categoryId}/{topicName}")
	@ResponseStatus(HttpStatus.CREATED)
	public GameSummary createGame(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody Game request) {
		return GameSummary.of(catalog.createGame(categoryId, topicName, request));
	}

	/** The solo games, all or filtered by country (WORLD: the shared topics that belong to no country). */
	@GetMapping("/games")
	public ResponseEntity<List<GameSummary>> games(@RequestParam(required = false) String country) {
		return cacheable(forCountry(allGames(), country));
	}

	/** The solo games built on one topic of a category. */
	@GetMapping("/category/{categoryId}/{topicName}")
	public List<GameSummary> gamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.gamesInTopic(categoryId, topicName).stream().map(GameSummary::of).toList();
	}

	/** Solo games whose name starts with the given text (case ignored), for search-as-you-type. */
	@GetMapping("/gamesNameStartsWith/{prefix}")
	public List<GameSummary> gamesNamedLike(@PathVariable String prefix) {
		return catalog.gamesNamedLike(prefix).stream().map(GameSummary::of).toList();
	}

	/** Solo games with exactly this name (case ignored). */
	@GetMapping("/games/{gameName}")
	public List<GameSummary> gamesNamed(@PathVariable String gameName) {
		return catalog.gamesNamed(gameName).stream().map(GameSummary::of).toList();
	}

	/** Changes a game's name, description, rules, image or question times (admin). */
	@PutMapping("/updategame/{gameId}")
	public Game updateGame(@PathVariable int gameId, @RequestBody Game changes) {
		return catalog.updateGame(gameId, changes);
	}

	/** Deletes a solo or multiplayer game (admin) and tells the engines to forget it. */
	@DeleteMapping({ "/deletegame/{gameId}", "/deletemultigame/{gameId}" })
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGame(@PathVariable int gameId) {
		catalog.deleteGame(gameId);
	}

	// multiplayer games

	/** Creates a multiplayer game on a topic (admin), checking the topic has enough questions for its level mix. */
	@PostMapping("/category/mp/{categoryId}/{topicName}")
	@ResponseStatus(HttpStatus.CREATED)
	public GameSummary createMultiPlayerGame(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody MultiPlayerGame request) {
		return GameSummary.of(catalog.createMultiPlayerGame(categoryId, topicName, request));
	}

	/** The multiplayer games, all or filtered by country like games(). */
	@GetMapping("/games/mp")
	public ResponseEntity<List<GameSummary>> multiPlayerGames(@RequestParam(required = false) String country) {
		return cacheable(forCountry(allMultiPlayerGames(), country));
	}

	/** The multiplayer games built on one topic of a category. */
	@GetMapping("/mp/category/{categoryId}/{topicName}")
	public List<GameSummary> multiPlayerGamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.multiPlayerGamesInTopic(categoryId, topicName).stream().map(GameSummary::of).toList();
	}
}
