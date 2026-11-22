package com.fazeel.quizcadia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.fazeel.quizcadia.bank.InvalidQuestionException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;

class DraftServiceTest {

	final QuestionBankService bank = mock(QuestionBankService.class);
	final DraftRepository repo = mock(DraftRepository.class);
	final Map<String, DraftQuestion> store = new HashMap<>();
	final Deque<Optional<GeneratedQuestion>> replies = new ArrayDeque<>();
	final List<String> prompts = new ArrayList<>();
	boolean modelAvailable = true;

	final QuestionModel model = new QuestionModel() {
		public boolean available() {
			return modelAvailable;
		}

		public Optional<GeneratedQuestion> write(String topic, String difficulty, String format) {
			prompts.add(topic + "|" + difficulty + "|" + format);
			return replies.isEmpty() ? Optional.empty() : replies.removeFirst();
		}
	};

	DraftService service;

	static GeneratedQuestion q(String stem, String answer, String... options) {
		return new GeneratedQuestion(stem, List.of(options), answer);
	}

	@BeforeEach
	void setUp() {
		service = new DraftService(model, repo, bank, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		Questions existing = new Questions();
		existing.setQuestionStem("What is the capital of France?");
		when(bank.questions(1, "Capitals")).thenReturn(List.of(existing));
		Category c = new Category();
		c.setCategoryName("Geography");
		when(bank.category(1)).thenReturn(c);
		when(repo.save(any())).thenAnswer(i -> {
			DraftQuestion d = i.getArgument(0);
			store.put(d.id(), d);
			return d;
		});
		when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get((String) i.getArgument(0))));
		when(repo.findByCategoryIdAndTopicNameIgnoreCase(1, "Capitals")).thenAnswer(i -> List.copyOf(store.values()));
	}

	@Test
	void keepsValidNewQuestionsAndSkipsInvalidOrDuplicateOnes() {
		replies.add(Optional.of(q("What is the capital of Peru?", "Lima", "Lima", "Quito", "Cusco", "La Paz")));
		replies.add(Optional.of(q("what is the CAPITAL of france", "Paris", "Paris", "Lyon", "Nice", "Lille"))); // in bank
		replies.add(Optional.of(q("Bad?", "Rome", "Paris", "Lyon", "Nice", "Lille"))); // answer not an option
		replies.add(Optional.empty()); // unparsable reply
		replies.add(Optional.of(q("What is the capital of Chile?", "Santiago", "Santiago", "Lima", "Quito", "Bogota")));

		var result = service.generate(1, "Capitals", 3, false, 2);

		assertThat(result.created()).isEqualTo(2);
		assertThat(result.duplicates()).isEqualTo(1);
		assertThat(result.invalidReplies()).isEqualTo(2);
		assertThat(result.drafts()).allMatch(d -> d.status() == DraftQuestion.Status.PENDING
				&& d.question().getQuestionLevel() == 3 && "ai".equals(d.question().getQuestionType()));
		assertThat(prompts).allMatch(p -> p.equals("Geography: Capitals|hard|multiple choice"));
	}

	@Test
	void repliesInTheWrongFormatAreSkipped() {
		replies.add(Optional.of(q("Is Canberra the capital of Australia?", "True", "True", "False"))); // asked for 4 options
		replies.add(Optional.of(q("Capital of Peru?", "Lima", "Lima", "Quito", "Cusco"))); // 3 options
		replies.add(Optional.of(q("What is the capital of Peru?", "Lima", "Lima", "Quito", "Cusco", "La Paz")));

		var result = service.generate(1, "Capitals", 1, false, 1);

		assertThat(result.created()).isEqualTo(1);
		assertThat(result.invalidReplies()).isEqualTo(2);
	}

	@Test
	void givesUpAfterThreeTriesPerQuestion() {
		var result = service.generate(1, "Capitals", 1, true, 2);

		assertThat(result.created()).isZero();
		assertThat(prompts).hasSize(6).allMatch(p -> p.endsWith("|easy|true/false"));
	}

	@Test
	void approvingAddsTheQuestionToTheTopicAndClosesTheDraft() {
		replies.add(Optional.of(q("What is the capital of Peru?", "Lima", "Lima", "Quito", "Cusco", "La Paz")));
		DraftQuestion d = service.generate(1, "Capitals", 2, false, 1).drafts().get(0);
		when(bank.addQuestion(eq(1), eq("Capitals"), any())).thenAnswer(i -> i.getArgument(2));

		service.approve(d.id(), null);

		verify(bank).addQuestion(eq(1), eq("Capitals"), ArgumentMatchers.argThat(
				x -> "ai".equals(x.getQuestionType()) && x.getQuestionLevel() == 2));
		assertThat(store.get(d.id()).status()).isEqualTo(DraftQuestion.Status.APPROVED);
		assertThatThrownBy(() -> service.reject(d.id())).isInstanceOf(InvalidQuestionException.class);
	}

	@Test
	void rejectingClosesTheDraft() {
		replies.add(Optional.of(q("What is the capital of Peru?", "Lima", "Lima", "Quito", "Cusco", "La Paz")));
		DraftQuestion d = service.generate(1, "Capitals", 1, false, 1).drafts().get(0);

		service.reject(d.id());

		assertThat(store.get(d.id()).status()).isEqualTo(DraftQuestion.Status.REJECTED);
	}

	@Test
	void refusesWithoutAModelOrWithBadInput() {
		assertThatThrownBy(() -> service.generate(1, "Capitals", 1, false, 50)).isInstanceOf(InvalidQuestionException.class);
		assertThatThrownBy(() -> service.generate(1, "Capitals", 4, false, 1)).isInstanceOf(InvalidQuestionException.class);
		modelAvailable = false;
		assertThatThrownBy(() -> service.generate(1, "Capitals", 1, false, 1)).isInstanceOf(AiUnavailableException.class);
	}
}
