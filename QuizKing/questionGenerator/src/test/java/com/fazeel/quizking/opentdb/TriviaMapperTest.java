package com.fazeel.quizking.opentdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.bank.QuestionRules;
import com.fazeel.quizking.domain.Questions;
import com.fazeel.quizking.opentdb.TriviaMapper.Placement;

class TriviaMapperTest {

	final TriviaMapper mapper = new TriviaMapper(new Random(5));

	static TriviaQuestion multiple(String category, String difficulty) {
		return new TriviaQuestion("multiple", difficulty, category, "Which planet is red?", "Mars",
				List.of("Venus", "Jupiter", "Saturn"));
	}

	@Test
	void multipleChoiceBecomesAValidFourOptionQuestion() {
		Questions q = mapper.map(multiple("Science & Nature", "medium")).orElseThrow();

		QuestionRules.validate(q);
		assertThat(Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4()))
				.containsExactlyInAnyOrder("Mars", "Venus", "Jupiter", "Saturn");
		assertThat(q.getQuestionLevel()).isEqualTo(2);
		assertThat(q.getQuestionType()).isEqualTo("imported");
	}

	@Test
	void trueFalseKeepsTrueThenFalse() {
		Questions q = mapper.map(new TriviaQuestion("boolean", "easy", "History", "The sky is blue.", "True",
				List.of("False"))).orElseThrow();

		assertThat(q.getOption1()).isEqualTo("True");
		assertThat(q.getOption2()).isEqualTo("False");
		assertThat(q.getOption3()).isNull();
		assertThat(q.getQuestionLevel()).isEqualTo(1);
	}

	@Test
	void difficultyMapsToLevels() {
		assertThat(TriviaMapper.level("easy")).isEqualTo(1);
		assertThat(TriviaMapper.level("medium")).isEqualTo(2);
		assertThat(TriviaMapper.level("hard")).isEqualTo(3);
		assertThat(TriviaMapper.level(null)).isEqualTo(1);
	}

	@Test
	void categoryAndTopicComeFromTheOpenTriviaCategory() {
		assertThat(mapper.placement(multiple("Entertainment: Video Games", "easy")))
				.isEqualTo(new Placement("Entertainment", "Video Games"));
		assertThat(mapper.placement(multiple("Geography", "easy"))).isEqualTo(new Placement("Geography", "Geography"));
		assertThat(mapper.placement(multiple(null, "easy")).category()).isEqualTo("General Knowledge");
	}

	@Test
	void idsAreStableAndInTheImportedRange() {
		int a = TriviaMapper.idFor("Which planet is red?");
		assertThat(a).isEqualTo(TriviaMapper.idFor("  which planet is RED? "))
				.isGreaterThanOrEqualTo(TriviaMapper.IMPORTED_ID_BASE);
	}

	@Test
	void incompleteQuestionsAreDropped() {
		assertThat(mapper.map(new TriviaQuestion("multiple", "easy", "X", "Q?", "A", List.of()))).isEmpty();
		assertThat(mapper.map(new TriviaQuestion("multiple", "easy", "X", null, "A", List.of("B")))).isEmpty();
	}
}
