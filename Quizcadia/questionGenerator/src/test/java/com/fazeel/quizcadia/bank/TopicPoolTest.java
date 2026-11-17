package com.fazeel.quizcadia.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;

class TopicPoolTest {

	static Questions q(int id, int level, String answer) {
		return new Questions(id, level, "Q" + id + "?", "pack", "a", "b", "c", "d", answer);
	}

	@Test
	void messagesCarryCountsNotQuestions() {
		List<Questions> qs = new ArrayList<>(List.of(q(1, 1, "a"), q(2, 1, "b"), q(3, 2, "c"), q(4, 3, "d"),
				q(5, 3, "not an option")));
		Topic history = new Topic(7, "History", null, qs);
		Category c = new Category();
		c.setCategoryId(3);
		c.setCategoryName("History");
		c.setTopic(List.of(history));

		Category slim = TopicPool.slim(c);

		Topic t = slim.getTopic().get(0);
		assertThat(t.getQuestions()).isNull();
		assertThat(t.getTopicId()).isEqualTo(7);
		assertThat(t.getQuestionCount()).isEqualTo(4); // the unplayable one is not counted
		assertThat(t.getLevelCounts()).containsExactly(2, 1, 1);
		assertThat(c.getTopic().get(0).getQuestions()).hasSize(5); // the bank's own copy is untouched
	}

	@Test
	void aTopicsPoolIsItsPlayableQuestions() {
		Topic t = new Topic(7, "History", null, List.of(q(1, 1, "a"), q(2, 1, "zzz")));

		assertThat(TopicPool.playable(t)).extracting(Questions::getQuestionId).containsExactly(1);
		assertThat(TopicPool.playable((Topic) null)).isEmpty();
	}
}
