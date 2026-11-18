package com.fazeel.quizcadia.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Questions;

class QuestionMixTest {

	private static int nextId = 1;

	private static Questions q(int level) {
		int id = nextId++;
		return new Questions(id, level, "Question " + id + "?", "pack", "Right " + id, "Wrong", "Other", "Nope",
				"Right " + id);
	}

	/** {@code perLevel} questions of each level in each of {@code topics} topics. */
	private static Map<Integer, List<Questions>> bank(int topics, int perLevel) {
		Map<Integer, List<Questions>> bank = new HashMap<>();
		for (int t = 1; t <= topics; t++) {
			List<Questions> qs = new ArrayList<>();
			for (int level = 1; level <= 3; level++) {
				for (int i = 0; i < perLevel; i++) {
					qs.add(q(level));
				}
			}
			bank.put(t, qs);
		}
		return bank;
	}

	private static Map<Integer, Integer> topicOf(Map<Integer, List<Questions>> bank) {
		Map<Integer, Integer> topicOf = new HashMap<>();
		bank.forEach((t, qs) -> qs.forEach(q -> topicOf.put(q.getQuestionId(), t)));
		return topicOf;
	}

	@Test
	void picksTwentyMediumQuestionsWithoutRepeats() {
		Map<Integer, List<Questions>> bank = bank(5, 30);

		List<Questions> quiz = QuestionMix.pick(bank, QuestionMix.MEDIUM, 20, new SplittableRandom(1));

		assertThat(quiz).hasSize(20).allMatch(q -> q.getQuestionLevel() == QuestionMix.MEDIUM);
		assertThat(quiz.stream().map(Questions::getQuestionId).distinct()).hasSize(20);
	}

	@Test
	void spreadsTheQuizOverEveryTopic() {
		Map<Integer, List<Questions>> bank = bank(24, 10);
		bank.get(7).addAll(IntStream.range(0, 500).mapToObj(i -> q(2)).toList()); // one huge topic

		List<Questions> quiz = QuestionMix.pick(bank, QuestionMix.MEDIUM, 20, new SplittableRandom(2));

		Map<Integer, Integer> topicOf = topicOf(bank);
		// 24 topics take turns, so 20 questions come from 20 different topics
		assertThat(quiz.stream().map(q -> topicOf.get(q.getQuestionId())).distinct()).hasSize(20);
	}

	@Test
	void fewTopicsStillFillTheQuizRoundRobin() {
		Map<Integer, List<Questions>> bank = bank(3, 10);

		List<Questions> quiz = QuestionMix.pick(bank, QuestionMix.MEDIUM, 20, new SplittableRandom(3));

		Map<Integer, Long> perTopic = quiz.stream()
				.collect(Collectors.groupingBy(q -> topicOf(bank).get(q.getQuestionId()), Collectors.counting()));
		assertThat(quiz).hasSize(20);
		assertThat(perTopic.values()).allMatch(n -> n >= 6 && n <= 7);
	}

	@Test
	void differentRandomnessGivesADifferentQuiz() {
		Map<Integer, List<Questions>> bank = bank(10, 40);

		List<Integer> a = QuestionMix.pick(bank, 2, 20, new SplittableRandom(4)).stream().map(Questions::getQuestionId).toList();
		List<Integer> b = QuestionMix.pick(bank, 2, 20, new SplittableRandom(5)).stream().map(Questions::getQuestionId).toList();
		List<Integer> again = QuestionMix.pick(bank, 2, 20, new SplittableRandom(4)).stream().map(Questions::getQuestionId).toList();

		assertThat(a).isNotEqualTo(b);
		assertThat(again).isEqualTo(a);
	}

	@Test
	void skipsCountryUnplayableAndDuplicateQuestions() {
		Questions good = q(2);
		Questions country = q(2);
		country.setCountry("IN");
		Questions noAnswer = q(2);
		noAnswer.setCorrectAnswer("Not an option");
		Questions copy = new Questions(good.getQuestionId(), 2, good.getQuestionStem(), "pack", "Right", "Wrong", null,
				null, "Right");
		Questions sameStem = new Questions(9999, 2, " " + good.getQuestionStem().toUpperCase() + " ", "pack", "Right",
				"Wrong", null, null, "Right");
		Map<Integer, List<Questions>> bank = Map.of(1, List.of(good, country, noAnswer), 2, List.of(copy, sameStem));

		List<Questions> quiz = QuestionMix.pick(bank, 2, 20, new SplittableRandom(6));

		assertThat(quiz).hasSize(1);
		assertThat(quiz.get(0).getQuestionStem().trim()).isEqualToIgnoringCase(good.getQuestionStem());
	}

	@Test
	void anEmptyBankGivesNoQuestions() {
		assertThat(QuestionMix.pick(Map.of(), 2, 20, new SplittableRandom(7))).isEmpty();
		assertThat(QuestionMix.pick(bank(2, 5), 2, 0, new SplittableRandom(7))).isEmpty();
	}
}
