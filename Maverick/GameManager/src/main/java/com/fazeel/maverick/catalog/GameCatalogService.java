package com.fazeel.maverick.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fazeel.maverick.catalog.QuestionSelector.Source;
import com.fazeel.maverick.domain.Category;
import com.fazeel.maverick.domain.Game;
import com.fazeel.maverick.domain.GameQuestionLevel;
import com.fazeel.maverick.domain.GameType;
import com.fazeel.maverick.domain.MultiPlayerGame;
import com.fazeel.maverick.domain.Questions;
import com.fazeel.maverick.domain.Topic;

@Service
public class GameCatalogService {

	static final int SINGLE_PLAYER = 1;
	static final int MULTI_PLAYER = 2;

	private final GameRepository games;
	private final MultiPlayerGameRepository multiGames;
	private final CategoryRepository categories;
	private final GameIdGenerator ids;
	private final QuestionSelector selector;
	private final CatalogEvents events;

	public GameCatalogService(GameRepository games, MultiPlayerGameRepository multiGames,
			CategoryRepository categories, GameIdGenerator ids, QuestionSelector selector, CatalogEvents events) {
		this.games = games;
		this.multiGames = multiGames;
		this.categories = categories;
		this.ids = ids;
		this.selector = selector;
		this.events = events;
	}

	// ---- categories -------------------------------------------------------------------------

	public List<Category> categories() {
		return categories.findAll();
	}

	/**
	 * Adds an incoming category (from the question generator or an admin) to the bank. Topics are
	 * matched by name and questions by id, so publishing more questions for a topic adds to it instead
	 * of wiping the questions that were already there.
	 */
	public Category mergeCategory(Category incoming) {
		if (incoming == null || incoming.getCategoryId() <= 0) {
			throw new InvalidGameException("A category needs a positive categoryId");
		}
		Category target = categories.findById(incoming.getCategoryId()).orElseGet(() -> {
			Category c = new Category();
			c.setCategoryId(incoming.getCategoryId());
			c.setTopic(new ArrayList<>());
			return c;
		});
		if (incoming.getCategoryName() != null) {
			target.setCategoryName(incoming.getCategoryName());
		}
		if (incoming.getCategoryImage() != null) {
			target.setCategoryImage(incoming.getCategoryImage());
		}
		List<Topic> topics = target.getTopic() == null ? new ArrayList<>() : new ArrayList<>(target.getTopic());
		for (Topic in : Objects.requireNonNullElse(incoming.getTopic(), List.<Topic>of())) {
			if (in == null || in.getTopicName() == null) {
				continue;
			}
			Topic existing = topics.stream().filter(t -> in.getTopicName().equalsIgnoreCase(t.getTopicName()))
					.findFirst().orElse(null);
			if (existing == null) {
				topics.add(in);
				continue;
			}
			List<Questions> qs = existing.getQuestions() == null ? new ArrayList<>()
					: new ArrayList<>(existing.getQuestions());
			for (Questions q : Objects.requireNonNullElse(in.getQuestions(), List.<Questions>of())) {
				qs.removeIf(old -> old.getQuestionId() == q.getQuestionId());
				qs.add(q);
			}
			existing.setQuestions(qs);
			if (in.getTopicImage() != null) {
				existing.setTopicImage(in.getTopicImage());
			}
		}
		target.setTopic(topics);
		return categories.save(target);
	}

	// ---- single-player games ----------------------------------------------------------------

	public Game createGame(int categoryId, String topicName, Game request) {
		Topic topic = topic(categoryId, topicName);
		List<Questions> picked = selector.select(topic.getQuestions(), request.getQuestionLevels(), Source.MANUAL);

		Game game = new Game();
		game.setGameId(ids.nextGameId());
		game.setGameName(required(request.getGameName(), "gameName"));
		game.setGameImage(request.getGameImage());
		game.setCreatedBy(request.getCreatedBy());
		game.setCreatedOn(new Date());
		game.setGameType(gameType(request.getGameType(), SINGLE_PLAYER, "Single player"));
		game.setGameDescription(request.getGameDescription());
		game.setGameRules(request.getGameRules());
		game.setCategoryId(categoryId);
		game.setUserId(request.getUserId());
		game.setQuestionLevels(request.getQuestionLevels());
		game.setQuestionTime(request.getQuestionTime());
		game.setQuestionScore(request.getQuestionScore());
		game.setTopic(summary(topic));
		game.setQuestions(picked);

		Game saved = games.save(game);
		events.gameCreated(saved);
		events.gameDefinition(saved);
		return saved;
	}

	public Game game(int gameId) {
		return games.findById(gameId).orElseThrow(() -> new NotFoundException("No game " + gameId));
	}

	public Optional<Game> findGame(int gameId) {
		return games.findById(gameId);
	}

	public List<Game> games() {
		return games.findAll();
	}

	public List<Game> gamesInTopic(int categoryId, String topicName) {
		return games.findByCategoryIdAndTopicTopicNameIgnoreCase(categoryId, topicName);
	}

	public List<Game> gamesNamedLike(String prefix) {
		return games.findByGameNameStartingWithIgnoreCase(prefix.trim());
	}

