package com.fazeel.quizcadia.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.GameType;

/**
 * What the game lists need, read without the questions. Games and categories embed whole question pools (and a
 * game stores its topic's pool a second time), so loading them in full to show names and counts would read every
 * question in the bank; these queries let the database count instead.
 */
@Component
public class CatalogCards {

	public record TopicCard(int topicId, String topicName, int questionCount) {
	}

	public record CategoryCard(int categoryId, String categoryName, String categoryImage, String countryCode,
			List<TopicCard> topics) {
	}

	public record GameCard(int gameId, String gameName, String gameDescription, String gameImage, String gameRules,
			int categoryId, Integer topicId, String topicName, GameType gameType, GameQuestionLevel questionLevels,
			int questionCount, String createdBy, Date createdOn) {
	}

	private final MongoOperations mongo;

	public CatalogCards(MongoOperations mongo) {
		this.mongo = mongo;
	}

	public List<GameCard> games() {
		return games("game", "questionLevels", "questions");
	}

	public List<GameCard> multiPlayerGames() {
		return games("multiplayergame", "questionLevel", "autoquestions");
	}

	public List<CategoryCard> categories() {
		AggregationOperation project = ctx -> new Document("$project", new Document("categoryName", 1)
				.append("categoryImage", 1).append("countryCode", 1)
				.append("topic", new Document("$map", new Document("input", new Document("$ifNull", List.of("$topic", List.of())))
						.append("as", "t")
						.append("in", new Document("topicId", "$$t.topicId").append("topicName", "$$t.topicName")
								.append("questionCount", countOr("$$t.questionCount", "$$t.questions"))))));
		List<CategoryCard> out = new ArrayList<>();
		for (Document d : mongo.aggregate(Aggregation.newAggregation(project), "category", Document.class)) {
			List<TopicCard> topics = new ArrayList<>();
			for (Document t : d.getList("topic", Document.class, List.of())) {
				if (t.getString("topicName") != null) {
					topics.add(new TopicCard(intOf(t.get("topicId")), t.getString("topicName"), intOf(t.get("questionCount"))));
				}
			}
			out.add(new CategoryCard(intOf(d.get("_id")), d.getString("categoryName"), d.getString("categoryImage"),
					d.getString("countryCode"), topics));
		}
		return out;
	}

	private List<GameCard> games(String collection, String levelsField, String questionsField) {
		AggregationOperation project = ctx -> new Document("$project", new Document("gameName", 1)
				.append("gameDescription", 1).append("gameImage", 1).append("gameRules", 1).append("categoryId", 1)
				.append("topicId", "$topic.topicId").append("topicName", "$topic.topicName").append("gameType", 1)
				.append("levels", "$" + levelsField).append("createdBy", 1).append("createdOn", 1)
				.append("questionCount", countOr("$questionCount", "$" + questionsField)));
		List<GameCard> out = new ArrayList<>();
		for (Document d : mongo.aggregate(Aggregation.newAggregation(project), collection, Document.class)) {
			Document type = d.get("gameType", Document.class);
			Document levels = d.get("levels", Document.class);
			Object topicId = d.get("topicId");
			out.add(new GameCard(intOf(d.get("_id")), d.getString("gameName"), d.getString("gameDescription"),
					d.getString("gameImage"), d.getString("gameRules"), intOf(d.get("categoryId")),
					topicId == null ? null : intOf(topicId), d.getString("topicName"),
					type == null ? null : mongo.getConverter().read(GameType.class, type),
					levels == null ? null : mongo.getConverter().read(GameQuestionLevel.class, levels),
					intOf(d.get("questionCount")), d.getString("createdBy"), d.getDate("createdOn")));
		}
		return out;
	}

	/** The stored count when there is one (questions kept in the question service), else the copied list's size. */
	private static Document countOr(String countField, String arrayField) {
		return new Document("$ifNull", List.of(countField, size(arrayField)));
	}

	private static Document size(String arrayField) {
		return new Document("$size", new Document("$ifNull", List.of(arrayField, List.of())));
	}

	private static int intOf(Object o) {
		return o instanceof Number n ? n.intValue() : 0;
	}
}
