package com.fazeel.quizking.api;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.fazeel.quizking.domain.Category;
import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.domain.GameQuestionLevel;
import com.fazeel.quizking.domain.GameType;
import com.fazeel.quizking.domain.MultiPlayerGame;
import com.fazeel.quizking.domain.Topic;

/**
 * What list endpoints return. A game carries its topic's whole question pool (with answers) for the
 * engines; lists only need the card, so they get the question count instead of the questions.
 */
public final class Summaries {

	private Summaries() {
	}

	public record TopicRef(int topicId, String topicName) {
		static TopicRef of(Topic t) {
			return t == null ? null : new TopicRef(t.getTopicId(), t.getTopicName());
		}
	}

	public record GameSummary(int gameId, String gameName, String gameDescription, String gameImage, String gameRules,
			int categoryId, TopicRef topic, GameType gameType, GameQuestionLevel questionLevels, int questionCount,
			String createdBy, Date createdOn) {

		static GameSummary of(Game g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), TopicRef.of(g.getTopic()), g.getGameType(),
					g.getQuestionLevels(), g.getQuestions() == null ? 0 : g.getQuestions().size(), g.getCreatedBy(),
					g.getCreatedOn());
		}

		static GameSummary of(MultiPlayerGame g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), TopicRef.of(g.getTopic()), g.getGameType(),
					g.getQuestionLevel(), g.getAutoquestions() == null ? 0 : g.getAutoquestions().size(),
					g.getCreatedBy(), g.getCreatedOn());
		}
	}

	public record TopicSummary(int topicId, String topicName, int questionCount) {
	}

	public record CategorySummary(int categoryId, String categoryName, String categoryImage, List<TopicSummary> topic) {

		static CategorySummary of(Category c) {
			List<TopicSummary> topics = Objects.requireNonNullElse(c.getTopic(), List.<Topic>of()).stream()
					.filter(Objects::nonNull)
					.map(t -> new TopicSummary(t.getTopicId(), t.getTopicName(),
							t.getQuestions() == null ? 0 : t.getQuestions().size()))
					.toList();
			return new CategorySummary(c.getCategoryId(), c.getCategoryName(), c.getCategoryImage(), topics);
		}
	}
}
