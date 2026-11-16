package com.fazeel.quizcadia.knowme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.fazeel.quizcadia.knowme.KnowMeQuiz.Question;
import com.fazeel.quizcadia.session.QuizRuleException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowMeServiceTest {

	@Mock
	KnowMeQuizRepository quizzes;
	@Mock
	KnowMeAttemptRepository attempts;

	final Map<String, KnowMeQuiz> savedQuizzes = new HashMap<>();
	final Map<String, KnowMeAttempt> savedAttempts = new HashMap<>();
	KnowMeService service;

	static List<Question> three() {
		return List.of(new Question(" Favourite food? ", List.of("Pizza", " Sushi "), 1),
				new Question("First concert?", List.of("Coldplay", "Drake", "None yet"), 2),
				new Question("Cats or dogs?", List.of("Cats", "Dogs"), 0));
	}

	@BeforeEach
	void setUp() {
		when(quizzes.save(any())).thenAnswer(i -> {
			KnowMeQuiz q = i.getArgument(0);
			savedQuizzes.put(q.id(), q);
			return q;
		});
		when(quizzes.findById(anyString())).thenAnswer(i -> Optional.ofNullable(savedQuizzes.get(i.<String>getArgument(0))));
		when(quizzes.countByOwnerId(anyInt())).thenReturn(0L);
		when(attempts.insert(any(KnowMeAttempt.class))).thenAnswer(i -> {
			KnowMeAttempt a = i.getArgument(0);
			savedAttempts.put(a.id(), a);
			return a;
		});
		when(attempts.findById(anyString())).thenAnswer(i -> Optional.ofNullable(savedAttempts.get(i.<String>getArgument(0))));
		service = new KnowMeService(quizzes, attempts, Clock.fixed(Instant.parse("2026-11-15T20:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void createsATidyQuizWithAShortLinkId() {
		KnowMeQuiz quiz = service.create(7, "Ada", three());

		assertThat(quiz.id()).matches("[a-z2-9]{8}");
		assertThat(quiz.questions().get(0).text()).isEqualTo("Favourite food?");
		assertThat(quiz.questions().get(0).options()).containsExactly("Pizza", "Sushi");
	}

	@Test
	void scoresAFriendOnceAndNeverTheOwner() {
		KnowMeQuiz quiz = service.create(7, "Ada", three());

		KnowMeAttempt a = service.answer(quiz.id(), 9, "Bo", List.of(1, 0, 0));
		assertThat(a.score()).isEqualTo(2);
		assertThatThrownBy(() -> service.answer(quiz.id(), 9, "Bo", List.of(1, 2, 0)))
				.isInstanceOf(QuizRuleException.class).hasMessageContaining("already");
		assertThatThrownBy(() -> service.answer(quiz.id(), 7, "Ada", List.of(1, 2, 0)))
				.isInstanceOf(QuizRuleException.class).hasMessageContaining("friends");
	}

	@Test
	void rejectsIncompleteOrOutOfRangeAnswers() {
		KnowMeQuiz quiz = service.create(7, "Ada", three());

		assertThatThrownBy(() -> service.answer(quiz.id(), 9, "Bo", List.of(1, 0))).hasMessageContaining("every question");
		assertThatThrownBy(() -> service.answer(quiz.id(), 9, "Bo", List.of(1, 3, 0))).hasMessageContaining("question 2");
	}

	@Test
	void validatesWhatTheOwnerWrites() {
		assertThatThrownBy(() -> service.create(7, "Ada", three().subList(0, 2))).hasMessageContaining("between 3 and 15");
		assertThatThrownBy(() -> service.create(7, "Ada", List.of(three().get(0), three().get(1),
				new Question("Tea?", List.of("Yes", " yes "), 0)))).hasMessageContaining("same option twice");
		assertThatThrownBy(() -> service.create(7, "Ada", List.of(three().get(0), three().get(1),
				new Question("Tea?", List.of("Yes", "No"), 2)))).hasMessageContaining("right answer");
		assertThatThrownBy(() -> service.create(7, "Ada", List.of(three().get(0), three().get(1),
				new Question("Tea?", List.of("Yes"), 0)))).hasMessageContaining("2 to 4 options");

		when(quizzes.countByOwnerId(7)).thenReturn((long) KnowMeService.MAX_QUIZZES_PER_PLAYER);
		assertThatThrownBy(() -> service.create(7, "Ada", three())).hasMessageContaining("plenty");
	}
}
