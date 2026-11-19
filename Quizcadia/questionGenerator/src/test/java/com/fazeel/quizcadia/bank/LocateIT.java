package com.fazeel.quizcadia.bank;

import static com.fazeel.quizcadia.bank.TopicCountsIT.category;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.mongodb.client.MongoClients;

/** Feedback finds one question by id: by index, returning just that question. Skipped when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class LocateIT {

	@Container
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

	static Topic topic(int topicId, String name, int firstId, int count) {
		Topic t = new Topic();
		t.setTopicId(topicId);
		t.setTopicName(name);
		t.setQuestions(java.util.stream.IntStream.range(firstId, firstId + count).mapToObj(id -> {
			Questions q = new Questions(id, 2, "Question " + id + "?", "mcq", "A" + id, "B", "C", "D", "A" + id);
			q.setExplanation("Because " + id);
			return q;
		}).toList());
		return t;
	}

	@Test
	void findsTheQuestionWithItsCategoryAndTopicByIndex() {
		MongoTemplate template = new MongoTemplate(MongoClients.create(mongo.getConnectionString()), "bank");
		template.indexOps(Category.class).createIndex(new Index("topic.questions.questionId", org.springframework.data.domain.Sort.Direction.ASC));
		template.save(category(1, "Film", List.of(topic(10, "Oscars", 100, 50), topic(11, "Directors", 200, 50))));
		template.save(category(2, "Science", List.of(topic(20, "Space", 300, 50))));
		QuestionBankService bank = new QuestionBankService(null, template, null, null, null);

		var found = bank.locate(215).orElseThrow();

		assertThat(found.categoryId()).isEqualTo(1);
		assertThat(found.topicName()).isEqualTo("Directors");
		assertThat(found.question().getQuestionId()).isEqualTo(215);
		assertThat(found.question().getQuestionStem()).isEqualTo("Question 215?");
		assertThat(found.question().getCorrectAnswer()).isEqualTo("A215");
		assertThat(found.question().getExplanation()).isEqualTo("Because 215");
		assertThat(bank.locate(999)).isEmpty();

		// MongoDB answers it from the index instead of reading every category
		Document plan = template.getDb().getCollection("category")
				.find(new Document("topic.questions.questionId", 215)).explain();
		assertThat(plan.toJson()).contains("IXSCAN").contains("topic.questions.questionId");
	}
}
