package com.fazeel.quizking.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizking.ai.AiUnavailableException;
import com.fazeel.quizking.auth.Callers;
import com.fazeel.quizking.topicquiz.TopicQuiz;
import com.fazeel.quizking.topicquiz.TopicQuizAttempt;
import com.fazeel.quizking.topicquiz.TopicQuizException;
import com.fazeel.quizking.topicquiz.TopicQuizService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AI quizzes on any topic, for every player. Right answers are only shown to someone who has played. */
@RestController
@RequestMapping("/api/v1/question-generator/topic-quizzes")
public class TopicQuizController {

	private final TopicQuizService service;

	public TopicQuizController(TopicQuizService service) {
		this.service = service;
	}

	public record CreateRequest(@NotNull @Size(max = 200) String topic) {
	}

	public record QuestionView(String text, List<String> options, Integer answer) {
	}

	public record Entry(int rank, String name, int score, boolean you) {
	}

	/** {@code written} grows while the AI works ({@code status} GENERATING); questions appear once READY. */
	public record QuizView(String id, String topic, String creatorName, TopicQuiz.Status status, int written, int target,
			String problem, List<QuestionView> questions, List<Integer> yourAnswers, Integer yourScore, int players,
			List<Entry> leaderboard, Instant createdAt) {
	}

	public record AnswerRequest(@NotNull @Size(max = 20) List<Integer> answers) {
	}

	@GetMapping("/status")
	public Map<String, Boolean> status() {
		return Map.of("available", service.available());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public QuizView create(@Valid @RequestBody CreateRequest r) {
		int me = Callers.currentUserId();
		return view(service.create(me, Callers.currentUserName(), r.topic()), me);
	}

	@GetMapping("/{id}")
	public QuizView get(@PathVariable String id) {
		return view(service.get(id), Callers.currentUserId());
	}

	@PostMapping("/{id}/answers")
	public QuizView answer(@PathVariable String id, @Valid @RequestBody AnswerRequest r) {
		int me = Callers.currentUserId();
		service.answer(id, me, Callers.currentUserName(), r.answers());
		return view(service.get(id), me);
	}

	private QuizView view(TopicQuiz q, int me) {
		Optional<TopicQuizAttempt> mine = service.attempt(q.getId(), me);
		boolean ready = q.getStatus() == TopicQuiz.Status.READY;
		List<QuestionView> questions = ready ? q.getQuestions().stream()
				.map(x -> new QuestionView(x.text(), x.options(), mine.isPresent() ? x.answer() : null)).toList() : List.of();
		List<TopicQuizAttempt> board = service.leaderboard(q.getId());
		List<Entry> top = new ArrayList<>();
		int rank = 0;
		int last = -1;
		for (int i = 0; i < board.size(); i++) {
			TopicQuizAttempt a = board.get(i);
			if (a.score() != last) {
				rank = i + 1;
				last = a.score();
			}
			if (i < 50 || a.userId() == me) {
				top.add(new Entry(rank, a.name(), a.score(), a.userId() == me));
			}
		}
		return new QuizView(q.getId(), q.getTopic(), q.getCreatorName(), q.getStatus(), q.getQuestions().size(),
				q.getTarget(), q.getProblem(), questions, mine.map(TopicQuizAttempt::answers).orElse(null),
				mine.map(TopicQuizAttempt::score).orElse(null), board.size(), top, q.getCreatedAt());
	}

	@ExceptionHandler(TopicQuizException.class)
	ProblemDetail rule(TopicQuizException e) {
		HttpStatus status = e.isNotFound() ? HttpStatus.NOT_FOUND
				: e.isBusy() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST;
		return ProblemDetail.forStatusAndDetail(status, e.getMessage());
	}

	@ExceptionHandler(AiUnavailableException.class)
	ProblemDetail offline(AiUnavailableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
	}
}
