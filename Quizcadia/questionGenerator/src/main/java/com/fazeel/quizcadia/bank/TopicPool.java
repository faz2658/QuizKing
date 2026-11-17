package com.fazeel.quizcadia.bank;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;

/**
 * A topic's questions as the games see them. The bank is the only place that holds them: other services get a
 * topic's counts in messages and load its questions from {@code /internal/topics/{id}/questions} when a game starts,
 * so no message or document has to carry a whole topic.
 */
public final class TopicPool {

	private TopicPool() {
	}

	/** Same rule as the game engines: a stem, two or more options, and the answer among them. */
	public static boolean playable(Questions q) {
		if (q == null || blank(q.getQuestionStem()) || blank(q.getCorrectAnswer())) {
			return false;
		}
		List<String> options = Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4())
				.filter(o -> !blank(o)).map(String::trim).toList();
		return options.size() >= 2 && options.stream().anyMatch(o -> o.equalsIgnoreCase(q.getCorrectAnswer().trim()));
	}

	public static List<Questions> playable(Topic t) {
		return t == null || t.getQuestions() == null ? List.of()
				: t.getQuestions().stream().filter(TopicPool::playable).toList();
	}

	/** The category as it goes out in messages: every topic with its counts, none with its questions. */
	public static Category slim(Category c) {
		Category out = new Category();
		out.setCategoryId(c.getCategoryId());
		out.setCategoryName(c.getCategoryName());
		out.setCategoryImage(c.getCategoryImage());
		out.setCountryCode(c.getCountryCode());
		out.setTopic(Objects.requireNonNullElse(c.getTopic(), List.<Topic>of()).stream().filter(Objects::nonNull)
				.map(TopicPool::slim).toList());
		return out;
	}

	static Topic slim(Topic t) {
		List<Questions> pool = playable(t);
		Topic out = new Topic();
		out.setTopicId(t.getTopicId());
		out.setTopicName(t.getTopicName());
		out.setTopicImage(t.getTopicImage());
		out.setQuestionCount(pool.size());
		int[] levels = new int[3];
		pool.forEach(q -> {
			if (q.getQuestionLevel() >= 1 && q.getQuestionLevel() <= 3) {
				levels[q.getQuestionLevel() - 1]++;
			}
		});
		out.setLevelCounts(List.of(levels[0], levels[1], levels[2]));
		return out;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
