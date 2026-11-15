package com.stackroute.maverick.catalog;

import java.util.ArrayList;
import java.util.List;

import com.stackroute.maverick.domain.Category;
import com.stackroute.maverick.domain.Questions;
import com.stackroute.maverick.domain.Topic;

final class Fixtures {

	private Fixtures() {
	}

	static Questions q(int id, int level, String type) {
		Questions q = new Questions();
		q.setQuestionId(id);
		q.setQuestionLevel(level);
		q.setQuestionType(type);
		q.setQuestionStem("Question " + id);
		q.setOption1("right");
		q.setOption2("wrong");
		q.setCorrectAnswer("right");
		return q;
	}

	static List<Questions> bank(String type, int easy, int medium, int advanced) {
		List<Questions> qs = new ArrayList<>();
		int id = 1;
		for (int i = 0; i < easy; i++) {
			qs.add(q(id++, 1, type));
		}
		for (int i = 0; i < medium; i++) {
			qs.add(q(id++, 2, type));
		}
		for (int i = 0; i < advanced; i++) {
			qs.add(q(id++, 3, type));
		}
		return qs;
	}

	static Category category(int id, String topicName, List<Questions> questions) {
		Topic t = new Topic();
		t.setTopicId(id * 10);
		t.setTopicName(topicName);
		t.setQuestions(new ArrayList<>(questions));
		Category c = new Category();
		c.setCategoryId(id);
		c.setCategoryName("Category " + id);
		c.setTopic(new ArrayList<>(List.of(t)));
		return c;
	}
}
