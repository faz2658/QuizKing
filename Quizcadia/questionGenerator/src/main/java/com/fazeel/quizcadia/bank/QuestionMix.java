package com.fazeel.quizcadia.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.fazeel.quizcadia.domain.Questions;

/**
 * Picks a quiz from every topic at once, for the live room quiz: only playable general questions (no country ones,
 * since a room mixes people from anywhere) of one level, no question twice, spread over the topics. Topics take turns
 * in a random order and each gives a random question, so a big topic (Entertainment has ten) doesn't crowd out small
 * ones; when topics run dry the rest come from those that still have questions.
 */
public final class QuestionMix {

	public static final int EASY = 1;
	public static final int MEDIUM = 2;
	public static final int ADVANCED = 3;

	private QuestionMix() {
	}

	/**
	 * @param byTopic every topic's questions, by topic id (any order; filtered here)
	 * @return up to {@code count} questions, in the order to ask them
	 */
	public static List<Questions> pick(Map<Integer, List<Questions>> byTopic, int level, int count,
			RandomGenerator random) {
		Set<Integer> seenIds = new HashSet<>();
		Set<String> seenStems = new HashSet<>();
		Map<Integer, List<Questions>> pools = new LinkedHashMap<>();
		List<Integer> topics = new ArrayList<>(byTopic.keySet());
		Collections.sort(topics); // same input and seed, same quiz
		for (int topic : topics) {
			List<Questions> pool = new ArrayList<>(byTopic.get(topic) == null ? List.of() : byTopic.get(topic).stream()
					.filter(q -> q != null && q.getQuestionLevel() == level && general(q) && TopicPool.playable(q))
					.toList());
			if (!pool.isEmpty()) {
				Collections.shuffle(pool, random(random));
				pools.put(topic, pool);
			}
		}
		List<Integer> order = new ArrayList<>(pools.keySet());
		Collections.shuffle(order, random(random));
		List<Questions> picked = new ArrayList<>();
		while (picked.size() < count && !order.isEmpty()) {
			for (Iterator<Integer> it = order.iterator(); it.hasNext() && picked.size() < count;) {
				List<Questions> pool = pools.get(it.next());
				Questions next = null;
				while (!pool.isEmpty() && next == null) {
					Questions q = pool.remove(pool.size() - 1);
					// the same question can sit in two topics (imports); ask it once
					if (seenIds.add(q.getQuestionId()) && seenStems.add(stem(q))) {
						next = q;
					}
				}
				if (next == null) {
					it.remove();
				} else {
					picked.add(next);
				}
			}
		}
		return picked;
	}

	private static boolean general(Questions q) {
		return q.getCountry() == null || q.getCountry().isBlank();
	}

	private static String stem(Questions q) {
		return q.getQuestionStem().trim().toLowerCase(Locale.ROOT);
	}

	private static Random random(RandomGenerator random) {
		return new Random(random.nextLong());
	}
}