	public List<Game> gamesNamed(String name) {
		return games.findByGameNameIgnoreCase(name.trim());
	}

	/** Updates the descriptive fields of a game. The id, questions and creation data are kept. */
	public Game updateGame(int gameId, Game changes) {
		Game game = game(gameId);
		if (changes.getGameName() != null) {
			game.setGameName(required(changes.getGameName(), "gameName"));
		}
		if (changes.getGameDescription() != null) {
			game.setGameDescription(changes.getGameDescription());
		}
		if (changes.getGameRules() != null) {
			game.setGameRules(changes.getGameRules());
		}
		if (changes.getGameImage() != null) {
			game.setGameImage(changes.getGameImage());
		}
		if (changes.getQuestionTime() != null) {
			game.setQuestionTime(changes.getQuestionTime());
		}
		if (changes.getQuestionScore() != null) {
			game.setQuestionScore(changes.getQuestionScore());
		}
		Game saved = games.save(game);
		events.gameDefinition(saved);
		return saved;
	}

	public void deleteGame(int gameId) {
		if (!games.existsById(gameId) && !multiGames.existsById(gameId)) {
			throw new NotFoundException("No game " + gameId);
		}
		games.deleteById(gameId);
		multiGames.deleteById(gameId);
		events.gameDeleted(gameId);
	}

	/** Answers a game engine's request for a game definition. Unknown ids are ignored. */
	public void publishDefinition(int gameId) {
		games.findById(gameId).ifPresent(events::gameDefinition);
	}

	// ---- multiplayer games ------------------------------------------------------------------

	public MultiPlayerGame createMultiPlayerGame(int categoryId, String topicName, MultiPlayerGame request) {
		Topic topic = topic(categoryId, topicName);
		GameQuestionLevel mix = request.getQuestionLevel();
		if ((mix == null || mix.getEasyLevel() + mix.getMediumLevel() + mix.getAdvancedLevel() == 0)
				&& request.getNumberOfQuestions() > 0) {
			mix = new GameQuestionLevel(request.getNumberOfQuestions(), 0, 0);
		}
		List<Questions> picked = selector.select(topic.getQuestions(), mix, Source.AUTO);
		if (request.getTimePerQuestion() <= 0) {
			throw new InvalidGameException("timePerQuestion must be positive");
		}

		MultiPlayerGame game = new MultiPlayerGame();
		game.setGameId(ids.nextGameId());
		game.setGameName(required(request.getGameName(), "gameName"));
		game.setGameImage(request.getGameImage());
		game.setCreatedBy(request.getCreatedBy());
		game.setCreatedOn(new Date());
		game.setGameType(gameType(request.getGameType(), MULTI_PLAYER, "Multiplayer"));
		game.setGameDescription(request.getGameDescription());
		game.setGameRules(request.getGameRules());
		game.setUserId(request.getUserId());
		game.setCategoryId(categoryId);
		game.setQuestionLevel(mix);
		game.setNumberOfQuestions(picked.size());
		game.setTimePerQuestion(request.getTimePerQuestion());
		game.setScorePerQuestion(request.getScorePerQuestion() > 0 ? request.getScorePerQuestion() : 10);
		game.setTopic(summary(topic));
		game.setAutoquestions(picked);

		MultiPlayerGame saved = multiGames.save(game);
		events.multiPlayerGameCreated(saved);
		return saved;
	}

	public MultiPlayerGame multiPlayerGame(int gameId) {
		return multiGames.findById(gameId).orElseThrow(() -> new NotFoundException("No multiplayer game " + gameId));
	}

	public List<MultiPlayerGame> multiPlayerGames() {
		return multiGames.findAll();
	}

	public List<MultiPlayerGame> multiPlayerGamesInTopic(int categoryId, String topicName) {
		return multiGames.findByCategoryIdAndTopicTopicNameIgnoreCase(categoryId, topicName);
	}

	// ---- helpers ----------------------------------------------------------------------------

	private Topic topic(int categoryId, String topicName) {
		Category category = categories.findById(categoryId)
				.orElseThrow(() -> new NotFoundException("No category " + categoryId));
		return Objects.requireNonNullElse(category.getTopic(), List.<Topic>of()).stream()
				.filter(t -> t != null && topicName.equalsIgnoreCase(t.getTopicName())).findFirst()
				.orElseThrow(() -> new NotFoundException("No topic '" + topicName + "' in category " + categoryId));
	}

	/** Topic reference stored on a game; the full question bank is not copied into every game. */
	private static Topic summary(Topic t) {
		Topic s = new Topic();
		s.setTopicId(t.getTopicId());
		s.setTopicName(t.getTopicName());
		s.setTopicImage(t.getTopicImage());
		return s;
	}

	private static GameType gameType(GameType requested, int id, String name) {
		GameType type = new GameType();
		type.setGameTypeId(id);
		type.setGameTypeName(requested != null && requested.getGameTypeName() != null ? requested.getGameTypeName()
				: name);
		type.setGameTypeDescription(requested == null ? null : requested.getGameTypeDescription());
		return type;
	}

	private static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new InvalidGameException(field + " is required");
		}
		return value.trim();
	}
}
