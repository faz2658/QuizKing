package com.stackroute.maverick.catalog;

import static com.stackroute.maverick.catalog.Fixtures.bank;
import static com.stackroute.maverick.catalog.Fixtures.category;
import static com.stackroute.maverick.catalog.Fixtures.q;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.stackroute.maverick.domain.Category;
import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.GameQuestionLevel;
import com.stackroute.maverick.domain.MultiPlayerGame;
import com.stackroute.maverick.domain.Questions;
import com.stackroute.maverick.domain.Topic;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameCatalogServiceTest {

	@Mock
	GameRepository games;
	@Mock
	MultiPlayerGameRepository multiGames;
	@Mock
	CategoryRepository categories;
	@Mock
	GameIdGenerator ids;
	@Mock
	CatalogEvents events;

	GameCatalogService service;

	@BeforeEach
	void setUp() {
		service = new GameCatalogService(games, multiGames, categories, ids, new QuestionSelector(new Random(1)),
				events);
		when(ids.nextGameId()).thenReturn(2_000_001, 2_000_002);
		when(games.save(any())).thenAnswer(i -> i.getArgument(0));
		when(multiGames.save(any())).thenAnswer(i -> i.getArgument(0));
		when(categories.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	private Game request(int easy, int medium, int advanced) {
		Game g = new Game();
		g.setGameName("  Capitals ");
		g.setUserId(9);
		g.setQuestionLevels(new GameQuestionLevel(easy, medium, advanced));
		return g;
	}

	@Test
	void createsAGameFromTheTopicAndPublishesIt() {
		when(categories.findById(1)).thenReturn(Optional.of(category(1, "Geography", bank("manual", 3, 3, 3))));

		Game game = service.createGame(1, "geography", request(2, 1, 1));

		assertThat(game.getGameId()).isEqualTo(2_000_001);
		assertThat(game.getGameName()).isEqualTo("Capitals");
		assertThat(game.getQuestions()).hasSize(4);
		assertThat(game.getCategoryId()).isEqualTo(1);
		assertThat(game.getGameType().getGameTypeId()).isEqualTo(GameCatalogService.SINGLE_PLAYER);
		assertThat(game.getTopic().getTopicName()).isEqualTo("Geography");
		assertThat(game.getTopic().getQuestions()).as("bank not copied into the game").isNull();
		verify(events).gameCreated(game);
		verify(events).gameDefinition(game);
	}

	@Test
	void unknownCategoryOrTopicIsNotFound() {
		when(categories.findById(1)).thenReturn(Optional.of(category(1, "Geography", bank("manual", 3, 0, 0))));

		assertThatThrownBy(() -> service.createGame(2, "Geography", request(1, 0, 0)))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.createGame(1, "History", request(1, 0, 0)))
				.isInstanceOf(NotFoundException.class);
		verify(games, never()).save(any());
	}

	@Test
	void gameNameIsRequired() {
		when(categories.findById(1)).thenReturn(Optional.of(category(1, "Geography", bank("manual", 3, 0, 0))));
		Game r = request(1, 0, 0);
		r.setGameName(" ");

		assertThatThrownBy(() -> service.createGame(1, "Geography", r)).isInstanceOf(InvalidGameException.class);
	}

	@Test
	void multiplayerGamesUseGeneratedQuestionsAndFallBackToNumberOfQuestions() {
		when(categories.findById(1)).thenReturn(Optional.of(category(1, "Geography", bank("Auto", 4, 0, 0))));
		MultiPlayerGame r = new MultiPlayerGame();
		r.setGameName("Race");
		r.setNumberOfQuestions(3);
		r.setTimePerQuestion(10);

		MultiPlayerGame game = service.createMultiPlayerGame(1, "Geography", r);

		assertThat(game.getAutoquestions()).hasSize(3);
		assertThat(game.getNumberOfQuestions()).isEqualTo(3);
		assertThat(game.getScorePerQuestion()).isEqualTo(10);
		verify(events).multiPlayerGameCreated(game);
	}

	@Test
	void multiplayerGameNeedsATimeLimit() {
		when(categories.findById(1)).thenReturn(Optional.of(category(1, "Geography", bank("Auto", 4, 0, 0))));
		MultiPlayerGame r = new MultiPlayerGame();
		r.setGameName("Race");
		r.setNumberOfQuestions(2);

		assertThatThrownBy(() -> service.createMultiPlayerGame(1, "Geography", r))
				.isInstanceOf(InvalidGameException.class);
	}

	@Test
	void updateKeepsIdAndQuestionsAndRepublishesTheDefinition() {
		Game existing = request(1, 0, 0);
		existing.setGameId(5);
		existing.setQuestions(List.of(q(1, 1, "manual")));
		when(games.findById(5)).thenReturn(Optional.of(existing));
		Game changes = new Game();
		changes.setGameId(999);
		changes.setGameDescription("New description");

		Game updated = service.updateGame(5, changes);

		assertThat(updated.getGameId()).isEqualTo(5);
		assertThat(updated.getQuestions()).hasSize(1);
		assertThat(updated.getGameDescription()).isEqualTo("New description");
		verify(events).gameDefinition(updated);
	}

	@Test
	void deletingAnUnknownGameIsNotFound() {
		assertThatThrownBy(() -> service.deleteGame(5)).isInstanceOf(NotFoundException.class);
		verify(events, never()).gameDeleted(5);
	}

	@Test
	void deletingAGameAnnouncesIt() {
		when(games.existsById(5)).thenReturn(true);

		service.deleteGame(5);

		verify(games).deleteById(5);
		verify(events).gameDeleted(5);
	}

	@Test
	void mergingACategoryAddsQuestionsInsteadOfReplacingTheBank() {
		Category stored = category(1, "Geography", List.of(q(1, 1, "manual"), q(2, 1, "manual")));
		when(categories.findById(1)).thenReturn(Optional.of(stored));
		Questions updated = q(2, 2, "manual");
		Category incoming = category(1, "GEOGRAPHY", List.of(updated, q(3, 1, "Auto")));
		Topic other = new Topic();
		other.setTopicName("Rivers");
		incoming.getTopic().add(other);

		Category merged = service.mergeCategory(incoming);

		assertThat(merged.getTopic()).extracting(Topic::getTopicName).containsExactly("Geography", "Rivers");
		assertThat(merged.getTopic().get(0).getQuestions()).extracting(Questions::getQuestionId)
				.containsExactlyInAnyOrder(1, 2, 3);
		assertThat(merged.getTopic().get(0).getQuestions()).filteredOn(q -> q.getQuestionId() == 2)
				.extracting(Questions::getQuestionLevel).containsExactly(2);
	}

	@Test
	void categoryNeedsAnId() {
		assertThatThrownBy(() -> service.mergeCategory(new Category())).isInstanceOf(InvalidGameException.class);
	}

	@Test
	void gameRequestFromAnEngineIsAnsweredWithTheDefinition() {
		Game g = request(1, 0, 0);
		when(games.findById(5)).thenReturn(Optional.of(g));

		service.publishDefinition(5);
		service.publishDefinition(6);

		ArgumentCaptor<Game> sent = ArgumentCaptor.forClass(Game.class);
		verify(events).gameDefinition(sent.capture());
		assertThat(sent.getValue()).isSameAs(g);
	}
}
