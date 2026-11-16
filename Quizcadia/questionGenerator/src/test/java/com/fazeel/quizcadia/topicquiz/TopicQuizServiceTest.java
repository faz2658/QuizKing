package com.fazeel.quizcadia.topicquiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizcadia.ai.AiUnavailableException;
import com.fazeel.quizcadia.ai.GeneratedQuestion;
import com.fazeel.quizcadia.ai.QuestionModel;
import com.fazeel.quizcadia.topicquiz.TopicQuiz.Status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicQuizServiceTest {

	@Mock
	TopicQuizRepository quizzes;
	@Mock
	TopicQuizAttemptRepository attempts;
	@Mock
	QuestionModel model;

	final Map<String, TopicQuiz> store = new HashMap<>();
	final Map<String, TopicQuizAttempt> played = new HashMap<>();
	final AtomicInteger n = new AtomicInteger();
	TopicQuizService service;

	@BeforeEach
	void setUp() {
		when(model.available()).thenReturn(true);
		when(quizzes.save(any())).thenAnswer(i -> {
			TopicQuiz q = i.getArgument(0);
			store.put(q.getId(), q);
			return q;
		});
		when(quizzes.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
		when(attempts.insert(any(TopicQuizAttempt.class))).thenAnswer(i -> {
			TopicQuizAttempt a = i.getArgument(0);
			played.put(a.id(), a);
			return a;
		});
		when(attempts.findById(anyString())).thenAnswer(i -> Optional.ofNullable(played.get(i.<String>getArgument(0))));
		// a model that writes a different, valid question every time
		when(model.write(anyString(), anyString(), eq("multiple choice"))).thenAnswer(i -> Optional.of(new GeneratedQuestion(
				"Question number " + n.incrementAndGet() + " about " + i.getArgument(0) + "?", List.of("Alpha", "Beta", "Gamma", "Delta"), "Gamma")));
		when(model.write(anyString(), anyString(), eq("true/false"))).thenAnswer(i -> Optional.of(new GeneratedQuestion(
				"Statement number " + n.incrementAndGet() + " is true?", List.of("True", "False"), "True")));
		service = new TopicQuizService(quizzes, attempts, model, Runnable::run,
				Clock.fixed(Instant.parse("2026-11-15T20:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void writesEightQuestionsInTheBackgroundAndScoresOneGoPerPlayer() {
		TopicQuiz quiz = service.create(7, "Ada", "  Toronto   Raptors ");

		TopicQuiz done = store.get(quiz.getId());
		assertThat(done.getTopic()).isEqualTo("Toronto Raptors");
		assertThat(done.getStatus()).isEqualTo(Status.READY);
		assertThat(done.getQuestions()).hasSize(TopicQuizService.QUESTIONS);
		assertThat(done.getQuestions().get(3).options()).containsExactly("True", "False");

		List<Integer> perfect = done.getQuestions().stream().map(TopicQuiz.Question::answer).toList();
		assertThat(service.answer(quiz.getId(), 9, "Bo", perfect).score()).isEqualTo(8);
		assertThatThrownBy(() -> service.answer(quiz.getId(), 9, "Bo", perfect)).hasMessageContaining("already");
		List<Integer> timedOut = perfect.stream().map(a -> -1).toList();
		assertThat(service.answer(quiz.getId(), 7, "Ada", timedOut).score()).isZero(); // the creator may play too
	}

	@Test
	void dropsBadRepliesAndFailsATopicTheModelCantHandle() {
		when(model.write(anyString(), anyString(), anyString()))
				.thenReturn(Optional.of(new GeneratedQuestion("Too few options here?", List.of("A", "B"), "A")));

		TopicQuiz quiz = service.create(7, "Ada", "Obscure stuff");

		assertThat(store.get(quiz.getId()).getStatus()).isEqualTo(Status.FAILED);
		assertThat(store.get(quiz.getId()).getProblem()).contains("broader");
	}

	@Test
	void anOfflineModelEndsTheQuizWithAClearMessage() {
		when(model.write(anyString(), anyString(), anyString())).thenThrow(new AiUnavailableException("down"));

		TopicQuiz quiz = service.create(7, "Ada", "Space");

		assertThat(store.get(quiz.getId()).getProblem()).contains("offline");
	}

	@Test
	void shufflesOptionsAndKeepsTheRightAnswer() {
		for (int seed = 0; seed < 20; seed++) {
			var q = TopicQuizService.clean(new GeneratedQuestion("Which planet is red?", List.of("Mars", "Venus", "Earth", "Jupiter"), "mars"),
					"multiple choice", new Random(seed)).orElseThrow();
			assertThat(q.options().get(q.answer())).isEqualTo("Mars");
		}
		assertThat(TopicQuizService.clean(new GeneratedQuestion("Which planet is red?", List.of("Mars", "Mars", "Earth", "Jupiter"), "Mars"),
				"multiple choice", new Random(1))).isEmpty();
		assertThat(TopicQuizService.clean(new GeneratedQuestion("Which planet is red?", List.of("Mars", "Venus", "Earth", "Jupiter"), "Pluto"),
				"multiple choice", new Random(1))).isEmpty();
	}

	@Test
	void limitsTopicsDailyQuotaAndQueue() {
		assertThatThrownBy(() -> service.create(7, "Ada", "x")).hasMessageContaining("2 to 60");
		assertThatThrownBy(() -> service.create(7, "Ada", "NSFW memes")).hasMessageContaining("another topic");

		when(quizzes.countByCreatorIdAndCreatedAtAfter(anyInt(), any())).thenReturn((long) TopicQuizService.PER_PLAYER_PER_DAY);
		assertThatThrownBy(() -> service.create(7, "Ada", "Space")).hasMessageContaining("tomorrow");

		when(quizzes.countByCreatorIdAndCreatedAtAfter(anyInt(), any())).thenReturn(0L);
		when(quizzes.countByStatus(Status.GENERATING)).thenReturn((long) TopicQuizService.MAX_IN_QUEUE);
		assertThatThrownBy(() -> service.create(7, "Ada", "Space")).isInstanceOf(TopicQuizException.class)
				.satisfies(e -> assertThat(((TopicQuizException) e).isBusy()).isTrue());

		when(model.available()).thenReturn(false);
		assertThatThrownBy(() -> service.create(7, "Ada", "Space")).isInstanceOf(AiUnavailableException.class);
	}
}
