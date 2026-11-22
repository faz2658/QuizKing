package com.fazeel.quizcadia.api;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.fazeel.quizcadia.catalog.CatalogCards;
import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.GameType;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.domain.Topic;

/**
 * What list endpoints return. A game carries its topic's whole question pool (with answers) for the
 * engines; lists only need the card, so they get the question count instead of the questions.
 */
public final class Summaries {

	private Summaries() {
	}

	/** Which topic a game is built on (id and name), without its questions. */
	public record TopicRef(int topicId, String topicName) {
		static TopicRef of(Topic t) {
			return t == null ? null : new TopicRef(t.getTopicId(), t.getTopicName());
		}
	}

	/**
	 * A game as the catalog lists it: what players see on a card, plus its country (null for shared world trivia).
	 * Never any questions.
	 */
	public record GameSummary(int gameId, String gameName, String gameDescription, String gameImage, String gameRules,
			int categoryId, TopicRef topic, GameType gameType, GameQuestionLevel questionLevels, int questionCount,
			String createdBy, Date createdOn, String country) {

		/** From a list query (no questions loaded); {@code country} is the game's country set, null for world trivia. */
		static GameSummary of(CatalogCards.GameCard g, String country) {
			return new GameSummary(g.gameId(), g.gameName(), g.gameDescription(), g.gameImage(), g.gameRules(),
					g.categoryId(), g.topicName() == null ? null : new TopicRef(g.topicId() == null ? 0 : g.topicId(), g.topicName()),
					g.gameType(), g.questionLevels(), g.questionCount(), g.createdBy(), g.createdOn(), country);
		}

		static GameSummary of(Game g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), TopicRef.of(g.getTopic()), g.getGameType(),
					g.getQuestionLevels(), g.getQuestionCount() != null ? g.getQuestionCount()
							: g.getQuestions() == null ? 0 : g.getQuestions().size(), g.getCreatedBy(),
					g.getCreatedOn(), null);
		}

		static GameSummary of(MultiPlayerGame g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), TopicRef.of(g.getTopic()), g.getGameType(),
					g.getQuestionLevel(), g.getQuestionCount() != null ? g.getQuestionCount()
							: g.getAutoquestions() == null ? 0 : g.getAutoquestions().size(),
					g.getCreatedBy(), g.getCreatedOn(), null);
		}
	}

	/** A topic in a category listing, with its question count instead of its questions. */
	public record TopicSummary(int topicId, String topicName, int questionCount) {
	}

	/** A category in the catalog with its topics' counts, and its country for a country's own question set. */
	public record CategorySummary(int categoryId, String categoryName, String categoryImage, List<TopicSummary> topic,
			String countryCode) {

		static CategorySummary of(CatalogCards.CategoryCard c) {
			return new CategorySummary(c.categoryId(), c.categoryName(), c.categoryImage(), c.topics().stream()
					.map(t -> new TopicSummary(t.topicId(), t.topicName(), t.questionCount())).toList(), c.countryCode());
		}

		static CategorySummary of(Category c) {
			List<TopicSummary> topics = Objects.requireNonNullElse(c.getTopic(), List.<Topic>of()).stream()
					.filter(Objects::nonNull)
					.map(t -> new TopicSummary(t.getTopicId(), t.getTopicName(), t.getQuestionCount() != null
							? t.getQuestionCount() : t.getQuestions() == null ? 0 : t.getQuestions().size()))
					.toList();
			return new CategorySummary(c.getCategoryId(), c.getCategoryName(), c.getCategoryImage(), topics,
					c.getCountryCode());
		}
	}

	/** A country with its own question set, for the International page. */
	public record CountrySummary(String country, int categoryId, String categoryName, int games, int questions) {
	}
}
