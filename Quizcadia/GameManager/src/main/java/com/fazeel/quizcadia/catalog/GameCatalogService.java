package com.fazeel.quizcadia.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.GameType;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;

/**
 * The catalog's rules: creating, changing and deleting games, merging categories from the question service, and
 * telling the engines (via CatalogEvents) about every change.
 */
@Service
public class GameCatalogService {

	static final int SINGLE_PLAYER = 1;
	static final int MULTI_PLAYER = 2;
	static final String AUTO_CREATOR = "QuizCadia";
	private static final int[] SOLO_MIX = { 4, 3, 3 };
	private static final int[] MULTI_MIX = { 3, 3, 2 };

	private final GameRepository games;
	private final MultiPlayerGameRepository multiGames;
	private final CategoryRepository categories;
	private final GameIdGenerator ids;
	private final QuestionSelector selector;
	private final CatalogEvents events;
	private final AutoGames autoGames;

	/** Bumped by every change to a game or category, so cached lists know they're out of date. */
	private final AtomicLong version = new AtomicLong();

	/** Goes up on every change to the catalog, so cached lists know when to rebuild. */
	public long version() {
		return version.get();
	}

	private <T> T changed(T result) {
		version.incrementAndGet();
		return result;
	}

	public GameCatalogService(GameRepository games, MultiPlayerGameRepository multiGames,
			CategoryRepository categories, GameIdGenerator ids, QuestionSelector selector, CatalogEvents events,
			AutoGames autoGames) {
		this.games = games;
		this.multiGames = multiGames;
		this.categories = categories;
		this.ids = ids;
		this.selector = selector;
		this.events = events;
		this.autoGames = autoGames;
	}

	// ---- categories -------------------------------------------------------------------------

