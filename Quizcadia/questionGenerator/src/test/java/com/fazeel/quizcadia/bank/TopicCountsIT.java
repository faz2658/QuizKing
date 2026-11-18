package com.fazeel.quizcadia.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import com.fazeel.quizcadia.bank.TopicCounts.TopicCount;
import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.mongodb.client.MongoClients;

/** The counts MongoDB works out must match counting the loaded questions. Skipped when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class TopicCountsIT {

	@Container
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

	static Topic topic(String name, Integer questions) {
		Topic t = new Topic();
		t.setTopicName(name);
		if (questions != null) {
			List<Questions> qs = new ArrayList<>();
			for (int i = 0; i < questions; i++) {
				Questions q = new Questions();
				q.setQuestionId(i);
				qs.add(q);
			}
			t.setQuestions(qs);
		}
		return t;
	}

	static Category category(int id, String name, List<Topic> topics) {
		Category c = new Category();
		c.setCategoryId(id);
		c.setCategoryName(name);
		c.setTopic(topics);
		return c;
	}

	@Test
	void countsEachTopicsQuestionsInTheDatabase() {
		MongoTemplate template = new MongoTemplate(MongoClients.create(mongo.getConnectionString()), "bank");
		template.save(category(1, "Film", List.of(topic("Oscars", 3), topic("Directors", 0))));
		template.save(category(2, "Science", List.of(topic("Space", 2), topic("Empty", null))));
		template.save(category(3, "No topics yet", null));

		assertThat(TopicCounts.read(template)).containsExactly(
				new TopicCount("Film", "Oscars", 3),
				new TopicCount("Film", "Directors", 0),
				new TopicCount("Science", "Space", 2),
				new TopicCount("Science", "Empty", 0));
	}
}
