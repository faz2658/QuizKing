package com.fazeel.quizcadia.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionRules;
import com.fazeel.quizcadia.domain.Questions;

class PackLoaderTest {

	@Test
	void everyPackQuestionIsPlayableAndEveryTopicCanBecomeAGame() {
		for (String resource : PackLoader.PACKS) {
			QuestionPack pack = QuestionPack.load(resource);
			assertThat(pack.category()).isNotBlank();
			Set<Integer> ids = new HashSet<>();
			Set<String> answersByTopic = new HashSet<>();
			for (QuestionPack.PackTopic topic : pack.topics()) {
				List<Questions> qs = QuestionPack.questions(topic);
				// the game manager makes automatic games for topics with 12 or more questions
				assertThat(qs).as(topic.name()).hasSizeGreaterThanOrEqualTo(20);
				for (int level = 1; level <= 3; level++) {
					int l = level;
					assertThat(qs.stream().filter(q -> q.getQuestionLevel() == l).count()).as(topic.name() + " level " + l)
							.isGreaterThanOrEqualTo(3);
				}
				for (Questions q : qs) {
					assertThatCode(() -> QuestionRules.validate(q)).as(q.getQuestionStem()).doesNotThrowAnyException();
					assertThat(q.getOption4()).as(q.getQuestionStem()).isNotBlank();
					assertThat(q.getExplanation()).as(q.getQuestionStem()).isNotBlank();
					assertThat(ids.add(q.getQuestionId())).as("duplicate id: " + q.getQuestionStem()).isTrue();
					assertThat(answersByTopic.add(topic.name() + "|" + q.getQuestionStem().toLowerCase()))
							.as("duplicate question").isTrue();
				}
			}
		}
	}

	@Test
	void idsAndOptionOrderAreStableSoReloadingUpdatesInsteadOfDuplicating() {
		QuestionPack.PackTopic topic = QuestionPack.load(PackLoader.PACKS.get(0)).topics().get(0);
		List<Questions> a = QuestionPack.questions(topic);
		List<Questions> b = QuestionPack.questions(topic);
		for (int i = 0; i < a.size(); i++) {
			assertThat(a.get(i).getQuestionId()).isEqualTo(b.get(i).getQuestionId())
					.isBetween(QuestionPack.PACK_ID_BASE, QuestionPack.PACK_ID_BASE + QuestionPack.PACK_ID_SPAN - 1);
			assertThat(a.get(i).getOption1()).isEqualTo(b.get(i).getOption1());
		}
		// the answer is not always in the same place
		assertThat(a.stream().map(q -> q.getOption4().equals(q.getCorrectAnswer())).distinct()).hasSize(2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void putsEachTopicInTheBankAndLeavesRetiredQuestionsOut() {
		QuestionBankService bank = mock(QuestionBankService.class);
		QuestionPack pack = QuestionPack.load(PackLoader.PACKS.get(0));
		int retiredId = QuestionPack.questions(pack.topics().get(0)).get(0).getQuestionId();

		new PackLoader(bank, id -> id == retiredId).load(PackLoader.PACKS);

		verify(bank, times(pack.topics().size())).importQuestions(eq(pack.category()), anyString(), anyList());
		ArgumentCaptor<List<Questions>> first = ArgumentCaptor.forClass(List.class);
		verify(bank).importQuestions(eq(pack.category()), eq(pack.topics().get(0).name()), first.capture());
		assertThat(first.getValue()).hasSize(pack.topics().get(0).questions().size() - 1)
				.noneMatch(q -> q.getQuestionId() == retiredId);
	}
}
