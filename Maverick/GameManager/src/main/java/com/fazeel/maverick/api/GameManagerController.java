package com.fazeel.maverick.api;

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

import com.fazeel.maverick.catalog.GameCatalogService;
import com.fazeel.maverick.domain.Category;
import com.fazeel.maverick.domain.Game;
import com.fazeel.maverick.domain.MultiPlayerGame;

/**
 * Game manager API. Paths are kept from the original service so existing callers keep working;
 * endpoints that only echoed their path variables back were removed.
 */
@RestController
@RequestMapping("/api/game")
public class GameManagerController {

	private final GameCatalogService catalog;

	public GameManagerController(GameCatalogService catalog) {
		this.catalog = catalog;
	}

	// categories / question bank

	@GetMapping("/categories")
	public List<Category> categories() {
		return catalog.categories();
	}

	@PostMapping("/category")
	public Category saveCategory(@RequestBody Category category) {
		return catalog.mergeCategory(category);
	}

	// single-player games

	@PostMapping("/category/{categoryId}/{topicName}")
	@ResponseStatus(HttpStatus.CREATED)
	public Game createGame(@PathVariable int categoryId, @PathVariable String topicName, @RequestBody Game request) {
		return catalog.createGame(categoryId, topicName, request);
	}

	@GetMapping("/games")
	public List<Game> games() {
		return catalog.games();
	}

	@GetMapping("/game1/{gameId}")
	public Game game(@PathVariable int gameId) {
		return catalog.game(gameId);
	}

	@GetMapping("/category/{categoryId}/{topicName}")
	public List<Game> gamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.gamesInTopic(categoryId, topicName);
	}

	@GetMapping("/gamesNameStartsWith/{prefix}")
	public List<Game> gamesNamedLike(@PathVariable String prefix) {
		return catalog.gamesNamedLike(prefix);
	}

	@GetMapping("/games/{gameName}")
	public List<Game> gamesNamed(@PathVariable String gameName) {
		return catalog.gamesNamed(gameName);
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
	public MultiPlayerGame createMultiPlayerGame(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody MultiPlayerGame request) {
		return catalog.createMultiPlayerGame(categoryId, topicName, request);
	}

	@GetMapping("/games/mp")
	public List<MultiPlayerGame> multiPlayerGames() {
		return catalog.multiPlayerGames();
	}

	@GetMapping("/games/mp/{gameId}")
	public MultiPlayerGame multiPlayerGame(@PathVariable int gameId) {
		return catalog.multiPlayerGame(gameId);
	}

	@GetMapping("/mp/category/{categoryId}/{topicName}")
	public List<MultiPlayerGame> multiPlayerGamesInTopic(@PathVariable int categoryId, @PathVariable String topicName) {
		return catalog.multiPlayerGamesInTopic(categoryId, topicName);
	}
}
