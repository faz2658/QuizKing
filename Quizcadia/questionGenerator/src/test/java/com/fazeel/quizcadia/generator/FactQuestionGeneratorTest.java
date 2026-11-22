package com.fazeel.quizcadia.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.bank.QuestionRules;
import com.fazeel.quizcadia.domain.Questions;

class FactQuestionGeneratorTest {

	static final List<Fact> CAPITALS = List.of(new Fact("France", "Paris"), new Fact("Japan", "Tokyo"),
			new Fact("Peru", "Lima"), new Fact("Kenya", "Nairobi"), new Fact("Chile", "Santiago"),
			new Fact("Egypt", "Cairo"));

	final FactQuestionGenerator generator = new FactQuestionGenerator(new Random(4));

	static List<String> options(Questions q) {
		return Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4()).toList();
	}

	@Test
	void everyGeneratedQuestionIsValidWithFourDistinctOptions() {
		List<Questions> qs = generator.generate(QuestionTemplate.CAPITALS, CAPITALS, 10);

		assertThat(qs).hasSize(6);
		for (Questions q : qs) {
			QuestionRules.validate(q);
			assertThat(options(q)).doesNotHaveDuplicates().contains(q.getCorrectAnswer());
			assertThat(q.getQuestionType()).isEqualTo("Auto");
			assertThat(q.getQuestionStem()).startsWith("What is the capital of ");
		}
	}

	@Test
	void correctAnswerIsNotAlwaysInTheSamePosition() {
		Set<Integer> positions = new HashSet<>();
		for (int seed = 0; seed < 10; seed++) {
			for (Questions q : new FactQuestionGenerator(new Random(seed)).generate(QuestionTemplate.CAPITALS,
					CAPITALS, 6)) {
				positions.add(options(q).indexOf(q.getCorrectAnswer()));
			}
		}
		assertThat(positions).containsExactlyInAnyOrder(0, 1, 2, 3);
	}

	@Test
	void notEnoughDistinctAnswersMeansNoQuestion() {
		List<Fact> few = List.of(new Fact("A", "x"), new Fact("B", "y"), new Fact("C", "x"));

		assertThat(generator.generate(QuestionTemplate.CAPITALS, few, 10)).isEmpty();
	}

	@Test
	void duplicatesAndJunkAreIgnoredAndCountIsRespected() {
		List<Fact> facts = new ArrayList<>(CAPITALS);
		facts.add(new Fact("france", "Paris"));
		facts.add(new Fact(" ", "x"));
		facts.add(null);

		assertThat(generator.generate(QuestionTemplate.CAPITALS, facts, 100)).hasSize(6);
		assertThat(generator.generate(QuestionTemplate.CAPITALS, facts, 2)).hasSize(2);
	}

	@Test
	void idsAreStableAndOutsideTheManualRange() {
		int a = FactQuestionGenerator.generatedId(QuestionTemplate.CAPITALS, new Fact("France", "Paris"));
		int b = FactQuestionGenerator.generatedId(QuestionTemplate.CAPITALS, new Fact(" france ", "Paris"));
		int c = FactQuestionGenerator.generatedId(QuestionTemplate.CURRENCIES, new Fact("France", "Euro"));

		assertThat(a).isEqualTo(b).isGreaterThanOrEqualTo(FactQuestionGenerator.GENERATED_ID_BASE);
		assertThat(c).isNotEqualTo(a);
	}

	@Test
	void templateLevelIsUsed() {
		assertThat(generator.generate(QuestionTemplate.US_PRESIDENT_BIRTH_YEAR,
				List.of(new Fact("A", "1732"), new Fact("B", "1735"), new Fact("C", "1743"), new Fact("D", "1751")), 1))
				.singleElement().extracting(Questions::getQuestionLevel).isEqualTo(3);
	}
}
