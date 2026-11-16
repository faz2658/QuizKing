package com.fazeel.quizking.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizking.api.Summaries.CategorySummary;
import com.fazeel.quizking.api.Summaries.GameSummary;
import com.fazeel.quizking.catalog.GameCatalogService;
import com.fazeel.quizking.domain.Category;
import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.domain.MultiPlayerGame;

/**
 * Game manager API. Paths are kept from the original service so existing callers keep working;
 * endpoints that only echoed their path variables back were removed.
 */
@RestController
@RequestMapping("/api/game")
public class GameManagerController {

	private final GameCatalogService catalog;

	/**
	 * The home page's lists. Building them reads every game or category with all its questions embedded (about
	 * 1.4 MB from the database), so each list is built once and shared until the catalog changes, or for a minute.
	 */
	private record Snapshot<T>(long version, long builtAt, List<T> items) {
	}

	private static final long MAX_AGE_MS = 60_000;

	private final java.util.concurrent.ConcurrentHashMap<String, Snapshot<?>> lists = new java.util.concurrent.ConcurrentHashMap<>();

	public GameManagerController(GameCatalogService catalog) {
		this.catalog = catalog;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> shared(String name, java.util.function.Supplier<List<T>> build) {
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

	@GetMapping("/categories")
	public List<CategorySummary> categories() {
		return shared("categories", () -> catalog.categories().stream().map(CategorySummary::of).toList());
	}

	@PostMapping("/category")
	public Category saveCategory(@RequestBody Category category) {
		return catalog.mergeCategory(category);
	}

	// single-player games

	@PostMapping("/category/{categoryId}/{topicName}")
	@ResponseStatus(HttpStatus.CREATED)
	public GameSummary createGame(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody Game request) {
		return GameSummary.of(catalog.createGame(categoryId, topicName, request));
	}

	@GetMapping("/games")
	public List<GameSummary> games() {
		return shared("games", () -> catalog.games().stream().map(GameSummary::of).toList());
	}

	@GetMapping("/game1/{gameId}")
	public Game game(@PathVariable int gameId) {
		return catalog.game(gameId);
	}

	@GetMapping("/category/{categoryId}/{topicName}")
	public List<GameSummary> gamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.gamesInTopic(categoryId, topicName).stream().map(GameSummary::of).toList();
	}

	@GetMapping("/gamesNameStartsWith/{prefix}")
	public List<GameSummary> gamesNamedLike(@PathVariable String prefix) {
		return catalog.gamesNamedLike(prefix).stream().map(GameSummary::of).toList();
	}

	@GetMapping("/games/{gameName}")
	public List<GameSummary> gamesNamed(@PathVariable String gameName) {
		return catalog.gamesNamed(gameName).stream().map(GameSummary::of).toList();
	}

	@PutMapping("/updategame/{gameId}")
	public Game updateGame(@PathVariable int gameId, @RequestBody Game changes) {
		return catalog.updateGame(gameId, changes);
	}

	@DeleteMapping({ "/deletegame/{gameId}", "/deletemultigame/{gameId}" })
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGame(@PathVariable int gameId) {
		catalog.deleteGame(gameId);
	}

	// multiplayer games

	@PostMapping("/category/mp/{categoryId}/{topicName}")
	@ResponseStatus(HttpStatus.CREATED)
	public GameSummary createMultiPlayerGame(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody MultiPlayerGame request) {
		return GameSummary.of(catalog.createMultiPlayerGame(categoryId, topicName, request));
	}

	@GetMapping("/games/mp")
	public List<GameSummary> multiPlayerGames() {
		return shared("mp", () -> catalog.multiPlayerGames().stream().map(GameSummary::of).toList());
	}

	@GetMapping("/games/mp/{gameId}")
	public MultiPlayerGame multiPlayerGame(@PathVariable int gameId) {
		return catalog.multiPlayerGame(gameId);
	}

	@GetMapping("/mp/category/{categoryId}/{topicName}")
	public List<GameSummary> multiPlayerGamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.multiPlayerGamesInTopic(categoryId, topicName).stream().map(GameSummary::of).toList();
	}
}
