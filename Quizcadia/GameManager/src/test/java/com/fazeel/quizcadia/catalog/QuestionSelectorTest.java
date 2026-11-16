package com.fazeel.quizcadia.catalog;

import static com.fazeel.quizcadia.catalog.Fixtures.bank;
import static com.fazeel.quizcadia.catalog.Fixtures.q;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.Questions;

class QuestionSelectorTest {

	private final QuestionSelector selector = new QuestionSelector();

	@Test
	void poolIsTheWholePlayableTopicSoEnginesCanPickFreshQuestionsEachTime() {
		List<Questions> pool = selector.pool(bank("manual", 5, 5, 5), new GameQuestionLevel(2, 3, 1));

		assertThat(pool).hasSize(15);
	}

	@Test
	void questionsOfEveryTypeArePlayable() {
		List<Questions> bank = new ArrayList<>(bank("Auto", 2, 0, 0));
		bank.add(q(90, 1, "imported"));
		bank.add(q(91, 1, null));

		assertThat(selector.pool(bank, new GameQuestionLevel(4, 0, 0))).hasSize(4);
	}

	@Test
	void refusesAMixThePoolCannotSatisfy() {
		assertThatThrownBy(() -> selector.pool(bank("manual", 1, 0, 0), new GameQuestionLevel(0, 0, 2)))
				.isInstanceOf(NotEnoughQuestionsException.class)
				.hasMessage("Asked for 2 advanced questions but the topic only has 0");
	}

	@Test
	void unplayableQuestionsAreLeftOut() {
		Questions broken = q(1, 1, "manual");
		broken.setCorrectAnswer("not an option");

		assertThat(QuestionSelector.playable(List.of(broken, q(2, 1, "manual")))).extracting(Questions::getQuestionId)
				.containsExactly(2);
	}

	@Test
	void emptyOrNegativeMixIsInvalid() {
		assertThatThrownBy(() -> selector.pool(bank("manual", 3, 0, 0), new GameQuestionLevel(0, 0, 0)))
				.isInstanceOf(InvalidGameException.class);
		assertThatThrownBy(() -> selector.pool(bank("manual", 3, 0, 0), new GameQuestionLevel(2, -1, 0)))
				.isInstanceOf(InvalidGameException.class);
		assertThatThrownBy(() -> selector.pool(bank("manual", 3, 0, 0), null)).isInstanceOf(InvalidGameException.class);
	}

	@Test
	void countsQuestionsPerLevel() {
		assertThat(QuestionSelector.countByLevel(bank("x", 2, 1, 4))).containsExactly(2, 1, 4);
	}

	@Test
	void autoGameMixMovesShortfallToOtherLevels() {
		assertThat(AutoGames.fit(10, new int[] { 4, 3, 3 }, new int[] { 50, 50, 50 })).containsExactly(4, 3, 3);
		assertThat(AutoGames.fit(10, new int[] { 4, 3, 3 }, new int[] { 20, 1, 0 })).containsExactly(9, 1, 0);
		assertThat(AutoGames.fit(10, new int[] { 4, 3, 3 }, new int[] { 2, 2, 2 })).containsExactly(2, 2, 2);
	}
}
