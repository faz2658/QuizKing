package com.fazeel.quizking.kafka;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.repository.GameRepository;

class GameDefinitionListenerTest {

	final GameRepository games = mock(GameRepository.class);
	final GameDefinitionListener listener = new GameDefinitionListener(games);

	@Test
	void cachesPublishedGames() {
		Game g = new Game();
		g.setGameId(7);

		listener.onGame(g);

		verify(games).save(g);
	}

	@Test
	void ignoresGamesWithoutAnId() {
		listener.onGame(new Game());

		verify(games, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deletedGamesLeaveTheCatalog() {
		listener.onGameDeleted(7);
		listener.onGameDeleted(null);

		verify(games).deleteById(7);
		verify(games, never()).deleteById(0);
		verify(games, org.mockito.Mockito.times(1)).deleteById(anyInt());
	}
}
