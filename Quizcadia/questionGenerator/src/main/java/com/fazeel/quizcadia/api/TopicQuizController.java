package com.fazeel.quizcadia.api;

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

import com.fazeel.quizcadia.ai.AiUnavailableException;
import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.topicquiz.TopicQuiz;
import com.fazeel.quizcadia.topicquiz.TopicQuizAttempt;
import com.fazeel.quizcadia.topicquiz.TopicQuizException;
import com.fazeel.quizcadia.topicquiz.TopicQuizService;

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

	/** The topic a player typed; the service accepts 2 to 60 characters. */
	public record CreateRequest(@NotNull @Size(max = 200) String topic) {
	}

	/** A question as a player sees it; answer is the right option's index, only after they played. */
	public record QuestionView(String text, List<String> options, Integer answer) {
	}

	/** A leaderboard row; you marks the player asking. */
	public record Entry(int rank, String name, int score, boolean you) {
	}

	/** {@code written} grows while the AI works ({@code status} GENERATING); questions appear once READY. */
	public record QuizView(String id, String topic, String creatorName, TopicQuiz.Status status, int written, int target,
			String problem, List<QuestionView> questions, List<Integer> yourAnswers, Integer yourScore, int players,
			List<Entry> leaderboard, Instant createdAt) {
	}

	/** The chosen option index per question, -1 where the timer ran out. */
	public record AnswerRequest(@NotNull @Size(max = 20) List<Integer> answers) {
	}

	/** Whether AI quizzes are switched on. */
	@GetMapping("/status")
	public Map<String, Boolean> status() {
		return Map.of("available", service.available());
	}

	/** Starts writing a quiz on the topic; the reply comes back at once with status GENERATING. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public QuizView create(@Valid @RequestBody CreateRequest r) {
		int me = Callers.currentUserId();
		return view(service.create(me, Callers.currentUserName(), r.topic()), me);
	}

	/** The quiz as the player asking sees it. */
	@GetMapping("/{id}")
	public QuizView get(@PathVariable String id) {
		return view(service.get(id), Callers.currentUserId());
	}

	/** Scores the player's one go and returns the quiz with the right answers. */
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
