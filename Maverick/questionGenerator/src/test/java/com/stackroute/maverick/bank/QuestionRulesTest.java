package com.stackroute.maverick.bank;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.stackroute.maverick.domain.Questions;

class QuestionRulesTest {

	static Questions q(String correct, int level, String... options) {
		Questions q = new Questions();
		q.setQuestionStem("Which?");
		q.setQuestionLevel(level);
		String[] o = { null, null, null, null };
		System.arraycopy(options, 0, o, 0, options.length);
		q.setOption1(o[0]);
		q.setOption2(o[1]);
		q.setOption3(o[2]);
		q.setOption4(o[3]);
		q.setCorrectAnswer(correct);
		return q;
	}

	@Test
	void acceptsValidQuestions() {
		assertThatCode(() -> QuestionRules.validate(q("True", 1, "True", "False"))).doesNotThrowAnyException();
		assertThatCode(() -> QuestionRules.validate(q(" paris", 2, "Paris", "Lyon", "Nice", "Lille")))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsBrokenQuestions() {
		assertThatThrownBy(() -> QuestionRules.validate(q("x", 1, "a", "b"))).hasMessageContaining("one of the options");
		assertThatThrownBy(() -> QuestionRules.validate(q("a", 1, "a"))).hasMessageContaining("two options");
		assertThatThrownBy(() -> QuestionRules.validate(q("a", 4, "a", "b"))).hasMessageContaining("questionLevel");
		assertThatThrownBy(() -> QuestionRules.validate(q("a", 1, "a", "A"))).hasMessageContaining("different");
		Questions noStem = q("a", 1, "a", "b");
		noStem.setQuestionStem(" ");
		assertThatThrownBy(() -> QuestionRules.validate(noStem)).hasMessageContaining("text");
	}
}
