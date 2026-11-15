package com.fazeel.maverick.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.fazeel.maverick.domain.Questions;

/**
 * Builds multiple-choice questions from facts. Each question gets three distractors taken from the
 * answers of other facts, never equal to the correct answer or to each other, and the correct answer
 * is placed at a random position. (The old generator always put the answer in option 3, could pick
 * the answer itself as a distractor, and indexed outside the result list.)
 */
public class FactQuestionGenerator {

	public static final int OPTIONS = 4;
	/** Generated ids live above this so they never collide with hand-written questions. */
	static final int GENERATED_ID_BASE = 1_000_000_000;

	private final RandomGenerator random;

	public FactQuestionGenerator(RandomGenerator random) {
		this.random = random;
	}

	public List<Questions> generate(QuestionTemplate template, List<Fact> facts, int maxQuestions) {
		Map<String, Fact> bySubject = new LinkedHashMap<>();
		for (Fact f : facts) {
			if (f != null && !blank(f.subject()) && !blank(f.answer()) && !f.subject().equalsIgnoreCase(f.answer())) {
				bySubject.putIfAbsent(key(f.subject()), new Fact(f.subject().trim(), f.answer().trim()));
			}
		}
		List<String> answers = bySubject.values().stream().map(Fact::answer).distinct().toList();
		List<Fact> pool = new ArrayList<>(bySubject.values());
		Collections.shuffle(pool, random);

		List<Questions> questions = new ArrayList<>();
		for (Fact fact : pool) {
			if (questions.size() >= maxQuestions) {
				break;
			}
			List<String> distractors = new ArrayList<>(answers.stream()
					.filter(a -> !a.equalsIgnoreCase(fact.answer())).toList());
			if (distractors.size() < OPTIONS - 1) {
				continue;
			}
			Collections.shuffle(distractors, random);
			List<String> options = new ArrayList<>(distractors.subList(0, OPTIONS - 1));
			options.add(random.nextInt(OPTIONS), fact.answer());

			Questions q = new Questions();
			q.setQuestionId(generatedId(template, fact));
			q.setQuestionLevel(template.level());
			q.setQuestionType("Auto");
			q.setQuestionStem(template.stemFormat().formatted(fact.subject()));
			q.setOption1(options.get(0));
			q.setOption2(options.get(1));
			q.setOption3(options.get(2));
			q.setOption4(options.get(3));
			q.setCorrectAnswer(fact.answer());
			questions.add(q);
		}
		return questions;
	}

	/** Same template and subject always give the same id, so regenerating updates instead of duplicating. */
	static int generatedId(QuestionTemplate template, Fact fact) {
		int h = (template.key() + "|" + key(fact.subject())).hashCode();
		return GENERATED_ID_BASE + Math.floorMod(h, Integer.MAX_VALUE - GENERATED_ID_BASE);
	}

	private static String key(String s) {
		return s.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
