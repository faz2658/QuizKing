package com.fazeel.quizcadia.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.catalog.GameCatalogService;

/**
 * Full game definitions, with every question and its correct answer, for the game engines (adaptive and
 * multiplayer) only. Deliberately outside /api: the public proxy forwards nothing but /api/ to the services, so
 * this can't be reached from the internet in any spelling. Players' browsers get questions from the engines,
 * which never send an answer before it's been given.
 */
@RestController
@RequestMapping("/internal/game")
public class InternalGameController {

	private final GameCatalogService catalog;

	public InternalGameController(GameCatalogService catalog) {
		this.catalog = catalog;
	}

	/** The full solo game, questions and answers included, for an engine that hasn't cached it. */
	@GetMapping("/{gameId}")
	public Game game(@PathVariable int gameId) {
		return catalog.game(gameId);
	}

	/** The full multiplayer game, questions and answers included, for the multiplayer engine. */
	@GetMapping("/mp/{gameId}")
	public MultiPlayerGame multiPlayerGame(@PathVariable int gameId) {
		return catalog.multiPlayerGame(gameId);
	}
}
