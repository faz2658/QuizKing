package com.fazeel.quizcadia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionBankService.Located;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.feedback.QuestionFeedback.FlagReason;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackServiceTest {

	@Mock
	FeedbackRepository feedback;
	@Mock
	RetiredQuestionRepository retired;
	@Mock
	QuestionBankService bank;

	FeedbackService service;
	Map<String, QuestionFeedback> store = new HashMap<>();
	Questions question = new Questions();

	@BeforeEach
	void setUp() {
		service = new FeedbackService(feedback, retired, bank, Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC));
		question.setQuestionId(42);
		question.setQuestionStem("Capital of Peru?");
		when(bank.locate(42)).thenReturn(Optional.of(new Located(5, "Geography", question)));
		when(feedback.save(any(QuestionFeedback.class))).thenAnswer(i -> {
			QuestionFeedback f = i.getArgument(0);
			store.put(f.id(), f);
			return f;
		});
		when(feedback.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get((String) i.getArgument(0))));
		when(feedback.findByQuestionId(anyInt())).thenAnswer(i -> store.values().stream()
				.filter(f -> f.questionId() == (int) i.getArgument(0)).toList());
		when(feedback.findAll()).thenAnswer(i -> List.copyOf(store.values()));
	}

	@Test
	void votingAgainChangesTheVoteInsteadOfAddingOne() {
		service.give(1, true, 42, 1, null, null);
		service.give(1, true, 42, -1, null, null);

		assertThat(store).hasSize(1);
		assertThat(store.get("1:42").rating()).isEqualTo(-1);
	}

	@Test
	void threeAccountFlagsRetireTheQuestionButGuestsAloneNever() {
		for (int guest = 100; guest < 110; guest++) {
			assertThat(service.give(guest, false, 42, -1, FlagReason.WRONG_ANSWER, "it's Lima")).isFalse();
		}
		verify(bank, never()).deleteQuestion(anyInt(), anyString(), anyInt());

		service.give(1, true, 42, 0, FlagReason.WRONG_ANSWER, null);
		service.give(2, true, 42, 0, FlagReason.UNCLEAR, null);
		assertThat(service.give(3, true, 42, 0, FlagReason.TYPO, null)).isTrue();

		verify(bank).deleteQuestion(5, "Geography", 42);
		verify(retired).save(any(RetiredQuestion.class));
	}

	@Test
	void mostlyThumbsDownRetiresOnceThereAreEnoughVotes() {
		service.give(1, true, 42, -1, null, null);
		service.give(2, true, 42, -1, null, null);
		service.give(3, true, 42, -1, null, null);
		assertThat(service.give(4, true, 42, -1, null, null)).isFalse(); // only 4 votes
		assertThat(service.give(5, true, 42, -1, null, null)).isTrue();
	}

	@Test
	void aWellLikedQuestionStays() {
		for (int u = 1; u <= 4; u++) {
			service.give(u, true, 42, 1, null, null);
		}
		assertThat(service.give(5, true, 42, -1, null, null)).isFalse();
		verify(bank, never()).deleteQuestion(anyInt(), anyString(), anyInt());
	}

	@Test
	void theReviewListsComplaintsWithReasonsAndNotes() {
		service.give(1, false, 42, -1, FlagReason.WRONG_ANSWER, "  The answer is Lima  ");
		service.give(2, true, 42, 1, null, null);

		var review = service.review();
		assertThat(review.questions()).singleElement().satisfies(item -> {
			assertThat(item.question().getQuestionStem()).isEqualTo("Capital of Peru?");
			assertThat(item.flags()).isEqualTo(1);
			assertThat(item.ups()).isEqualTo(1);
			assertThat(item.downs()).isEqualTo(1);
			assertThat(item.reasons()).containsEntry(FlagReason.WRONG_ANSWER, 1L);
			assertThat(item.notes()).containsExactly("The answer is Lima");
		});
	}

	@Test
	void restoringPutsTheQuestionBackAndClearsItsFeedback() {
		when(retired.findById(42)).thenReturn(Optional.of(new RetiredQuestion(42, 5, "Geography", question, "flags", 0, 3, 3,
				Instant.EPOCH)));
		when(bank.addQuestion(eq(5), eq("Geography"), any())).thenReturn(question);

		service.restore(42);

		verify(bank).addQuestion(5, "Geography", question);
		verify(retired).deleteById(42);
		verify(feedback).deleteByQuestionId(42);
	}
}