	/** Every category in full (with questions), for the admin tools. */
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
		if (incoming.getCountryCode() != null) {
			target.setCountryCode(incoming.getCountryCode().isBlank() ? null : incoming.getCountryCode());
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
			if (in.getQuestionCount() != null) {
				// counts only: the questions live in the question service, so drop any old copy
				existing.setQuestions(null);
				existing.setRemovedQuestionIds(null);
				existing.setQuestionCount(in.getQuestionCount());
				existing.setLevelCounts(in.getLevelCounts());
				if (in.getTopicImage() != null) {
					existing.setTopicImage(in.getTopicImage());
				}
				continue;
			}
			existing.setQuestionCount(null);
			existing.setLevelCounts(null);
			List<Questions> qs = existing.getQuestions() == null ? new ArrayList<>()
					: new ArrayList<>(existing.getQuestions());
			for (Questions q : Objects.requireNonNullElse(in.getQuestions(), List.<Questions>of())) {
				qs.removeIf(old -> old.getQuestionId() == q.getQuestionId());
				qs.add(q);
			}
			List<Integer> removed = Objects.requireNonNullElse(in.getRemovedQuestionIds(), List.<Integer>of());
			qs.removeIf(old -> removed.contains(old.getQuestionId()));
			existing.setQuestions(qs);
			if (in.getTopicImage() != null) {
				existing.setTopicImage(in.getTopicImage());
			}
		}
		target.setTopic(topics);
		Category saved = categories.save(target);
		for (Topic in : Objects.requireNonNullElse(incoming.getTopic(), List.<Topic>of())) {
			if (in != null && in.getTopicName() != null) {
				Topic merged = topic(saved.getCategoryId(), in.getTopicName());
				refreshGames(saved.getCategoryId(), merged);
				if (autoGames.enabled()) {
					createAutoGames(saved, merged);
				}
			}
		}
		return changed(saved);
	}

	/** Games of a topic always draw from its current pool, so new questions show up in existing games. */
	private void refreshGames(int categoryId, Topic topic) {
		List<Questions> pool = copiedPool(topic);
		int[] available = available(topic);
		for (Game g : games.findByCategoryIdAndTopicTopicNameIgnoreCase(categoryId, topic.getTopicName())) {
			g.setQuestions(pool);
			g.setQuestionCount(count(topic));
			g.setTopic(summary(topic));
			if (AUTO_CREATOR.equals(g.getCreatedBy())) {
				// auto games made early in an import get a better mix as more levels fill up
				int[] mix = AutoGames.fit(autoGames.soloQuestions(), SOLO_MIX, available);
				g.setQuestionLevels(new GameQuestionLevel(mix[0], mix[1], mix[2]));
			}
			events.gameDefinition(games.save(g));
		}
		for (MultiPlayerGame g : multiGames.findByCategoryIdAndTopicTopicNameIgnoreCase(categoryId,
				topic.getTopicName())) {
			g.setAutoquestions(pool);
			g.setQuestionCount(count(topic));
			g.setTopic(summary(topic));
			if (AUTO_CREATOR.equals(g.getCreatedBy())) {
				int[] mix = AutoGames.fit(autoGames.multiplayerQuestions(), MULTI_MIX, available);
				g.setQuestionLevel(new GameQuestionLevel(mix[0], mix[1], mix[2]));
			}
			events.multiPlayerGameCreated(multiGames.save(g));
		}
	}

	/** Gives a topic with enough questions a solo and a multiplayer game, once. */
	private void createAutoGames(Category category, Topic topic) {
		int poolSize = count(topic);
		if (poolSize < autoGames.minQuestions()) {
			return;
		}
		int[] available = available(topic);
		if (games.findByCategoryIdAndTopicTopicNameIgnoreCase(category.getCategoryId(), topic.getTopicName())
				.isEmpty()) {
			int[] mix = AutoGames.fit(autoGames.soloQuestions(), SOLO_MIX, available);
			Game g = new Game();
			g.setGameName(topic.getTopicName());
			g.setGameDescription(mix[0] + mix[1] + mix[2] + " questions from " + poolSize
					+ ". New ones every time you play.");
			g.setGameRules("Answer before the timer runs out. Easy 10 points, medium 20, advanced 30.");
			g.setCreatedBy(AUTO_CREATOR);
			g.setQuestionLevels(new GameQuestionLevel(mix[0], mix[1], mix[2]));
			createGame(category.getCategoryId(), topic.getTopicName(), g);
		}
		if (multiGames.findByCategoryIdAndTopicTopicNameIgnoreCase(category.getCategoryId(), topic.getTopicName())
				.isEmpty()) {
			int[] mix = AutoGames.fit(autoGames.multiplayerQuestions(), MULTI_MIX, available);
			MultiPlayerGame g = new MultiPlayerGame();
			g.setGameName(topic.getTopicName() + " Showdown");
			g.setGameDescription("Fastest finger first: " + (mix[0] + mix[1] + mix[2]) + " questions against 1-3 rivals.");
			g.setCreatedBy(AUTO_CREATOR);
			g.setQuestionLevel(new GameQuestionLevel(mix[0], mix[1], mix[2]));
			g.setTimePerQuestion(15);
			createMultiPlayerGame(category.getCategoryId(), topic.getTopicName(), g);
		}
	}

	// ---- single-player games ----------------------------------------------------------------

	/**
	 * Creates a solo game on a topic: checks the topic has enough questions for the requested level mix, gives it a
	 * new id, saves it and announces it.
	 */
	public Game createGame(int categoryId, String topicName, Game request) {
		Topic topic = topic(categoryId, topicName);
		selector.require(available(topic), request.getQuestionLevels());

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
		game.setQuestions(copiedPool(topic));
		game.setQuestionCount(count(topic));

		Game saved = games.save(game);
		events.gameCreated(saved);
		events.gameDefinition(saved);
		return changed(saved);
	}

	/** The solo game with this id; throws NotFoundException when there is none. */
	public Game game(int gameId) {
		return games.findById(gameId).orElseThrow(() -> new NotFoundException("No game " + gameId));
	}

	/** The solo game with this id, if any. */
	public Optional<Game> findGame(int gameId) {
		return games.findById(gameId);
	}

	/** Every solo game in full. */
	public List<Game> games() {
		return games.findAll();
	}

	/** The solo games built on one topic of a category (topic name matched ignoring case). */
	public List<Game> gamesInTopic(int categoryId, String topicName) {
		return games.findByCategoryIdAndTopicTopicNameIgnoreCase(categoryId, topicName);
	}

	/** Solo games whose name starts with the prefix, ignoring case. */
	public List<Game> gamesNamedLike(String prefix) {
		return games.findByGameNameStartingWithIgnoreCase(prefix.trim());
	}

	/** Solo games with exactly this name, ignoring case. */
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
		return changed(saved);
	}

	/** Deletes the game whatever its kind and announces it, so the engines drop their cached copies. */
	public void deleteGame(int gameId) {
		if (!games.existsById(gameId) && !multiGames.existsById(gameId)) {
			throw new NotFoundException("No game " + gameId);
		}
		games.deleteById(gameId);
		multiGames.deleteById(gameId);
		events.gameDeleted(gameId);
		version.incrementAndGet();
	}

	/** Answers a game engine's request for a game definition. Unknown ids are ignored. */
	public void publishDefinition(int gameId) {
		games.findById(gameId).ifPresent(events::gameDefinition);
	}

	// ---- multiplayer games ------------------------------------------------------------------

	/**
	 * Creates a multiplayer game on a topic: checks the topic can fill its level mix (numberOfQuestions easy ones
	 * when no mix is given) and that the time per question is positive, then saves and announces it.
	 */
	public MultiPlayerGame createMultiPlayerGame(int categoryId, String topicName, MultiPlayerGame request) {
		Topic topic = topic(categoryId, topicName);
		GameQuestionLevel mix = request.getQuestionLevel();
		if ((mix == null || mix.getEasyLevel() + mix.getMediumLevel() + mix.getAdvancedLevel() == 0)
				&& request.getNumberOfQuestions() > 0) {
			mix = new GameQuestionLevel(request.getNumberOfQuestions(), 0, 0);
		}
		selector.require(available(topic), mix);
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
		game.setNumberOfQuestions(mix.getEasyLevel() + mix.getMediumLevel() + mix.getAdvancedLevel());
		game.setTimePerQuestion(request.getTimePerQuestion());
		game.setScorePerQuestion(request.getScorePerQuestion() > 0 ? request.getScorePerQuestion() : 10);
		game.setTopic(summary(topic));
		game.setAutoquestions(copiedPool(topic));
		game.setQuestionCount(count(topic));

		MultiPlayerGame saved = multiGames.save(game);
		events.multiPlayerGameCreated(saved);
		return changed(saved);
	}

	/** The multiplayer game with this id; throws NotFoundException when there is none. */
	public MultiPlayerGame multiPlayerGame(int gameId) {
		return multiGames.findById(gameId).orElseThrow(() -> new NotFoundException("No multiplayer game " + gameId));
	}

	/** Every multiplayer game in full. */
	public List<MultiPlayerGame> multiPlayerGames() {
		return multiGames.findAll();
	}

	/** The multiplayer games built on one topic of a category. */
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

	/**
	 * The questions to copy into a topic's games: none for a topic sent as counts (the engines load its questions
	 * from the question service), otherwise its playable questions as before.
	 */
	static List<Questions> copiedPool(Topic t) {
		return t.getQuestionCount() != null ? null : QuestionSelector.playable(t.getQuestions());
	}

	/** Playable questions per level (easy, medium, advanced), from the counts or the copied questions. */
	static int[] available(Topic t) {
		List<Integer> c = t.getLevelCounts();
		if (t.getQuestionCount() != null && c != null && c.size() == 3) {
			return new int[] { c.get(0), c.get(1), c.get(2) };
		}
		return QuestionSelector.countByLevel(QuestionSelector.playable(t.getQuestions()));
	}

	static int count(Topic t) {
		return t.getQuestionCount() != null ? t.getQuestionCount() : QuestionSelector.playable(t.getQuestions()).size();
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
