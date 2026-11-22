package com.fazeel.quizcadia.bank;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import java.util.List;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;

/**
 * How many questions each topic has, counted by MongoDB itself. Reading the categories instead loads every question
 * with its answers (megabytes) only to count them, and the welcome page asks for these counts on every visit.
 */
public final class TopicCounts {

	/** One topic's count; serialised as {@code {category, topic, questions}}, the shape the app reads. */
	public record TopicCount(String category, String topic, int questions) {
	}

	private TopicCounts() {
	}

	/** Every topic's question count, in one aggregation over the categories. */
	public static List<TopicCount> read(MongoOperations mongo) {
		var counts = newAggregation(
				unwind("topic"),
				project()
						.and("categoryName").as("category")
						.and("topic.topicName").as("topic")
						.and(ArrayOperators.Size.lengthOfArray(
								ConditionalOperators.ifNull("topic.questions").then(List.of()))).as("questions")
						.andExclude("_id"));
		return mongo.aggregate(counts, "category", TopicCount.class).getMappedResults();
	}
}
