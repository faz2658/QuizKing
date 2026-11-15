package com.fazeel.maverick.catalog;

import static com.fazeel.maverick.catalog.Fixtures.bank;
import static com.fazeel.maverick.catalog.Fixtures.q;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fazeel.maverick.catalog.QuestionSelector.Source;
import com.fazeel.maverick.domain.GameQuestionLevel;
import com.fazeel.maverick.domain.Questions;

class QuestionSelectorTest {

	private final QuestionSelector selector = new QuestionSelector(new Random(11));

	@Test
	void selectsExactlyTheRequestedMix() {
		List<Questions> picked = selector.select(bank("manual", 5, 5, 5), new GameQuestionLevel(2, 3, 1),
				Source.MANUAL);

		assertThat(picked).extracting(Questions::getQuestionLevel).containsExactly(1, 1, 2, 2, 2, 3);
	}

	@Test
	void selectionIsRandomRatherThanAlwaysTheFirstQuestions() {
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 20; i++) {
			selector.select(bank("manual", 10, 0, 0), new GameQuestionLevel(1, 0, 0), Source.MANUAL)
					.forEach(q -> seen.add(q.getQuestionId()));
		}
		assertThat(seen.size()).isGreaterThan(3);
	}

	@Test
	void questionTypeIsMatchedIgnoringCaseAndMissingTypeMeansManual() {
		List<Questions> bank = new ArrayList<>(bank("Auto", 2, 0, 0));
		bank.add(q(99, 1, null));

		assertThat(selector.select(bank, new GameQuestionLevel(1, 0, 0), Source.MANUAL))
				.extracting(Questions::getQuestionId).containsExactly(99);
		assertThat(selector.select(bank, new GameQuestionLevel(2, 0, 0), Source.AUTO)).hasSize(2);
	}

	@Test
	void refusesToBuildAGameWithFewerQuestionsThanRequested() {
		assertThatThrownBy(
				() -> selector.select(bank("manual", 1, 0, 0), new GameQuestionLevel(0, 0, 2), Source.MANUAL))
				.isInstanceOf(NotEnoughQuestionsException.class)
				.hasMessage("Asked for 2 advanced questions but the topic only has 0");
	}

	@Test
	void unplayableQuestionsDoNotCount() {
		Questions broken = q(1, 1, "manual");
		broken.setCorrectAnswer("not an option");

		assertThatThrownBy(() -> selector.select(List.of(broken), new GameQuestionLevel(1, 0, 0), Source.MANUAL))
				.isInstanceOf(NotEnoughQuestionsException.class);
	}

	@Test
	void emptyOrNegativeMixIsInvalid() {
		assertThatThrownBy(() -> selector.select(bank("manual", 3, 0, 0), new GameQuestionLevel(0, 0, 0),
				Source.MANUAL)).isInstanceOf(InvalidGameException.class);
		assertThatThrownBy(() -> selector.select(bank("manual", 3, 0, 0), new GameQuestionLevel(2, -1, 0),
				Source.MANUAL)).isInstanceOf(InvalidGameException.class);
		assertThatThrownBy(() -> selector.select(bank("manual", 3, 0, 0), null, Source.MANUAL))
				.isInstanceOf(InvalidGameException.class);
	}
}
